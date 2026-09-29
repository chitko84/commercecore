package com.commercecore.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.email.mode", havingValue = "smtp")
public class SmtpEmailService implements EmailService {
    private final JavaMailSender mailSender;
    private final String from;
    private final String frontendBaseUrl;

    public SmtpEmailService(JavaMailSender mailSender,
                            @Value("${app.email.from}") String from,
                            @Value("${app.email.frontend-base-url}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override public void sendVerification(String email, String rawToken) {
        send(email, "Verify your CommerceCore email", frontendBaseUrl + "/verify-email?token=" + rawToken);
    }
    @Override public void sendPasswordReset(String email, String rawToken) {
        send(email, "Reset your CommerceCore password", frontendBaseUrl + "/reset-password?token=" + rawToken);
    }
    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(to); message.setSubject(subject); message.setText(text);
        mailSender.send(message);
    }
}
