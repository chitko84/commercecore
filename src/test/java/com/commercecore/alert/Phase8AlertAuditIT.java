package com.commercecore.alert;

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
class Phase8AlertAuditIT {
    private static final String PASSWORD = "Alert-Audit-Phase8-42!";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    void lowStockAndFailedPaymentAlertsAreTransitionBasedDeduplicatedAndAdminOnly() {
        String admin = access("p8l.admin@example.com", UserRole.ADMIN);
        String staff = access("p8l.staff@example.com", UserRole.STAFF);
        String customer = access("p8l.customer@example.com", UserRole.CUSTOMER);

        // --- Low-stock: initialization already at/below threshold alerts once.
        JsonNode category = post(staff, "/api/v1/staff/categories",
                Map.of("name", "Alert Category", "slug", "p8l-cat-" + shortId(),
                        "description", "phase8", "active", true)).getBody();
        JsonNode stocky = product(staff, category, "Stocky", "P8L-1-" + shortId(), 20);
        UUID inventoryId = uuid(post(staff, "/api/v1/staff/inventory",
                Map.of("productId", uuid(stocky), "initialQuantity", 5L, "lowStockThreshold", 5L))
                .getBody());

        assertThat(unacknowledged(admin)).isEqualTo(1);
        JsonNode alerts = get(admin, "/api/v1/admin/alerts").getBody();
        assertThat(alerts.path("totalElements").asLong()).isEqualTo(1);
        JsonNode alert = alerts.path("content").get(0);
        assertThat(alert.path("type").asText()).isEqualTo("LOW_STOCK_ALERT");
        assertThat(alert.path("resourceType").asText()).isEqualTo("INVENTORY");
        assertThat(alert.path("resourceId").asText()).isEqualTo(inventoryId.toString());
        assertThat(alert.path("acknowledged").asBoolean()).isFalse();

        // Reads never generate alerts.
        get(admin, "/api/v1/admin/alerts");
        get(admin, "/api/v1/admin/alerts/unacknowledged-count");
        assertThat(unacknowledged(admin)).isEqualTo(1);
        assertThat(get(staff, "/api/v1/admin/alerts").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Restocking above the threshold is not a crossing -> no new alert.
        post(staff, "/api/v1/staff/inventory/" + inventoryId + "/restock",
                Map.of("quantity", 10L), null);
        assertThat(unacknowledged(admin)).isEqualTo(1);

        // A decrease that crosses the threshold alerts again.
        post(staff, "/api/v1/staff/inventory/" + inventoryId + "/adjust",
                Map.of("delta", -12L, "reason", "shrink"), null);
        assertThat(unacknowledged(admin)).isEqualTo(2);

        // Staying at/below the threshold is not a new crossing.
        post(staff, "/api/v1/staff/inventory/" + inventoryId + "/adjust",
                Map.of("delta", 1L, "reason", "nudge"), null);
        assertThat(unacknowledged(admin)).isEqualTo(2);

        // Acknowledging clears the queue but keeps history.
        List<String> alertIds = new ArrayList<>();
        get(admin, "/api/v1/admin/alerts").getBody().path("content")
                .forEach(item -> alertIds.add(item.path("id").asText()));
        post(admin, "/api/v1/admin/alerts/" + alertIds.get(0) + "/acknowledge", null);
        assertThat(unacknowledged(admin)).isEqualTo(1);
        post(admin, "/api/v1/admin/alerts/" + alertIds.get(1) + "/acknowledge", null);
        assertThat(unacknowledged(admin)).isZero();
        assertThat(get(admin, "/api/v1/admin/alerts?unacknowledgedOnly=false").getBody()
                .path("totalElements").asLong()).isEqualTo(2);

        // --- Failed payment: a definitive decline alerts once per payment.
        JsonNode failProduct = product(staff, category, "Failable", "P8L-2-" + shortId(), 40);
        inventory(staff, failProduct, 5, 1);
        String failedOrderId = buy(customer, failProduct, "p8l-fail-checkout-" + shortId());
        String failedPaymentKey = "p8l-fail-pay-" + shortId();
        JsonNode declined = pay(customer, failedOrderId, failedPaymentKey, "decline").getBody();
        assertThat(declined.path("status").asText()).isEqualTo("FAILED");
        assertThat(unacknowledged(admin)).isEqualTo(1);
        List<String> paymentAlerts = new ArrayList<>();
        get(admin, "/api/v1/admin/alerts").getBody().path("content")
                .forEach(item -> paymentAlerts.add(item.path("type").asText()));
        assertThat(paymentAlerts).containsExactly("FAILED_PAYMENT_ALERT");

        // Idempotent replay never duplicates the failed-payment alert.
        pay(customer, failedOrderId, failedPaymentKey, "decline");
        assertThat(unacknowledged(admin)).isEqualTo(1);

        // Transient provider retries that recover never produce the alert.
        JsonNode transientProduct = product(staff, category, "Transientable", "P8L-3-" + shortId(), 40);
        inventory(staff, transientProduct, 5, 1);
        String transientOrderId = buy(customer, transientProduct, "p8l-transient-checkout-" + shortId());
        JsonNode paid = pay(customer, transientOrderId,
                "p8l-transient-pay-" + shortId(), "transient-2").getBody();
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        // The definitive decline alert remains; the transient recovery added nothing.
        assertThat(unacknowledged(admin)).isEqualTo(1);
        List<String> remaining = new ArrayList<>();
        get(admin, "/api/v1/admin/alerts").getBody().path("content")
                .forEach(item -> remaining.add(item.path("type").asText()));
        assertThat(remaining).containsExactly("FAILED_PAYMENT_ALERT");
        post(admin, "/api/v1/admin/alerts/"
                + get(admin, "/api/v1/admin/alerts").getBody().path("content").get(0).path("id").asText()
                + "/acknowledge", null);
        assertThat(unacknowledged(admin)).isZero();
    }

    @Test
    void auditCoverageSpansCatalogOrdersInventoryPromotionsPaymentsAndNeverContainsSecrets() {
        String admin = access("p8c.admin@example.com", UserRole.ADMIN);
        String staff = access("p8c.staff@example.com", UserRole.STAFF);
        String customer = access("p8c.customer@example.com", UserRole.CUSTOMER);
        UUID staffId = users.findByEmail("p8c.staff@example.com").orElseThrow().getId();
        UUID customerId = users.findByEmail("p8c.customer@example.com").orElseThrow().getId();
        String refreshToken = refreshFor("p8c.customer@example.com");
        String staffToken = staff;

        // --- Categories, products, price updates, variants.
        JsonNode category = post(staffToken, "/api/v1/staff/categories",
                Map.of("name", "Audit Category", "slug", "p8c-cat-" + shortId(),
                        "description", "phase8", "active", true)).getBody();
        String categoryId = category.path("id").asText();
        JsonNode productNode = product(staffToken, category, "Audit Widget", "P8C-1-" + shortId(), 30);
        String productId = productNode.path("id").asText();
        JsonNode sellableNode = product(staffToken, category, "Sellable", "P8C-2-" + shortId(), 25);
        String sellableId = sellableNode.path("id").asText();

        put(staffToken, "/api/v1/staff/categories/" + categoryId,
                Map.of("name", "Audit Category Renamed", "slug", categoryNodeSlug(category),
                        "description", "phase8", "active", true));
        put(staffToken, "/api/v1/staff/products/" + productId,
                Map.of("name", "Audit Widget Renamed", "slug", productNode.path("slug").asText(),
                        "description", "phase8", "sku", "P8C-1-" + shortId(), "brand", "Phase8",
                        "basePrice", new BigDecimal(30), "categoryId", UUID.fromString(categoryId),
                        "active", true));
        put(staffToken, "/api/v1/staff/products/" + productId,
                Map.of("name", "Audit Widget Renamed", "slug", productNode.path("slug").asText(),
                        "description", "phase8", "sku", productNode.path("sku").asText(), "brand", "Phase8",
                        "basePrice", new BigDecimal(42), "categoryId", UUID.fromString(categoryId),
                        "active", true));
        post(staffToken, "/api/v1/staff/products/" + productId + "/deactivate", null);
        post(staffToken, "/api/v1/staff/products/" + productId + "/activate", null);

        JsonNode variant = post(staffToken, "/api/v1/staff/products/" + productId + "/variants",
                Map.of("sku", "P8C-V1-" + shortId(), "priceOverride", new BigDecimal(35), "active", true,
                        "attributes", List.of(Map.of("name", "Color", "value", "Red", "displayOrder", 1))))
                .getBody();
        String variantId = variant.path("id").asText();
        put(staffToken, "/api/v1/staff/variants/" + variantId,
                Map.of("sku", variant.path("sku").asText(), "priceOverride", new BigDecimal(36), "active", true,
                        "attributes", List.of(Map.of("name", "Color", "value", "Blue", "displayOrder", 1))));
        post(staffToken, "/api/v1/staff/variants/" + variantId + "/deactivate", null);
        post(staffToken, "/api/v1/staff/variants/" + variantId + "/activate", null);

        post(staffToken, "/api/v1/staff/categories/" + categoryId + "/deactivate", null);
        post(staffToken, "/api/v1/staff/categories/" + categoryId + "/activate", null);

        // --- Inventory restock/adjust.
        JsonNode inventory = post(staffToken, "/api/v1/staff/inventory",
                Map.of("productId", UUID.fromString(sellableId), "initialQuantity", 10L,
                        "lowStockThreshold", 2L)).getBody();
        String inventoryId = inventory.path("id").asText();
        post(staffToken, "/api/v1/staff/inventory/" + inventoryId + "/restock",
                Map.of("quantity", 5L), null);
        post(staffToken, "/api/v1/staff/inventory/" + inventoryId + "/adjust",
                Map.of("delta", 2L, "reason", "cycle count"), null);

        // --- Promotions.
        Instant now = Instant.now();
        Map<String, Object> promotionRequest = new HashMap<>();
        promotionRequest.put("code", "P8C-" + shortId().toUpperCase(Locale.ROOT));
        promotionRequest.put("name", "Audit Promo");
        promotionRequest.put("description", "phase8");
        promotionRequest.put("type", "FIXED_AMOUNT");
        promotionRequest.put("scope", "CART");
        promotionRequest.put("discountValue", new BigDecimal("3.00"));
        promotionRequest.put("minimumSpend", new BigDecimal("0.00"));
        promotionRequest.put("maximumDiscount", new BigDecimal("10.00"));
        promotionRequest.put("startsAt", now.minusSeconds(60));
        promotionRequest.put("endsAt", now.plusSeconds(3600));
        promotionRequest.put("totalUsageLimit", 100L);
        promotionRequest.put("perUserUsageLimit", 1L);
        promotionRequest.put("active", true);
        JsonNode promotion = post(staffToken, "/api/v1/staff/promotions", promotionRequest).getBody();
        String promotionId = promotion.path("id").asText();
        Map<String, Object> promotionUpdate = new HashMap<>(promotionRequest);
        promotionUpdate.put("code", promotion.path("code").asText());
        promotionUpdate.put("name", "Audit Promo Renamed");
        put(staffToken, "/api/v1/staff/promotions/" + promotionId, promotionUpdate);
        post(staffToken, "/api/v1/staff/promotions/" + promotionId + "/deactivate", null);
        post(staffToken, "/api/v1/staff/promotions/" + promotionId + "/activate", null);

        // --- Order lifecycle + refunds + sale reversal.
        String order1Id = buy(customer, sellableNode, "p8c-order1-checkout-" + shortId());
        String payment1 = pay(customer, order1Id, "p8c-order1-pay-" + shortId())
                .getBody().path("id").asText();
        deliver(staffToken, order1Id);
        refund(staffToken, payment1, "p8c-refund-" + shortId(), "5.00");

        String order2Id = buy(customer, sellableNode, "p8c-order2-checkout-" + shortId());
        pay(customer, order2Id, "p8c-order2-pay-" + shortId());
        post(staffToken, "/api/v1/staff/orders/" + order2Id + "/cancel-paid", null);

        String order3Id = buy(customer, sellableNode, "p8c-order3-checkout-" + shortId());
        post(customer, "/api/v1/orders/" + order3Id + "/cancel", null);

        // --- Payment reconciliation after an uncertain outcome.
        String order4Id = buy(customer, sellableNode, "p8c-order4-checkout-" + shortId());
        String payment4 = pay(customer, order4Id, "p8c-order4-pay-" + shortId(), "slow-timeout")
                .getBody().path("id").asText();
        post(staffToken, "/api/v1/staff/payments/" + payment4 + "/reconcile", null);

        // --- Required audit actions are all present (scoped to this test's actors
        // because both methods in this class share the database).
        assertAuditFor(staffId, "CATEGORY_CREATED", "CATEGORY", 1);
        assertAuditFor(staffId, "CATEGORY_UPDATED", "CATEGORY", 1);
        assertAuditFor(staffId, "CATEGORY_DEACTIVATED", "CATEGORY", 1);
        assertAuditFor(staffId, "CATEGORY_ACTIVATED", "CATEGORY", 1);
        assertAuditFor(staffId, "PRODUCT_CREATED", "CATALOG", 2);
        assertAuditFor(staffId, "PRODUCT_UPDATED", "CATALOG", 2);
        assertAuditFor(staffId, "PRODUCT_PRICE_UPDATED", "CATALOG", 1);
        assertAuditFor(staffId, "PRODUCT_DEACTIVATED", "CATALOG", 1);
        assertAuditFor(staffId, "PRODUCT_ACTIVATED", "CATALOG", 1);
        assertAuditFor(staffId, "VARIANT_CREATED", "VARIANT", 1);
        assertAuditFor(staffId, "VARIANT_UPDATED", "VARIANT", 1);
        assertAuditFor(staffId, "VARIANT_DEACTIVATED", "VARIANT", 1);
        assertAuditFor(staffId, "VARIANT_ACTIVATED", "VARIANT", 1);
        assertAuditFor(staffId, "STOCK_RESTOCKED", "INVENTORY", 1);
        assertAuditFor(staffId, "STOCK_ADJUSTED", "INVENTORY", 1);
        assertAuditFor(staffId, "SALE_REVERSED", "INVENTORY", 1);
        assertAuditFor(staffId, "PROMOTION_CREATED", "PROMOTION", 1);
        assertAuditFor(staffId, "PROMOTION_UPDATED", "PROMOTION", 1);
        assertAuditFor(staffId, "PROMOTION_DEACTIVATED", "PROMOTION", 1);
        assertAuditFor(staffId, "PROMOTION_ACTIVATED", "PROMOTION", 1);
        assertAuditFor(customerId, "ORDER_CANCELLED", "ORDER", 1);
        assertAuditFor(staffId, "PAID_ORDER_CANCELLED", "ORDER", 1);
        assertAuditFor(staffId, "REFUND_CREATED", "PAYMENT", 2);
        assertAuditFor(staffId, "REFUND_COMPLETED", "PAYMENT", 2);
        assertAuditFor(staffId, "PAYMENT_RECONCILED", "PAYMENT", 1);
        // Confirmations run with a system/null actor; staff transitions add the rest.
        long statusChanges = countAudit("ORDER_STATUS_CHANGED", "ORDER");
        assertThat(statusChanges).isGreaterThanOrEqualTo(4);

        // --- Audit log is append-only: no update/delete surface exists.
        long before = jdbc.queryForObject("select count(*) from audit_logs", Long.class);
        List<String> ids = jdbc.queryForList("select id from audit_logs order by created_at desc limit 1",
                String.class);
        // No write surface exists for audit entries: PUT/DELETE are rejected
        // (405 when Spring knows the path, 404 otherwise) and the row count never
        // changes.
        ResponseEntity<JsonNode> putAttempt = exchange(admin, HttpMethod.PUT,
                "/api/v1/admin/audit-logs/" + ids.get(0), Map.of("details", "tampered"), null);
        assertThat(putAttempt.getStatusCode()).isIn(HttpStatus.METHOD_NOT_ALLOWED, HttpStatus.NOT_FOUND);
        ResponseEntity<JsonNode> deleteAttempt = exchange(admin, HttpMethod.DELETE,
                "/api/v1/admin/audit-logs/" + ids.get(0), null, null);
        assertThat(deleteAttempt.getStatusCode()).isIn(HttpStatus.METHOD_NOT_ALLOWED, HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("select count(*) from audit_logs", Long.class)).isEqualTo(before);

        // --- Never any credentials/tokens in audit details.
        Long leaks = jdbc.queryForObject("select count(*) from audit_logs where details is not null and (" +
                "lower(details) like '%password%' or lower(details) like '%token%' or " +
                "lower(details) like '%hash%' or lower(details) like '%authorization%' or " +
                "details like ? or details like ?)", Long.class, "%" + PASSWORD + "%",
                "%" + refreshToken + "%");
        assertThat(leaks).isZero();
    }

    private void assertAuditFor(UUID actorId, String action, String category, long expected) {
        long count = jdbc.queryForObject(
                "select count(*) from audit_logs where action=? and category=? and actor_user_id=?",
                Long.class, action, category, actorId);
        assertThat(count).as("audit action %s in %s by %s", action, category, actorId)
                .isEqualTo(expected);
    }

    private long countAudit(String action, String category) {
        return jdbc.queryForObject(
                "select count(*) from audit_logs where action=? and category=?", Long.class,
                action, category);
    }

    private void deliver(String token, String orderId) {
        post(token, "/api/v1/staff/orders/" + orderId + "/processing", null);
        post(token, "/api/v1/staff/orders/" + orderId + "/shipment",
                Map.of("carrier", "Carrier", "trackingNumber", "TRACK-" + shortId().toUpperCase(Locale.ROOT),
                        "estimatedDeliveryAt", Instant.now().plusSeconds(3600)), null);
        post(token, "/api/v1/staff/orders/" + orderId + "/ship", null);
        post(token, "/api/v1/staff/orders/" + orderId + "/deliver", null);
    }

    private String categoryNodeSlug(JsonNode category) {
        return category.path("slug").asText();
    }

    private String buy(String token, JsonNode product, String checkoutKey) {
        post(token, "/api/v1/cart/items", Map.of("productId", uuid(product), "quantity", 1));
        UUID address = address(token, "Alert Street " + shortId());
        JsonNode order = post(token, "/api/v1/checkout",
                Map.of("shippingAddressId", address, "shippingServiceLevel", "STANDARD"), checkoutKey).getBody();
        return order.path("id").asText();
    }

    private ResponseEntity<JsonNode> pay(String token, String orderId, String key) {
        return pay(token, orderId, key, null);
    }

    private ResponseEntity<JsonNode> pay(String token, String orderId, String key, String paymentToken) {
        Map<String, Object> body = new HashMap<>();
        body.put("paymentMethod", "CARD");
        if (paymentToken != null) {
            body.put("paymentMethodToken", paymentToken);
        }
        return post(token, "/api/v1/orders/" + orderId + "/payments", body, key);
    }

    private void refund(String token, String paymentId, String key, String amount) {
        post(token, "/api/v1/staff/payments/" + paymentId + "/refunds",
                Map.of("amount", new BigDecimal(amount)), key);
    }

    private JsonNode product(String token, JsonNode category, String name, String sku, int price) {
        String slug = "p8c-" + slugPart(name) + "-" + shortId();
        return post(token, "/api/v1/staff/products",
                Map.of("name", name, "slug", slug, "description", "phase8 alert product",
                        "sku", sku, "brand", "Phase8", "basePrice", new BigDecimal(price),
                        "categoryId", uuid(category), "active", true)).getBody();
    }

    private void inventory(String token, JsonNode product, int quantity, int threshold) {
        post(token, "/api/v1/staff/inventory",
                Map.of("productId", uuid(product), "initialQuantity", quantity,
                        "lowStockThreshold", threshold));
    }

    private long unacknowledged(String token) {
        return get(token, "/api/v1/admin/alerts/unacknowledged-count").getBody()
                .path("unacknowledgedCount").asLong();
    }

    private UUID address(String token, String line) {
        return uuid(post(token, "/api/v1/me/addresses",
                Map.of("label", "Home", "recipientName", "Phase Eight", "phone", "+60123456789",
                        "addressLine1", line, "city", "Kuala Lumpur", "stateRegion", "KL",
                        "postalCode", "50000", "countryCode", "MY", "defaultShipping", true,
                        "defaultBilling", true), null).getBody());
    }

    private String access(String email, UserRole role) {
        UserAccount user = new UserAccount(email, encoder.encode(PASSWORD), "Phase Eight", null, role);
        user.verifyEmail();
        users.saveAndFlush(user);
        return rest.postForObject("/api/v1/auth/login",
                Map.of("email", email, "password", PASSWORD), JsonNode.class).path("accessToken").asText();
    }

    private String refreshFor(String email) {
        return rest.postForObject("/api/v1/auth/login",
                Map.of("email", email, "password", PASSWORD), JsonNode.class).path("refreshToken").asText();
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