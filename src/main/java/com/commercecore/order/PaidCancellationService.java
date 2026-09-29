package com.commercecore.order;

import com.commercecore.audit.AuditService;
import com.commercecore.common.exception.ApiException;
import com.commercecore.inventory.InventoryReservation;
import com.commercecore.inventory.InventoryService;
import com.commercecore.payment.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaidCancellationService {
    private final CustomerOrderRepository orders;
    private final OrderItemRepository items;
    private final OrderStatusHistoryRepository history;
    private final InventoryService inventory;
    private final PaymentRepository payments;
    private final PaymentService paymentService;
    private final OrderService orderService;
    private final Clock clock;
    private final ApplicationEventPublisher publisher;
    private final AuditService audits;

    public PaidCancellationService(CustomerOrderRepository orders,OrderItemRepository items,
            OrderStatusHistoryRepository history,InventoryService inventory,PaymentRepository payments,
            PaymentService paymentService,OrderService orderService,Clock clock,
            ApplicationEventPublisher publisher,AuditService audits){
        this.orders=orders;this.items=items;this.history=history;this.inventory=inventory;
        this.payments=payments;this.paymentService=paymentService;this.orderService=orderService;this.clock=clock;
        this.publisher=publisher;this.audits=audits;
    }

    @Transactional
    public OrderDtos.OrderResponse cancel(UUID actor,UUID orderId){
        CustomerOrder order=orders.findLockedById(orderId)
                .orElseThrow(()->err("ORDER_NOT_FOUND","Order was not found."));
        if(order.getStatus()==OrderStatus.CANCELLED)return orderService.staffOrder(orderId);
        if(order.getStatus()!=OrderStatus.CONFIRMED&&order.getStatus()!=OrderStatus.PROCESSING)
            throw err("PAID_ORDER_CANCELLATION_NOT_ALLOWED","Only confirmed or processing orders before shipment can be cancelled.");
        Payment payment=payments.findAllByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .filter(found->found.getStatus()==PaymentStatus.PAID||found.getStatus()==PaymentStatus.PARTIALLY_REFUNDED||found.getStatus()==PaymentStatus.REFUNDED)
                .findFirst().orElseThrow(()->err("PAYMENT_NOT_FOUND","Paid payment was not found."));
        PaymentDtos.RefundResponse refund=paymentService.refund(actor,payment.getId(),"CANCEL:"+orderId,new PaymentDtos.RefundRequest(null));
        if(refund.status()!=RefundStatus.COMPLETED)
            throw err("PAYMENT_RECONCILIATION_REQUIRED","Cancellation waits for a confirmed completed refund.");
        for(OrderItem item:items.findAllByOrderIdOrderByCreatedAtAscIdAsc(orderId)){
            InventoryReservation reservation=item.getInventoryReservation();
            inventory.reverseSale(reservation.getInventory().getId(),item.getQuantity(),
                    "CANCEL:"+orderId+":"+item.getId(),actor);
        }
        OrderStatus from=order.getStatus();order.transition(OrderStatus.CANCELLED);
        Instant occurredAt=clock.instant();
        history.save(new OrderStatusHistory(order,from,OrderStatus.CANCELLED,actor,
                "PAID_ORDER_CANCELLED","CANCEL:"+orderId,occurredAt));
        publisher.publishEvent(new OrderStatusChangedEvent(order.getId(),order.getCustomer().getId(),
                order.getOrderNumber(),from,OrderStatus.CANCELLED,actor,"PAID_ORDER_CANCELLED",
                "CANCEL:"+orderId,occurredAt));
        audits.record(actor,"PAID_ORDER_CANCELLED","ORDER","ORDER",order.getId().toString(),
                "orderNumber="+order.getOrderNumber()+"; from="+from+"; to=CANCELLED"
                        +"; refund="+refund.id());
        return orderService.staffOrder(orderId);
    }

    private static ApiException err(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
}
