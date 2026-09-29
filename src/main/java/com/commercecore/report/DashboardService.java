package com.commercecore.report;

import com.commercecore.inventory.InventoryRepository;
import com.commercecore.order.CustomerOrderRepository;
import com.commercecore.order.OrderStatus;
import com.commercecore.payment.PaymentRefundRepository;
import com.commercecore.payment.PaymentRepository;
import com.commercecore.payment.PaymentStatus;
import com.commercecore.product.ProductRepository;
import com.commercecore.user.UserAccountRepository;
import com.commercecore.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private static final List<OrderStatus> CAPTURED_ORDER_STATUSES = List.of(
            OrderStatus.CONFIRMED, OrderStatus.PROCESSING,
            OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final CustomerOrderRepository orders;
    private final PaymentRefundRepository refunds;
    private final UserAccountRepository users;
    private final ProductRepository products;
    private final InventoryRepository inventories;
    private final PaymentRepository payments;

    public DashboardService(CustomerOrderRepository orders, PaymentRefundRepository refunds,
                            UserAccountRepository users, ProductRepository products,
                            InventoryRepository inventories, PaymentRepository payments) {
        this.orders = orders;
        this.refunds = refunds;
        this.users = users;
        this.products = products;
        this.inventories = inventories;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public DashboardDtos.DashboardResponse dashboard() {
        // Revenue only counts captured orders (PENDING and CANCELLED are never
        // revenue) and completed refunds reduce net revenue.
        BigDecimal totalRevenue = money(orders.reportCapturedTotal(CAPTURED_ORDER_STATUSES));
        BigDecimal refunded = money(refunds.reportCompletedRefundTotal());
        BigDecimal netRevenue = money(totalRevenue.subtract(refunded));

        Map<OrderStatus, Long> ordersByStatus = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            ordersByStatus.put(status, 0L);
        }
        for (Object[] row : orders.countByStatusAll()) {
            ordersByStatus.put((OrderStatus) row[0], ((Number) row[1]).longValue());
        }

        List<DashboardDtos.RecentOrder> recent = orders.findTop10ByOrderByCreatedAtDesc().stream()
                .map(order -> new DashboardDtos.RecentOrder(order.getId(), order.getOrderNumber(),
                        order.getStatus(), order.getCustomer().getEmail(), money(order.getGrandTotal()),
                        order.getCreatedAt()))
                .toList();

        return new DashboardDtos.DashboardResponse(totalRevenue, netRevenue, ordersByStatus,
                users.countByRole(UserRole.CUSTOMER), products.countByActiveTrue(),
                inventories.countLowStock(), payments.countByStatus(PaymentStatus.FAILED), recent);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}