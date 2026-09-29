package com.commercecore.review;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/reviews")
@SecurityRequirement(name = "bearerAuth")
public class AdminReviewController {

    private final ReviewService reviews;

    public AdminReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    ReviewDtos.PageResponse<ReviewDtos.ReviewAdminResponse> list(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reviews.adminPage(status, productId, page, size);
    }

    @PostMapping("/{id}/approve")
    ReviewDtos.ReviewAdminResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return reviews.moderate(UUID.fromString(jwt.getSubject()), id, ReviewStatus.APPROVED);
    }

    @PostMapping("/{id}/reject")
    ReviewDtos.ReviewAdminResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return reviews.moderate(UUID.fromString(jwt.getSubject()), id, ReviewStatus.REJECTED);
    }
}
