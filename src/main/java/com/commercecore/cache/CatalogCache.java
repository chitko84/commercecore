package com.commercecore.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Service
public class CatalogCache {
    private static final Logger log=LoggerFactory.getLogger(CatalogCache.class);
    private final StringRedisTemplate redis; private final ObjectMapper mapper;
    private final boolean enabled; private final Duration ttl;
    private final Counter hit,miss,error;

    public CatalogCache(StringRedisTemplate redis,ObjectMapper mapper,MeterRegistry metrics,
                        @Value("${app.cache.enabled:false}") boolean enabled,
                        @Value("${app.cache.ttl:5m}") Duration ttl) {
        this.redis=redis;this.mapper=mapper;this.enabled=enabled;this.ttl=ttl;
        hit=metrics.counter("redis.cache.hit");miss=metrics.counter("redis.cache.miss");error=metrics.counter("redis.cache.error");
    }
    public <T> T get(String key,Class<T> type,Supplier<T> database) {
        if(!enabled)return database.get();
        try {
            String value=redis.opsForValue().get(key);
            if(value!=null){hit.increment();return mapper.readValue(value,type);}
            miss.increment();
        } catch(Exception unavailable){error.increment();log.warn("Redis cache read failed key={}; using PostgreSQL",key);}
        T loaded=database.get();
        try {
            long jitter=ThreadLocalRandom.current().nextLong(0,Math.max(2,ttl.toSeconds()/10+1));
            redis.opsForValue().set(key,mapper.writeValueAsString(loaded),ttl.plusSeconds(jitter));
        } catch(Exception unavailable){error.increment();log.warn("Redis cache write failed key={}; response remains authoritative",key);}
        return loaded;
    }
    public void evictAfterCommit(String... keys) {
        if(!enabled)return;
        Runnable action=()->{for(String key:keys)try{redis.delete(key);}catch(Exception unavailable){error.increment();log.warn("Redis cache invalidation failed key={}",key);}};
        if(TransactionSynchronizationManager.isActualTransactionActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){action.run();}});
        else action.run();
    }
    public static String product(String slug){return "commercecore:v1:product:"+safe(slug);}
    public static String rating(String slug){return "commercecore:v1:rating:"+safe(slug);}
    private static String safe(String value){return Optional.ofNullable(value).orElse("").toLowerCase().replaceAll("[^a-z0-9-]","");}
}
