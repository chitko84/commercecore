package com.commercecore.phase9;

import com.commercecore.CommerceCoreApplication;
import com.commercecore.cache.CatalogCache;
import com.commercecore.outbox.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(classes=CommerceCoreApplication.class,properties={"app.events.enabled=true","app.notifications.async=true","app.cache.enabled=true","app.events.poll-interval=3600000","app.security.jwt-secret=phase9_test_signing_secret_at_least_32_bytes_long"})
class Phase9OutboxRedisIT {
    @Container @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static KafkaContainer kafka=new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.9.1"));
    @Container static GenericContainer<?> redis=new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        r.add("spring.kafka.bootstrap-servers",kafka::getBootstrapServers);
        r.add("spring.data.redis.host",redis::getHost);r.add("spring.data.redis.port",()->redis.getMappedPort(6379));
    }
    @Autowired OutboxService outbox; @Autowired OutboxEventRepository events;
    @Autowired OutboxPublisher publisher; @Autowired TransactionTemplate tx; @Autowired CatalogCache cache;

    @Test @Order(1) void outboxSharesBusinessTransactionRollback(){
        long before=events.count();
        assertThatThrownBy(()->tx.executeWithoutResult(status->{outbox.append("ORDER_CREATED","ORDER",UUID.randomUUID().toString(),Map.of("recipientId",UUID.randomUUID()));throw new IllegalStateException("rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(events.count()).isEqualTo(before);
    }

    @Test @Order(2) void committedOutboxPublishesRealKafkaEnvelopeWithStableKey(){
        String aggregate=UUID.randomUUID().toString();UUID recipient=UUID.randomUUID();
        tx.executeWithoutResult(status->outbox.append("ORDER_CREATED","ORDER",aggregate,Map.of("recipientId",recipient,"notificationType","ORDER_CREATED","title","Order created","body","Created","resourceType","ORDER","resourceId",aggregate)));
        try(KafkaConsumer<String,String> consumer=consumer()){
            consumer.subscribe(List.of("commercecore.order.events.v1"));
            publisher.publishBatch();
            ConsumerRecords<String,String> found=poll(consumer);
            assertThat(found).anySatisfy(record->{assertThat(record.key()).isEqualTo(aggregate);assertThat(record.value()).contains("ORDER_CREATED").contains(aggregate).contains("eventVersion");});
        }
        assertThat(events.countByPublishedAtIsNull()).isZero();
    }

    @Test @Order(3) void cacheAsideMissThenHitAndExplicitInvalidation(){
        String key="commercecore:v1:product:test-cache";AtomicInteger db=new AtomicInteger();
        assertThat(cache.get(key,String.class,()->"v"+db.incrementAndGet())).isEqualTo("v1");
        assertThat(cache.get(key,String.class,()->"v"+db.incrementAndGet())).isEqualTo("v1");
        assertThat(db).hasValue(1);
        cache.evictAfterCommit(key);
        assertThat(cache.get(key,String.class,()->"v"+db.incrementAndGet())).isEqualTo("v2");
    }

    @Test @Order(4) void redisOutageFallsBackToAuthoritativeSupplier(){
        redis.stop();AtomicInteger db=new AtomicInteger();
        assertThat(cache.get("commercecore:v1:product:redis-down",String.class,()->"postgres-"+db.incrementAndGet())).isEqualTo("postgres-1");
        assertThat(db).hasValue(1);
    }

    private static KafkaConsumer<String,String> consumer(){Properties p=new Properties();p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,kafka.getBootstrapServers());p.put(ConsumerConfig.GROUP_ID_CONFIG,"phase9-verification-"+UUID.randomUUID());p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class);p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class);return new KafkaConsumer<>(p);}
    private static ConsumerRecords<String,String> poll(KafkaConsumer<String,String> c){long end=System.nanoTime()+Duration.ofSeconds(20).toNanos();ConsumerRecords<String,String> records=ConsumerRecords.empty();while(records.isEmpty()&&System.nanoTime()<end)records=c.poll(Duration.ofMillis(500));return records;}
}
