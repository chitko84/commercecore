package com.commercecore.report;

import com.commercecore.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DashboardDtos {
    private DashboardDtos() { }

    public record RecentOrder(UUID id, String orderNumber, OrderStatus status,
                              String customerEmail, BigDecimal grandTotal, Instant createdAt) { }

    public record DashboardResponse(BigDecimal totalRevenue, BigDecimal netRevenue,
                                    Map<OrderStatus, Long> ordersByStatus, long customerCount,
                                    long activeProductCount, long lowStockCount,
                                    long failedPaymentCount, List<RecentOrder> recentOrders) { }
}