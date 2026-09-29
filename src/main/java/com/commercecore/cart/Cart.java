package com.commercecore.cart;
import com.commercecore.promotion.Promotion;import com.commercecore.user.UserAccount;import jakarta.persistence.*;import java.time.Instant;import java.util.UUID;
@Entity@Table(name="carts")public class Cart{
 @Id@GeneratedValue(strategy=GenerationType.UUID)private UUID id;@OneToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="user_id",nullable=false,unique=true)private UserAccount user;
 @ManyToOne(fetch=FetchType.LAZY)@JoinColumn(name="applied_promotion_id")private Promotion appliedPromotion;@Column(name="created_at",nullable=false,updatable=false)private Instant createdAt;@Column(name="updated_at",nullable=false)private Instant updatedAt;@Version@Column(nullable=false)private long version;
 protected Cart(){}public Cart(UserAccount user){this.user=user;}public void apply(Promotion p){appliedPromotion=p;}public void removePromotion(){appliedPromotion=null;}@PrePersist void create(){Instant now=Instant.now();createdAt=now;updatedAt=now;}@PreUpdate void touch(){updatedAt=Instant.now();}
 public void markChanged(){updatedAt=Instant.now();}
 public UUID getId(){return id;}public UserAccount getUser(){return user;}public Promotion getAppliedPromotion(){return appliedPromotion;}public Instant getCreatedAt(){return createdAt;}public Instant getUpdatedAt(){return updatedAt;}
}
