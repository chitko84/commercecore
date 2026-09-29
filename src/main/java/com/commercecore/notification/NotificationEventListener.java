package com.commercecore.notification;

import com.commercecore.order.OrderStatus;
import com.commercecore.order.OrderStatusChangedEvent;
import com.commercecore.payment.RefundCompletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;

/**
 * Converts order lifecycle and refund events into customer notifications.
 * Runs synchronously inside the publisher's transaction so a notification
 * commits or rolls back with the order/payment change that caused it.
 * Phase 9 may replace this with async delivery via Kafka/Redis.
 */
@Component
public class NotificationEventListener {

    private final NotificationService notifications;

    public NotificationEventListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        NotificationType type;
        String title;
        String body;
        switch (event.toStatus()) {
            case PENDING -> {
                type = NotificationType.ORDER_CREATED;
                title = "Order created";
                body = "Your order " + event.orderNumber() + " was received and is awaiting payment.";
            }
            case CONFIRMED -> {
                type = NotificationType.ORDER_CONFIRMED;
                title = "Payment confirmed";
                body = "Payment for order " + event.orderNumber()
                        + " was confirmed and your items are being prepared.";
            }
            case PROCESSING -> {
                type = NotificationType.ORDER_PROCESSING;
                title = "Order in progress";
                body = "Order " + event.orderNumber() + " is being prepared for shipment.";
            }
            case SHIPPED -> {
                type = NotificationType.ORDER_SHIPPED;
                title = "Order shipped";
                body = "Order " + event.orderNumber() + " has shipped.";
            }
            case DELIVERED -> {
                type = NotificationType.ORDER_DELIVERED;
                title = "Order delivered";
                body = "Order " + event.orderNumber() + " was delivered. Enjoy your purchase!";
            }
            case CANCELLED -> {
                type = NotificationType.ORDER_CANCELLED;
                title = "Order cancelled";
                body = "Order " + event.orderNumber() + " was cancelled.";
            }
            default -> throw new IllegalStateException("Unexpected order status: " + event.toStatus());
        }
        notifications.notify(event.customerId(), type, title, body, "ORDER", event.orderId().toString());
    }

    @EventListener
    public void onRefundCompleted(RefundCompletedEvent event) {
        String amount = event.currencyCode() + " "
                + event.amount().setScale(2, RoundingMode.HALF_UP).toPlainString();
        notifications.notify(event.customerId(), NotificationType.REFUND_COMPLETED,
                "Refund completed",
                "A refund of " + amount + " for order " + event.orderNumber() + " was completed.",
                "REFUND", event.refundId().toString());
    }
}
