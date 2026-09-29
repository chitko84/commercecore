package com.commercecore.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CategoryDtos {
    private CategoryDtos() { }

    public record CategoryRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 140)
            @Pattern(regexp = "(?i)[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
            @Size(max = 1000) String description,
            UUID parentCategoryId,
            @NotNull Boolean active) { }

    public record CategorySummary(UUID id, String name, String slug) { }

    public record CategoryResponse(UUID id, String name, String slug, String description,
                                   CategorySummary parent, List<CategorySummary> children,
                                   boolean active, Instant createdAt, Instant updatedAt) { }
}
