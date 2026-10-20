package com.geoprobe.service;

public interface AuthEmailSender {

    void sendPasswordResetEmail(String recipient, String resetUrl);
}
