package com.commercecore.product;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    long countByActiveTrue();
    Optional<Product> findBySlug(String slug);
    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, UUID id);
    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, UUID id);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findBySlugAndActiveTrueAndCategoryActiveTrue(String slug);

    @Override
    @EntityGraph(attributePaths = "category")
    Optional<Product> findById(UUID id);
}
