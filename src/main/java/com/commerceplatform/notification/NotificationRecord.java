package com.commerceplatform.notification;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="notifications")
public class NotificationRecord {
    @Id private UUID id;
    @Column(name="recipient_id",nullable=false) private UUID recipientId;
    @Column(nullable=false,length=32) private String type;
    @Column(nullable=false,length=120) private String title;
    @Column(nullable=false,length=1000) private String body;
    @Column(name="resource_type",length=32) private String resourceType;
    @Column(name="resource_id",length=128) private String resourceId;
    @Column(name="read_at") private Instant readAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected NotificationRecord() { }
    NotificationRecord(UUID recipientId,String type,String title,String body,String resourceType,String resourceId,Instant now){
        this.id=UUID.randomUUID();this.recipientId=recipientId;this.type=type;this.title=title;this.body=body;
        this.resourceType=resourceType;this.resourceId=resourceId;this.createdAt=now;
    }
    void markRead(Instant now){if(readAt==null)readAt=now;}
    public UUID getId(){return id;} public UUID getRecipientId(){return recipientId;} public String getType(){return type;}
    public String getTitle(){return title;} public String getBody(){return body;} public String getResourceType(){return resourceType;}
    public String getResourceId(){return resourceId;} public Instant getReadAt(){return readAt;}
    public Instant getCreatedAt(){return createdAt;} public boolean isRead(){return readAt!=null;}
}
