package com.commercecore.auth;

import com.commercecore.common.exception.ApiException;
import com.commercecore.config.SecurityProperties;
import com.commercecore.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthenticationService {
    private static final String INVALID_MESSAGE = "The email or password is incorrect.";
    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenService secureTokens;
    private final JwtTokenService jwtTokens;
    private final SecurityProperties properties;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthenticationService(UserAccountRepository users, RefreshTokenRepository refreshTokens,
                                 PasswordEncoder passwordEncoder, SecureTokenService secureTokens,
                                 JwtTokenService jwtTokens, SecurityProperties properties, Clock clock) {
        this.users = users; this.refreshTokens = refreshTokens; this.passwordEncoder = passwordEncoder;
        this.secureTokens = secureTokens; this.jwtTokens = jwtTokens; this.properties = properties; this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("timing-equalization-value-never-used");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthDtos.AuthTokensResponse login(AuthDtos.LoginRequest request) {
        Instant now = clock.instant();
        String email = UserAccount.normalizeEmail(request.email());
        UserAccount user = users.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw invalidCredentials();
        }
        user.unlockIfTemporaryLockExpired(now);
        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (!passwordMatches) {
            user.recordFailedLogin(properties.maximumFailedLogins(), now.plus(properties.loginLockDuration()));
            throw invalidCredentials();
        }
        if (!user.isEmailVerified() || user.getAccountStatus() != AccountStatus.ACTIVE) throw invalidCredentials();
        user.resetFailedLogins();
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.changePasswordHash(passwordEncoder.encode(request.password()));
        }
        return issueTokens(user, UUID.randomUUID(), now);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthDtos.AuthTokensResponse refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken old = refreshTokens.findLockedByTokenHash(secureTokens.hash(rawRefreshToken))
                .orElseThrow(AuthenticationService::invalidRefreshToken);
        if (old.getRevokedAt() != null) {
            old.recordReuse(now);
            refreshTokens.revokeActiveFamily(old.getFamilyId(), now);
            throw invalidRefreshToken();
        }
        if (!old.isActive(now)) {
            old.revoke(now);
            throw invalidRefreshToken();
        }
        UserAccount user = old.getUser();
        if (!user.isEmailVerified() || user.getAccountStatus() != AccountStatus.ACTIVE) {
            refreshTokens.revokeActiveFamily(old.getFamilyId(), now);
            throw invalidRefreshToken();
        }
        String rawNew = secureTokens.generate();
        RefreshToken replacement = new RefreshToken(user, old.getFamilyId(), secureTokens.hash(rawNew),
                now.plus(properties.refreshTokenTtl()), now);
        refreshTokens.saveAndFlush(replacement);
        old.rotateTo(replacement.getId(), now);
        return response(user, rawNew, replacement.getExpiresAt());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findLockedByTokenHash(secureTokens.hash(rawRefreshToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    private AuthDtos.AuthTokensResponse issueTokens(UserAccount user, UUID familyId, Instant now) {
        String rawRefresh = secureTokens.generate();
        RefreshToken refresh = new RefreshToken(user, familyId, secureTokens.hash(rawRefresh),
                now.plus(properties.refreshTokenTtl()), now);
        refreshTokens.save(refresh);
        return response(user, rawRefresh, refresh.getExpiresAt());
    }

    private AuthDtos.AuthTokensResponse response(UserAccount user, String rawRefresh, Instant refreshExpiry) {
        JwtTokenService.AccessToken access = jwtTokens.issue(user);
        return new AuthDtos.AuthTokensResponse("Bearer", access.value(), access.expiresAt(), rawRefresh, refreshExpiry);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", INVALID_MESSAGE);
    }
    private static ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "The refresh token is invalid or expired.");
    }
}
