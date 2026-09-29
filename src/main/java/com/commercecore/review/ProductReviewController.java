package com.commercecore.review;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public product review listing/summary plus verified-purchase review
 * creation. GET routes are intentionally public (product pages); POST is
 * restricted to CUSTOMER by {@code SecurityConfig}.
 */
@Validated
@RestController
@RequestMapping("/api/v1/products/{productSlug}/reviews")
public class ProductReviewController {

    private final ReviewService reviews;

    public ProductReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    ReviewDtos.PageResponse<ReviewDtos.PublicReviewResponse> list(
            @PathVariable String productSlug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reviews.publicPage(productSlug, page, size);
    }

    @GetMapping("/summary")
    ReviewDtos.ReviewSummaryResponse summary(@PathVariable String productSlug) {
        return reviews.publicSummary(productSlug);
    }

    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ReviewDtos.MyReviewResponse create(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable String productSlug,
                                       @Valid @RequestBody ReviewDtos.ReviewRequest request) {
        return reviews.create(UUID.fromString(jwt.getSubject()), productSlug, request);
    }
}
