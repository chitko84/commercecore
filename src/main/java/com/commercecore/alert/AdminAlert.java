package com.commercecore.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An append-only operational alert for administrators. Alerts are produced
 * only by durable state transitions and deduplicated via {@code dedupKey}.
 */
@Entity
@Table(name = "admin_alerts")
public class AdminAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdminAlertType type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(name = "resource_type", nullable = false, length = 32)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, length = 128)
    private String resourceId;

    @Column(name = "dedup_key", nullable = false, unique = true, length = 255)
    private String dedupKey;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAlert() {
        // Required by JPA.
    }

    public AdminAlert(AdminAlertType type, String title, String body,
                      String resourceType, String resourceId, String dedupKey) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.title = requireText(title, "title", 160);
        this.body = requireText(body, "body", 1000);
        this.resourceType = requireText(resourceType, "resourceType", 32);
        this.resourceId = requireText(resourceId, "resourceId", 128);
        this.dedupKey = requireText(dedupKey, "dedupKey", 255);
    }

    public void acknowledge(Instant now) {
        if (acknowledgedAt == null) {
            acknowledgedAt = Objects.requireNonNull(now, "now must not be null");
        }
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
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

    public UUID getId() { return id; }
    public AdminAlertType getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getDedupKey() { return dedupKey; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public boolean isAcknowledged() { return acknowledgedAt != null; }
    public Instant getCreatedAt() { return createdAt; }
}