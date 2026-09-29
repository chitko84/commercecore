package com.commercecore;

import com.commercecore.user.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
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
class Phase8AdminAuditReportIT {
    private static final String PASSWORD = "Admin-Phase8-42!";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    void adminManagesAccountsWithSelfGuardsSessionRevocationAndUserAuditActions() {
        String admin = access("p8a.admin@example.com", UserRole.ADMIN);
        String staff = access("p8a.staff@example.com", UserRole.STAFF);
        String manager = access("p8a.manager@example.com", UserRole.ADMIN);
        String customerEmail = "p8a.customer@example.com";
        String customer = access(customerEmail, UserRole.CUSTOMER);
        UUID customerId = users.findByEmail(customerEmail).orElseThrow().getId();
        UUID adminId = users.findByEmail("p8a.admin@example.com").orElseThrow().getId();
        UUID managerId = users.findByEmail("p8a.manager@example.com").orElseThrow().getId();

        assertThat(get(staff, "/api/v1/admin/users").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(customer, "/api/v1/admin/users").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(staff, "/api/v1/admin/audit-logs").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        JsonNode page = get(admin, "/api/v1/admin/users?email=p8a.customer@example.com&role=CUSTOMER").getBody();
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("id").asText()).isEqualTo(customerId.toString());
        JsonNode detail = get(admin, "/api/v1/admin/users/" + customerId).getBody();
        assertThat(detail.path("email").asText()).isEqualTo(customerEmail);
        assertThat(detail.path("passwordHash").isMissingNode()).isTrue();

        // Self lock/deactivate are refused.
        assertThat(post(manager, "/api/v1/admin/users/" + managerId + "/lock", null).getBody()
                .path("code").asText()).isEqualTo("ADMIN_SELF_OPERATION_NOT_ALLOWED");
        assertThat(post(manager, "/api/v1/admin/users/" + managerId + "/deactivate", null).getBody()
                .path("code").asText()).isEqualTo("ADMIN_SELF_OPERATION_NOT_ALLOWED");
        assertThat(put(manager, "/api/v1/admin/users/" + managerId + "/role",
                Map.of("role", "CUSTOMER")).getBody().path("code").asText())
                .isEqualTo("ADMIN_ROLE_CHANGE_NOT_ALLOWED");

        // Deactivation revokes the target's refresh sessions.
        JsonNode customerLogin = login(customerEmail);
        String customerRefresh = customerLogin.path("refreshToken").asText();
        post(admin, "/api/v1/admin/users/" + customerId + "/deactivate", null);
        assertThat(get(admin, "/api/v1/admin/users/" + customerId).getBody().path("accountStatus").asText())
                .isEqualTo("DEACTIVATED");
        assertThat(refresh(customerRefresh)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post(admin, "/api/v1/admin/users/" + customerId + "/lock", null).getBody()
                .path("code").asText()).isEqualTo("ACCOUNT_OPERATION_NOT_ALLOWED");

        // Activation then locking also revokes sessions.
        post(admin, "/api/v1/admin/users/" + customerId + "/activate", null);
        assertThat(get(admin, "/api/v1/admin/users/" + customerId).getBody().path("accountStatus").asText())
                .isEqualTo("ACTIVE");
        String unlockedRefresh = login(customerEmail).path("refreshToken").asText();
        post(admin, "/api/v1/admin/users/" + customerId + "/lock", null);
        assertThat(get(admin, "/api/v1/admin/users/" + customerId).getBody().path("accountStatus").asText())
                .isEqualTo("LOCKED");
        assertThat(refresh(unlockedRefresh)).isEqualTo(HttpStatus.UNAUTHORIZED);
        post(admin, "/api/v1/admin/users/" + customerId + "/unlock", null);
        assertThat(get(admin, "/api/v1/admin/users/" + customerId).getBody().path("accountStatus").asText())
                .isEqualTo("ACTIVE");

        // Role changes also revoke the target's sessions.
        String roleChangeRefresh = login(customerEmail).path("refreshToken").asText();
        put(admin, "/api/v1/admin/users/" + customerId + "/role", Map.of("role", "STAFF"));
        assertThat(get(admin, "/api/v1/admin/users/" + customerId).getBody().path("role").asText())
                .isEqualTo("STAFF");
        assertThat(refresh(roleChangeRefresh)).isEqualTo(HttpStatus.UNAUTHORIZED);
        put(admin, "/api/v1/admin/users/" + customerId + "/role", Map.of("role", "CUSTOMER"));
        assertThat(get(admin, "/api/v1/admin/users/" + customerId).getBody().path("role").asText())
                .isEqualTo("CUSTOMER");

        assertThat(auditCount(adminId, customerId, "USER_DEACTIVATED")).isEqualTo(1);
        assertThat(auditCount(adminId, customerId, "USER_ACTIVATED")).isEqualTo(1);
        assertThat(auditCount(adminId, customerId, "USER_LOCKED")).isEqualTo(1);
        assertThat(auditCount(adminId, customerId, "USER_UNLOCKED")).isEqualTo(1);
        assertThat(auditCount(adminId, customerId, "USER_ROLE_CHANGED")).isEqualTo(2);

        JsonNode auditPage = get(admin, "/api/v1/admin/audit-logs?actorId=" + adminId + "&action=USER").getBody();
        assertThat(auditPage.path("totalElements").asLong()).isEqualTo(6);
        assertThat(auditPage.path("content").get(0).path("category").asText()).isEqualTo("ACCOUNT");
        assertThat(auditPage.path("content").get(0).path("resourceType").asText()).isEqualTo("USER");
    }

