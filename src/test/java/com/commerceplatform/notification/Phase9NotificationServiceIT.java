package com.commerceplatform.notification;

import com.commerceplatform.events.EventEnvelope;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
@SpringBootTest(classes=NotificationServiceApplication.class,properties={"spring.profiles.active=notification","server.port=0","app.security.jwt-secret=phase9_test_signing_secret_at_least_32_bytes_long"})
class Phase9NotificationServiceIT {
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static KafkaContainer kafka=new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.9.1"));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.kafka.bootstrap-servers",kafka::getBootstrapServers);}
    @Autowired ObjectMapper mapper; @Autowired NotificationRecordRepository notifications;
    @Autowired ProcessedEventRepository processed; @Autowired NotificationQueryService queries;

    @BeforeEach void clear(){notifications.deleteAll();processed.deleteAll();}

    @Test void realKafkaCreatesOneNotificationAndDurablyDeduplicates() throws Exception {
        UUID eventId=UUID.randomUUID(),recipient=UUID.randomUUID();String order=UUID.randomUUID().toString();
        String json=envelope(eventId,"ORDER_CREATED",1,recipient,order,"corr-phase9");
        send("commercecore.order.events.v1",order,json);send("commercecore.order.events.v1",order,json);
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(()->{assertThat(notifications.count()).isOne();assertThat(processed.count()).isOne();});
        NotificationRecord record=notifications.findAll().getFirst();
        assertThat(record.getRecipientId()).isEqualTo(recipient);assertThat(record.getType()).isEqualTo("ORDER_CREATED");
    }

    @Test void userOwnershipIsEnforcedByEveryRepositoryLookup() throws Exception {
        UUID owner=UUID.randomUUID();send("commercecore.order.events.v1","owned",envelope(UUID.randomUUID(),"ORDER_CONFIRMED",1,owner,"owned","corr-owner"));
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertThat(notifications.count()).isOne());
        UUID id=notifications.findAll().getFirst().getId();
        assertThatThrownBy(()->queries.read(UUID.randomUUID(),id)).isInstanceOf(ResponseStatusException.class);
        assertThat(queries.page(UUID.randomUUID(),false,0,20).totalElements()).isZero();
    }

    @Test void unsupportedVersionRoutesToDlqWithoutStateCorruption() throws Exception {
        UUID id=UUID.randomUUID();String json=envelope(id,"ORDER_CREATED",99,UUID.randomUUID(),"unsupported","corr-v99");
        try(KafkaConsumer<String,String> dlq=consumer("commercecore.notification.events.dlq")){
            send("commercecore.order.events.v1","unsupported",json);
            assertThat(pollFor(dlq,id.toString())).isPresent();
        }
        assertThat(notifications.count()).isZero();assertThat(processed.count()).isZero();
    }

    @Test void malformedJsonRoutesToDlqAndConsumerContinues() throws Exception {
        String poison="{not-valid-json-"+UUID.randomUUID();
        try(KafkaConsumer<String,String> dlq=consumer("commercecore.notification.events.dlq")){
            send("commercecore.order.events.v1","poison",poison);
            assertThat(pollFor(dlq,poison)).isPresent();
        }
        UUID good=UUID.randomUUID();send("commercecore.order.events.v1","good",envelope(good,"ORDER_SHIPPED",1,UUID.randomUUID(),"good","corr-good"));
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(()->assertThat(processed.existsById(good)).isTrue());
    }

    private String envelope(UUID id,String type,int version,UUID recipient,String resource,String correlation)throws Exception{
        ObjectNode payload=mapper.createObjectNode();payload.put("recipientId",recipient.toString());payload.put("notificationType",type);payload.put("title",type);payload.put("body","Phase 9 event");payload.put("resourceType","ORDER");payload.put("resourceId",resource);
        return mapper.writeValueAsString(new EventEnvelope(id,type,version,Instant.now(),correlation,"ORDER",resource,payload));
    }
    private static void send(String topic,String key,String value){Properties p=new Properties();p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,kafka.getBootstrapServers());p.put(ProducerConfig.ACKS_CONFIG,"all");p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class);p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,StringSerializer.class);try(KafkaProducer<String,String> producer=new KafkaProducer<>(p)){producer.send(new ProducerRecord<>(topic,key,value));producer.flush();}}
    private static KafkaConsumer<String,String> consumer(String topic){Properties p=new Properties();p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,kafka.getBootstrapServers());p.put(ConsumerConfig.GROUP_ID_CONFIG,"phase9-dlq-"+UUID.randomUUID());p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class);p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class);KafkaConsumer<String,String> c=new KafkaConsumer<>(p);c.subscribe(List.of(topic));return c;}
    private static Optional<ConsumerRecord<String,String>> pollFor(KafkaConsumer<String,String> c,String marker){long end=System.nanoTime()+Duration.ofSeconds(20).toNanos();while(System.nanoTime()<end)for(ConsumerRecord<String,String> r:c.poll(Duration.ofMillis(500)))if(r.value().contains(marker))return Optional.of(r);return Optional.empty();}
}
