package com.commercecore.auth;

import com.commercecore.common.exception.ApiException;
import com.commercecore.config.SecurityProperties;
import com.commercecore.notification.NotificationService;
import com.commercecore.notification.NotificationType;
import com.commercecore.user.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;

@Service
public class RegistrationService {
    private final UserAccountRepository users;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenService secureTokens;
    private final EmailService emailService;
    private final SecurityProperties properties;
    private final Clock clock;
    private final NotificationService notifications;

    public RegistrationService(UserAccountRepository users, EmailVerificationTokenRepository verificationTokens,
                               PasswordEncoder passwordEncoder, SecureTokenService secureTokens,
                               EmailService emailService, SecurityProperties properties, Clock clock,
                               NotificationService notifications) {
        this.users = users; this.verificationTokens = verificationTokens; this.passwordEncoder = passwordEncoder;
        this.secureTokens = secureTokens; this.emailService = emailService; this.properties = properties; this.clock = clock;
        this.notifications = notifications;
    }

    @Transactional
    public AuthDtos.RegistrationResponse register(AuthDtos.RegisterRequest request) {
        String email = UserAccount.normalizeEmail(request.email());
        if (users.existsByEmail(email)) throw duplicateEmail();
        UserAccount user = new UserAccount(email, passwordEncoder.encode(request.password()),
                request.fullName(), request.phone(), UserRole.CUSTOMER);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException duplicate) {
            throw duplicateEmail();
        }
        issueVerification(user);
        return new AuthDtos.RegistrationResponse(user.getId(), user.getEmail(),
                user.getAccountStatus(), user.isEmailVerified());
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        Instant now = clock.instant();
        EmailVerificationToken token = verificationTokens.findByTokenHash(secureTokens.hash(rawToken))
                .orElseThrow(RegistrationService::invalidVerificationToken);
        if (!token.isUsable(now) || token.getUser().isEmailVerified()) throw invalidVerificationToken();
        UserAccount user = token.getUser();
        token.use(now);
        user.verifyEmail();
        notifications.notify(user.getId(), NotificationType.WELCOME, "Welcome to CommerceCore",
                "Your account is active and verified. Happy shopping!", "ACCOUNT", user.getId().toString());
    }

    @Transactional
    public void resendVerification(String requestedEmail) {
        String email = UserAccount.normalizeEmail(requestedEmail);
        users.findByEmail(email).filter(user -> !user.isEmailVerified()
                        && user.getAccountStatus() == AccountStatus.PENDING_VERIFICATION)
                .ifPresent(this::issueVerification);
    }

    private void issueVerification(UserAccount user) {
        Instant now = clock.instant();
        verificationTokens.invalidateUsableByUser(user.getId(), now);
        String raw = secureTokens.generate();
        verificationTokens.save(new EmailVerificationToken(user, secureTokens.hash(raw),
                now.plus(properties.verificationTokenTtl()), now));
        emailService.sendVerification(user.getEmail(), raw);
    }

    private static ApiException duplicateEmail() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "An account with that email already exists.");
    }
    private static ApiException invalidVerificationToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "VERIFICATION_TOKEN_INVALID",
                "The verification token is invalid or expired.");
    }
}
