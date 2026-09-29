package com.commerceplatform.gateway;

import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.URI;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes=ApiGatewayApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="spring.profiles.active=gateway")
@AutoConfigureObservability
class Phase9GatewayObservabilityIT {
    static final DisposableServer CORE=backend("core");
    static final DisposableServer NOTIFICATIONS=backend("notification");
    @DynamicPropertySource static void urls(DynamicPropertyRegistry r){r.add("gateway.core-url",()->"http://localhost:"+CORE.port());r.add("gateway.notification-url",()->"http://localhost:"+NOTIFICATIONS.port());}
    @Autowired WebTestClient client;
    @LocalServerPort int port;

    @AfterAll static void stop(){CORE.disposeNow();NOTIFICATIONS.disposeNow();}

    @Test void routesCoreAndAddsValidatedCorrelationId(){client.get().uri("/api/v1/products/demo").exchange().expectStatus().isOk().expectHeader().valueMatches("X-Correlation-Id","[0-9a-f-]{36}").expectBody(String.class).value(body->assertThat(body).contains("core").contains("/api/v1/products/demo"));}
    @Test void routesNotificationsAndStripsUntrustedIdentityHeader(){client.get().uri("/api/v1/me/notifications").header("X-User-Id","attacker").header("X-Correlation-Id","trusted-correlation").exchange().expectStatus().isOk().expectHeader().valueEquals("X-Correlation-Id","trusted-correlation").expectBody(String.class).value(body->assertThat(body).contains("notification").contains("identity=none").contains("correlation=trusted-correlation"));}
    @Test void preservesEncodedQueryParametersWithoutDoubleEncoding(){client.get().uri(URI.create("http://localhost:"+port+"/api/v1/admin/reports/sales?from=2026-06-30T19%3A50%3A16Z")).exchange().expectStatus().isOk().expectBody(String.class).value(body->assertThat(body).contains("from=2026-06-30T19%3A50%3A16Z").doesNotContain("%253A"));}
    @Test void gatewayHealthAndPrometheusAreExposed(){client.get().uri("/actuator/health").exchange().expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("UP");client.get().uri("/actuator/prometheus").exchange().expectStatus().isOk().expectBody(String.class).value(body->assertThat(body).contains("jvm_info"));}

    private static DisposableServer backend(String name){return HttpServer.create().port(0).handle((req,res)->{String identity=req.requestHeaders().get("X-User-Id");String correlation=req.requestHeaders().get("X-Correlation-Id");return res.header(HttpHeaderNames.CONTENT_TYPE,"text/plain").sendString(reactor.core.publisher.Mono.just(name+" path="+req.uri()+" identity="+(identity==null?"none":identity)+" correlation="+correlation));}).bindNow();}
}
