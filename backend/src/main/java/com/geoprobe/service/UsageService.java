package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.dto.brandspace.UsageDailyView;
import com.geoprobe.dto.response.CreditBalanceResponse;
import com.geoprobe.entity.CreditTransaction;
import com.geoprobe.entity.v1.OrganizationMembership;
import com.geoprobe.mapper.CreditTransactionMapper;
import com.geoprobe.mapper.v1.OrganizationMembershipMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class UsageService {

    private static final int CREDIT_TYPE_CONSUME = 2;
    private static final int MEMBERSHIP_STATUS_ACTIVE = 1;
    private static final ZoneId USAGE_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ServiceAccessService serviceAccessService;
    private final CreditService creditService;
    private final CreditTransactionMapper creditTransactionMapper;
    private final OrganizationMembershipMapper organizationMembershipMapper;

    public UsageDailyView getDailyUsage(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        LocalDate today = LocalDate.now(USAGE_ZONE_ID);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        List<String> usageUserIds = resolveUsageUserIds(userId);
        List<CreditTransaction> consumedTransactions = creditTransactionMapper.selectList(new LambdaQueryWrapper<CreditTransaction>()
                .in(CreditTransaction::getUserId, usageUserIds)
                .eq(CreditTransaction::getType, CREDIT_TYPE_CONSUME)
                .ge(CreditTransaction::getCreatedAt, start)
                .lt(CreditTransaction::getCreatedAt, end));

        int consumedCredits = consumedTransactions.stream()
                .map(CreditTransaction::getAmount)
                .filter(amount -> amount != null)
                .mapToInt(amount -> Math.abs(amount))
                .sum();
        CreditBalanceResponse balance = creditService.getBalance(userId);
        int creditsRemaining = balance == null || balance.getCreditsRemaining() == null
                ? 0
                : Math.max(0, balance.getCreditsRemaining());

        return UsageDailyView.builder()
                .date(DATE_FORMATTER.format(today))
                .consumedCredits(consumedCredits)
                .creditsRemaining(creditsRemaining)
                .build();
    }

    private List<String> resolveUsageUserIds(String userId) {
        String normalizedUserId = userId.trim();
        String organizationId = serviceAccessService.resolveOrganizationId(normalizedUserId);
        if (!StringUtils.hasText(organizationId)) {
            return List.of(normalizedUserId);
        }

        List<String> memberUserIds = organizationMembershipMapper.selectList(new LambdaQueryWrapper<OrganizationMembership>()
                        .eq(OrganizationMembership::getOrganizationId, organizationId)
                        .eq(OrganizationMembership::getStatusCode, MEMBERSHIP_STATUS_ACTIVE))
                .stream()
                .map(OrganizationMembership::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        if (memberUserIds.isEmpty()) {
            return List.of(normalizedUserId);
        }

        return Stream.concat(memberUserIds.stream(), Stream.of(normalizedUserId))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }
}
