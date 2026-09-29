package com.commercecore.auth;

import com.commercecore.user.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(Phase2SecurityIT.TestSupport.class)
class Phase2SecurityIT {
    private static final String PASSWORD = "Correct-Horse-42!";
    private static final String NEW_PASSWORD = "Better-Battery-84!";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired RecordingEmailService email;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtDecoder jwtDecoder;

    @Test
    void registrationVerificationLoginAndSafeProfileWorkEndToEnd() {
        String requestedEmail = "User.Flow@Example.COM";
        ResponseEntity<JsonNode> registered = post("/api/v1/auth/register", Map.of(
                "fullName", "Flow User", "email", requestedEmail, "password", PASSWORD, "phone", "+60123456789"));
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registered.getBody().path("email").asText()).isEqualTo("user.flow@example.com");

        UserAccount account = users.findByEmail("USER.FLOW@EXAMPLE.COM").orElseThrow();
        assertThat(account.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(account.isEmailVerified()).isFalse();
        assertThat(account.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("{bcrypt}");
        String verificationToken = email.verification("user.flow@example.com");
        assertRawTokenAbsent("email_verification_tokens", verificationToken);

        assertThat(post("/api/v1/auth/verify-email", Map.of("token", verificationToken)).getStatusCode()).isEqualTo(HttpStatus.OK);
        account = users.findById(account.getId()).orElseThrow();
        assertThat(account.isEmailVerified()).isTrue();
        assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);

        JsonNode tokens = login("USER.FLOW@EXAMPLE.COM", PASSWORD).getBody();
        assertThat(tokens).isNotNull();
        Jwt jwt = jwtDecoder.decode(tokens.path("accessToken").asText());
        assertThat(jwt.getSubject()).isEqualTo(account.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CUSTOMER");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaims()).doesNotContainKeys("email", "phone", "passwordHash");

        ResponseEntity<JsonNode> me = getAuth("/api/v1/me", tokens.path("accessToken").asText());
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().path("fullName").asText()).isEqualTo("Flow User");
        assertThat(me.getBody().toString()).doesNotContainIgnoringCase("password");
    }

    @Test
    void duplicateEmailIsConflictAndRegistrationCannotSelectPrivilegedRole() {
        registerAndVerify("duplicate.phase2@example.com");
        ResponseEntity<JsonNode> duplicate = post("/api/v1/auth/register", Map.of(
                "fullName", "Duplicate", "email", "DUPLICATE.PHASE2@EXAMPLE.COM", "password", PASSWORD));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().path("code").asText()).isEqualTo("EMAIL_ALREADY_EXISTS");

