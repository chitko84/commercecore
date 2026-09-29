package com.commercecore.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published when a refund reaches COMPLETED so other modules (notifications)
 * can react without coupling to payment internals.
 */
public record RefundCompletedEvent(UUID customerId, String orderNumber, String currencyCode,
                                   BigDecimal amount, UUID refundId, UUID paymentId) {
}
