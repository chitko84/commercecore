package com.commerceplatform.notification;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="processed_events")
public class ProcessedEvent {
    @Id @Column(name="event_id") private UUID eventId;
    @Column(name="consumer_name",nullable=false,length=64) private String consumerName;
    @Column(name="processed_at",nullable=false) private Instant processedAt;
    protected ProcessedEvent() { }
    ProcessedEvent(UUID id,String consumer,Instant at){eventId=id;consumerName=consumer;processedAt=at;}
}
