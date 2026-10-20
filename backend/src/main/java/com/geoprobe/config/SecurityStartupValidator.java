package com.geoprobe.config;

import com.geoprobe.config.properties.AuthProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityStartupValidator {

    private static final String JWT_SECRET_DEFAULT_PLACEHOLDER = "replace-with-256-bit-secret";
    private static final int JWT_SECRET_MIN_LENGTH = 32;

    private final Environment environment;
    private final AuthProperties authProperties;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${app.security.enforce-jwt-secret:false}")
    private boolean enforceJwtSecret;

    @Value("${app.security.expose-swagger:false}")
    private boolean exposeSwagger;

    @Value("${app.security.expose-dev-endpoints:false}")
    private boolean exposeDevEndpoints;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${spring.mail.host:}")
    private String smtpHost;

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private boolean smtpAuth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
    private boolean smtpStartTlsEnabled;

    @PostConstruct
    public void validateJwtSecret() {
        if (isJwtSecretValidationRequired() && !isSecureJwtSecret(jwtSecret)) {
            throw new IllegalStateException("Unsafe JWT secret for current environment. Please set a strong JWT_SECRET (>=32 chars) and avoid default placeholder.");
        }
        if (isProductionProfile()) {
            validateProdSecurityToggles();
            validateProdAuthConfig();
        }
    }

    boolean isJwtSecretValidationRequired() {
        return enforceJwtSecret || isProductionProfile();
    }

    boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(String::trim)
                .anyMatch(this::isProductionProfileName);
    }

    private boolean isProductionProfileName(String profile) {
        return "prod".equalsIgnoreCase(profile)
                || "production".equalsIgnoreCase(profile)
                || "preview".equalsIgnoreCase(profile);
    }

    boolean isSecureJwtSecret(String secret) {
        if (!StringUtils.hasText(secret)) {
            return false;
        }
        String trimmed = secret.trim();
        if (JWT_SECRET_DEFAULT_PLACEHOLDER.equals(trimmed)) {
            return false;
        }
        return trimmed.length() >= JWT_SECRET_MIN_LENGTH;
    }

    void validateProdSecurityToggles() {
        if (exposeSwagger) {
            throw new IllegalStateException("Unsafe production toggle: APP_SECURITY_EXPOSE_SWAGGER must stay false.");
        }
        if (exposeDevEndpoints) {
            throw new IllegalStateException("Unsafe production toggle: APP_SECURITY_EXPOSE_DEV_ENDPOINTS must stay false.");
        }
        log.info("Production security surface validation passed.");
    }

    void validateProdAuthConfig() {
        validateProdEmailConfig();
        validateProdTurnstileConfig();
        log.info("Production auth configuration validation passed.");
    }

    private void validateProdEmailConfig() {
        AuthProperties.Email email = authProperties.getEmail();
        String provider = email == null ? "" : email.getProvider();
        if (!"zoho".equals(StringUtils.trimWhitespace(provider))) {
            throw new IllegalStateException("Unsafe production email provider. Set APP_AUTH_EMAIL_PROVIDER=zoho for preview/production.");
        }
        if (!StringUtils.hasText(smtpUsername) || !StringUtils.hasText(smtpPassword)) {
            throw new IllegalStateException("Zoho SMTP credentials are required for preview/production password reset emails.");
        }
        if (email == null || !StringUtils.hasText(email.getFrom()) || !StringUtils.hasText(email.getResetUrlBase())) {
            throw new IllegalStateException("Email sender and reset URL base are required for preview/production.");
        }
        if (!"smtp.zoho.com".equals(StringUtils.trimWhitespace(smtpHost))) {
            throw new IllegalStateException("Zoho SMTP host must be smtp.zoho.com for preview/production.");
        }
        if (!smtpAuth || !smtpStartTlsEnabled) {
            throw new IllegalStateException("Zoho SMTP auth and STARTTLS must stay enabled for preview/production.");
        }
        validatePublicHttpsUrl(email.getResetUrlBase(), "Password reset URL base");
    }

    private void validateProdTurnstileConfig() {
        AuthProperties.Turnstile turnstile = authProperties.getTurnstile();
        if (turnstile == null || !turnstile.isEnabled()) {
            throw new IllegalStateException("Cloudflare Turnstile must be enabled for preview/production auth.");
        }
        if (!StringUtils.hasText(turnstile.getSecret())) {
            throw new IllegalStateException("APP_AUTH_TURNSTILE_SECRET is required when Turnstile is enabled.");
        }
        if (!StringUtils.hasText(turnstile.getSiteKey())) {
            throw new IllegalStateException("NUXT_PUBLIC_TURNSTILE_SITE_KEY must be configured with backend preview/production auth.");
        }
        validateTurnstileSiteverifyUrl(turnstile.getSiteverifyUrl());
        validatePasswordResetTtl();
    }

    private void validatePasswordResetTtl() {
        int tokenTtlMinutes = authProperties.getPasswordReset().getTokenTtlMinutes();
        if (tokenTtlMinutes < 5 || tokenTtlMinutes > 60) {
            throw new IllegalStateException("Password reset token TTL must stay between 5 and 60 minutes for preview/production.");
        }
    }

    private void validateTurnstileSiteverifyUrl(String rawUrl) {
        URI uri = parseUri(rawUrl, "Turnstile siteverify URL");
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !"challenges.cloudflare.com".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalStateException("Turnstile siteverify URL must use https://challenges.cloudflare.com for preview/production.");
        }
    }

    private void validatePublicHttpsUrl(String rawUrl, String label) {
        URI uri = parseUri(rawUrl, label);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException(label + " must use HTTPS for preview/production.");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host) || isLocalOrPrivateHost(host)) {
            throw new IllegalStateException(label + " must not point to localhost or a private network for preview/production.");
        }
    }

    private URI parseUri(String rawUrl, String label) {
        if (!StringUtils.hasText(rawUrl)) {
            throw new IllegalStateException(label + " is required for preview/production.");
        }
        try {
            URI uri = new URI(rawUrl.trim());
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalStateException(label + " must be an absolute URL for preview/production.");
            }
            return uri;
        } catch (URISyntaxException ex) {
            throw new IllegalStateException(label + " is not a valid URL for preview/production.", ex);
        }
    }

    private boolean isLocalOrPrivateHost(String rawHost) {
        String host = rawHost.trim().toLowerCase();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if ("localhost".equals(host) || host.endsWith(".localhost") || host.endsWith(".local") || "::1".equals(host)) {
            return true;
        }
        if (host.startsWith("127.") || host.startsWith("0.") || host.startsWith("10.") || host.startsWith("192.168.")) {
            return true;
        }
        if (host.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) {
            return true;
        }
        return host.startsWith("169.254.")
                || host.startsWith("fc")
                || host.startsWith("fd")
                || host.startsWith("fe80:");
    }
}
