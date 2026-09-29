package com.commercecore.report;

import com.commercecore.order.OrderStatus;
import com.commercecore.payment.PaymentStatus;
import com.commercecore.payment.RefundStatus;
import com.commercecore.user.AccountStatus;
import com.commercecore.user.UserRole;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/admin/reports")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/sales")
    ReportDtos.SalesReport sales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reports.sales(from, to);
    }

    @GetMapping("/top-products")
    ReportDtos.TopProductsReport topProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return reports.topProducts(from, to, limit);
    }

    @GetMapping("/reviews")
    ReportDtos.ReviewsReport reviews(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return reports.reviews(from, to);
    }

    @GetMapping("/orders")
    ReportDtos.OrdersReport orders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String customerEmail,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reports.orders(from, to, status, customerEmail, page, size);
    }

    @GetMapping("/customers")
    ReportDtos.CustomersReport customers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reports.customers(from, to, role, status, email, page, size);
    }

    @GetMapping("/inventory")
    ReportDtos.InventoryReport inventory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) String stockStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reports.inventory(from, to, ownerType, stockStatus, page, size);
    }

    @GetMapping("/payments")
    ReportDtos.PaymentsReport payments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reports.payments(from, to, status, page, size);
    }

    @GetMapping("/refunds")
    ReportDtos.RefundsReport refunds(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) RefundStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reports.refunds(from, to, status, page, size);
    }

    // ------------------------------------------------------------ CSV export

    @GetMapping(value = "/sales.csv", produces = "text/csv")
    ResponseEntity<String> salesCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        ReportDtos.SalesReport report = reports.sales(from, to);
        List<List<Object>> rows = report.days().stream()
                .map(day -> java.util.List.<Object>of(day.date(), day.orderCount(), day.grossSales(),
                        day.refundCount(), day.refunds(), day.netSales()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        rows.add(java.util.List.<Object>of("TOTAL", report.totalOrders(), report.grossSales(),
                report.totalRefunds(), report.refunds(), report.netSales()));
        return ReportCsv.render("sales-report.csv",
                List.of("date", "orderCount", "grossSales", "refundCount", "refunds", "netSales"), rows);
    }

    @GetMapping(value = "/orders.csv", produces = "text/csv")
    ResponseEntity<String> ordersCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String customerEmail) {
        List<List<Object>> rows = reports.allOrders(from, to, status, customerEmail).stream()
                .map(order -> java.util.List.<Object>of(order.orderNumber(), order.status(),
                        order.customerEmail(), order.grandTotal(), order.createdAt()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        return ReportCsv.render("orders-report.csv",
                List.of("orderNumber", "status", "customerEmail", "grandTotal", "createdAt"), rows);
    }

    @GetMapping(value = "/customers.csv", produces = "text/csv")
    ResponseEntity<String> customersCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) String email) {
        List<List<Object>> rows = reports.allCustomers(from, to, role, status, email).stream()
                .map(customer -> java.util.List.<Object>of(customer.email(), customer.fullName(),
                        customer.role(), customer.accountStatus(), customer.emailVerified(),
                        customer.createdAt()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        return ReportCsv.render("customers-report.csv",
                List.of("email", "fullName", "role", "accountStatus", "emailVerified", "createdAt"), rows);
    }

    @GetMapping(value = "/inventory.csv", produces = "text/csv")
    ResponseEntity<String> inventoryCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) String stockStatus) {
        List<List<Object>> rows = reports.allInventory(from, to, ownerType, stockStatus).stream()
                .map(row -> java.util.List.<Object>of(row.ownerType(), row.sku(), row.ownerName(),
                        row.totalQuantity(), row.reservedQuantity(), row.soldQuantity(),
                        row.availableQuantity(), row.lowStockThreshold(), row.stockStatus()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        return ReportCsv.render("inventory-report.csv",
                List.of("ownerType", "sku", "ownerName", "totalQuantity", "reservedQuantity",
                        "soldQuantity", "availableQuantity", "lowStockThreshold", "stockStatus"), rows);
    }

    @GetMapping(value = "/payments.csv", produces = "text/csv")
    ResponseEntity<String> paymentsCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) PaymentStatus status) {
        List<List<Object>> rows = reports.allPayments(from, to, status).stream()
                .map(payment -> java.util.List.<Object>of(payment.orderNumber(), payment.customerEmail(),
                        payment.paymentMethod(), payment.status(), payment.amount(),
                        payment.reconciliationRequired(), payment.createdAt()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        return ReportCsv.render("payments-report.csv",
                List.of("orderNumber", "customerEmail", "paymentMethod", "status", "amount",
                        "reconciliationRequired", "createdAt"), rows);
    }

    @GetMapping(value = "/refunds.csv", produces = "text/csv")
    ResponseEntity<String> refundsCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) RefundStatus status) {
        List<List<Object>> rows = reports.allRefunds(from, to, status).stream()
                .map(refund -> java.util.List.<Object>of(refund.orderNumber(), refund.amount(),
                        refund.status(), refund.createdByEmail(), refund.createdAt()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        return ReportCsv.render("refunds-report.csv",
                List.of("orderNumber", "amount", "status", "createdByEmail", "createdAt"), rows);
    }
}