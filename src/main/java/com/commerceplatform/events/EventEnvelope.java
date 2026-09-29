package com.commerceplatform.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Stable JSON contract shared by the core producer and asynchronous consumers. */
public record EventEnvelope(UUID eventId, String eventType, int eventVersion,
                            Instant occurredAt, String correlationId,
                            String aggregateType, String aggregateId,
                            JsonNode payload) {
}
