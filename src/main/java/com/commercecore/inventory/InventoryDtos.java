package com.commercecore.inventory;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class InventoryDtos {
    private InventoryDtos() { }
    public record InitializeRequest(UUID productId, UUID variantId, @PositiveOrZero long initialQuantity,
                                    @PositiveOrZero long lowStockThreshold) { }
    public record RestockRequest(@Positive long quantity, @Size(max=160) String reference,
                                 @Size(max=500) String reason) { }
    public record AdjustmentRequest(long delta, @NotBlank @Size(max=500) String reason,
                                    @Size(max=160) String reference) { }
    public record ReservationRequest(@Positive long quantity, @NotBlank @Size(max=160) String referenceKey,
                                     Instant expiresAt) { }
    public record InventoryResponse(UUID id, UUID productId, UUID variantId, long totalQuantity,
            long reservedQuantity, long soldQuantity, long availableQuantity, long lowStockThreshold,
            StockStatus stockStatus, Instant createdAt, Instant updatedAt) { }
    public record ReservationResponse(UUID id, UUID inventoryId, long quantity, ReservationStatus status,
            String referenceKey, Instant expiresAt, Instant createdAt, Instant updatedAt) { }
    public record HistoryResponse(UUID id, MovementType movementType, long quantity,
            long beforeTotal, long beforeReserved, long beforeSold,
            long afterTotal, long afterReserved, long afterSold,
            String reference, String reason, UUID actorUserId, Instant occurredAt) { }
}
