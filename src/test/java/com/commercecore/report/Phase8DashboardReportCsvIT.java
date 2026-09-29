package com.commercecore.report;

import com.commercecore.user.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Phase8DashboardReportCsvIT {
    private static final String PASSWORD = "Dashboard-Phase8-42!";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void dashboardAllReportsAndCsvExportsAreRepositoryBackedAndRoleBound() {
        Instant start = Instant.now();
        String admin = access("p8d.admin@example.com", UserRole.ADMIN, "Admin Eight");
        String staff = access("p8d.staff@example.com", UserRole.STAFF, "Staff Eight");
        String customer = access("p8d.customer@example.com", UserRole.CUSTOMER, "=1+1,formula");
        String injectionProductName = "=SUM(1,1),B2";

        JsonNode category = post(staff, "/api/v1/staff/categories",
                Map.of("name", "Dashboard Category", "slug", "p8d-cat-" + shortId(),
                        "description", "phase8", "active", true)).getBody();
        String suffix = shortId();
        JsonNode alpha = product(staff, category, "Alpha Widget", 100, "P8DA-1-" + suffix,
                "p8d-alpha-" + suffix);
        JsonNode beta = product(staff, category, "Beta Gadget", 50, "P8DA-2-" + suffix,
                "p8d-beta-" + suffix);
        JsonNode injected = product(staff, category, injectionProductName, 30, "P8DA-3-" + suffix,
                "p8d-injected-" + suffix);
        inventory(staff, alpha, 5, 1);
        inventory(staff, beta, 5, 1);
        inventory(staff, injected, 1, 5); // deliberately low stock

        String alphaOrderId = buy(customer, alpha, "p8d-alpha-checkout-" + shortId());
        String alphaPayment = pay(customer, alphaOrderId, "p8d-alpha-pay-" + shortId());
        String betaOrderId = buy(customer, beta, "p8d-beta-checkout-" + shortId());
        String betaPayment = pay(customer, betaOrderId, "p8d-beta-pay-" + shortId());
        // Orders stay CONFIRMED for the dashboard/orders-report assertions.
        refund(staff, alphaPayment, "p8d-refund-" + shortId(), "10.00");

        BigDecimal alphaTotal = orderTotal(customer, alphaOrderId);
        BigDecimal betaTotal = orderTotal(customer, betaOrderId);
        String alphaNumber = orderNumber(customer, alphaOrderId);
        String betaNumber = orderNumber(customer, betaOrderId);
        Instant to = Instant.now().plusSeconds(5);
        String range = "from=" + start + "&to=" + to;

        // ------------------------------------------------------------- RBAC
        assertThat(get(staff, "/api/v1/admin/dashboard").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(customer, "/api/v1/admin/dashboard").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(customer, "/api/v1/admin/reports/orders?" + range).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(staff, "/api/v1/admin/reports/sales.csv?" + range).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // ---------------------------------------------------------- Dashboard
        JsonNode dashboard = get(admin, "/api/v1/admin/dashboard").getBody();
        assertThat(dashboard.path("totalRevenue").decimalValue())
                .isEqualByComparingTo(alphaTotal.add(betaTotal));
        assertThat(dashboard.path("netRevenue").decimalValue())
                .isEqualByComparingTo(alphaTotal.add(betaTotal).subtract(new BigDecimal("10.00")));
        assertThat(dashboard.path("ordersByStatus").path("CONFIRMED").asLong()).isEqualTo(2);
        assertThat(dashboard.path("customerCount").asLong()).isEqualTo(1);
        assertThat(dashboard.path("activeProductCount").asLong()).isEqualTo(3);
        assertThat(dashboard.path("lowStockCount").asLong()).isEqualTo(1);
        assertThat(dashboard.path("failedPaymentCount").asLong()).isZero();
        List<String> recentNumbers = new ArrayList<>();
        dashboard.path("recentOrders").forEach(order ->
                recentNumbers.add(order.path("orderNumber").asText()));
        assertThat(recentNumbers).contains(alphaNumber, betaNumber);

        // --------------------------------------------------- JSON reports
        JsonNode orders = get(admin, "/api/v1/admin/reports/orders?" + range
                + "&status=CONFIRMED&size=50").getBody();
        assertThat(orders.path("totalOrders").asLong()).isEqualTo(2);
        assertThat(orders.path("totalAmount").decimalValue())
                .isEqualByComparingTo(alphaTotal.add(betaTotal));
        assertThat(orders.path("totalElements").asLong()).isEqualTo(2);

        JsonNode customers = get(admin, "/api/v1/admin/reports/customers?" + range
                + "&email=p8d.customer@example.com").getBody();
        assertThat(customers.path("totalCustomers").asLong()).isEqualTo(1);
        assertThat(customers.path("totalElements").asLong()).isEqualTo(1);
        assertThat(customers.path("totalSpent").decimalValue())
                .isEqualByComparingTo(alphaTotal.add(betaTotal));
        assertThat(customers.path("customers").get(0).path("email").asText())
                .isEqualTo("p8d.customer@example.com");

        // Alpha's payment is PARTIALLY_REFUNDED after the 10.00 refund; beta is PAID.
        JsonNode payments = get(admin, "/api/v1/admin/reports/payments?" + range + "&size=50").getBody();
        assertThat(payments.path("totalPayments").asLong()).isEqualTo(2);
        assertThat(payments.path("totalAmount").decimalValue())
                .isEqualByComparingTo(alphaTotal.add(betaTotal));
        assertThat(payments.path("byStatus").path("PAID").asLong()).isEqualTo(1);
        assertThat(payments.path("byStatus").path("PARTIALLY_REFUNDED").asLong()).isEqualTo(1);

        JsonNode refunds = get(admin, "/api/v1/admin/reports/refunds?" + range
                + "&status=COMPLETED").getBody();
        assertThat(refunds.path("totalRefunds").asLong()).isEqualTo(1);
        assertThat(refunds.path("totalAmount").decimalValue()).isEqualByComparingTo("10.00");

        JsonNode inventoryReport = get(admin, "/api/v1/admin/reports/inventory?" + range
                + "&stockStatus=LOW").getBody();
        assertThat(inventoryReport.path("lowStock").asLong()).isEqualTo(1);
        assertThat(inventoryReport.path("totalElements").asLong()).isEqualTo(1);
        assertThat(inventoryReport.path("rows").get(0).path("sku").asText())
                .isEqualTo(injected.path("sku").asText());

        // ---------------------------------------------------------- CSV export
        ResponseEntity<String> salesCsv = csv(admin, "/api/v1/admin/reports/sales.csv?" + range);
        assertThat(salesCsv.getHeaders().getContentType().toString()).startsWith("text/csv");
        assertThat(salesCsv.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"sales-report.csv\"");
        assertThat(salesCsv.getBody()).contains("date,orderCount,grossSales,refundCount,refunds,netSales");
        BigDecimal refundAmount = new BigDecimal("10.00");
        BigDecimal grossSales = alphaTotal.add(betaTotal).setScale(2);
        BigDecimal netSales = grossSales.subtract(refundAmount).setScale(2);
        String totalLine = "TOTAL,2," + grossSales + ",1," + refundAmount + "," + netSales;
        assertThat(salesCsv.getBody()).contains(totalLine);

        ResponseEntity<String> ordersCsv = csv(admin, "/api/v1/admin/reports/orders.csv?" + range);
        assertThat(ordersCsv.getBody()).contains("orderNumber,status,customerEmail,grandTotal,createdAt");
        assertThat(ordersCsv.getBody()).contains(alphaNumber);
        assertThat(ordersCsv.getBody()).contains(betaNumber);

        ResponseEntity<String> customersCsv = csv(admin, "/api/v1/admin/reports/customers.csv?" + range);
        assertThat(customersCsv.getBody()).contains("\"'=1+1,formula\"");

        ResponseEntity<String> inventoryCsv = csv(admin, "/api/v1/admin/reports/inventory.csv?" + range);
        assertThat(inventoryCsv.getBody()).contains("\"'=SUM(1,1),B2\"");

        ResponseEntity<String> paymentsCsv = csv(admin, "/api/v1/admin/reports/payments.csv?" + range);
        assertThat(paymentsCsv.getBody()).contains("paymentMethod,status,amount,reconciliationRequired");
        assertThat(paymentsCsv.getBody()).contains("PAID");

        ResponseEntity<String> refundsCsv = csv(admin, "/api/v1/admin/reports/refunds.csv?" + range);
        assertThat(refundsCsv.getBody()).contains("orderNumber,amount,status,createdByEmail,createdAt");
        assertThat(refundsCsv.getBody()).contains(",10.00,COMPLETED,");

        // Formula injection values never appear un-prefixed (the dangerous
        // un-neutralized cells would have been "=SUM(1,1),B2" and "=1+1,formula").
        assertThat(inventoryCsv.getBody()).contains("\"'=SUM(1,1),B2\"");
        assertThat(inventoryCsv.getBody()).doesNotContain("\"=SUM(1,1),B2\"");
        assertThat(customersCsv.getBody()).doesNotContain("\"=1+1,formula\"");
    }

    private String buy(String token, JsonNode product, String checkoutKey) {
        post(token, "/api/v1/cart/items", Map.of("productId", uuid(product), "quantity", 1));
        UUID address = address(token, "Dashboard Street " + shortId());
        JsonNode order = post(token, "/api/v1/checkout",
                Map.of("shippingAddressId", address, "shippingServiceLevel", "STANDARD"), checkoutKey).getBody();
        return order.path("id").asText();
    }

    private BigDecimal orderTotal(String token, String orderId) {
        return get(token, "/api/v1/orders/" + orderId).getBody().path("grandTotal").decimalValue();
    }

    private String orderNumber(String token, String orderId) {
        return get(token, "/api/v1/orders/" + orderId).getBody().path("orderNumber").asText();
    }

    private String pay(String token, String orderId, String key) {
        return post(token, "/api/v1/orders/" + orderId + "/payments",
                Map.of("paymentMethod", "CARD"), key).getBody().path("id").asText();
    }

    private void refund(String token, String paymentId, String key, String amount) {
        post(token, "/api/v1/staff/payments/" + paymentId + "/refunds",
                Map.of("amount", new BigDecimal(amount)), key);
    }

    private JsonNode product(String token, JsonNode category, String name, int price, String sku,
                             String slug) {
        return post(token, "/api/v1/staff/products",
                Map.of("name", name, "slug", slug, "description", "phase8 dashboard product",
                        "sku", sku, "brand", "Phase8", "basePrice", new BigDecimal(price),
                        "categoryId", uuid(category), "active", true)).getBody();
    }

    private void inventory(String token, JsonNode product, int quantity, int threshold) {
        post(token, "/api/v1/staff/inventory",
                Map.of("productId", uuid(product), "initialQuantity", quantity,
                        "lowStockThreshold", threshold));
    }

    private UUID address(String token, String line) {
        return uuid(post(token, "/api/v1/me/addresses",
                Map.of("label", "Home", "recipientName", "Phase Eight", "phone", "+60123456789",
                        "addressLine1", line, "city", "Kuala Lumpur", "stateRegion", "KL",
                        "postalCode", "50000", "countryCode", "MY", "defaultShipping", true,
                        "defaultBilling", true), null).getBody());
    }

    private String access(String email, UserRole role, String fullName) {
        UserAccount user = new UserAccount(email, encoder.encode(PASSWORD), fullName, null, role);
        user.verifyEmail();
        users.saveAndFlush(user);
        return rest.postForObject("/api/v1/auth/login",
                Map.of("email", email, "password", PASSWORD), JsonNode.class).path("accessToken").asText();
    }

    private ResponseEntity<String> csv(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.valueOf("text/csv")));
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<JsonNode> post(String token, String path, Object body, String key) {
        return exchange(token, HttpMethod.POST, path, body, key);
    }
    private ResponseEntity<JsonNode> post(String token, String path, Object body) {
        return post(token, path, body, null);
    }
    private ResponseEntity<JsonNode> get(String token, String path) {
        return exchange(token, HttpMethod.GET, path, null, null);
    }
    private ResponseEntity<JsonNode> exchange(String token, HttpMethod method, String path,
                                              Object body, String key) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        if (key != null) headers.set("Idempotency-Key", key);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response =
                rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            System.out.println("PROBE " + method + " " + path + " -> " + response.getStatusCode()
                    + " " + response.getBody());
        }
        return response;
    }

    private static UUID uuid(JsonNode node) { return UUID.fromString(node.path("id").asText()); }
    private static String slugPart(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"); }
    private static String shortId() { return UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
}