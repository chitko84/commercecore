package com.commercecore.auth;

import com.commercecore.user.AccountStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() { }

    public record RegisterRequest(
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 12, max = 128) String password,
            @Size(max = 32) String phone) { }

    public record RegistrationResponse(UUID id, String email, AccountStatus accountStatus,
                                       boolean emailVerified) { }
    public record TokenRequest(@NotBlank @Size(max = 512) String token) { }
    public record EmailRequest(@NotBlank @Email @Size(max = 320) String email) { }
    public record LoginRequest(@NotBlank @Email @Size(max = 320) String email,
                               @NotBlank @Size(max = 128) String password) { }
    public record RefreshRequest(@NotBlank @Size(max = 512) String refreshToken) { }
    public record LogoutRequest(@NotBlank @Size(max = 512) String refreshToken) { }
    public record ResetPasswordRequest(@NotBlank @Size(max = 512) String token,
                                       @NotBlank @Size(min = 12, max = 128) String newPassword) { }
    public record AuthTokensResponse(String tokenType, String accessToken, Instant accessTokenExpiresAt,
                                     String refreshToken, Instant refreshTokenExpiresAt) { }
    public record MessageResponse(String message) { }
}
