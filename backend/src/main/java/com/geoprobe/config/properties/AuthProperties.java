package com.geoprobe.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private boolean registrationEnabled = true;
    private Turnstile turnstile = new Turnstile();
    private Email email = new Email();
    private PasswordReset passwordReset = new PasswordReset();

    @Data
    public static class Turnstile {
        private boolean enabled = false;
        private String secret = "";
        private String siteKey = "";
        private String siteverifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
        private int timeoutSeconds = 5;
    }

    @Data
    public static class Email {
        private String provider = "fake";
        private String from = "support@traclux.com";
        private String resetUrlBase = "http://127.0.0.1:3000/login";
    }

    @Data
    public static class PasswordReset {
        private int tokenTtlMinutes = 30;
    }
}
