package com.commerceplatform.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.net.URI;

@Configuration
public class GatewayRoutes {
    @Bean WebClient gatewayClient(WebClient.Builder builder){return builder.build();}
    @Bean RouterFunction<ServerResponse> routes(WebClient client,
            @Value("${gateway.core-url}") String core,@Value("${gateway.notification-url}") String notification){
        return RouterFunctions.route(RequestPredicates.path("/api/**"),request->proxy(client,request,
                request.path().startsWith("/api/v1/me/notifications")?notification:core));
    }
    private Mono<ServerResponse> proxy(WebClient client,ServerRequest request,String base){
        String query=request.uri().getRawQuery();String target=base+request.path()+(query==null?"":"?"+query);
        return request.bodyToMono(byte[].class).defaultIfEmpty(new byte[0]).flatMap(body->client.method(request.method()).uri(URI.create(target))
                .headers(headers->{headers.addAll(request.headers().asHttpHeaders());headers.remove(HttpHeaders.HOST);headers.remove(HttpHeaders.CONTENT_LENGTH);headers.remove("X-User-Id");headers.remove("X-Internal-User-Id");})
                .body(body.length==0?BodyInserters.empty():BodyInserters.fromValue(body)).exchangeToMono(response->
                        response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0]).flatMap(bytes->{
                            ServerResponse.BodyBuilder result=ServerResponse.status(response.statusCode());
                            response.headers().asHttpHeaders().forEach((name,values)->{if(!name.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)&&!name.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH))result.header(name,values.toArray(String[]::new));});
                            return bytes.length==0?result.build():result.bodyValue(bytes);
                        })));
    }
}
