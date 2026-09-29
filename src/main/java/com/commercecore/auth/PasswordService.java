package com.commercecore.auth;

import com.commercecore.common.exception.ApiException;
import com.commercecore.config.SecurityProperties;
import com.commercecore.notification.NotificationService;
import com.commercecore.notification.NotificationType;
import com.commercecore.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class PasswordService {
    private final UserAccountRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenService secureTokens;
    private final EmailService emailService;
    private final SecurityProperties properties;
    private final Clock clock;
    private final NotificationService notifications;

    public PasswordService(UserAccountRepository users, PasswordResetTokenRepository resetTokens,
                           RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                           SecureTokenService secureTokens, EmailService emailService,
                           SecurityProperties properties, Clock clock, NotificationService notifications) {
        this.users = users; this.resetTokens = resetTokens; this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder; this.secureTokens = secureTokens;
        this.emailService = emailService; this.properties = properties; this.clock = clock;
        this.notifications = notifications;
    }

    @Transactional
    public void changePassword(UUID userId, UserDtos.ChangePasswordRequest request) {
        UserAccount user = users.findById(userId).orElseThrow(PasswordService::accountNotFound);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "The current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "The new password must be different.");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokens.revokeActiveForUser(userId, clock.instant());
        notifications.notify(userId, NotificationType.PASSWORD_CHANGED, "Password changed",
                "Your password was changed. If this was not you, contact support.",
                "ACCOUNT", userId.toString());
    }

    @Transactional
    public void forgotPassword(String requestedEmail) {
        String email = UserAccount.normalizeEmail(requestedEmail);
        users.findByEmail(email).filter(user -> user.isEmailVerified()
                        && user.getAccountStatus() == AccountStatus.ACTIVE)
                .ifPresent(this::issueResetToken);
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByTokenHash(secureTokens.hash(request.token()))
                .orElseThrow(PasswordService::invalidResetToken);
        if (!token.isUsable(now) || token.getUser().getAccountStatus() == AccountStatus.DEACTIVATED) {
            throw invalidResetToken();
        }
        UserAccount user = token.getUser();
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        token.use(now);
        refreshTokens.revokeActiveForUser(user.getId(), now);
        notifications.notify(user.getId(), NotificationType.PASSWORD_RESET, "Password reset",
                "Your password was reset successfully.", "ACCOUNT", user.getId().toString());
    }

    private void issueResetToken(UserAccount user) {
        Instant now = clock.instant();
        resetTokens.invalidateUsableByUser(user.getId(), now);
        String raw = secureTokens.generate();
        resetTokens.save(new PasswordResetToken(user, secureTokens.hash(raw),
                now.plus(properties.passwordResetTokenTtl()), now));
        emailService.sendPasswordReset(user.getEmail(), raw);
    }

    private static ApiException invalidResetToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_TOKEN_INVALID",
                "The password reset token is invalid or expired.");
    }
    private static ApiException accountNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "The account was not found.");
    }
}
