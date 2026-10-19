package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.common.JwtUtil;
import com.geoprobe.config.properties.AuthProperties;
import com.geoprobe.dto.request.LoginRequest;
import com.geoprobe.dto.request.RegisterRequest;
import com.geoprobe.dto.response.AuthLookupResponse;
import com.geoprobe.dto.response.AuthSessionResponse;
import com.geoprobe.dto.response.LoginResponse;
import com.geoprobe.entity.User;
import com.geoprobe.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;
    private final UserTenancyBootstrapService userTenancyBootstrapService;
    private final PasswordAuditService passwordAuditService;

    public AuthLookupResponse lookupAccount(String username) {
        boolean registered = findByUsername(username) != null;
        return AuthLookupResponse.builder()
                .registered(registered)
                .nextAction(registered ? "login" : "register")
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request) {
        if (!authProperties.isRegistrationEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前环境已关闭公开注册，请使用预置测试账号登录");
        }

        User existingUser = findByUsername(request.getUsername());
        if (existingUser != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName())
                .companyName(request.getCompanyName())
                .creditsRemaining(0)
                .role(0)
                .enabled(1)
                .build();

        int insertedRows = userMapper.insert(user);
        if (insertedRows != 1 || user.getId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建用户失败");
        }
        userTenancyBootstrapService.ensureUserTenancy(user.getId());
        recordPasswordAudit(user, "USER_PASSWORD_REGISTERED", "REGISTER");

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        User user = findByUsername(request.getUsername());
        if (user == null || !isPasswordValid(user, request.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getEnabled() != null && user.getEnabled() == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已禁用");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }

    public AuthSessionResponse buildSessionResponse(String userId) {
        User user = findActiveUserById(userId);
        if (user == null) {
            return null;
        }
        return AuthSessionResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }

    private User findByUsername(String username) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1");
        return userMapper.selectOne(queryWrapper);
    }

    private User findActiveUserById(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user == null || Integer.valueOf(0).equals(user.getEnabled())) {
            return null;
        }
        return user;
    }

    /**
     * 兼容历史明文密码数据：登录命中后自动迁移为加密存储。
     */
    private boolean isPasswordValid(User user, String rawPassword) {
        String storedPassword = user.getPassword();
        if (isBcryptHash(storedPassword)) {
            try {
                return passwordEncoder.matches(rawPassword, storedPassword);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }

        if (!Objects.equals(storedPassword, rawPassword)) {
            return false;
        }

        user.setPassword(passwordEncoder.encode(rawPassword));
        userMapper.updateById(user);
        userTenancyBootstrapService.ensureUserTenancy(user.getId());
        recordPasswordAudit(user, "USER_PASSWORD_LEGACY_MIGRATED", "LEGACY_PLAINTEXT_LOGIN_MIGRATION");
        return true;
    }

    private boolean isBcryptHash(String value) {
        return value != null && value.startsWith("$2");
    }

    private void recordPasswordAudit(User user, String auditTypeCode, String source) {
        passwordAuditService.recordUserPasswordWrite(user.getId(), user.getUsername(), auditTypeCode, source);
    }
}
