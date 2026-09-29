package com.commercecore.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {
    private final OutboxEventRepository events;
    private final ObjectMapper mapper;
    private final Clock clock;

    public OutboxService(OutboxEventRepository events, ObjectMapper mapper, Clock clock) {
        this.events=events; this.mapper=mapper; this.clock=clock;
    }

    /** Must be invoked inside the business transaction; the row shares its commit/rollback. */
    public UUID append(String type, String aggregateType, String aggregateId, Map<String, ?> payload) {
        UUID id=UUID.randomUUID();
        String correlation=MDC.get("traceId");
        if(correlation==null || correlation.isBlank()) correlation=UUID.randomUUID().toString();
        events.save(new OutboxEvent(id,type,aggregateType,aggregateId,
                mapper.valueToTree(payload),correlation,clock.instant()));
        return id;
    }
}
