package com.commercecore.report;

import com.commercecore.inventory.StockStatus;
import com.commercecore.order.OrderStatus;
import com.commercecore.payment.PaymentMethod;
import com.commercecore.payment.PaymentStatus;
import com.commercecore.payment.RefundStatus;
import com.commercecore.user.AccountStatus;
import com.commercecore.user.UserRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReportDtos {
    private ReportDtos() { }

    // --- Sales ---
    public record SalesDay(LocalDate date, long orderCount, BigDecimal grossSales,
                           long refundCount, BigDecimal refunds, BigDecimal netSales) { }

    public record SalesReport(Instant from, Instant toExclusive, long totalOrders,
                              long totalRefunds, BigDecimal grossSales, BigDecimal refunds,
                              BigDecimal netSales, List<SalesDay> days) { }

    // --- Top products ---
    public record TopProduct(String productName, String sku, long unitsSold,
                             BigDecimal revenue) { }

    public record TopProductsReport(Instant from, Instant toExclusive,
                                    List<TopProduct> products) { }

    // --- Reviews ---
    public record ReviewsReport(Instant from, Instant toExclusive, long reviewCount,
                                BigDecimal averageRating, Map<Integer, Long> distribution) { }

    // --- Orders ---
    public record OrderRow(UUID id, String orderNumber, OrderStatus status, String customerEmail,
                           BigDecimal grandTotal, Instant createdAt) { }

    public record OrdersReport(Instant from, Instant toExclusive, long totalOrders,
                               BigDecimal totalAmount, List<OrderRow> orders, int page, int size,
                               long totalElements, int totalPages) { }

    // --- Customers ---
    public record CustomerRow(UUID id, String email, String fullName, UserRole role,
                              AccountStatus accountStatus, boolean emailVerified,
                              Instant createdAt) { }

    public record CustomersReport(Instant from, Instant toExclusive, long totalCustomers,
                                  long newCustomers, BigDecimal totalSpent,
                                  List<CustomerRow> customers, int page, int size,
                                  long totalElements, int totalPages) { }

    // --- Inventory ---
    public record InventoryRow(UUID id, String ownerType, String sku, String ownerName,
                               long totalQuantity, long reservedQuantity, long soldQuantity,
                               long availableQuantity, long lowStockThreshold,
                               StockStatus stockStatus, Instant updatedAt) { }

    public record InventoryReport(Instant from, Instant toExclusive, long inStock,
                                  long lowStock, long outOfStock, List<InventoryRow> rows,
                                  int page, int size, long totalElements, int totalPages) { }

    // --- Payments ---
    public record PaymentRow(UUID id, String orderNumber, String customerEmail,
                             PaymentMethod paymentMethod, PaymentStatus status, BigDecimal amount,
                             boolean reconciliationRequired, Instant createdAt) { }

    public record PaymentsReport(Instant from, Instant toExclusive, long totalPayments,
                                 BigDecimal totalAmount, Map<PaymentStatus, Long> byStatus,
                                 List<PaymentRow> payments, int page, int size,
                                 long totalElements, int totalPages) { }

    // --- Refunds ---
    public record RefundRow(UUID id, String orderNumber, BigDecimal amount, RefundStatus status,
                            String createdByEmail, Instant createdAt) { }

    public record RefundsReport(Instant from, Instant toExclusive, long totalRefunds,
                                BigDecimal totalAmount, List<RefundRow> refunds, int page,
                                int size, long totalElements, int totalPages) { }
}