package com.commerceplatform.notification;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfiguration {
    @Bean
    DefaultErrorHandler notificationErrorHandler(KafkaTemplate<Object,Object> kafka,MeterRegistry metrics) {
        DeadLetterPublishingRecoverer recoverer=new DeadLetterPublishingRecoverer(kafka,
                (record,error)->new TopicPartition("commercecore.notification.events.dlq",record.partition()));
        DefaultErrorHandler handler=new DefaultErrorHandler((record,error)->{
            metrics.counter("kafka.consumer.dlq").increment();recoverer.accept(record,error);
        },new FixedBackOff(500,2));
        handler.setRetryListeners((record,error,attempt)->metrics.counter("kafka.consumer.retry").increment());
        handler.addNotRetryableExceptions(UnsupportedEventVersionException.class,
                com.fasterxml.jackson.core.JsonProcessingException.class,IllegalArgumentException.class);
        return handler;
    }
    @Bean NewTopic userTopic(){return TopicBuilder.name("commercecore.user.events.v1").partitions(3).replicas(1).build();}
    @Bean NewTopic orderTopic(){return TopicBuilder.name("commercecore.order.events.v1").partitions(3).replicas(1).build();}
    @Bean NewTopic paymentTopic(){return TopicBuilder.name("commercecore.payment.events.v1").partitions(3).replicas(1).build();}
    @Bean NewTopic inventoryTopic(){return TopicBuilder.name("commercecore.inventory.events.v1").partitions(3).replicas(1).build();}
    @Bean NewTopic reviewTopic(){return TopicBuilder.name("commercecore.review.events.v1").partitions(3).replicas(1).build();}
    @Bean NewTopic dlqTopic(){return TopicBuilder.name("commercecore.notification.events.dlq").partitions(3).replicas(1).build();}
}