    @Test
    void salesTopProductAndReviewReportsAreAccurateAndAdminOnly() {
        String admin = access("p8r.admin@example.com", UserRole.ADMIN);
        String staff = access("p8r.staff@example.com", UserRole.STAFF);
        String customerEmail = "p8r.customer@example.com";
        String customer = access(customerEmail, UserRole.CUSTOMER);
        Instant start = Instant.now();

        JsonNode category = post(staff, "/api/v1/staff/categories",
                Map.of("name", "Report Category", "slug", "p8r-cat-" + shortId(),
                        "description", "phase8", "active", true)).getBody();
        JsonNode alpha = product(staff, category, "Alpha Widget", 100);
        JsonNode beta = product(staff, category, "Beta Gadget", 50);
        inventory(staff, alpha, 5);
        inventory(staff, beta, 5);

        String alphaOrderId = buy(customer, alpha, "alpha-checkout-" + shortId());
        String alphaPayment = pay(customer, alphaOrderId, "alpha-pay-" + shortId());
        String betaOrderId = buy(customer, beta, "beta-checkout-" + shortId());
        String betaPayment = pay(customer, betaOrderId, "beta-pay-" + shortId());
        refund(staff, alphaPayment, "report-refund-" + shortId(), "10.00");

        // Reviews need a DELIVERED order, then an APPROVED status to be public.
        deliver(staff, alphaOrderId);
        JsonNode review = post(customer, "/api/v1/products/" + alpha.path("slug").asText() + "/reviews",
                Map.of("rating", 4, "title", "Report review", "body", "Nice product.")).getBody();
        assertThat(review.path("rating").asInt()).isEqualTo(4);
        assertThat(review.path("status").asText()).isEqualTo("PENDING");
        UUID reviewId = UUID.fromString(review.path("id").asText());
        post(admin, "/api/v1/admin/reviews/" + reviewId + "/approve", null);

        BigDecimal alphaTotal = orderTotal(customer, alphaOrderId);
        BigDecimal betaTotal = orderTotal(customer, betaOrderId);
        Instant to = Instant.now().plusSeconds(5);
        String range = "from=" + start + "&to=" + to;

        assertThat(get(staff, "/api/v1/admin/reports/sales?" + range).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(customer, "/api/v1/admin/reports/sales?" + range).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        JsonNode sales = get(admin, "/api/v1/admin/reports/sales?" + range).getBody();
        assertThat(sales.path("totalOrders").asLong()).isEqualTo(2);
        assertThat(sales.path("totalRefunds").asLong()).isEqualTo(1);
        assertThat(sales.path("grossSales").decimalValue())
                .isEqualByComparingTo(alphaTotal.add(betaTotal));
        assertThat(sales.path("refunds").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(sales.path("netSales").decimalValue())
                .isEqualByComparingTo(alphaTotal.add(betaTotal).subtract(new BigDecimal("10.00")));
        assertThat(sales.path("days")).hasSize(1);

        JsonNode top = get(admin, "/api/v1/admin/reports/top-products?" + range + "&limit=10").getBody();
        assertThat(top.path("products").get(0).path("productName").asText()).isEqualTo("Alpha Widget");
        assertThat(top.path("products").get(0).path("unitsSold").asLong()).isEqualTo(1);
        assertThat(top.path("products").get(0).path("revenue").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(top.path("products").get(1).path("productName").asText()).isEqualTo("Beta Gadget");
        assertThat(top.path("products").get(1).path("revenue").decimalValue()).isEqualByComparingTo("50.00");

        JsonNode reviewReport = get(admin, "/api/v1/admin/reports/reviews?" + range).getBody();
        assertThat(reviewReport.path("reviewCount").asLong()).isEqualTo(1);
        assertThat(reviewReport.path("averageRating").decimalValue()).isEqualByComparingTo("4.00");
        assertThat(reviewReport.path("distribution").get("4").asLong()).isEqualTo(1);

        assertThat(get(admin, "/api/v1/admin/reports/sales?from=" + to + "&to=" + start).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Moderation is audit logged with the REVIEW_APPROVED/REVIEW_REJECTED actions.
        post(admin, "/api/v1/admin/reviews/" + reviewId + "/reject", null);
        assertThat(auditCount(null, reviewId, "REVIEW_REJECTED")).isEqualTo(1);
        assertThat(auditCount(null, reviewId, "REVIEW_APPROVED")).isEqualTo(1);
        JsonNode reviewAudit = get(admin, "/api/v1/admin/audit-logs?category=REVIEW&resourceType=REVIEW"
                + "&resourceId=" + reviewId).getBody();
        assertThat(reviewAudit.path("totalElements").asLong()).isEqualTo(2);
        assertThat(reviewAudit.path("content").get(0).path("action").asText()).isEqualTo("REVIEW_REJECTED");

        // Refund audit trail: REFUND_CREATED and REFUND_COMPLETED were recorded
        // (keyed by refund id; details carry the order number).
        String alphaOrderNumber = orderNumber(customer, alphaOrderId);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action=? " +
                        "and details like ?", Long.class, "REFUND_COMPLETED",
                "%order=" + alphaOrderNumber + "%")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action=? " +
                        "and details like ?", Long.class, "REFUND_CREATED",
                "%order=" + alphaOrderNumber + "%")).isEqualTo(1);
    }

    private void deliver(String token, String orderId) {
        post(token, "/api/v1/staff/orders/" + orderId + "/processing", null);
        post(token, "/api/v1/staff/orders/" + orderId + "/shipment",
                Map.of("carrier", "Carrier", "trackingNumber", "TRACK-" + shortId().toUpperCase(Locale.ROOT),
                        "estimatedDeliveryAt", Instant.now().plusSeconds(3600)), null);
        post(token, "/api/v1/staff/orders/" + orderId + "/ship", null);
        post(token, "/api/v1/staff/orders/" + orderId + "/deliver", null);
    }

    private String buy(String token, JsonNode product, String checkoutKey) {
        post(token, "/api/v1/cart/items", Map.of("productId", uuid(product), "quantity", 1));
        UUID address = address(token, "Report Street " + shortId());
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

    private JsonNode product(String token, JsonNode category, String name, int price) {
        String slug = "p8r-" + slugPart(name) + "-" + shortId();
        return post(token, "/api/v1/staff/products",
                Map.of("name", name, "slug", slug, "description", "phase8 report product",
                        "sku", "P8R-" + shortId().toUpperCase(Locale.ROOT), "brand", "Phase8",
                        "basePrice", new BigDecimal(price), "categoryId", uuid(category), "active", true))
                .getBody();
    }

    private void inventory(String token, JsonNode product, int quantity) {
        post(token, "/api/v1/staff/inventory",
                Map.of("productId", uuid(product), "initialQuantity", quantity, "lowStockThreshold", 1));
    }

    private UUID address(String token, String line) {
        return uuid(post(token, "/api/v1/me/addresses",
                Map.of("label", "Home", "recipientName", "Phase Eight", "phone", "+60123456789",
                        "addressLine1", line, "city", "Kuala Lumpur", "stateRegion", "KL",
                        "postalCode", "50000", "countryCode", "MY", "defaultShipping", true,
                        "defaultBilling", true), null).getBody());
    }

    private JsonNode login(String email) {
        return rest.postForObject("/api/v1/auth/login",
                Map.of("email", email, "password", PASSWORD), JsonNode.class);
    }

    private HttpStatusCode refresh(String refreshToken) {
        return rest.postForEntity("/api/v1/auth/refresh",
                Map.of("refreshToken", refreshToken), JsonNode.class).getStatusCode();
    }

    private long auditCount(UUID actorId, UUID resourceId, String action) {
        if (actorId == null) {
            return jdbc.queryForObject(
                    "select count(*) from audit_logs where action=? and resource_id=?", Long.class,
                    action, resourceId.toString());
        }
        return jdbc.queryForObject(
                "select count(*) from audit_logs where action=? and actor_user_id=? and resource_id=?",
                Long.class, action, actorId, resourceId.toString());
    }

    private String access(String email, UserRole role) {
        UserAccount user = new UserAccount(email, encoder.encode(PASSWORD), "Phase Eight", null, role);
        user.verifyEmail();
        users.saveAndFlush(user);
        return login(email).path("accessToken").asText();
    }

    private ResponseEntity<JsonNode> post(String token, String path, Object body, String key) {
        return exchange(token, HttpMethod.POST, path, body, key);
    }
    private ResponseEntity<JsonNode> post(String token, String path, Object body) {
        return post(token, path, body, null);
    }
    private ResponseEntity<JsonNode> put(String token, String path, Object body) {
        return exchange(token, HttpMethod.PUT, path, body, null);
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
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private static UUID uuid(JsonNode node) { return UUID.fromString(node.path("id").asText()); }
    private static String slugPart(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"); }
    private static String shortId() { return UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
}