package com.commercecore.product;

import com.commercecore.inventory.StockStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class VariantDtos {
    private VariantDtos() { }
    public record AttributeRequest(@NotBlank @Size(max=80) String name,
                                   @NotBlank @Size(max=200) String value,
                                   @Min(0) int displayOrder) { }
    public record VariantRequest(@NotBlank @Size(max=64)
            @Pattern(regexp="(?i)[a-z0-9][a-z0-9._-]*") String sku,
            @DecimalMin("0.01") @Digits(integer=17, fraction=2) BigDecimal priceOverride,
            boolean active, @NotNull List<@Valid AttributeRequest> attributes) { }
    public record AttributeResponse(String name, String value, int displayOrder) { }
    public record VariantResponse(UUID id, UUID productId, String sku, BigDecimal priceOverride,
            BigDecimal effectivePrice, boolean active, List<AttributeResponse> attributes,
            StockStatus availability, Instant createdAt, Instant updatedAt) { }
}
