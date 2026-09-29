package com.commerceplatform.gateway;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args){SpringApplication app=new SpringApplication(ApiGatewayApplication.class);app.setWebApplicationType(WebApplicationType.REACTIVE);app.setAdditionalProfiles("gateway");app.run(args);}
}
