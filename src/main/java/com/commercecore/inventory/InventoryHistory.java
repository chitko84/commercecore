package com.commercecore.inventory;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="inventory_history")
public class InventoryHistory {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="inventory_id",nullable=false) private Inventory inventory;
    @Enumerated(EnumType.STRING) @Column(name="movement_type",nullable=false,length=32) private MovementType movementType;
    @Column(nullable=false) private long quantity;
    @Column(name="before_total",nullable=false) private long beforeTotal;
    @Column(name="before_reserved",nullable=false) private long beforeReserved;
    @Column(name="before_sold",nullable=false) private long beforeSold;
    @Column(name="after_total",nullable=false) private long afterTotal;
    @Column(name="after_reserved",nullable=false) private long afterReserved;
    @Column(name="after_sold",nullable=false) private long afterSold;
    @Column(name="reference_key",length=160) private String referenceKey;
    @Column(length=500) private String reason;
    @Column(name="actor_user_id") private UUID actorUserId;
    @Column(name="occurred_at",nullable=false,updatable=false) private Instant occurredAt;
    protected InventoryHistory() { }
    public InventoryHistory(Inventory inventory,MovementType type,long quantity,Snapshot before,String reference,String reason,UUID actor){
        this.id=UUID.randomUUID();this.inventory=inventory;this.movementType=type;this.quantity=quantity;
        this.beforeTotal=before.total();this.beforeReserved=before.reserved();this.beforeSold=before.sold();
        this.afterTotal=inventory.getTotalQuantity();this.afterReserved=inventory.getReservedQuantity();this.afterSold=inventory.getSoldQuantity();
        this.referenceKey=reference;this.reason=reason;this.actorUserId=actor;this.occurredAt=Instant.now();
    }
    public static Snapshot snapshot(Inventory i){return new Snapshot(i.getTotalQuantity(),i.getReservedQuantity(),i.getSoldQuantity());}
    public UUID getId(){return id;} public MovementType getMovementType(){return movementType;} public long getQuantity(){return quantity;}
    public long getBeforeTotal(){return beforeTotal;} public long getBeforeReserved(){return beforeReserved;} public long getBeforeSold(){return beforeSold;}
    public long getAfterTotal(){return afterTotal;} public long getAfterReserved(){return afterReserved;} public long getAfterSold(){return afterSold;}
    public String getReferenceKey(){return referenceKey;} public String getReason(){return reason;} public UUID getActorUserId(){return actorUserId;}
    public Instant getOccurredAt(){return occurredAt;} public record Snapshot(long total,long reserved,long sold) { }
}
