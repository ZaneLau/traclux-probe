package com.geoprobe.service;

import com.geoprobe.config.properties.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.auth.email", name = "provider", havingValue = "zoho")
public class SmtpAuthEmailSender implements AuthEmailSender {

    private final JavaMailSender mailSender;
    private final AuthProperties authProperties;

    @Override
    public void sendPasswordResetEmail(String recipient, String resetUrl) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(authProperties.getEmail().getFrom());
        message.setTo(recipient);
        message.setSubject("Reset your Traclux password");
        message.setText("Use this link to reset your Traclux password:\n\n" + resetUrl + "\n\nThis link expires soon. If you did not request it, you can ignore this email.");
        mailSender.send(message);
    }
}
