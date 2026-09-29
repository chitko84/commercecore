package com.commercecore.product;

import com.commercecore.category.CategoryDtos;
import com.commercecore.inventory.StockStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProductDtos {
    private ProductDtos() { }

    public record ProductRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 220)
            @Pattern(regexp = "(?i)[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
            @NotBlank @Size(max = 5000) String description,
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "(?i)[a-z0-9][a-z0-9._-]*") String sku,
            @NotBlank @Size(max = 120) String brand,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal basePrice,
            @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal salePrice,
            @NotNull UUID categoryId,
            @NotNull Boolean active) { }

    public record MediaRequest(
            @NotBlank @Size(max = 2048)
            @Pattern(regexp = "^(https?://|/).+") String mediaUrl,
            @NotBlank @Size(max = 255) String altText,
            @Min(0) int displayOrder,
            boolean primary) { }

    public record MediaResponse(UUID id, String mediaUrl, String altText,
                                int displayOrder, boolean primary, Instant createdAt) { }

    public record ProductSummary(UUID id, String name, String slug, String sku, String brand,
                                 BigDecimal basePrice, BigDecimal salePrice, BigDecimal effectivePrice,
                                 CategoryDtos.CategorySummary category, MediaResponse primaryMedia,
                                 boolean active, StockStatus availability, Instant createdAt) { }

    public record ProductResponse(UUID id, String name, String slug, String description, String sku,
                                  String brand, BigDecimal basePrice, BigDecimal salePrice,
                                  BigDecimal effectivePrice, CategoryDtos.CategorySummary category,
                                  List<MediaResponse> media, List<VariantDtos.VariantResponse> variants,
                                  boolean active, StockStatus availability,
                                  Instant createdAt, Instant updatedAt) { }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
                                  int totalPages, boolean first, boolean last) { }
}
