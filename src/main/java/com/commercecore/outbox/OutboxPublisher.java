package com.commercecore.outbox;

import com.commerceplatform.events.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name="app.events.enabled", havingValue="true")
public class OutboxPublisher {
    private static final Logger log=LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxEventRepository events; private final KafkaTemplate<String,Object> kafka;
    private final ObjectMapper mapper; private final Clock clock; private final int batch; private final Duration retention;
    private final Counter success, failure;

    public OutboxPublisher(OutboxEventRepository events, KafkaTemplate<String,Object> kafka,
                           ObjectMapper mapper, Clock clock, MeterRegistry metrics,
                           @Value("${app.events.batch-size:50}") int batch,
                           @Value("${app.events.retention:30d}") Duration retention) {
        this.events=events;this.kafka=kafka;this.mapper=mapper;this.clock=clock;this.batch=batch;this.retention=retention;
        success=metrics.counter("outbox.publish.success"); failure=metrics.counter("outbox.publish.failure");
        metrics.gauge("outbox.pending",events,OutboxEventRepository::countByPublishedAtIsNull);
    }

    @Scheduled(fixedDelayString="${app.events.poll-interval:1000}")
    @Transactional
    public void publishBatch() {
        List<OutboxEvent> batchEvents=events.lockPublishable(clock.instant(),batch);
        for(OutboxEvent event:batchEvents) {
            try {
                EventEnvelope envelope=new EventEnvelope(event.getId(),event.getEventType(),event.getEventVersion(),
                        event.getOccurredAt(),event.getCorrelationId(),event.getAggregateType(),event.getAggregateId(),
                        event.getPayload());
                kafka.send(topic(event.getAggregateType()),event.getAggregateId(),envelope).get(10, TimeUnit.SECONDS);
                event.published(clock.instant()); success.increment();
                log.info("outbox published eventId={} eventType={} aggregateId={}",event.getId(),event.getEventType(),event.getAggregateId());
            } catch(Exception error) {
                event.failed(clock.instant().plusSeconds(Math.min(60,1L << Math.min(6,event.getAttemptCount()))),error.getMessage());
                failure.increment();
                log.warn("outbox publish failed eventId={} eventType={} aggregateId={}",event.getId(),event.getEventType(),event.getAggregateId(),error);
            }
        }
    }

    @Scheduled(cron="${app.events.cleanup-cron:0 15 3 * * *}")
    @Transactional
    public void cleanup() {
        events.deletePublishedBefore(clock.instant().minus(retention));
    }

    static String topic(String aggregateType) {
        return "commercecore."+aggregateType.toLowerCase()+".events.v1";
    }
}
