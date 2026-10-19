package com.geoprobe.service;

import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.common.JwtUtil;
import com.geoprobe.config.properties.AuthProperties;
import com.geoprobe.dto.request.LoginRequest;
import com.geoprobe.dto.request.RegisterRequest;
import com.geoprobe.dto.response.LoginResponse;
import com.geoprobe.entity.User;
import com.geoprobe.mapper.UserMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthProperties authProperties;
    @Mock
    private UserTenancyBootstrapService userTenancyBootstrapService;
    @Mock
    private PasswordAuditService passwordAuditService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerShouldCreateUserAndReturnToken() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("123456");
        request.setDisplayName("Alice");
        request.setCompanyName("GeoProbe");

        when(authProperties.isRegistrationEnabled()).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$hash-123456");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId("user-1");
            Assertions.assertEquals("$2a$hash-123456", user.getPassword());
            return 1;
        });
        when(jwtUtil.generateToken("user-1", "alice")).thenReturn("jwt-token");

        LoginResponse response = authService.register(request);

        assertEquals("user-1", response.getUserId());
        assertEquals("alice", response.getUsername());
        assertEquals("jwt-token", response.getToken());
        assertEquals(0, response.getRole());
        verify(userTenancyBootstrapService).ensureUserTenancy("user-1");
        verify(passwordAuditService).recordUserPasswordWrite(
                "user-1",
                "alice",
                "USER_PASSWORD_REGISTERED",
                "REGISTER"
        );
    }

    @Test
    void registerShouldFailWhenUsernameExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("123456");

        when(authProperties.isRegistrationEnabled()).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(User.builder().id("exists").username("alice").build());

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(ErrorCode.USERNAME_EXISTS, exception.getErrorCode());
    }

    @Test
    void registerShouldFailWhenRegistrationDisabled() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("123456");

        when(authProperties.isRegistrationEnabled()).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    void loginShouldReturnTokenWhenPasswordMatches() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("123456");

        User user = User.builder()
                .id("user-1")
                .username("alice")
                .password("$2a$hashed-password")
                .role(1)
                .build();

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("123456", "$2a$hashed-password")).thenReturn(true);
        when(jwtUtil.generateToken("user-1", "alice")).thenReturn("jwt-token");

        LoginResponse response = authService.login(request);

        assertEquals("user-1", response.getUserId());
        assertEquals("jwt-token", response.getToken());
        assertEquals(1, response.getRole());
    }

    @Test
    void loginShouldFailWhenPasswordNotMatch() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong");

        User user = User.builder()
                .id("user-1")
                .username("alice")
                .password("$2a$hashed-password")
                .build();

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrong", "$2a$hashed-password")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
    }

    @Test
    void loginShouldMigrateLegacyPlainPasswordToBcrypt() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("123456");

        User user = User.builder()
                .id("user-1")
                .username("alice")
                .password("123456")
                .build();

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$migrated-hash");
        when(jwtUtil.generateToken("user-1", "alice")).thenReturn("jwt-token");

        LoginResponse response = authService.login(request);

        assertEquals("jwt-token", response.getToken());
        assertEquals("$2a$migrated-hash", user.getPassword());
        verify(userMapper).updateById(user);
        verify(userTenancyBootstrapService).ensureUserTenancy("user-1");
        verify(passwordAuditService).recordUserPasswordWrite(
                "user-1",
                "alice",
                "USER_PASSWORD_LEGACY_MIGRATED",
                "LEGACY_PLAINTEXT_LOGIN_MIGRATION"
        );
    }
}
