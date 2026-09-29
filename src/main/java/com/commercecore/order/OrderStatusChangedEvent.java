package com.commercecore.order;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after an order status transition is durably recorded so other
 * modules (notifications, reporting) can react without coupling to the
 * order module's internals.
 */
public record OrderStatusChangedEvent(UUID orderId, UUID customerId, String orderNumber,
                                      OrderStatus fromStatus, OrderStatus toStatus,
                                      UUID actorUserId, String reason, String reference,
                                      Instant occurredAt) {
}
