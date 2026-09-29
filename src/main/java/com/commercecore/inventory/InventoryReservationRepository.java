package com.commercecore.inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation,UUID>{
    Optional<InventoryReservation> findByInventoryIdAndReferenceKey(UUID inventoryId,String referenceKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from InventoryReservation r where r.id=:id") Optional<InventoryReservation> findLockedById(@Param("id") UUID id);
}
