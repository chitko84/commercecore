package com.commercecore.review;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/me/reviews")
@SecurityRequirement(name = "bearerAuth")
public class MyReviewController {

    private final ReviewService reviews;

    public MyReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    ReviewDtos.PageResponse<ReviewDtos.MyReviewResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reviews.myPage(UUID.fromString(jwt.getSubject()), page, size);
    }

    @GetMapping("/{id}")
    ReviewDtos.MyReviewResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return reviews.getMy(UUID.fromString(jwt.getSubject()), id);
    }

    @PutMapping("/{id}")
    ReviewDtos.MyReviewResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                       @Valid @RequestBody ReviewDtos.ReviewRequest request) {
        return reviews.update(UUID.fromString(jwt.getSubject()), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        reviews.delete(UUID.fromString(jwt.getSubject()), id);
    }
}
