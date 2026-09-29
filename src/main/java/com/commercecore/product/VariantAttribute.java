package com.commercecore.product;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "variant_attributes")
public class VariantAttribute {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false) private ProductVariant variant;
    @Column(name = "attribute_name", nullable = false, length = 80) private String name;
    @Column(name = "attribute_value", nullable = false, length = 200) private String value;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    protected VariantAttribute() { }
    VariantAttribute(ProductVariant variant, String name, String value, int displayOrder) {
        if (name == null || name.isBlank() || name.trim().length() > 80 ||
                value == null || value.isBlank() || value.trim().length() > 200 || displayOrder < 0)
            throw new IllegalArgumentException("invalid variant attribute");
        this.variant = variant; this.name = name.trim(); this.value = value.trim(); this.displayOrder = displayOrder;
    }
    public UUID getId() { return id; } public String getName() { return name; }
    public String getValue() { return value; } public int getDisplayOrder() { return displayOrder; }
}
