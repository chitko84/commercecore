package com.commercecore.inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface InventoryRepository extends JpaRepository<Inventory,UUID>,JpaSpecificationExecutor<Inventory>{
    Optional<Inventory> findByProductId(UUID productId); Optional<Inventory> findByVariantId(UUID variantId);
    boolean existsByProductId(UUID productId); boolean existsByVariantId(UUID variantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Inventory i where i.id=:id") Optional<Inventory> findLockedById(@Param("id") UUID id);
    @Query("select count(i) from Inventory i where i.totalQuantity - i.reservedQuantity - i.soldQuantity <= i.lowStockThreshold")
    long countLowStock();
    @Query("select count(i) from Inventory i where i.totalQuantity - i.reservedQuantity - i.soldQuantity = 0")
    long countOutOfStock();
    @Query("select count(i) from Inventory i where i.totalQuantity - i.reservedQuantity - i.soldQuantity > i.lowStockThreshold")
    long countInStock();
    @Query("select count(i) from Inventory i where i.totalQuantity - i.reservedQuantity - i.soldQuantity <= i.lowStockThreshold " +
            "and i.createdAt >= :from and i.createdAt < :toExclusive")
    long countLowStockBetween(@Param("from") java.time.Instant from, @Param("toExclusive") java.time.Instant toExclusive);
    @Query("select count(i) from Inventory i where i.totalQuantity - i.reservedQuantity - i.soldQuantity = 0 " +
            "and i.createdAt >= :from and i.createdAt < :toExclusive")
    long countOutOfStockBetween(@Param("from") java.time.Instant from, @Param("toExclusive") java.time.Instant toExclusive);
    @Query("select count(i) from Inventory i where i.totalQuantity - i.reservedQuantity - i.soldQuantity > i.lowStockThreshold " +
            "and i.createdAt >= :from and i.createdAt < :toExclusive")
    long countInStockBetween(@Param("from") java.time.Instant from, @Param("toExclusive") java.time.Instant toExclusive);
}
