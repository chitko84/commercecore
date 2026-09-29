package com.commercecore.product;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_media")
public class ProductMedia {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false) private Product product;
    @Column(name = "media_url", nullable = false, length = 2048) private String mediaUrl;
    @Column(name = "alt_text", nullable = false, length = 255) private String altText;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "is_primary", nullable = false) private boolean primary;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected ProductMedia() { }
    public ProductMedia(Product product, String mediaUrl, String altText, int displayOrder, boolean primary) {
        id = UUID.randomUUID(); this.product = product;
        update(mediaUrl, altText, displayOrder, primary);
    }
    public void update(String mediaUrl, String altText, int displayOrder, boolean primary) {
        this.mediaUrl = required(mediaUrl, "mediaUrl", 2048);
        if (!(this.mediaUrl.startsWith("https://") || this.mediaUrl.startsWith("http://") || this.mediaUrl.startsWith("/"))) {
            throw new IllegalArgumentException("mediaUrl must be an HTTP(S) URL or absolute application path");
        }
        this.altText = required(altText, "altText", 255);
        if (displayOrder < 0) throw new IllegalArgumentException("displayOrder must not be negative");
        this.displayOrder = displayOrder; this.primary = primary;
    }
    @PrePersist void onCreate() { createdAt = Instant.now(); }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new IllegalArgumentException(field + " is too long");
        return trimmed;
    }
    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public String getMediaUrl() { return mediaUrl; }
    public String getAltText() { return altText; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isPrimary() { return primary; }
    public Instant getCreatedAt() { return createdAt; }
}
