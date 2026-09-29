package com.commercecore.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Minimal springdoc/OpenAPI metadata. Not over-customized: just enough
 * to identify the API in the generated docs at
 * {@code /v3/api-docs} / {@code /swagger-ui.html}.
 *
 * No license metadata is set: this is a portfolio project without a
 * settled license decision. Add one deliberately (and record it in
 * DECISIONS.md) rather than defaulting to a placeholder string.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI commerceCoreOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")))
                .info(new Info()
                        .title("CommerceCore API")
                        .description("Enterprise e-commerce API: authentication, catalog, inventory, carts, wishlists, promotions, checkout, orders and shipping.")
                        .version("v1-phase7"));
    }
}
