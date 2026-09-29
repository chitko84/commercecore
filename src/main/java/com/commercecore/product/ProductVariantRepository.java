package com.commercecore.product;

import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID id);
    @EntityGraph(attributePaths = {"product", "attributes"})
    List<ProductVariant> findAllByProductIdOrderByCreatedAtAsc(UUID productId);
    @EntityGraph(attributePaths = {"product", "attributes"})
    List<ProductVariant> findAllByProductIdAndActiveTrueOrderByCreatedAtAsc(UUID productId);
    @Override @EntityGraph(attributePaths = {"product", "attributes"}) Optional<ProductVariant> findById(UUID id);
}
