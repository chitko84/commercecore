package com.commercecore.outbox;

import jakarta.persistence.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id private UUID id;
    @Column(name="event_type", nullable=false, length=64) private String eventType;
    @Column(name="event_version", nullable=false) private int eventVersion;
    @Column(name="aggregate_type", nullable=false, length=32) private String aggregateType;
    @Column(name="aggregate_id", nullable=false, length=128) private String aggregateId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable=false, columnDefinition="jsonb") private JsonNode payload;
    @Column(name="correlation_id", nullable=false, length=64) private String correlationId;
    @Column(name="occurred_at", nullable=false) private Instant occurredAt;
    @Column(name="published_at") private Instant publishedAt;
    @Column(name="attempt_count", nullable=false) private int attemptCount;
    @Column(name="next_attempt_at") private Instant nextAttemptAt;
    @Column(name="last_error", length=1000) private String lastError;

    protected OutboxEvent() { }

    OutboxEvent(UUID id, String eventType, String aggregateType, String aggregateId,
                JsonNode payload, String correlationId, Instant occurredAt) {
        this.id=id; this.eventType=eventType; this.eventVersion=1;
        this.aggregateType=aggregateType; this.aggregateId=aggregateId;
        this.payload=payload; this.correlationId=correlationId; this.occurredAt=occurredAt;
    }

    void published(Instant now) { publishedAt=now; lastError=null; nextAttemptAt=null; }
    void failed(Instant retryAt, String error) {
        attemptCount++; nextAttemptAt=retryAt;
        lastError=error == null ? "unknown publish failure" : error.substring(0, Math.min(1000, error.length()));
    }
    public UUID getId(){return id;} public String getEventType(){return eventType;}
    public int getEventVersion(){return eventVersion;} public String getAggregateType(){return aggregateType;}
    public String getAggregateId(){return aggregateId;} public JsonNode getPayload(){return payload;}
    public String getCorrelationId(){return correlationId;} public Instant getOccurredAt(){return occurredAt;}
    public Instant getPublishedAt(){return publishedAt;} public int getAttemptCount(){return attemptCount;}
}
