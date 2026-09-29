package com.commercecore.review;

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
class Phase8ReviewIT {
    private static final String PASSWORD = "Review-Phase8-42!";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayV12AddsPhase8Tables() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables " +
                "where table_name in ('reviews','notifications','audit_logs','admin_alerts')", Integer.class))
                .isEqualTo(4);
    }

    @Test
    void verifiedPurchaseRequiresDeliveredOrderBeforeReviewCreation() {
        String staff = access("p8.review.staff@example.com", UserRole.STAFF);
        String customer = access("p8.review.customer@example.com", UserRole.CUSTOMER);
        String stranger = access("p8.review.stranger@example.com", UserRole.CUSTOMER);
        JsonNode setup = sellable(staff, "gated", 40, 5);
        UUID address = address(customer, "Review Street");
        post(customer, "/api/v1/cart/items", Map.of("productId", id(setup, "product"), "quantity", 1));
        JsonNode order = postCheckout(customer, "review-checkout-" + shortId(), address).getBody();
        String orderId = order.path("id").asText();

        // PENDING (just placed, unpaid): blocked.
        assertThat(post(customer, "/api/v1/products/" + slug(setup) + "/reviews",
                Map.of("rating", 5, "title", "Great", "body", "Really good product.")).getBody()
                .path("code").asText()).isEqualTo("REVIEW_REQUIRES_VERIFIED_PURCHASE");

        pay(customer, orderId, "review-pay-" + shortId());
        // CONFIRMED (paid but not yet delivered): still blocked.
        assertThat(post(customer, "/api/v1/products/" + slug(setup) + "/reviews",
                Map.of("rating", 5, "title", "Great", "body", "Really good product.")).getBody()
                .path("code").asText()).isEqualTo("REVIEW_REQUIRES_VERIFIED_PURCHASE");

        deliver(staff, orderId);
        assertThat(orderStatus(customer, orderId)).isEqualTo("DELIVERED");

        ResponseEntity<JsonNode> created = post(customer, "/api/v1/products/" + slug(setup) + "/reviews",
                Map.of("rating", 5, "title", "Excellent!", "body", "Better than expected."));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().path("rating").asInt()).isEqualTo(5);
        assertThat(created.getBody().path("status").asText()).isEqualTo("PENDING");

        assertThat(post(stranger, "/api/v1/products/" + slug(setup) + "/reviews",
                Map.of("rating", 5, "title", "Not mine", "body", "I did not buy this.")).getBody()
                .path("code").asText()).isEqualTo("REVIEW_REQUIRES_VERIFIED_PURCHASE");
        assertThat(post(staff, "/api/v1/products/" + slug(setup) + "/reviews",
                Map.of("rating", 5, "title", "Staff", "body", "Staff cannot review.")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void moderationWorkflowGatesPublicVisibilityAndEditsReturnToPending() {
        String admin = access("p8.mod.admin@example.com", UserRole.ADMIN);
        String staff = access("p8.mod.staff@example.com", UserRole.STAFF);
        String customer = access("p8.mod.customer@example.com", UserRole.CUSTOMER);
        JsonNode setup = sellable(staff, "moderate", 35, 5);
        UUID address = address(customer, "Moderation Street");
        post(customer, "/api/v1/cart/items", Map.of("productId", id(setup, "product"), "quantity", 1));
        JsonNode order = postCheckout(customer, "mod-checkout-" + shortId(), address).getBody();
        pay(customer, order.path("id").asText(), "mod-pay-" + shortId());
        deliver(staff, order.path("id").asText());

        UUID reviewId = uuid(post(customer, "/api/v1/products/" + slug(setup) + "/reviews",
                Map.of("rating", 5, "title", "Love it", "body", "Five stars.")).getBody());
        assertThat(reviewStatus(staff, reviewId)).isEqualTo("PENDING");

        // New review is PENDING: not public, not in the rating summary.
        assertThat(get(null, "/api/v1/products/" + slug(setup) + "/reviews").getBody()
                .path("totalElements").asLong()).isZero();
        assertThat(get(null, "/api/v1/products/" + slug(setup) + "/reviews/summary").getBody()
                .path("reviewCount").asLong()).isZero();

        // CUSTOMER cannot moderate.
        assertThat(post(customer, "/api/v1/admin/reviews/" + reviewId + "/approve", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // STAFF may moderate (approval queue + approve/reject).
        JsonNode pending = get(staff, "/api/v1/admin/reviews?status=PENDING").getBody();
        assertThat(pending.path("totalElements").asLong()).isEqualTo(1);
        JsonNode approved = post(staff, "/api/v1/admin/reviews/" + reviewId + "/approve", null).getBody();
        assertThat(approved.path("status").asText()).isEqualTo("APPROVED");

        JsonNode publicPage = get(null, "/api/v1/products/" + slug(setup) + "/reviews").getBody();
        assertThat(publicPage.path("totalElements").asLong()).isEqualTo(1);
        assertThat(publicPage.path("content").get(0).path("rating").asInt()).isEqualTo(5);
        assertThat(get(null, "/api/v1/products/" + slug(setup) + "/reviews/summary").getBody()
                .path("averageRating").decimalValue()).isEqualByComparingTo("5.00");

        // A meaningful edit returns the review to PENDING (removed from public).
        JsonNode edited = put(customer, "/api/v1/me/reviews/" + reviewId,
                Map.of("rating", 3, "title", "Changed my mind", "body", "Average after all.")).getBody();
        assertThat(edited.path("rating").asInt()).isEqualTo(3);
        assertThat(edited.path("status").asText()).isEqualTo("PENDING");
        assertThat(get(null, "/api/v1/products/" + slug(setup) + "/reviews").getBody()
                .path("totalElements").asLong()).isZero();

        // ADMIN approves the edited review; rating reflects it.
        JsonNode reApproved = post(admin, "/api/v1/admin/reviews/" + reviewId + "/approve", null).getBody();
        assertThat(reApproved.path("status").asText()).isEqualTo("APPROVED");
        assertThat(get(null, "/api/v1/products/" + slug(setup) + "/reviews/summary").getBody()
                .path("averageRating").decimalValue()).isEqualByComparingTo("3.00");

        // ADMIN rejects: removed from public again, customer still sees status.
        JsonNode rejected = post(admin, "/api/v1/admin/reviews/" + reviewId + "/reject", null).getBody();
        assertThat(rejected.path("status").asText()).isEqualTo("REJECTED");
        assertThat(get(null, "/api/v1/products/" + slug(setup) + "/reviews").getBody()
                .path("totalElements").asLong()).isZero();
        JsonNode mine = get(customer, "/api/v1/me/reviews").getBody();
        assertThat(mine.path("content").get(0).path("status").asText()).isEqualTo("REJECTED");

        List<String> actions = jdbc.queryForList(
                "select action from audit_logs where resource_id=? and category='REVIEW' order by created_at",
                String.class, reviewId.toString());
        assertThat(actions).containsExactly("REVIEW_APPROVED", "REVIEW_APPROVED", "REVIEW_REJECTED");
    }

    @Test
    void duplicateRatingsInvalidRatingsOwnershipAndDeleteAreEnforced() {
        String staff = access("p8.dup.staff@example.com", UserRole.STAFF);
        String customer = access("p8.dup.customer@example.com", UserRole.CUSTOMER);
        String other = access("p8.dup.other@example.com", UserRole.CUSTOMER);
        JsonNode setup = sellable(staff, "rules", 25, 5);
        UUID address = address(customer, "Rules Street");
        post(customer, "/api/v1/cart/items", Map.of("productId", id(setup, "product"), "quantity", 1));
        JsonNode order = postCheckout(customer, "rules-checkout-" + shortId(), address).getBody();
        pay(customer, order.path("id").asText(), "rules-pay-" + shortId());
        deliver(staff, order.path("id").asText());

        ResponseEntity<JsonNode> first = post(customer, "/api/v1/products/" + slug(setup) + "/reviews",
                Map.of("rating", 4, "title", "Solid", "body", "Works as described."));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID reviewId = uuid(first.getBody());

        assertThat(post(customer, "/api/v1/products/" + slug(setup) + "/reviews",
                Map.of("rating", 5, "title", "Again", "body", "Duplicate attempt.")).getBody()
                .path("code").asText()).isEqualTo("REVIEW_ALREADY_EXISTS");
        assertThat(post(customer, "/api/v1/products/" + slug(setup) + "/reviews",
                Map.of("rating", 9, "title", "Bad", "body", "Invalid rating.")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post(customer, "/api/v1/products/" + slug(setup) + "/reviews",
                Map.of("rating", 4, "title", "   ", "body", "   ")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(put(other, "/api/v1/me/reviews/" + reviewId,
                Map.of("rating", 1, "title", "Hijack", "body", "Not mine to edit.")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete(other, "/api/v1/me/reviews/" + reviewId).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(other, "/api/v1/me/reviews").getBody().path("totalElements").asLong()).isZero();

        assertThat(delete(customer, "/api/v1/me/reviews/" + reviewId).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get(customer, "/api/v1/me/reviews").getBody().path("totalElements").asLong()).isZero();
        long audit = jdbc.queryForObject("select count(*) from audit_logs where action=? and resource_id=?",
                Long.class, "REVIEW_DELETED", reviewId.toString());
        assertThat(audit).isEqualTo(1);
    }

    private String reviewStatus(String token, UUID reviewId) {
        return get(token, "/api/v1/admin/reviews?status=PENDING").getBody().path("content").isEmpty()
                ? get(token, "/api/v1/me/reviews/" + reviewId).getBody().path("status").asText()
                : findInAdminList(token, reviewId);
    }

    private String findInAdminList(String token, UUID reviewId) {
        JsonNode page = get(token, "/api/v1/admin/reviews?size=100").getBody();
        for (JsonNode item : page.path("content")) {
            if (item.path("id").asText().equals(reviewId.toString())) {
                return item.path("status").asText();
            }
        }
        throw new AssertionError("Review not found in admin list: " + reviewId);
    }

    private String orderStatus(String token, String orderId) {
        return get(token, "/api/v1/orders/" + orderId).getBody().path("status").asText();
    }

    private void deliver(String token, String orderId) {
        post(token, "/api/v1/staff/orders/" + orderId + "/processing", null);
        post(token, "/api/v1/staff/orders/" + orderId + "/shipment",
                Map.of("carrier", "Carrier", "trackingNumber", "TRACK-" + shortId().toUpperCase(Locale.ROOT),
                        "estimatedDeliveryAt", Instant.now().plusSeconds(3600)), null);
        post(token, "/api/v1/staff/orders/" + orderId + "/ship", null);
        post(token, "/api/v1/staff/orders/" + orderId + "/deliver", null);
    }

    private JsonNode sellable(String token, String tag, int price, int quantity) {
        JsonNode category = post(token, "/api/v1/staff/categories",
                Map.of("name", "Reviews " + tag, "slug", "p8-" + slugPart(tag) + "-" + shortId(),
                        "description", "phase8", "active", true)).getBody();
        JsonNode product = post(token, "/api/v1/staff/products",
                Map.of("name", "Product " + tag, "slug", "p8-product-" + slugPart(tag) + "-" + shortId(),
                        "description", "phase8 review product", "sku", "P8-" + shortId().toUpperCase(Locale.ROOT),
                        "brand", "Phase8", "basePrice", new BigDecimal(price), "categoryId", uuid(category),
                        "active", true)).getBody();
        JsonNode inventory = post(token, "/api/v1/staff/inventory",
                Map.of("productId", uuid(product), "initialQuantity", quantity, "lowStockThreshold", 1)).getBody();
        com.fasterxml.jackson.databind.node.ObjectNode result =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        result.set("product", product);
        result.set("inventory", inventory);
        return result;
    }

    private UUID id(JsonNode setup, String field) { return uuid(setup.path(field)); }

    private String slug(JsonNode setup) { return setup.path("product").path("slug").asText(); }

    private UUID address(String token, String line) {
        return uuid(post(token, "/api/v1/me/addresses",
                Map.of("label", "Home", "recipientName", "Phase Eight", "phone", "+60123456789",
                        "addressLine1", line, "city", "Kuala Lumpur", "stateRegion", "KL",
                        "postalCode", "50000", "countryCode", "MY", "defaultShipping", true,
                        "defaultBilling", true), null).getBody());
    }

    private ResponseEntity<JsonNode> pay(String token, String orderId, String key) {
        return post(token, "/api/v1/orders/" + orderId + "/payments",
                Map.of("paymentMethod", "CARD"), key);
    }

    private ResponseEntity<JsonNode> postCheckout(String token, String key, UUID address) {
        return post(token, "/api/v1/checkout",
                Map.of("shippingAddressId", address, "shippingServiceLevel", "STANDARD"), key);
    }

    private String access(String email, UserRole role) {
        UserAccount user = new UserAccount(email, encoder.encode(PASSWORD), "Phase Eight", null, role);
        user.verifyEmail();
        users.saveAndFlush(user);
        return rest.postForObject("/api/v1/auth/login",
                Map.of("email", email, "password", PASSWORD), JsonNode.class).path("accessToken").asText();
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
    private ResponseEntity<JsonNode> delete(String token, String path) {
        return exchange(token, HttpMethod.DELETE, path, null, null);
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
