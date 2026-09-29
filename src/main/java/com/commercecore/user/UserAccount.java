package com.commercecore.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Email
    @Size(max = 320)
    @Column(nullable = false, unique = true, length = 320, updatable = false)
    private String email;

    @NotBlank
    @Size(max = 255)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @NotBlank
    @Size(max = 200)
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Size(max = 32)
    @Column(length = 32)
    private String phone;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 32)
    private AccountStatus accountStatus;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected UserAccount() {
        // Required by JPA.
    }

    public UserAccount(String email, String passwordHash, String fullName,
                       String phone, UserRole role) {
        this.email = normalizeEmail(email);
        this.passwordHash = requireText(passwordHash, "passwordHash", 255);
        this.fullName = requireText(fullName, "fullName", 200);
        this.phone = normalizeOptional(phone, 32);
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.accountStatus = AccountStatus.PENDING_VERIFICATION;
        this.emailVerified = false;
    }

    public void changeStatus(AccountStatus newStatus) {
        this.accountStatus = Objects.requireNonNull(newStatus, "newStatus must not be null");
    }

    public void changeRole(UserRole newRole) {
        this.role = Objects.requireNonNull(newRole, "newRole must not be null");
    }

    public void verifyEmail() {
        emailVerified = true;
        if (accountStatus == AccountStatus.PENDING_VERIFICATION) {
            accountStatus = AccountStatus.ACTIVE;
        }
    }

    public void updateProfile(String fullName, String phone) {
        this.fullName = requireText(fullName, "fullName", 200);
        this.phone = normalizeOptional(phone, 32);
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = requireText(passwordHash, "passwordHash", 255);
    }

    public void recordFailedLogin(int maximumAttempts, Instant lockUntil) {
        if (accountStatus != AccountStatus.ACTIVE) return;
        failedLoginAttempts++;
        if (failedLoginAttempts >= maximumAttempts) {
            accountStatus = AccountStatus.LOCKED;
            lockedUntil = Objects.requireNonNull(lockUntil, "lockUntil must not be null");
        }
    }

    public boolean unlockIfTemporaryLockExpired(Instant now) {
        if (accountStatus == AccountStatus.LOCKED && lockedUntil != null && !lockedUntil.isAfter(now)) {
            accountStatus = AccountStatus.ACTIVE;
            resetFailedLogins();
            return true;
        }
        return false;
    }

    public void resetFailedLogins() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static String normalizeEmail(String value) {
        String normalized = requireText(value, "email", 320).toLowerCase(Locale.ROOT);
        if (!normalized.contains("@")) {
            throw new IllegalArgumentException("email must be a valid email address");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("phone must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public UserRole getRole() {
        return role;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }

    public Instant getLockedUntil() { return lockedUntil; }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
