package com.geoprobe.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.auth.email", name = "provider", havingValue = "fake", matchIfMissing = true)
public class FakeAuthEmailSender implements AuthEmailSender {

    @Override
    public void sendPasswordResetEmail(String recipient, String resetUrl) {
        log.info("Fake password reset email queued. recipient={}, resetUrl={}", recipient, resetUrl);
    }
}
