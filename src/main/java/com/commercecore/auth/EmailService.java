package com.commercecore.auth;

public interface EmailService {
    void sendVerification(String email, String rawToken);
    void sendPasswordReset(String email, String rawToken);
}
