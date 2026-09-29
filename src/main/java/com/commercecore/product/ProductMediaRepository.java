package com.commercecore.product;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductMediaRepository extends JpaRepository<ProductMedia, UUID> {
    List<ProductMedia> findAllByProductIdOrderByDisplayOrderAscCreatedAtAsc(UUID productId);
    Optional<ProductMedia> findByIdAndProductId(UUID id, UUID productId);
    boolean existsByProductIdAndDisplayOrder(UUID productId, int displayOrder);
    boolean existsByProductIdAndDisplayOrderAndIdNot(UUID productId, int displayOrder, UUID id);

    @Modifying
    @Query("update ProductMedia m set m.primary = false where m.product.id = :productId and m.primary = true")
    void clearPrimary(@Param("productId") UUID productId);

    @Modifying
    @Query("update ProductMedia m set m.primary = false where m.product.id = :productId and m.id <> :mediaId and m.primary = true")
    void clearOtherPrimary(@Param("productId") UUID productId, @Param("mediaId") UUID mediaId);
}
