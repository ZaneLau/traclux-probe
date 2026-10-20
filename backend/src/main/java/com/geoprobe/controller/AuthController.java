package com.geoprobe.controller;

import com.geoprobe.common.Result;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.dto.request.AuthLookupRequest;
import com.geoprobe.dto.request.LoginRequest;
import com.geoprobe.dto.request.PasswordResetConfirmRequest;
import com.geoprobe.dto.request.PasswordResetRequest;
import com.geoprobe.dto.request.RegisterRequest;
import com.geoprobe.dto.response.AuthLookupResponse;
import com.geoprobe.dto.response.AuthSessionResponse;
import com.geoprobe.dto.response.LoginResponse;
import com.geoprobe.filter.JwtAuthFilter;
import com.geoprobe.service.AuthService;
import com.geoprobe.service.AuthRateLimitService;
import com.geoprobe.service.PasswordResetService;
import com.geoprobe.service.TurnstileVerifier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimitService authRateLimitService;
    private final PasswordResetService passwordResetService;
    private final TurnstileVerifier turnstileVerifier;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    @PostMapping("/lookup")
    public Result<AuthLookupResponse> lookup(@Valid @RequestBody AuthLookupRequest request, HttpServletRequest httpRequest) {
        authRateLimitService.checkLookupAllowed(request.getUsername(), resolveClientIp(httpRequest));
        return Result.ok(authService.lookupAccount(request.getUsername()));
    }

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request,
                                          HttpServletRequest httpRequest,
                                          HttpServletResponse httpResponse) {
        String clientIp = resolveClientIp(httpRequest);
        authRateLimitService.checkRegisterAllowed(request.getUsername(), clientIp);
        turnstileVerifier.verify(request.getTurnstileToken(), clientIp);
        LoginResponse loginResponse = authService.register(request);
        writeAuthCookie(httpRequest, httpResponse, loginResponse.getToken());
        return Result.ok(loginResponse);
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        authRateLimitService.checkLoginAllowed(request.getUsername(), resolveClientIp(httpRequest));
        LoginResponse loginResponse = authService.login(request);
        writeAuthCookie(httpRequest, httpResponse, loginResponse.getToken());
        return Result.ok(loginResponse);
    }

    @GetMapping("/session")
    public ResponseEntity<Result<AuthSessionResponse>> getSession(Authentication authentication) {
        String userId = authentication == null ? null : String.valueOf(authentication.getPrincipal());
        AuthSessionResponse session = authService.buildSessionResponse(userId);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.error(ErrorCode.UNAUTHORIZED));
        }
        return ResponseEntity.ok(Result.ok(session));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        clearAuthCookie(httpRequest, httpResponse);
        return Result.ok();
    }

    @PostMapping("/password-reset/request")
    public Result<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                             HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);
        authRateLimitService.checkPasswordResetAllowed(request.getUsername(), clientIp);
        turnstileVerifier.verify(request.getTurnstileToken(), clientIp);
        passwordResetService.requestReset(request.getUsername(), clientIp);
        return Result.ok();
    }

    @PostMapping("/password-reset/confirm")
    public Result<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.getToken(), request.getPassword());
        return Result.ok();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void writeAuthCookie(HttpServletRequest request, HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildAuthCookie(request, token, jwtExpirationMs).toString());
    }

    private void clearAuthCookie(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildAuthCookie(request, "", 0).toString());
    }

    private ResponseCookie buildAuthCookie(HttpServletRequest request, String token, long maxAgeSeconds) {
        boolean secure = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        return ResponseCookie.from(JwtAuthFilter.AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds / 1000)
                .build();
    }
}
