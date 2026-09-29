package com.commercecore.report;

import com.commercecore.common.exception.ApiException;
import com.commercecore.inventory.Inventory;
import com.commercecore.inventory.InventoryRepository;
import com.commercecore.order.CustomerOrder;
import com.commercecore.order.CustomerOrderRepository;
import com.commercecore.order.OrderItemRepository;
import com.commercecore.order.OrderStatus;
import com.commercecore.payment.Payment;
import com.commercecore.payment.PaymentRefund;
import com.commercecore.payment.PaymentRefundRepository;
import com.commercecore.payment.PaymentRepository;
import com.commercecore.payment.PaymentStatus;
import com.commercecore.payment.RefundStatus;
import com.commercecore.review.ReviewRepository;
import com.commercecore.review.ReviewStatus;
import com.commercecore.user.AccountStatus;
import com.commercecore.user.UserAccount;
import com.commercecore.user.UserAccountRepository;
import com.commercecore.user.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportService {

    private static final List<OrderStatus> CAPTURED_ORDER_STATUSES = List.of(
            OrderStatus.CONFIRMED, OrderStatus.PROCESSING,
            OrderStatus.SHIPPED, OrderStatus.DELIVERED);
    private static final int CSV_PAGE_SIZE = 500;

    private final CustomerOrderRepository orders;
    private final OrderItemRepository items;
    private final PaymentRefundRepository refunds;
    private final ReviewRepository reviews;
    private final UserAccountRepository users;
    private final InventoryRepository inventories;
    private final PaymentRepository payments;
    private final Clock clock;

    public ReportService(CustomerOrderRepository orders, OrderItemRepository items,
                         PaymentRefundRepository refunds, ReviewRepository reviews,
                         UserAccountRepository users, InventoryRepository inventories,
                         PaymentRepository payments, Clock clock) {
        this.orders = orders;
        this.items = items;
        this.refunds = refunds;
        this.reviews = reviews;
        this.users = users;
        this.inventories = inventories;
        this.payments = payments;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- Sales

    @Transactional(readOnly = true)
    public ReportDtos.SalesReport sales(Instant from, Instant toExclusive) {
        validateRange(from, toExclusive);
        List<String> statuses = CAPTURED_ORDER_STATUSES.stream().map(Enum::name).toList();
        List<Object[]> orderRows = orders.reportSalesByDay(from, toExclusive, statuses);
        List<Object[]> refundRows = refunds.reportCompletedRefundsByDay(from, toExclusive);

        Map<LocalDate, ReportDtos.SalesDay> days = new LinkedHashMap<>();
        long totalOrders = 0;
        BigDecimal grossSales = BigDecimal.ZERO;
        for (Object[] row : orderRows) {
            LocalDate date = day(row[0]);
            long orderCount = ((Number) row[1]).longValue();
            BigDecimal gross = decimal(row[2]);
            days.put(date, new ReportDtos.SalesDay(date, orderCount, gross, 0L, money(BigDecimal.ZERO),
                    money(gross)));
            totalOrders += orderCount;
            grossSales = grossSales.add(gross);
        }
        long totalRefunds = 0;
        BigDecimal refundTotal = BigDecimal.ZERO;
        for (Object[] row : refundRows) {
            LocalDate date = day(row[0]);
            long refundCount = ((Number) row[1]).longValue();
            BigDecimal refunded = decimal(row[2]);
            ReportDtos.SalesDay existing = days.get(date);
            if (existing == null) {
                days.put(date, new ReportDtos.SalesDay(date, 0L, money(BigDecimal.ZERO), refundCount,
                        money(refunded), money(refunded.negate())));
            } else {
                days.put(date, new ReportDtos.SalesDay(date, existing.orderCount(),
                        existing.grossSales(), refundCount, money(existing.refunds().add(refunded)),
                        money(existing.grossSales().subtract(existing.refunds().add(refunded)))));
            }
            totalRefunds += refundCount;
            refundTotal = refundTotal.add(refunded);
        }
        BigDecimal gross = money(grossSales);
        BigDecimal refundedTotal = money(refundTotal);
        return new ReportDtos.SalesReport(from, toExclusive, totalOrders, totalRefunds,
                gross, refundedTotal, money(gross.subtract(refundedTotal)),
                new ArrayList<>(days.values()));
    }

    @Transactional(readOnly = true)
    public ReportDtos.TopProductsReport topProducts(Instant from, Instant toExclusive, int limit) {
        validateRange(from, toExclusive);
        if (limit < 1 || limit > 50) {
            throw error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Limit must be between 1 and 50.");
        }
        List<String> statuses = CAPTURED_ORDER_STATUSES.stream().map(Enum::name).toList();
        List<Object[]> rows = items.reportTopProducts(from, toExclusive, statuses);
        List<ReportDtos.TopProduct> products = new ArrayList<>();
        for (Object[] row : rows) {
            if (products.size() >= limit) {
                break;
            }
            products.add(new ReportDtos.TopProduct((String) row[0], (String) row[1],
                    ((Number) row[2]).longValue(), money(decimal(row[3]))));
        }
        return new ReportDtos.TopProductsReport(from, toExclusive, products);
    }

    @Transactional(readOnly = true)
    public ReportDtos.ReviewsReport reviews(Instant from, Instant toExclusive) {
        validateRange(from, toExclusive);
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (Object[] row : reviews.ratingDistributionBetween(from, toExclusive, ReviewStatus.APPROVED)) {
            counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        for (int rating = 1; rating <= 5; rating++) {
            counts.putIfAbsent(rating, 0L);
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        BigDecimal average = BigDecimal.ZERO.setScale(2);
        if (total > 0) {
            long points = counts.entrySet().stream()
                    .mapToLong(entry -> entry.getKey() * entry.getValue()).sum();
            average = BigDecimal.valueOf(points)
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        }
        return new ReportDtos.ReviewsReport(from, toExclusive, total, average, counts);
    }

    // ---------------------------------------------------------------- Orders

    @Transactional(readOnly = true)
    public ReportDtos.OrdersReport orders(Instant from, Instant toExclusive, OrderStatus status,
                                          String email, int page, int size) {
        Range range = range(from, toExclusive);
        Specification<CustomerOrder> spec = (root, query, cb) -> cb.conjunction();
        spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), range.from()));
        spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), range.toExclusive()));
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (email != null && !email.isBlank()) {
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("customer").get("email"), normalized));
        }
        Page<CustomerOrder> result = orders.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<ReportDtos.OrderRow> rows = result.getContent().stream()
                .map(order -> new ReportDtos.OrderRow(order.getId(), order.getOrderNumber(),
                        order.getStatus(), order.getCustomer().getEmail(), money(order.getGrandTotal()),
                        order.getCreatedAt()))
                .toList();
        return new ReportDtos.OrdersReport(range.from(), range.toExclusive(),
                orders.reportOrderCount(range.from(), range.toExclusive(), status),
                money(orders.reportOrderTotal(range.from(), range.toExclusive(), status)),
                rows, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    // -------------------------------------------------------------- Customers

    @Transactional(readOnly = true)
    public ReportDtos.CustomersReport customers(Instant from, Instant toExclusive, UserRole role,
                                                AccountStatus status, String email, int page,
                                                int size) {
        Range range = range(from, toExclusive);
        // The customers report defaults to the CUSTOMER role so it stays a
        // customer report unless an admin explicitly asks for another role.
        UserRole effectiveRole = role == null ? UserRole.CUSTOMER : role;
        Specification<UserAccount> spec = (root, query, cb) -> cb.conjunction();
        spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), range.from()));
        spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), range.toExclusive()));
        spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), effectiveRole));
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("accountStatus"), status));
        }
        if (email != null && !email.isBlank()) {
            String pattern = "%" + email.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("email")), pattern));
        }
        Page<UserAccount> result = users.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<ReportDtos.CustomerRow> rows = result.getContent().stream()
                .map(user -> new ReportDtos.CustomerRow(user.getId(), user.getEmail(),
                        user.getFullName(), user.getRole(), user.getAccountStatus(),
                        user.isEmailVerified(), user.getCreatedAt()))
                .toList();
        long total = users.count((root, query, cb) -> {
            jakarta.persistence.criteria.Predicate[] predicates = {
                    cb.greaterThanOrEqualTo(root.get("createdAt"), range.from()),
                    cb.lessThan(root.get("createdAt"), range.toExclusive()),
                    cb.equal(root.get("role"), effectiveRole),
                    status == null ? cb.conjunction() : cb.equal(root.get("accountStatus"), status)
            };
            return cb.and(predicates);
        });
        return new ReportDtos.CustomersReport(range.from(), range.toExclusive(), total,
                users.countNewByRoleBetween(effectiveRole, range.from(), range.toExclusive()),
                money(orders.reportCapturedTotalBetween(range.from(), range.toExclusive(),
                        CAPTURED_ORDER_STATUSES)),
                rows, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    // -------------------------------------------------------------- Inventory

    @Transactional(readOnly = true)
    public ReportDtos.InventoryReport inventory(Instant from, Instant toExclusive,
                                                String ownerType, String stockStatus, int page,
                                                int size) {
        Range range = range(from, toExclusive);
        Specification<Inventory> spec = (root, query, cb) -> cb.conjunction();
        spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), range.from()));
        spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), range.toExclusive()));
        if (ownerType != null) {
            if ("PRODUCT".equalsIgnoreCase(ownerType)) {
                spec = spec.and((root, query, cb) -> cb.isNotNull(root.get("product")));
            } else if ("VARIANT".equalsIgnoreCase(ownerType)) {
                spec = spec.and((root, query, cb) -> cb.isNotNull(root.get("variant")));
            } else {
                throw error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                        "ownerType must be PRODUCT or VARIANT.");
            }
        }
        if (stockStatus != null) {
            spec = spec.and(stockStatusSpec(stockStatus));
        }
        Page<Inventory> result = inventories.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
        List<ReportDtos.InventoryRow> rows = result.getContent().stream()
                .map(inv -> new ReportDtos.InventoryRow(inv.getId(),
                        inv.getProduct() != null ? "PRODUCT" : "VARIANT",
                        inv.getProduct() != null ? inv.getProduct().getSku() : inv.getVariant().getSku(),
                        inv.getProduct() != null ? inv.getProduct().getName() : inv.getVariant().getSku(),
                        inv.getTotalQuantity(), inv.getReservedQuantity(), inv.getSoldQuantity(),
                        inv.availableQuantity(), inv.getLowStockThreshold(), inv.stockStatus(),
                        inv.getUpdatedAt()))
                .toList();
        return new ReportDtos.InventoryReport(range.from(), range.toExclusive(),
                inventories.countInStockBetween(range.from(), range.toExclusive()),
                inventories.countLowStockBetween(range.from(), range.toExclusive()),
                inventories.countOutOfStockBetween(range.from(), range.toExclusive()),
                rows, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    // --------------------------------------------------------------- Payments

    @Transactional(readOnly = true)
    public ReportDtos.PaymentsReport payments(Instant from, Instant toExclusive,
                                              PaymentStatus status, int page, int size) {
        Range range = range(from, toExclusive);
        Specification<Payment> spec = (root, query, cb) -> cb.conjunction();
        spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), range.from()));
        spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), range.toExclusive()));
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        Page<Payment> result = payments.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<ReportDtos.PaymentRow> rows = result.getContent().stream()
                .map(payment -> new ReportDtos.PaymentRow(payment.getId(),
                        payment.getOrder().getOrderNumber(), payment.getCustomer().getEmail(),
                        payment.getPaymentMethod(), payment.getStatus(), money(payment.getAmount()),
                        payment.isReconciliationRequired(), payment.getCreatedAt()))
                .toList();
        Map<PaymentStatus, Long> byStatus = new LinkedHashMap<>();
        for (PaymentStatus value : PaymentStatus.values()) {
            byStatus.put(value, 0L);
        }
        for (Object[] row : payments.countByStatusBetween(range.from(), range.toExclusive())) {
            byStatus.put((PaymentStatus) row[0], ((Number) row[1]).longValue());
        }
        return new ReportDtos.PaymentsReport(range.from(), range.toExclusive(),
                payments.reportCount(range.from(), range.toExclusive(), status),
                money(payments.reportTotal(range.from(), range.toExclusive(), status)),
                byStatus, rows, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    // ---------------------------------------------------------------- Refunds

    @Transactional(readOnly = true)
    public ReportDtos.RefundsReport refunds(Instant from, Instant toExclusive, RefundStatus status,
                                            int page, int size) {
        Range range = range(from, toExclusive);
        Specification<PaymentRefund> spec = (root, query, cb) -> cb.conjunction();
        spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), range.from()));
        spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), range.toExclusive()));
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        Page<PaymentRefund> result = refunds.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<ReportDtos.RefundRow> rows = result.getContent().stream()
                .map(refund -> new ReportDtos.RefundRow(refund.getId(),
                        refund.getPayment().getOrder().getOrderNumber(), money(refund.getAmount()),
                        refund.getStatus(), refund.getCreatedBy() == null ? null
                                : refund.getCreatedBy().getEmail(), refund.getCreatedAt()))
                .toList();
        return new ReportDtos.RefundsReport(range.from(), range.toExclusive(),
                refunds.reportCount(range.from(), range.toExclusive(), status),
                money(refunds.reportTotal(range.from(), range.toExclusive(), status)),
                rows, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    // ------------------------------------------------------ CSV full listings

    @Transactional(readOnly = true)
    public List<ReportDtos.OrderRow> allOrders(Instant from, Instant toExclusive, OrderStatus status,
                                               String email) {
        List<ReportDtos.OrderRow> all = new ArrayList<>();
        int page = 0;
        while (true) {
            ReportDtos.OrdersReport report = orders(from, toExclusive, status, email, page, CSV_PAGE_SIZE);
            all.addAll(report.orders());
            if (report.orders().size() < CSV_PAGE_SIZE || page >= report.totalPages() - 1) {
                break;
            }
            page++;
        }
        return all;
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.CustomerRow> allCustomers(Instant from, Instant toExclusive,
                                                     UserRole role, AccountStatus status, String email) {
        List<ReportDtos.CustomerRow> all = new ArrayList<>();
        int page = 0;
        while (true) {
            ReportDtos.CustomersReport report = customers(from, toExclusive, role, status, email, page, CSV_PAGE_SIZE);
            all.addAll(report.customers());
            if (report.customers().size() < CSV_PAGE_SIZE || page >= report.totalPages() - 1) {
                break;
            }
            page++;
        }
        return all;
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.InventoryRow> allInventory(Instant from, Instant toExclusive,
                                                      String ownerType, String stockStatus) {
        List<ReportDtos.InventoryRow> all = new ArrayList<>();
        int page = 0;
        while (true) {
            ReportDtos.InventoryReport report = inventory(from, toExclusive, ownerType, stockStatus, page, CSV_PAGE_SIZE);
            all.addAll(report.rows());
            if (report.rows().size() < CSV_PAGE_SIZE || page >= report.totalPages() - 1) {
                break;
            }
            page++;
        }
        return all;
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.PaymentRow> allPayments(Instant from, Instant toExclusive,
                                                   PaymentStatus status) {
        List<ReportDtos.PaymentRow> all = new ArrayList<>();
        int page = 0;
        while (true) {
            ReportDtos.PaymentsReport report = payments(from, toExclusive, status, page, CSV_PAGE_SIZE);
            all.addAll(report.payments());
            if (report.payments().size() < CSV_PAGE_SIZE || page >= report.totalPages() - 1) {
                break;
            }
            page++;
        }
        return all;
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.RefundRow> allRefunds(Instant from, Instant toExclusive,
                                                 RefundStatus status) {
        List<ReportDtos.RefundRow> all = new ArrayList<>();
        int page = 0;
        while (true) {
            ReportDtos.RefundsReport report = refunds(from, toExclusive, status, page, CSV_PAGE_SIZE);
            all.addAll(report.refunds());
            if (report.refunds().size() < CSV_PAGE_SIZE || page >= report.totalPages() - 1) {
                break;
            }
            page++;
        }
        return all;
    }

    // ---------------------------------------------------------------- Helpers

    private static Specification<Inventory> stockStatusSpec(String stockStatus) {
        String normalized = stockStatus.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "IN" -> (root, query, cb) -> cb.greaterThan(
                    available(root, query, cb), root.<Long>get("lowStockThreshold"));
            case "LOW" -> (root, query, cb) -> cb.and(
                    cb.greaterThan(available(root, query, cb), 0L),
                    cb.lessThanOrEqualTo(available(root, query, cb), root.<Long>get("lowStockThreshold")));
            case "OUT" -> (root, query, cb) -> cb.equal(available(root, query, cb), 0L);
            default -> throw error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "stockStatus must be IN, LOW or OUT.");
        };
    }

    private static jakarta.persistence.criteria.Expression<Long> available(
            jakarta.persistence.criteria.Root<Inventory> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.diff(cb.diff(root.<Long>get("totalQuantity"), root.<Long>get("reservedQuantity")),
                root.<Long>get("soldQuantity"));
    }

    private record Range(Instant from, Instant toExclusive) { }

    private Range range(Instant from, Instant toExclusive) {
        Instant effectiveFrom = from == null ? Instant.EPOCH : from;
        Instant effectiveTo = toExclusive == null
                ? clock.instant().plus(Duration.ofDays(1))
                : toExclusive;
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_REPORT_RANGE",
                    "A valid from/to range is required.");
        }
        return new Range(effectiveFrom, effectiveTo);
    }

    private static void validateRange(Instant from, Instant toExclusive) {
        if (from == null || toExclusive == null || !from.isBefore(toExclusive)) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_REPORT_RANGE",
                    "A valid from/to range is required.");
        }
    }

    private static LocalDate day(Object value) {
        Instant instant;
        if (value instanceof Instant direct) {
            instant = direct;
        } else if (value instanceof Timestamp timestamp) {
            instant = timestamp.toInstant();
        } else if (value instanceof java.util.Date date) {
            instant = date.toInstant();
        } else {
            throw new IllegalArgumentException("Unsupported report date column type: "
                    + value.getClass().getName());
        }
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}