package com.commercecore.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() { }

    public record ProfileResponse(UUID id, String email, String fullName, String phone,
                                  UserRole role, boolean emailVerified, AccountStatus accountStatus,
                                  Instant createdAt) { }
    public record UpdateProfileRequest(@Size(max = 200) String fullName,
                                       @Size(max = 32) String phone) { }
    public record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
                                        @NotBlank @Size(min = 12, max = 128) String newPassword) { }

    public record AdminUserResponse(UUID id, String email, String fullName, String phone,
                                    UserRole role, AccountStatus accountStatus,
                                    boolean emailVerified, int failedLoginAttempts,
                                    Instant lockedUntil, Instant createdAt, Instant updatedAt) {
        public static AdminUserResponse from(UserAccount user) {
            return new AdminUserResponse(user.getId(), user.getEmail(), user.getFullName(),
                    user.getPhone(), user.getRole(), user.getAccountStatus(),
                    user.isEmailVerified(), user.getFailedLoginAttempts(), user.getLockedUntil(),
                    user.getCreatedAt(), user.getUpdatedAt());
        }
    }

    public record ChangeRoleRequest(@NotNull UserRole role) { }

    public record PageResponse<T>(java.util.List<T> content, int page, int size,
                                  long totalElements, int totalPages, boolean first, boolean last) { }

    public record AddressRequest(
            @NotBlank @Size(max = 50) String label,
            @NotBlank @Size(max = 150) String recipientName,
            @NotBlank @Size(max = 32) String phone,
            @NotBlank @Size(max = 200) String addressLine1,
            @Size(max = 200) String addressLine2,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String stateRegion,
            @NotBlank @Size(max = 20) String postalCode,
            @NotBlank @Pattern(regexp = "(?i)[A-Z]{2}") String countryCode,
            boolean defaultShipping,
            boolean defaultBilling) { }

    public record AddressResponse(UUID id, String label, String recipientName, String phone,
                                  String addressLine1, String addressLine2, String city,
                                  String stateRegion, String postalCode, String countryCode,
                                  boolean defaultShipping, boolean defaultBilling,
                                  Instant createdAt, Instant updatedAt) { }
}
