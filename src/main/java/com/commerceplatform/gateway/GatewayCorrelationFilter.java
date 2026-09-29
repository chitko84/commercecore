package com.commerceplatform.gateway;

import org.slf4j.MDC;
import org.springframework.core.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Component
public class GatewayCorrelationFilter implements WebFilter,Ordered {
    public static final String HEADER="X-Correlation-Id";
    @Override public Mono<Void> filter(ServerWebExchange exchange,WebFilterChain chain){
        String id=exchange.getRequest().getHeaders().getFirst(HEADER);
        if(id==null||!id.matches("[A-Za-z0-9._:-]{1,64}"))id=UUID.randomUUID().toString();
        String correlation=id;
        ServerWebExchange clean=exchange.mutate().request(r->r.headers(h->{h.remove("X-User-Id");h.remove("X-Internal-User-Id");h.remove(HEADER);h.add(HEADER,correlation);})).response(exchange.getResponse()).build();
        clean.getResponse().getHeaders().set(HEADER,correlation);
        return chain.filter(clean).contextWrite(ctx->ctx.put("correlationId",correlation));
    }
    @Override public int getOrder(){return Ordered.HIGHEST_PRECEDENCE;}
}
