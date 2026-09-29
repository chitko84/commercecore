package com.commercecore.product;

import com.commercecore.category.Category;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "products")
public class Product {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern SKU = Pattern.compile("[A-Z0-9][A-Z0-9._-]*");

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, unique = true, length = 220) private String slug;
    @Column(nullable = false, length = 5000) private String description;
    @Column(nullable = false, unique = true, length = 64) private String sku;
    @Column(nullable = false, length = 120) private String brand;
    @Column(name = "base_price", nullable = false, precision = 19, scale = 2) private BigDecimal basePrice;
    @Column(name = "sale_price", precision = 19, scale = 2) private BigDecimal salePrice;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false) private Category category;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected Product() { }

    public Product(String name, String slug, String description, String sku, String brand,
                   BigDecimal basePrice, BigDecimal salePrice, Category category, boolean active) {
        update(name, slug, description, sku, brand, basePrice, salePrice, category, active);
    }

    public void update(String name, String slug, String description, String sku, String brand,
                       BigDecimal basePrice, BigDecimal salePrice, Category category, boolean active) {
        this.name = required(name, "name", 200);
        this.slug = normalizeSlug(slug);
        this.description = required(description, "description", 5000);
        this.sku = normalizeSku(sku);
        this.brand = required(brand, "brand", 120);
        validatePrices(basePrice, salePrice);
        this.basePrice = basePrice;
        this.salePrice = salePrice;
        this.category = java.util.Objects.requireNonNull(category, "category must not be null");
        this.active = active;
    }

    public void activate() { active = true; }
    public void deactivate() { active = false; }
    public BigDecimal effectivePrice() { return salePrice == null ? basePrice : salePrice; }

    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public static String normalizeSlug(String value) {
        String normalized = required(value, "slug", 220).toLowerCase(Locale.ROOT);
        if (!SLUG.matcher(normalized).matches()) throw new IllegalArgumentException("invalid product slug");
        return normalized;
    }
    public static String normalizeSku(String value) {
        String normalized = required(value, "sku", 64).toUpperCase(Locale.ROOT);
        if (!SKU.matcher(normalized).matches()) throw new IllegalArgumentException("invalid SKU");
        return normalized;
    }
    private static void validatePrices(BigDecimal basePrice, BigDecimal salePrice) {
        if (basePrice == null || basePrice.signum() <= 0 ||
                (salePrice != null && (salePrice.signum() <= 0 || salePrice.compareTo(basePrice) > 0))) {
            throw new IllegalArgumentException("invalid product price");
        }
    }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new IllegalArgumentException(field + " is too long");
        return trimmed;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getSku() { return sku; }
    public String getBrand() { return brand; }
    public BigDecimal getBasePrice() { return basePrice; }
    public BigDecimal getSalePrice() { return salePrice; }
    public Category getCategory() { return category; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
