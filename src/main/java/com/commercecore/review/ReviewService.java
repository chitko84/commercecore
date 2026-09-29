package com.commercecore.review;

import com.commercecore.audit.AuditService;
import com.commercecore.cache.CatalogCache;
import com.commercecore.common.exception.ApiException;
import com.commercecore.order.OrderItemRepository;
import com.commercecore.order.OrderStatus;
import com.commercecore.product.Product;
import com.commercecore.product.ProductRepository;
import com.commercecore.user.UserAccount;
import com.commercecore.user.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ReviewService {

    /**
     * A review is only postable after the order that included the product has
     * been DELIVERED. PENDING/CONFIRMED/PROCESSING/SHIPPED do not qualify:
     * the customer must actually have received the purchase.
     */
    private static final List<OrderStatus> VERIFIED_ORDER_STATUSES = List.of(OrderStatus.DELIVERED);

    private final ReviewRepository reviews;
    private final ProductRepository products;
    private final OrderItemRepository orderItems;
    private final UserAccountRepository users;
    private final AuditService audits;
    private final CatalogCache cache;

    public ReviewService(ReviewRepository reviews, ProductRepository products,
                         OrderItemRepository orderItems, UserAccountRepository users,
                         AuditService audits, CatalogCache cache) {
        this.reviews = reviews;
        this.products = products;
        this.orderItems = orderItems;
        this.users = users;
        this.audits = audits;
        this.cache = cache;
    }

    @Transactional
    public ReviewDtos.MyReviewResponse create(UUID customerId, String productSlug,
                                              ReviewDtos.ReviewRequest request) {
        Product product = productBySlug(productSlug);
        requireVerifiedPurchase(customerId, product.getId());
        UserAccount customer = users.findById(customerId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND",
                        "The account was not found."));
        Review review;
        try {
            review = new Review(product, customer, request.rating(), request.title(), request.body());
        } catch (IllegalArgumentException invalid) {
            throw error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", invalid.getMessage());
        }
        if (reviews.existsByCustomerIdAndProductId(customerId, product.getId())) {
            throw error(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS",
                    "You have already reviewed this product.");
        }
        try {
            reviews.saveAndFlush(review);
        } catch (DataIntegrityViolationException duplicate) {
            throw error(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS",
                    "You have already reviewed this product.");
        }
        return myResponse(review);
    }

    @Transactional(readOnly = true)
    public ReviewDtos.MyReviewResponse getMy(UUID customerId, UUID reviewId) {
        return myResponse(review(customerId, reviewId));
    }

    @Transactional
    public ReviewDtos.MyReviewResponse update(UUID customerId, UUID reviewId,
                                              ReviewDtos.ReviewRequest request) {
        Review review = review(customerId, reviewId);
        try {
            review.update(request.rating(), request.title(), request.body());
        } catch (IllegalArgumentException invalid) {
            throw error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", invalid.getMessage());
        }
        return myResponse(review);
    }

    @Transactional
    public void delete(UUID customerId, UUID reviewId) {
        Review review = review(customerId, reviewId);
        String details = "rating=" + review.getRating() + "; product=" + review.getProduct().getId()
                + (review.getTitle() == null ? "" : "; title=" + review.getTitle());
        reviews.delete(review);
        audits.record(customerId, "REVIEW_DELETED", "REVIEW", "REVIEW",
                reviewId.toString(), details);
    }

    @Transactional(readOnly = true)
    public ReviewDtos.PageResponse<ReviewDtos.PublicReviewResponse> publicPage(
            String productSlug, int page, int size) {
        Product product = productBySlug(productSlug);
        Page<Review> result = reviews.findAllByProductIdAndStatusOrderByCreatedAtDescIdDesc(
                product.getId(), ReviewStatus.APPROVED, pageRequest(page, size));
        return new ReviewDtos.PageResponse<>(
                result.getContent().stream().map(this::publicResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Transactional(readOnly = true)
    public ReviewDtos.ReviewSummaryResponse publicSummary(String productSlug) {
        Product product = productBySlug(productSlug);
        return cache.get(CatalogCache.rating(product.getSlug()),ReviewDtos.ReviewSummaryResponse.class,
                ()->summary(distribution(reviews.ratingDistributionByProduct(product.getId(), ReviewStatus.APPROVED))));
    }

    @Transactional(readOnly = true)
    public ReviewDtos.PageResponse<ReviewDtos.MyReviewResponse> myPage(
            UUID customerId, int page, int size) {
        Page<Review> result = reviews.findAllByCustomerIdOrderByCreatedAtDescIdDesc(
                customerId, pageRequest(page, size));
        return new ReviewDtos.PageResponse<>(
                result.getContent().stream().map(this::myResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Transactional(readOnly = true)
    public ReviewDtos.PageResponse<ReviewDtos.ReviewAdminResponse> adminPage(
            ReviewStatus status, UUID productId, int page, int size) {
        Specification<Review> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (productId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("product").get("id"), productId));
        }
        Page<Review> result = reviews.findAll(spec,
                pageRequest(page, size).withSort(Sort.by(Sort.Direction.DESC, "createdAt")));
        return new ReviewDtos.PageResponse<>(
                result.getContent().stream().map(this::adminResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Transactional
    public ReviewDtos.ReviewAdminResponse moderate(UUID actorId, UUID reviewId,
                                                   ReviewStatus target) {
        if (target != ReviewStatus.APPROVED && target != ReviewStatus.REJECTED) {
            throw error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "Moderation only approves or rejects a review.");
        }
        Review review = reviews.findById(reviewId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND",
                        "Review was not found."));
        if (review.getStatus() == target) {
            return adminResponse(review);
        }
        review.changeStatus(target);
        String action = target == ReviewStatus.REJECTED ? "REVIEW_REJECTED" : "REVIEW_APPROVED";
        audits.record(actorId, action, "REVIEW", "REVIEW", reviewId.toString(),
                "status=" + review.getStatus() + "; product=" + review.getProduct().getId()
                        + "; rating=" + review.getRating());
        cache.evictAfterCommit(CatalogCache.rating(review.getProduct().getSlug()));
        return adminResponse(review);
    }

    private void requireVerifiedPurchase(UUID customerId, UUID productId) {
        boolean verified = orderItems
                .existsByOrder_Customer_IdAndOrder_StatusInAndSourceProduct_Id(
                        customerId, VERIFIED_ORDER_STATUSES, productId);
        if (!verified) {
            throw error(HttpStatus.FORBIDDEN, "REVIEW_REQUIRES_VERIFIED_PURCHASE",
                    "Only customers who received this product (order delivered) can review it.");
        }
    }

    private Review review(UUID customerId, UUID reviewId) {
        return reviews.findByIdAndCustomerId(reviewId, customerId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "REVIEW_NOT_FOUND",
                        "Review was not found."));
    }

    private Product productBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product was not found.");
        }
        return products.findBySlug(slug.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND",
                        "Product was not found."));
    }

    private static Map<Integer, Long> distribution(List<Object[]> rows) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        for (int rating = 1; rating <= 5; rating++) {
            counts.putIfAbsent(rating, 0L);
        }
        return counts;
    }

    private static ReviewDtos.ReviewSummaryResponse summary(Map<Integer, Long> counts) {
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            return new ReviewDtos.ReviewSummaryResponse(0L, BigDecimal.ZERO.setScale(2), counts);
        }
        long points = counts.entrySet().stream()
                .mapToLong(entry -> entry.getKey() * entry.getValue()).sum();
        BigDecimal average = BigDecimal.valueOf(points)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new ReviewDtos.ReviewSummaryResponse(total, average, counts);
    }

    private ReviewDtos.PublicReviewResponse publicResponse(Review review) {
        return new ReviewDtos.PublicReviewResponse(review.getId(),
                displayName(review.getCustomer().getFullName()), review.getRating(),
                review.getTitle(), review.getBody(), review.getCreatedAt());
    }

    private ReviewDtos.MyReviewResponse myResponse(Review review) {
        return new ReviewDtos.MyReviewResponse(review.getId(), review.getProduct().getId(),
                review.getProduct().getName(), review.getProduct().getSlug(),
                review.getRating(), review.getTitle(), review.getBody(), review.getStatus(),
                review.getCreatedAt(), review.getUpdatedAt());
    }

    private ReviewDtos.ReviewAdminResponse adminResponse(Review review) {
        return new ReviewDtos.ReviewAdminResponse(review.getId(), review.getProduct().getId(),
                review.getProduct().getName(), review.getCustomer().getId(), review.getRating(),
                review.getTitle(), review.getBody(), review.getStatus(),
                review.getCreatedAt(), review.getUpdatedAt());
    }

    private static String displayName(String fullName) {
        if (fullName == null) {
            return "Customer";
        }
        int index = fullName.indexOf(' ');
        String first = index < 0 ? fullName : fullName.substring(0, index);
        return first.isBlank() ? "Customer" : first;
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Page parameters are invalid.");
        }
        return PageRequest.of(page, size);
    }

    private static ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}
