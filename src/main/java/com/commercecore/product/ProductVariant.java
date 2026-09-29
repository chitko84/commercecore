package com.commercecore.product;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false) private Product product;
    @Column(nullable = false, unique = true, length = 64) private String sku;
    @Column(name = "price_override", precision = 19, scale = 2) private BigDecimal priceOverride;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;
    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, name ASC") private final List<VariantAttribute> attributes = new ArrayList<>();

    protected ProductVariant() { }
    public ProductVariant(Product product, String sku, BigDecimal priceOverride, boolean active) {
        this.product = Objects.requireNonNull(product); update(sku, priceOverride, active);
    }
    public void update(String sku, BigDecimal priceOverride, boolean active) {
        this.sku = Product.normalizeSku(sku);
        if (priceOverride != null && priceOverride.signum() <= 0) throw new IllegalArgumentException("invalid variant price");
        this.priceOverride = priceOverride; this.active = active;
    }
    public void replaceAttributes(List<AttributeValue> values) {
        attributes.clear();
        Set<String> names = new HashSet<>();
        for (AttributeValue value : values) {
            String key = value.name().trim().toLowerCase(Locale.ROOT);
            if (!names.add(key)) throw new IllegalArgumentException("duplicate variant attribute");
            attributes.add(new VariantAttribute(this, value.name(), value.value(), value.displayOrder()));
        }
    }
    public void activate() { active = true; }
    public void deactivate() { active = false; }
    public BigDecimal effectivePrice() { return priceOverride == null ? product.effectivePrice() : priceOverride; }
    @PrePersist void create() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void updateTimestamp() { updatedAt = Instant.now(); }
    public UUID getId() { return id; } public Product getProduct() { return product; }
    public String getSku() { return sku; } public BigDecimal getPriceOverride() { return priceOverride; }
    public boolean isActive() { return active; } public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; } public List<VariantAttribute> getAttributes() { return attributes; }
    public record AttributeValue(String name, String value, int displayOrder) { }
}
