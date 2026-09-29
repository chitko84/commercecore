package com.commercecore.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Service
@ConditionalOnProperty(name = "app.email.mode", havingValue = "development", matchIfMissing = true)
public class DevelopmentEmailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(DevelopmentEmailService.class);
    private final Path outbox;

    public DevelopmentEmailService(@Value("${app.email.development-outbox:build/dev-mail-outbox.txt}") String outbox) {
        this.outbox = Path.of(outbox).normalize();
    }

    @Override
    public void sendVerification(String email, String rawToken) {
        deliver("VERIFY", email, rawToken);
    }

    @Override
    public void sendPasswordReset(String email, String rawToken) {
        deliver("RESET", email, rawToken);
    }

    private void deliver(String type, String email, String rawToken) {
        try {
            Path parent = outbox.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(outbox, type + " " + email + " " + rawToken + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("Development {} email written to configured outbox for {}", type.toLowerCase(), email);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not write the development email outbox", failure);
        }
    }
}
