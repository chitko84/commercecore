package com.commercecore.inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InventoryHistoryRepository extends JpaRepository<InventoryHistory,UUID>{
    List<InventoryHistory> findAllByInventoryIdOrderByOccurredAtAscIdAsc(UUID inventoryId);
    boolean existsByInventoryIdAndReferenceKeyAndMovementType(UUID inventoryId,String referenceKey,MovementType movementType);
}
