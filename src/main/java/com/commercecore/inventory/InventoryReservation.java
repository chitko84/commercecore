package com.commercecore.inventory;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="inventory_reservations")
public class InventoryReservation {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="inventory_id",nullable=false) private Inventory inventory;
    @Column(nullable=false) private long quantity;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ReservationStatus status;
    @Column(name="reference_key",nullable=false,length=160) private String referenceKey;
    @Column(name="expires_at") private Instant expiresAt;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected InventoryReservation() { }
    public InventoryReservation(Inventory inventory,long quantity,String referenceKey,Instant expiresAt){
        if(quantity<=0||referenceKey==null||referenceKey.isBlank()||referenceKey.trim().length()>160)throw new IllegalArgumentException();
        this.inventory=inventory;this.quantity=quantity;this.referenceKey=referenceKey.trim();this.expiresAt=expiresAt;this.status=ReservationStatus.ACTIVE;
    }
    public void release(){requireActive();status=ReservationStatus.RELEASED;} public void confirm(){requireActive();status=ReservationStatus.CONFIRMED;}
    public void expire(){requireActive();status=ReservationStatus.EXPIRED;} private void requireActive(){if(status!=ReservationStatus.ACTIVE)throw new IllegalStateException();}
    @PrePersist void create(){Instant now=Instant.now();createdAt=now;updatedAt=now;} @PreUpdate void touch(){updatedAt=Instant.now();}
    public UUID getId(){return id;} public Inventory getInventory(){return inventory;} public long getQuantity(){return quantity;}
    public ReservationStatus getStatus(){return status;} public String getReferenceKey(){return referenceKey;}
    public Instant getExpiresAt(){return expiresAt;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
