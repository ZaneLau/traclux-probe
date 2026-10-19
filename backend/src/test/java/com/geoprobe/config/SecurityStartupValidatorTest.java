package com.geoprobe.config;

import com.geoprobe.config.properties.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityStartupValidatorTest {

    @Mock
    private Environment environment;

    private SecurityStartupValidator validator;
    private AuthProperties authProperties;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        validator = new SecurityStartupValidator(environment, authProperties);
        ReflectionTestUtils.setField(validator, "enforceJwtSecret", false);
        ReflectionTestUtils.setField(validator, "jwtSecret", "replace-with-256-bit-secret");
        ReflectionTestUtils.setField(validator, "exposeSwagger", false);
        ReflectionTestUtils.setField(validator, "exposeDevEndpoints", false);
        ReflectionTestUtils.setField(validator, "smtpUsername", "");
        ReflectionTestUtils.setField(validator, "smtpPassword", "");
        ReflectionTestUtils.setField(validator, "smtpHost", "smtp.zoho.com");
        ReflectionTestUtils.setField(validator, "smtpAuth", true);
        ReflectionTestUtils.setField(validator, "smtpStartTlsEnabled", true);
    }

    @Test
    void shouldSkipValidationWhenNonProdAndNotEnforced() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        assertDoesNotThrow(() -> validator.validateJwtSecret());
    }

    @Test
    void shouldFailWhenProdProfileUsesDefaultSecret() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());
    }

    @Test
    void shouldFailWhenEnforcedAndSecretTooShort() {
        ReflectionTestUtils.setField(validator, "enforceJwtSecret", true);
        ReflectionTestUtils.setField(validator, "jwtSecret", "too-short-secret");

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());
    }

    @Test
    void shouldPassWhenProdProfileUsesStrongSecret() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(validator, "jwtSecret", "prod-strong-jwt-secret-minimum-thirty-two");
        configureSafeProductionAuth();

        assertDoesNotThrow(() -> validator.validateJwtSecret());
    }

    @Test
    void shouldSkipProdSurfaceChecksWhenJwtValidationIsOnlyEnforcedForNonProd() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        ReflectionTestUtils.setField(validator, "enforceJwtSecret", true);
        ReflectionTestUtils.setField(validator, "jwtSecret", "test-strong-jwt-secret-minimum-thirty-two");
        ReflectionTestUtils.setField(validator, "exposeSwagger", true);
        ReflectionTestUtils.setField(validator, "exposeDevEndpoints", true);

        assertDoesNotThrow(() -> validator.validateJwtSecret());
    }

    @Test
    void shouldFailWhenProdProfileExposesSwagger() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(validator, "jwtSecret", "prod-strong-jwt-secret-minimum-thirty-two");
        ReflectionTestUtils.setField(validator, "exposeSwagger", true);
        configureSafeProductionAuth();

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());
    }

    @Test
    void shouldFailWhenProdProfileExposesDevEndpoints() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(validator, "jwtSecret", "prod-strong-jwt-secret-minimum-thirty-two");
        ReflectionTestUtils.setField(validator, "exposeDevEndpoints", true);
        configureSafeProductionAuth();

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());
    }

    @Test
    void shouldTreatPreviewProfileAsProductionLike() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"preview"});

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());
    }

    @Test
    void shouldFailWhenProductionUsesFakeEmailProvider() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(validator, "jwtSecret", "prod-strong-jwt-secret-minimum-thirty-two");
        configureSafeProductionAuth();
        authProperties.getEmail().setProvider("fake");

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());
    }

    @Test
    void shouldFailWhenProductionZohoSmtpCredentialsMissing() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(validator, "jwtSecret", "prod-strong-jwt-secret-minimum-thirty-two");
        configureSafeProductionAuth();
        ReflectionTestUtils.setField(validator, "smtpPassword", "");

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());
    }

    @Test
    void shouldFailWhenProductionTurnstileIsDisabled() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(validator, "jwtSecret", "prod-strong-jwt-secret-minimum-thirty-two");
        configureSafeProductionAuth();
        authProperties.getTurnstile().setEnabled(false);

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());
    }

    @Test
    void shouldFailWhenProductionTurnstileSiteKeyMissing() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(validator, "jwtSecret", "prod-strong-jwt-secret-minimum-thirty-two");
        configureSafeProductionAuth();
        authProperties.getTurnstile().setSiteKey("");

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());
    }

    @Test
    void shouldFailWhenProductionResetUrlUsesLocalhost() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(validator, "jwtSecret", "prod-strong-jwt-secret-minimum-thirty-two");
        configureSafeProductionAuth();
        authProperties.getEmail().setResetUrlBase("http://127.0.0.1:3000/login");

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());
    }

    @Test
    void shouldFailWhenProductionResetUrlUsesPrivateIpv6Host() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(validator, "jwtSecret", "prod-strong-jwt-secret-minimum-thirty-two");
        configureSafeProductionAuth();
        authProperties.getEmail().setResetUrlBase("https://[::1]/login");

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());

        configureSafeProductionAuth();
        authProperties.getEmail().setResetUrlBase("https://[fc00::1]/login");

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());

        configureSafeProductionAuth();
        authProperties.getEmail().setResetUrlBase("https://[fe80::1]/login");

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());
    }

    @Test
    void shouldFailWhenProductionSmtpHostIsNotZoho() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(validator, "jwtSecret", "prod-strong-jwt-secret-minimum-thirty-two");
        configureSafeProductionAuth();
        ReflectionTestUtils.setField(validator, "smtpHost", "smtp.example.com");

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());
    }

    @Test
    void shouldFailWhenProductionSmtpTlsIsDisabled() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(validator, "jwtSecret", "prod-strong-jwt-secret-minimum-thirty-two");
        configureSafeProductionAuth();
        ReflectionTestUtils.setField(validator, "smtpStartTlsEnabled", false);

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());
    }

    @Test
    void shouldFailWhenProductionTurnstileEndpointIsNotCloudflareHttps() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(validator, "jwtSecret", "prod-strong-jwt-secret-minimum-thirty-two");
        configureSafeProductionAuth();
        authProperties.getTurnstile().setSiteverifyUrl("http://example.com/siteverify");

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());
    }

    @Test
    void shouldFailWhenProductionPasswordResetTtlIsTooLong() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(validator, "jwtSecret", "prod-strong-jwt-secret-minimum-thirty-two");
        configureSafeProductionAuth();
        authProperties.getPasswordReset().setTokenTtlMinutes(240);

        assertThrows(IllegalStateException.class, () -> validator.validateJwtSecret());
    }

    private void configureSafeProductionAuth() {
        authProperties.getEmail().setProvider("zoho");
        authProperties.getEmail().setFrom("support@traclux.com");
        authProperties.getEmail().setResetUrlBase("https://app.traclux.com/login");
        authProperties.getTurnstile().setEnabled(true);
        authProperties.getTurnstile().setSecret("turnstile-secret");
        authProperties.getTurnstile().setSiteKey("turnstile-site-key");
        ReflectionTestUtils.setField(validator, "smtpUsername", "support@traclux.com");
        ReflectionTestUtils.setField(validator, "smtpPassword", "zoho-app-password");
    }
}
