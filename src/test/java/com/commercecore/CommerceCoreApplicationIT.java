package com.commercecore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration test against real PostgreSQL and Redis instances
 * (via Testcontainers), verifying the pieces that only make sense with
 * a running application + database:
 *
 * <ul>
 *   <li>the Spring context loads and Flyway runs successfully;</li>
 *   <li>{@code /actuator/health} reports UP;</li>
 *   <li>Swagger UI and {@code /v3/api-docs} are reachable without
 *       authentication (per the Phase-1 security config).</li>
 * </ul>
 *
 * Redis is included because the Phase 9 Redis cache health indicator is
 * part of the overall health status; the app is genuinely healthy only
 * when it can reach the components it depends on.
 *
 * Requires Docker. Uses the official
 * {@code @Testcontainers(disabledWithoutDocker = true)} mechanism: when
 * Docker is unavailable, JUnit reports every test in this class as
 * disabled/skipped (visible in the test report as "disabled", not
 * silently omitted, and not a manual assumption hack). When Docker IS
 * available, these tests genuinely execute against real containers --
 * a real failure here is a real failure, never hidden.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CommerceCoreApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void actuatorHealth_isUpAndPubliclyReachable() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void apiDocs_isPubliclyReachable() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("CommerceCore API");
    }

    @Test
    void swaggerUi_isPubliclyReachable() {
        ResponseEntity<String> response = restTemplate.getForEntity("/swagger-ui.html", String.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.FOUND);
    }
}