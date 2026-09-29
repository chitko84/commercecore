package com.commerceplatform.gateway;

import org.springframework.context.annotation.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class GatewaySecurityConfiguration {
    /** Core and Notification Service validate the bearer JWT; gateway never manufactures identity. */
    @Bean SecurityWebFilterChain gatewaySecurity(ServerHttpSecurity http){return http.csrf(ServerHttpSecurity.CsrfSpec::disable).authorizeExchange(a->a.anyExchange().permitAll()).build();}
}
