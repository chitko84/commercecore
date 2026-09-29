package com.commerceplatform.notification;

import com.commerceplatform.events.EventEnvelope;
import com.fasterxml.jackson.databind.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;

@Service
public class NotificationConsumer {
    static final String CONSUMER="notification-service-v1";
    private static final Logger log=LoggerFactory.getLogger(NotificationConsumer.class);
    private final ObjectMapper mapper; private final NotificationRecordRepository notifications;
    private final ProcessedEventRepository processed; private final Clock clock;
    private final Counter consumed,duplicates,created;

    NotificationConsumer(ObjectMapper mapper,NotificationRecordRepository notifications,
                         ProcessedEventRepository processed,MeterRegistry metrics) {
        this.mapper=mapper;this.notifications=notifications;this.processed=processed;this.clock=Clock.systemUTC();
        consumed=metrics.counter("kafka.consumer.processed");duplicates=metrics.counter("kafka.consumer.duplicate");
        created=metrics.counter("notifications.created");
    }

    @KafkaListener(topics={"commercecore.user.events.v1","commercecore.order.events.v1",
            "commercecore.payment.events.v1","commercecore.inventory.events.v1","commercecore.review.events.v1"},
            groupId="commercecore-notification-v1")
    @Transactional
    public void receive(ConsumerRecord<String,String> record) throws Exception {
        EventEnvelope event=mapper.readValue(record.value(),EventEnvelope.class);
        if(event.eventId()==null||event.eventType()==null||event.payload()==null)
            throw new IllegalArgumentException("Event envelope is semantically invalid");
        if(event.eventVersion()!=1)throw new UnsupportedEventVersionException(event.eventType(),event.eventVersion());
        if(processed.existsById(event.eventId())){duplicates.increment();return;}
        try {
            MDC.put("traceId",event.correlationId());MDC.put("eventId",event.eventId().toString());
            JsonNode p=event.payload();
            UUID recipient=UUID.fromString(required(p,"recipientId"));
            notifications.save(new NotificationRecord(recipient,required(p,"notificationType"),required(p,"title"),
                    required(p,"body"),optional(p,"resourceType"),optional(p,"resourceId"),clock.instant()));
            processed.save(new ProcessedEvent(event.eventId(),CONSUMER,clock.instant()));
            created.increment();consumed.increment();
            log.info("notification created eventType={} aggregateId={}",event.eventType(),event.aggregateId());
        } finally {MDC.remove("traceId");MDC.remove("eventId");}
    }
    private static String required(JsonNode p,String name){String v=optional(p,name);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing payload field: "+name);return v;}
    private static String optional(JsonNode p,String name){JsonNode v=p.get(name);return v==null||v.isNull()?null:v.asText();}
}

class UnsupportedEventVersionException extends IllegalArgumentException {
    UnsupportedEventVersionException(String type,int version){super("Unsupported "+type+" eventVersion="+version);}
}
