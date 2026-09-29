package com.commercecore.auth;

import com.commercecore.user.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;
    @Column(name = "family_id", nullable = false) private UUID familyId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "replaced_by_id") private UUID replacedById;
    @Column(name = "reuse_detected_at") private Instant reuseDetectedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected RefreshToken() { }
    public RefreshToken(UserAccount user, UUID familyId, String tokenHash, Instant expiresAt, Instant now) {
        id = UUID.randomUUID(); this.user = user; this.familyId = familyId;
        this.tokenHash = tokenHash; this.expiresAt = expiresAt; createdAt = now;
    }
    public boolean isActive(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public void rotateTo(UUID replacementId, Instant now) { revokedAt = now; replacedById = replacementId; }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
    public void recordReuse(Instant now) { reuseDetectedAt = now; }
    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public UUID getFamilyId() { return familyId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public UUID getReplacedById() { return replacedById; }
}
