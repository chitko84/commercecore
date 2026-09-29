package com.commercecore.notification;

import com.commercecore.user.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Phase8NotificationIT {
    private static final String PASSWORD = "Notify-Phase8-42!";
    private static final String NEW_PASSWORD = "Notify-Phase8-Reset-77!";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Value("${app.email.development-outbox:build/dev-mail-outbox.txt}")
    String outboxPath;

    @Test
    void placedConfirmedAndPaidNotificationsAreCreatedOnceAndReadStateIsPerCustomer() {
        String staff = access("p8n.one.staff@example.com", UserRole.STAFF);
        String customer = access("p8n.one.customer@example.com", UserRole.CUSTOMER);
        String other = access("p8n.one.other@example.com", UserRole.CUSTOMER);
        JsonNode setup = sellable(staff, "placed", 30, 5);
        UUID address = address(customer, "One Street");
        post(customer, "/api/v1/cart/items", Map.of("productId", id(setup), "quantity", 1));
        JsonNode order = postCheckout(customer, "notify-checkout-" + shortId(), address).getBody();
        String orderId = order.path("id").asText();

        assertTypes(customer, "ORDER_CREATED");
        JsonNode paid = pay(customer, orderId, "notify-pay-" + shortId()).getBody();
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        assertTypes(customer, "ORDER_CREATED", "ORDER_CONFIRMED", "PAYMENT_SUCCEEDED");
        assertThat(unreadCount(customer)).isEqualTo(3);

        // Idempotent replay of the same payment never duplicates notifications.
        pay(customer, orderId, "notify-pay-" + shortId());
        assertThat(unreadCount(customer)).isEqualTo(3);
        assertThat(list(customer, false).path("totalElements").asLong()).isEqualTo(3);

        JsonNode page = list(customer, false);
        JsonNode notification = page.path("content").get(0);
        String notificationId = notification.path("id").asText();
        assertThat(notification.path("read").asBoolean()).isFalse();
        assertThat(notification.path("resourceType").asText()).isEqualTo("ORDER");

        assertThat(post(other, "/api/v1/me/notifications/" + notificationId + "/read", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode read = post(customer, "/api/v1/me/notifications/" + notificationId + "/read", null).getBody();
        assertThat(read.path("read").asBoolean()).isTrue();
        assertThat(unreadCount(customer)).isEqualTo(2);
        assertThat(list(customer, true).path("totalElements").asLong()).isEqualTo(2);

        JsonNode cleared = post(customer, "/api/v1/me/notifications/read-all", null).getBody();
        assertThat(cleared.path("unreadCount").asLong()).isZero();
        assertThat(list(customer, true).path("totalElements").asLong()).isZero();
        assertThat(list(customer, false).path("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    void shippingAndRefundLifecycleEmitsTypedNotificationsToTheOrderOwner() {
        String staff = access("p8n.ship.staff@example.com", UserRole.STAFF);
        String customer = access("p8n.ship.customer@example.com", UserRole.CUSTOMER);
        JsonNode setup = sellable(staff, "shipping", 45, 5);
        UUID address = address(customer, "Shipping Street");
        post(customer, "/api/v1/cart/items", Map.of("productId", id(setup), "quantity", 1));
        JsonNode order = postCheckout(customer, "ship-checkout-" + shortId(), address).getBody();
        String orderId = order.path("id").asText();
        JsonNode paid = pay(customer, orderId, "ship-pay-" + shortId()).getBody();
        String paymentId = paid.path("id").asText();

        post(staff, "/api/v1/staff/orders/" + orderId + "/processing", null);
        post(staff, "/api/v1/staff/orders/" + orderId + "/shipment",
                Map.of("carrier", "Carrier", "trackingNumber", "TRACK-" + shortId().toUpperCase(Locale.ROOT),
                        "estimatedDeliveryAt", Instant.now().plusSeconds(3600)), null);
        post(staff, "/api/v1/staff/orders/" + orderId + "/ship", null);
        post(staff, "/api/v1/staff/orders/" + orderId + "/deliver", null);
        assertThat(orderStatus(customer, orderId)).isEqualTo("DELIVERED");

        refund(staff, paymentId, "ship-refund-" + shortId(), "5.00");
        assertTypes(customer, "ORDER_CREATED", "ORDER_CONFIRMED", "PAYMENT_SUCCEEDED",
                "ORDER_PROCESSING", "ORDER_SHIPPED", "ORDER_DELIVERED", "REFUND_COMPLETED");
        assertThat(unreadCount(customer)).isEqualTo(7);

        JsonNode refundNotification = null;
        for (JsonNode item : list(customer, true).path("content")) {
            if (item.path("type").asText().equals("REFUND_COMPLETED")) {
                refundNotification = item;
                break;
            }
        }
        assertThat(refundNotification).isNotNull();
        assertThat(refundNotification.path("resourceType").asText()).isEqualTo("REFUND");
        assertThat(refundNotification.path("body").asText()).contains("USD 5.00");

        assertThat(unreadCount(staff)).isZero();
        assertThat(list(staff, false).path("totalElements").asLong()).isZero();
    }

    @Test
    void pendingAndPaidCancellationsNotifyCustomers() {
        String staff = access("p8n.cancel.staff@example.com", UserRole.STAFF);
        String pendingCustomer = access("p8n.cancel.pending@example.com", UserRole.CUSTOMER);
        String paidCustomer = access("p8n.cancel.paid@example.com", UserRole.CUSTOMER);

        JsonNode pendingSetup = sellable(staff, "cancel-pending", 20, 5);
        UUID pendingAddress = address(pendingCustomer, "Cancel Pending Street");
        post(pendingCustomer, "/api/v1/cart/items", Map.of("productId", id(pendingSetup), "quantity", 1));
        JsonNode pendingOrder = postCheckout(pendingCustomer, "cancel-pending-checkout-" + shortId(), pendingAddress).getBody();
        post(pendingCustomer, "/api/v1/orders/" + pendingOrder.path("id").asText() + "/cancel", null);
        assertTypes(pendingCustomer, "ORDER_CREATED", "ORDER_CANCELLED");

        JsonNode paidSetup = sellable(staff, "cancel-paid", 70, 5);
        UUID paidAddress = address(paidCustomer, "Cancel Paid Street");
        post(paidCustomer, "/api/v1/cart/items", Map.of("productId", id(paidSetup), "quantity", 1));
        JsonNode paidOrder = postCheckout(paidCustomer, "cancel-paid-checkout-" + shortId(), paidAddress).getBody();
        String paidOrderId = paidOrder.path("id").asText();
        pay(paidCustomer, paidOrderId, "cancel-paid-pay-" + shortId());
        JsonNode cancelled = post(staff, "/api/v1/staff/orders/" + paidOrderId + "/cancel-paid", null).getBody();
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertTypes(paidCustomer, "ORDER_CREATED", "ORDER_CONFIRMED", "PAYMENT_SUCCEEDED",
                "ORDER_CANCELLED", "REFUND_COMPLETED");
    }

    @Test
    void transientRetriesNeverFailPaymentsWhileDefinitiveFailureNotifiesExactlyOnce() {
        String staff = access("p8n.fail.staff@example.com", UserRole.STAFF);
        String customer = access("p8n.fail.customer@example.com", UserRole.CUSTOMER);

        // Transient provider errors recover inside the retry loop: the payment
        // ends PAID and no failure notification or alert is produced.
        JsonNode transientSetup = sellable(staff, "transient", 15, 5);
        UUID transientAddress = address(customer, "Transient Street");
        post(customer, "/api/v1/cart/items", Map.of("productId", id(transientSetup), "quantity", 1));
        JsonNode transientOrder = postCheckout(customer, "transient-checkout-" + shortId(), transientAddress).getBody();
        JsonNode transientPaid = payWithToken(customer, transientOrder.path("id").asText(),
                "transient-pay-" + shortId(), "transient-2").getBody();
        assertThat(transientPaid.path("status").asText()).isEqualTo("PAID");
        List<String> customerTypes = types(customer);
        assertThat(customerTypes).doesNotContain("PAYMENT_FAILED");
        assertThat(customerTypes).contains("PAYMENT_SUCCEEDED");
        assertThat(countType(customer, "PAYMENT_SUCCEEDED")).isEqualTo(1);

        // A definitive decline fails the payment and notifies exactly once,
        // even when the same idempotency key is replayed.
        JsonNode declineSetup = sellable(staff, "decline", 60, 5);
        UUID declineAddress = address(customer, "Decline Street");
        post(customer, "/api/v1/cart/items", Map.of("productId", id(declineSetup), "quantity", 1));
        JsonNode declineOrder = postCheckout(customer, "decline-checkout-" + shortId(), declineAddress).getBody();
        String declinePaymentKey = "decline-pay-" + shortId();
        JsonNode declined = payWithToken(customer, declineOrder.path("id").asText(),
                declinePaymentKey, "decline").getBody();
        assertThat(declined.path("status").asText()).isEqualTo("FAILED");
        assertThat(countType(customer, "PAYMENT_FAILED")).isEqualTo(1);

        payWithToken(customer, declineOrder.path("id").asText(), declinePaymentKey, "decline");
        assertThat(countType(customer, "PAYMENT_FAILED")).isEqualTo(1);
        // 3 notifications for the transient order (created/confirmed/succeeded)
        // plus 2 for the declined order (created/failed), none duplicated.
        assertThat(unreadCount(customer)).isEqualTo(5);
    }

    @Test
    void welcomePasswordChangedAndPasswordResetAreDeliveredAndPasswordChangeRevokesSessions() throws IOException {
        String email = "p8n.auth." + shortId() + "@example.com";
        JsonNode registered = rest.postForObject("/api/v1/auth/register",
                Map.of("fullName", "Auth Customer", "email", email, "password", PASSWORD),
                JsonNode.class);
        assertThat(registered.path("accountStatus").asText()).isEqualTo("PENDING_VERIFICATION");

        String verificationToken = lastToken(email, "VERIFY");
        rest.postForObject("/api/v1/auth/verify-email",
                Map.of("token", verificationToken), JsonNode.class);

        JsonNode login = rest.postForObject("/api/v1/auth/login",
                Map.of("email", email, "password", PASSWORD), JsonNode.class);
        String token = login.path("accessToken").asText();
        String refreshToken = login.path("refreshToken").asText();
        assertTypes(token, "WELCOME");

        // Password change: notification plus refresh-session revocation.
        put(token, "/api/v1/me/password",
                Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD));
        assertTypes(token, "WELCOME", "PASSWORD_CHANGED");
        ResponseEntity<JsonNode> staleRefresh = rest.postForEntity("/api/v1/auth/refresh",
                Map.of("refreshToken", refreshToken), JsonNode.class);
        assertThat(staleRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Password reset via forgot-password email token.
        String resetPassword = "Notify-Phase8-Final-99!";
        rest.postForObject("/api/v1/auth/forgot-password", Map.of("email", email), JsonNode.class);
        String resetToken = lastToken(email, "RESET");
        rest.postForObject("/api/v1/auth/reset-password",
                Map.of("token", resetToken, "newPassword", resetPassword), JsonNode.class);
        assertTypes(token, "WELCOME", "PASSWORD_CHANGED", "PASSWORD_RESET");

        // The new password works.
        JsonNode relogin = rest.postForObject("/api/v1/auth/login",
                Map.of("email", email, "password", resetPassword), JsonNode.class);
        assertThat(relogin.path("accessToken").asText()).isNotBlank();
    }

    private String orderStatus(String token, String orderId) {
        return get(token, "/api/v1/orders/" + orderId).getBody().path("status").asText();
    }

    private void assertTypes(String token, String... expected) {
        JsonNode page = list(token, false);
        assertThat(page.path("totalElements").asLong()).isEqualTo(expected.length);
        List<String> actual = types(token);
        assertThat(actual).containsExactlyInAnyOrder(expected);
    }

    private List<String> types(String token) {
        List<String> types = new ArrayList<>();
        list(token, false).path("content").forEach(item -> types.add(item.path("type").asText()));
        return types;
    }

    private long countType(String token, String type) {
        return types(token).stream().filter(type::equals).count();
    }

    private long unreadCount(String token) {
        return get(token, "/api/v1/me/notifications/unread-count").getBody().path("unreadCount").asLong();
    }

    private JsonNode list(String token, boolean unreadOnly) {
        return get(token, "/api/v1/me/notifications?unreadOnly=" + unreadOnly + "&size=50").getBody();
    }

    private String lastToken(String email, String type) throws IOException {
        Path path = Path.of(outboxPath);
        assertThat(Files.exists(path)).as("development outbox exists").isTrue();
        List<String> lines = Files.readAllLines(path);
        for (int index = lines.size() - 1; index >= 0; index--) {
            String[] parts = lines.get(index).split(" ", 3);
            if (parts.length == 3 && parts[0].equals(type) && parts[1].equals(email)) {
                return parts[2];
            }
        }
        throw new AssertionError("No " + type + " token found for " + email + " in " + outboxPath);
    }

    private JsonNode sellable(String token, String tag, int price, int quantity) {
        JsonNode category = post(token, "/api/v1/staff/categories",
                Map.of("name", "Notify " + tag, "slug", "p8n-" + slugPart(tag) + "-" + shortId(),
                        "description", "phase8", "active", true)).getBody();
        JsonNode product = post(token, "/api/v1/staff/products",
                Map.of("name", "Product " + tag, "slug", "p8n-product-" + slugPart(tag) + "-" + shortId(),
                        "description", "phase8 notify product", "sku", "P8N-" + shortId().toUpperCase(Locale.ROOT),
                        "brand", "Phase8", "basePrice", new BigDecimal(price), "categoryId", uuid(category),
                        "active", true)).getBody();
        post(token, "/api/v1/staff/inventory",
                Map.of("productId", uuid(product), "initialQuantity", quantity, "lowStockThreshold", 1));
        return product;
    }

    private UUID id(JsonNode product) { return uuid(product); }

    private UUID address(String token, String line) {
        return uuid(post(token, "/api/v1/me/addresses",
                Map.of("label", "Home", "recipientName", "Phase Eight", "phone", "+60123456789",
                        "addressLine1", line, "city", "Kuala Lumpur", "stateRegion", "KL",
                        "postalCode", "50000", "countryCode", "MY", "defaultShipping", true,
                        "defaultBilling", true), null).getBody());
    }

    private ResponseEntity<JsonNode> pay(String token, String orderId, String key) {
        return payWithToken(token, orderId, key, null);
    }

    private ResponseEntity<JsonNode> payWithToken(String token, String orderId, String key,
                                                  String paymentToken) {
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