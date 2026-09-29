package com.commercecore.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReviewDtos {
    private ReviewDtos() { }

    public record ReviewRequest(@NotNull @Min(1) @Max(5) Integer rating,
                                @Size(max = 150) String title,
                                @Size(max = 2000) String body) { }

    public record PublicReviewResponse(UUID id, String authorName, int rating,
                                       String title, String body, Instant createdAt) { }

    public record MyReviewResponse(UUID id, UUID productId, String productName,
                                   String productSlug, int rating, String title, String body,
                                   ReviewStatus status, Instant createdAt, Instant updatedAt) { }

    public record ReviewSummaryResponse(long reviewCount, BigDecimal averageRating,
                                        Map<Integer, Long> distribution) { }

    public record ReviewAdminResponse(UUID id, UUID productId, String productName,
                                      UUID customerId, int rating, String title, String body,
                                      ReviewStatus status, Instant createdAt, Instant updatedAt) { }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
                                  int totalPages, boolean first, boolean last) { }
}
