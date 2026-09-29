package com.commercecore.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID>,
        JpaSpecificationExecutor<Review> {

    Optional<Review> findByIdAndCustomerId(UUID id, UUID customerId);

    boolean existsByCustomerIdAndProductId(UUID customerId, UUID productId);

    Page<Review> findAllByProductIdAndStatusOrderByCreatedAtDescIdDesc(
            UUID productId, ReviewStatus status, Pageable pageable);

    Page<Review> findAllByCustomerIdOrderByCreatedAtDescIdDesc(UUID customerId, Pageable pageable);

    @Query("select r.rating, count(r) from Review r where r.product.id = :productId " +
            "and r.status = :status group by r.rating")
    List<Object[]> ratingDistributionByProduct(@Param("productId") UUID productId,
                                               @Param("status") ReviewStatus status);

    @Query("select r.rating, count(r) from Review r where r.status = :status " +
            "and r.createdAt >= :from and r.createdAt < :toExclusive group by r.rating")
    List<Object[]> ratingDistributionBetween(@Param("from") Instant from,
                                             @Param("toExclusive") Instant toExclusive,
                                             @Param("status") ReviewStatus status);
}