        Map<String, Object> attemptedRole = new LinkedHashMap<>();
        attemptedRole.put("fullName", "No Admin"); attemptedRole.put("email", "no-admin@example.com");
        attemptedRole.put("password", PASSWORD); attemptedRole.put("role", "ADMIN");
        assertThat(post("/api/v1/auth/register", attemptedRole).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(users.findByEmail("no-admin@example.com").orElseThrow().getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void verificationTokensExpireAreSingleUseAndResendReplacesThem() {
        register("verification.lifecycle@example.com");
        String expired = email.verification("verification.lifecycle@example.com");
        jdbc.update("update email_verification_tokens set created_at = now() - interval '2 hours', expires_at = now() - interval '1 hour' where token_hash = ?",
                new SecureTokenService().hash(expired));
        assertThat(post("/api/v1/auth/verify-email", Map.of("token", expired)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(post("/api/v1/auth/resend-verification", Map.of("email", "verification.lifecycle@example.com"))
                .getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String replacement = email.verification("verification.lifecycle@example.com");
        assertThat(replacement).isNotEqualTo(expired);
        assertThat(post("/api/v1/auth/verify-email", Map.of("token", expired)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/api/v1/auth/verify-email", Map.of("token", replacement)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/api/v1/auth/verify-email", Map.of("token", replacement)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void loginFailuresAreGenericProtectedRoutesRequireBearerAndTemporaryLockExpires() {
        register("pending.login@example.com");
        ResponseEntity<JsonNode> pending = login("pending.login@example.com", PASSWORD);
        ResponseEntity<JsonNode> unknown = login("absent.login@example.com", PASSWORD);
        assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(pending.getBody().path("message")).isEqualTo(unknown.getBody().path("message"));
        assertThat(rest.getForEntity("/api/v1/me", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        registerAndVerify("locked.login@example.com");
        for (int i = 0; i < 5; i++) assertThat(login("locked.login@example.com", "Wrong-Password-42!").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        UserAccount locked = users.findByEmail("locked.login@example.com").orElseThrow();
        assertThat(locked.getAccountStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(login("locked.login@example.com", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        jdbc.update("update users set locked_until = now() - interval '1 minute' where id = ?", locked.getId());
        assertThat(login("locked.login@example.com", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        locked = users.findById(locked.getId()).orElseThrow();
        assertThat(locked.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(locked.getFailedLoginAttempts()).isZero();
    }

    @Test
    void refreshRotatesDetectsReuseAndLogoutRevokes() {
        registerAndVerify("refresh.flow@example.com");
        JsonNode first = login("refresh.flow@example.com", PASSWORD).getBody();
        String oldRefresh = first.path("refreshToken").asText();
        assertRawTokenAbsent("refresh_tokens", oldRefresh);
        ResponseEntity<JsonNode> rotated = post("/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh));
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newRefresh = rotated.getBody().path("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);
        assertThat(post("/api/v1/auth/refresh", Map.of("refreshToken", oldRefresh)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/api/v1/auth/refresh", Map.of("refreshToken", newRefresh)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String expired = login("refresh.flow@example.com", PASSWORD).getBody().path("refreshToken").asText();
        jdbc.update("update refresh_tokens set created_at = now() - interval '40 days', expires_at = now() - interval '1 day' where token_hash = ?",
                new SecureTokenService().hash(expired));
        assertThat(post("/api/v1/auth/refresh", Map.of("refreshToken", expired)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String logoutToken = login("refresh.flow@example.com", PASSWORD).getBody().path("refreshToken").asText();
        assertThat(post("/api/v1/auth/logout", Map.of("refreshToken", logoutToken)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post("/api/v1/auth/refresh", Map.of("refreshToken", logoutToken)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void profileUpdateAndPasswordChangeAreSafeAndRevokeSessions() {
        registerAndVerify("profile.password@example.com");
        JsonNode tokens = login("profile.password@example.com", PASSWORD).getBody();
        String access = tokens.path("accessToken").asText();
        String refresh = tokens.path("refreshToken").asText();
        ResponseEntity<JsonNode> updated = exchangeAuth(HttpMethod.PATCH, "/api/v1/me", access,
                Map.of("fullName", "Updated Name", "phone", "+60111111111"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().path("fullName").asText()).isEqualTo("Updated Name");
        assertThat(updated.getBody().toString()).doesNotContainIgnoringCase("passwordHash");

        assertThat(exchangeAuth(HttpMethod.PUT, "/api/v1/me/password", access,
                Map.of("currentPassword", "Wrong-Current-42!", "newPassword", NEW_PASSWORD)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchangeAuth(HttpMethod.PUT, "/api/v1/me/password", access,
                Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login("profile.password@example.com", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("profile.password@example.com", NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/api/v1/auth/refresh", Map.of("refreshToken", refresh)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passwordResetIsNonEnumeratingHashedExpiringSingleUseAndRevokesSessions() {
        registerAndVerify("reset.flow@example.com");
        String session = login("reset.flow@example.com", PASSWORD).getBody().path("refreshToken").asText();
        ResponseEntity<JsonNode> unknown = post("/api/v1/auth/forgot-password", Map.of("email", "nobody.reset@example.com"));
        ResponseEntity<JsonNode> known = post("/api/v1/auth/forgot-password", Map.of("email", "reset.flow@example.com"));
        assertThat(known.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(known.getBody().path("message")).isEqualTo(unknown.getBody().path("message"));
        String expired = email.reset("reset.flow@example.com");
        assertRawTokenAbsent("password_reset_tokens", expired);
        jdbc.update("update password_reset_tokens set created_at = now() - interval '2 hours', expires_at = now() - interval '1 hour' where token_hash = ?",
                new SecureTokenService().hash(expired));
        assertThat(post("/api/v1/auth/reset-password", Map.of("token", expired, "newPassword", NEW_PASSWORD)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        post("/api/v1/auth/forgot-password", Map.of("email", "reset.flow@example.com"));
        String valid = email.reset("reset.flow@example.com");
        assertThat(post("/api/v1/auth/reset-password", Map.of("token", valid, "newPassword", NEW_PASSWORD)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(post("/api/v1/auth/reset-password", Map.of("token", valid, "newPassword", "Another-Good-99!")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/api/v1/auth/refresh", Map.of("refreshToken", session)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("reset.flow@example.com", NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void addressCrudOwnershipAndDefaultConstraintsWork() {
        String ownerAccess = activeAccess("address.owner@example.com");
        String otherAccess = activeAccess("address.other@example.com");
        JsonNode first = exchangeAuth(HttpMethod.POST, "/api/v1/me/addresses", ownerAccess,
                address("Home", true, true)).getBody();
        JsonNode second = exchangeAuth(HttpMethod.POST, "/api/v1/me/addresses", ownerAccess,
                address("Office", true, false)).getBody();
        UUID firstId = UUID.fromString(first.path("id").asText());
        UUID secondId = UUID.fromString(second.path("id").asText());
        JsonNode listed = getAuth("/api/v1/me/addresses", ownerAccess).getBody();
        assertThat(listed).hasSize(2);
        assertThat(countDefaults(listed, "defaultShipping")).isEqualTo(1);
        assertThat(countDefaults(listed, "defaultBilling")).isEqualTo(1);
        assertThat(exchangeAuth(HttpMethod.PUT, "/api/v1/me/addresses/" + secondId + "/default-billing",
                ownerAccess, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        listed = getAuth("/api/v1/me/addresses", ownerAccess).getBody();
        assertThat(countDefaults(listed, "defaultBilling")).isEqualTo(1);
        assertThat(exchangeAuth(HttpMethod.PUT, "/api/v1/me/addresses/" + firstId,
                otherAccess, address("Stolen", false, false)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchangeAuth(HttpMethod.DELETE, "/api/v1/me/addresses/" + firstId,
                ownerAccess, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getAuth("/api/v1/me/addresses", ownerAccess).getBody()).hasSize(1);
    }

    @Test
    void roleNamespacesEnforceCustomerStaffAndAdmin() {
        String customer = activeAccess("rbac.customer@example.com");
        String staff = roleAccess("rbac.staff@example.com", UserRole.STAFF);
        String admin = roleAccess("rbac.admin@example.com", UserRole.ADMIN);
        assertThat(getAuth("/api/v1/staff/__test", customer).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getAuth("/api/v1/admin/__test", customer).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getAuth("/api/v1/staff/__test", staff).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAuth("/api/v1/admin/__test", staff).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getAuth("/api/v1/staff/__test", admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAuth("/api/v1/admin/__test", admin).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void register(String emailAddress) {
        assertThat(post("/api/v1/auth/register", Map.of("fullName", "Test User", "email", emailAddress,
                "password", PASSWORD)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
    private void registerAndVerify(String emailAddress) {
        register(emailAddress);
        assertThat(post("/api/v1/auth/verify-email", Map.of("token", email.verification(emailAddress))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
    private String activeAccess(String emailAddress) {
        registerAndVerify(emailAddress);
        return login(emailAddress, PASSWORD).getBody().path("accessToken").asText();
    }
    private String roleAccess(String emailAddress, UserRole role) {
        UserAccount user = new UserAccount(emailAddress, passwordEncoder.encode(PASSWORD), "Role User", null, role);
        user.verifyEmail(); users.saveAndFlush(user);
        return login(emailAddress, PASSWORD).getBody().path("accessToken").asText();
    }
    private ResponseEntity<JsonNode> login(String emailAddress, String password) {
        return post("/api/v1/auth/login", Map.of("email", emailAddress, "password", password));
    }
    private ResponseEntity<JsonNode> post(String path, Object body) {
        return rest.postForEntity(path, body, JsonNode.class);
    }
    private ResponseEntity<JsonNode> getAuth(String path, String accessToken) {
        return exchangeAuth(HttpMethod.GET, path, accessToken, null);
    }
    private ResponseEntity<JsonNode> exchangeAuth(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token); headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
    private Map<String, Object> address(String label, boolean shipping, boolean billing) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("label", label); value.put("recipientName", "Test Recipient"); value.put("phone", "+60123456789");
        value.put("addressLine1", "1 Test Street"); value.put("city", "Kuala Lumpur");
        value.put("stateRegion", "Kuala Lumpur"); value.put("postalCode", "50000"); value.put("countryCode", "my");
        value.put("defaultShipping", shipping); value.put("defaultBilling", billing); return value;
    }
    private long countDefaults(JsonNode list, String field) {
        long count = 0; for (JsonNode node : list) if (node.path(field).asBoolean()) count++; return count;
    }
    private void assertRawTokenAbsent(String table, String rawToken) {
        Integer count = jdbc.queryForObject("select count(*) from " + table + " where token_hash = ?", Integer.class, rawToken);
        assertThat(count).isZero();
    }

    @TestConfiguration
    static class TestSupport {
        @Bean @Primary RecordingEmailService recordingEmailService() { return new RecordingEmailService(); }
        @Bean RbacProbeController rbacProbeController() { return new RbacProbeController(); }
    }

    static class RecordingEmailService implements EmailService {
        private final Map<String, String> verification = new ConcurrentHashMap<>();
        private final Map<String, String> reset = new ConcurrentHashMap<>();
        public void sendVerification(String address, String token) { verification.put(address, token); }
        public void sendPasswordReset(String address, String token) { reset.put(address, token); }
        String verification(String address) { return verification.get(address.toLowerCase(Locale.ROOT)); }
        String reset(String address) { return reset.get(address.toLowerCase(Locale.ROOT)); }
    }

    @RestController
    static class RbacProbeController {
        @GetMapping("/api/v1/staff/__test") Map<String, Boolean> staff() { return Map.of("ok", true); }
        @GetMapping("/api/v1/admin/__test") Map<String, Boolean> admin() { return Map.of("ok", true); }
    }
}
