package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.config.properties.AuthProperties;
import com.geoprobe.entity.PasswordResetToken;
import com.geoprobe.entity.User;
import com.geoprobe.mapper.PasswordResetTokenMapper;
import com.geoprobe.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private final UserMapper userMapper;
    private final PasswordResetTokenMapper tokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;
    private final AuthEmailSender authEmailSender;
    private final PasswordAuditService passwordAuditService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(rollbackFor = Exception.class)
    public void requestReset(String username, String clientIp) {
        User user = findByUsername(username);
        if (user == null || Integer.valueOf(0).equals(user.getEnabled())) {
            return;
        }

        String token = generateToken();
        LocalDateTime now = LocalDateTime.now();
        invalidateUnusedTokens(user.getId(), now);
        PasswordResetToken record = PasswordResetToken.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .tokenHash(hashToken(token))
                .expiresAt(now.plusMinutes(Math.max(1, authProperties.getPasswordReset().getTokenTtlMinutes())))
                .requestedIp(clientIp)
                .createdAt(now)
                .build();
        tokenMapper.insert(record);
        authEmailSender.sendPasswordResetEmail(user.getUsername(), buildResetUrl(token));
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirmReset(String token, String newPassword) {
        PasswordResetToken record = findUsableToken(token);
        if (record == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Password reset link is invalid or expired");
        }

        User user = userMapper.selectById(record.getUserId());
        if (user == null || Integer.valueOf(0).equals(user.getEnabled())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Password reset link is invalid or expired");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!consumeToken(record, now)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Password reset link is invalid or expired");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        invalidateUnusedTokens(user.getId(), now);
        passwordAuditService.recordUserPasswordWrite(user.getId(), user.getUsername(), "USER_PASSWORD_RESET", "PASSWORD_RESET_EMAIL");
    }

    private PasswordResetToken findUsableToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return tokenMapper.selectOne(new LambdaQueryWrapper<PasswordResetToken>()
                .eq(PasswordResetToken::getTokenHash, hashToken(token))
                .isNull(PasswordResetToken::getUsedAt)
                .gt(PasswordResetToken::getExpiresAt, LocalDateTime.now())
                .last("LIMIT 1"));
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
    }

    private void invalidateUnusedTokens(String userId, LocalDateTime usedAt) {
        PasswordResetToken update = new PasswordResetToken();
        update.setUsedAt(usedAt);
        tokenMapper.update(update, new LambdaQueryWrapper<PasswordResetToken>()
                .eq(PasswordResetToken::getUserId, userId)
                .isNull(PasswordResetToken::getUsedAt));
    }

    private boolean consumeToken(PasswordResetToken record, LocalDateTime usedAt) {
        PasswordResetToken update = new PasswordResetToken();
        update.setUsedAt(usedAt);
        return tokenMapper.update(update, new LambdaQueryWrapper<PasswordResetToken>()
                .eq(PasswordResetToken::getId, record.getId())
                .isNull(PasswordResetToken::getUsedAt)
                .gt(PasswordResetToken::getExpiresAt, LocalDateTime.now())) == 1;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Password reset is temporarily unavailable");
        }
    }

    private String buildResetUrl(String token) {
        String base = authProperties.getEmail().getResetUrlBase();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "intent=reset-password&token=" + token;
    }
}
