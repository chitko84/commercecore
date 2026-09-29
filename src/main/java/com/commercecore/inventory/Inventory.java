package com.commercecore.inventory;

import com.commercecore.product.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="inventory")
public class Inventory {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="product_id") private Product product;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="variant_id") private ProductVariant variant;
    @Column(name="total_quantity",nullable=false) private long totalQuantity;
    @Column(name="reserved_quantity",nullable=false) private long reservedQuantity;
    @Column(name="sold_quantity",nullable=false) private long soldQuantity;
    @Column(name="low_stock_threshold",nullable=false) private long lowStockThreshold;
    @Version @Column(nullable=false) private long version;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected Inventory() { }
    public Inventory(Product product, ProductVariant variant, long initialQuantity, long threshold) {
        if ((product == null) == (variant == null)) throw new IllegalArgumentException("exactly one inventory owner is required");
        if (initialQuantity < 0 || threshold < 0) throw new IllegalArgumentException("inventory quantities must not be negative");
        this.product=product; this.variant=variant; this.totalQuantity=initialQuantity; this.lowStockThreshold=threshold;
    }
    public void restock(long quantity) { if(quantity<=0) throw new IllegalArgumentException(); totalQuantity=Math.addExact(totalQuantity,quantity); }
    public void adjust(long delta) { if(delta==0) throw new IllegalArgumentException(); long next=Math.addExact(totalQuantity,delta); if(next<reservedQuantity+soldQuantity) throw new IllegalArgumentException(); totalQuantity=next; }
    public void reserve(long quantity) { if(quantity<=0 || availableQuantity()<quantity) throw new IllegalArgumentException(); reservedQuantity=Math.addExact(reservedQuantity,quantity); }
    public void release(long quantity) { if(quantity<=0 || reservedQuantity<quantity) throw new IllegalArgumentException(); reservedQuantity-=quantity; }
    public void confirm(long quantity) { release(quantity); soldQuantity=Math.addExact(soldQuantity,quantity); }
    public void reverseSale(long quantity) { if(quantity<=0||soldQuantity<quantity) throw new IllegalArgumentException(); soldQuantity-=quantity; }
    public long availableQuantity(){ return totalQuantity-reservedQuantity-soldQuantity; }
    public StockStatus stockStatus(){ long available=availableQuantity(); return available==0?StockStatus.OUT_OF_STOCK:available<=lowStockThreshold?StockStatus.LOW_STOCK:StockStatus.IN_STOCK; }
    @PrePersist void create(){Instant now=Instant.now();createdAt=now;updatedAt=now;} @PreUpdate void touch(){updatedAt=Instant.now();}
    public UUID getId(){return id;} public Product getProduct(){return product;} public ProductVariant getVariant(){return variant;}
    public long getTotalQuantity(){return totalQuantity;} public long getReservedQuantity(){return reservedQuantity;}
    public long getSoldQuantity(){return soldQuantity;} public long getLowStockThreshold(){return lowStockThreshold;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
