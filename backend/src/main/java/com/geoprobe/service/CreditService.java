package com.geoprobe.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.dto.response.CreditBalanceResponse;
import com.geoprobe.dto.response.PurchaseCreditsResponse;
import com.geoprobe.entity.CreditTransaction;
import com.geoprobe.entity.User;
import com.geoprobe.mapper.CreditTransactionMapper;
import com.geoprobe.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private static final int CREDIT_TYPE_PURCHASE = 1;
    private static final int CREDIT_TYPE_CONSUME = 2;
    private static final int CREDIT_TYPE_REFUND = 3;
    private static final int CREDIT_TYPE_GRANT = 4;

    private final UserMapper userMapper;
    private final CreditTransactionMapper creditTransactionMapper;
    @Autowired(required = false)
    private LegacyCreditMirrorService legacyCreditMirrorService;

    public CreditBalanceResponse getBalance(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return CreditBalanceResponse.builder()
                    .creditsRemaining(0)
                    .totalPurchased(0)
                    .totalConsumed(0)
                    .build();
        }

        List<CreditTransaction> transactions = creditTransactionMapper.selectList(
                Wrappers.<CreditTransaction>lambdaQuery()
                        .eq(CreditTransaction::getUserId, userId)
        );

        int totalPurchased = transactions.stream()
                .filter(tx -> intEquals(tx.getType(), CREDIT_TYPE_PURCHASE))
                .map(CreditTransaction::getAmount)
                .filter(amount -> amount != null && amount > 0)
                .mapToInt(Integer::intValue)
                .sum();

        int totalConsumed = transactions.stream()
                .filter(tx -> intEquals(tx.getType(), CREDIT_TYPE_CONSUME))
                .map(CreditTransaction::getAmount)
                .filter(amount -> amount != null)
                .mapToInt(amount -> Math.abs(amount))
                .sum();

        return CreditBalanceResponse.builder()
                .creditsRemaining(defaultZero(user.getCreditsRemaining()))
                .totalPurchased(totalPurchased)
                .totalConsumed(totalConsumed)
                .build();
    }

    public boolean hasEnoughCredits(String userId, int required) {
        if (!StringUtils.hasText(userId) || required <= 0) {
            return false;
        }
        User user = userMapper.selectById(userId);
        return user != null && defaultZero(user.getCreditsRemaining()) >= required;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseCreditsResponse purchaseCredits(String userId, int amount, String paymentMethod) {
        String normalizedPaymentMethod = StringUtils.trimWhitespace(paymentMethod);
        if (!StringUtils.hasText(userId) || amount <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "购买额度必须大于0");
        }
        if (!"MANUAL".equalsIgnoreCase(normalizedPaymentMethod)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "MVP 阶段仅支持 MANUAL 充值");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户不存在");
        }

        int updatedRows = userMapper.addCredits(userId, amount);
        if (updatedRows != 1) {
            throw new IllegalStateException("Purchase credits failed");
        }

        CreditTransaction transaction = CreditTransaction.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .type(CREDIT_TYPE_PURCHASE)
                .amount(amount)
                .orderId(buildManualPurchaseOrderId(userId))
                .build();
        creditTransactionMapper.insert(transaction);
        mirrorCreditTransaction(userId, transaction);

        return PurchaseCreditsResponse.builder()
                .transactionId(transaction.getId())
                .creditsRemaining(defaultZero(user.getCreditsRemaining()) + amount)
                .amount(amount)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deductCredits(String userId, String diagnosisId, int amount) {
        if (!StringUtils.hasText(userId) || amount <= 0) {
            return false;
        }

        if (StringUtils.hasText(diagnosisId) && hasDiagnosisTransaction(userId, diagnosisId, CREDIT_TYPE_CONSUME)) {
            return true;
        }

        int updatedRows = userMapper.deductCreditsIfEnough(userId, amount);
        if (updatedRows != 1) {
            return false;
        }

        CreditTransaction transaction = CreditTransaction.builder()
                .userId(userId)
                .type(CREDIT_TYPE_CONSUME)
                .amount(-amount)
                .diagnosisId(diagnosisId)
                .build();
        try {
            creditTransactionMapper.insert(transaction);
            mirrorCreditTransaction(userId, transaction);
            return true;
        } catch (DuplicateKeyException duplicateKeyException) {
            // Already deducted for this diagnosis; rollback this extra decrement.
            userMapper.addCredits(userId, amount);
            return true;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void refundCredits(String userId, String diagnosisId, int amount) {
        if (!StringUtils.hasText(userId) || amount <= 0) {
            return;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        if (StringUtils.hasText(diagnosisId) && hasDiagnosisTransaction(userId, diagnosisId, CREDIT_TYPE_REFUND)) {
            return;
        }
        if (StringUtils.hasText(diagnosisId) && !hasDiagnosisTransaction(userId, diagnosisId, CREDIT_TYPE_CONSUME)) {
            return;
        }
        CreditTransaction transaction = CreditTransaction.builder()
                .userId(userId)
                .type(CREDIT_TYPE_REFUND)
                .amount(amount)
                .diagnosisId(diagnosisId)
                .build();
        try {
            creditTransactionMapper.insert(transaction);
        } catch (DuplicateKeyException duplicateKeyException) {
            return;
        }

        int updatedRows = userMapper.addCredits(userId, amount);
        if (updatedRows != 1) {
            log.error("Refund failed to add credits, userId={}, diagnosisId={}", userId, diagnosisId);
            throw new IllegalStateException("Refund credits failed");
        }
        mirrorCreditTransaction(userId, transaction);
    }

    @Transactional(rollbackFor = Exception.class)
    public CreditTransaction grantCredits(String userId, int amount, String reason) {
        if (!StringUtils.hasText(userId) || amount <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "赠送额度必须大于0");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户不存在");
        }
        int updatedRows = userMapper.addCredits(userId, amount);
        if (updatedRows != 1) {
            throw new IllegalStateException("Grant credits failed");
        }
        CreditTransaction transaction = CreditTransaction.builder()
                .userId(userId)
                .type(CREDIT_TYPE_GRANT)
                .amount(amount)
                .orderId(encodeGrantReason(reason))
                .build();
        creditTransactionMapper.insert(transaction);
        mirrorCreditTransaction(userId, transaction);
        return transaction;
    }

    private void mirrorCreditTransaction(String userId, CreditTransaction transaction) {
        if (legacyCreditMirrorService == null) {
            return;
        }
        if (!legacyCreditMirrorService.shouldWriteLegacyCredit()) {
            return;
        }
        legacyCreditMirrorService.mirrorCreditTransaction(userId, transaction);
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean intEquals(Integer value, int expected) {
        return value != null && value == expected;
    }

    private boolean hasDiagnosisTransaction(String userId, String diagnosisId, int type) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(diagnosisId)) {
            return false;
        }
        Long count = creditTransactionMapper.selectCount(
                Wrappers.<CreditTransaction>lambdaQuery()
                        .eq(CreditTransaction::getUserId, userId)
                        .eq(CreditTransaction::getDiagnosisId, diagnosisId)
                        .eq(CreditTransaction::getType, type)
                        .last("limit 1")
        );
        return count != null && count > 0L;
    }

    private String encodeGrantReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "GRANT";
        }
        String normalized = "GRANT:" + reason.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private String buildManualPurchaseOrderId(String userId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        String orderId = "MANUAL:" + userId + ":" + suffix;
        return orderId.length() <= 100 ? orderId : orderId.substring(0, 100);
    }
}
