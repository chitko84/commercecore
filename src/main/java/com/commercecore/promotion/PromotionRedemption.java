package com.commercecore.promotion;
import com.commercecore.user.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="promotion_redemptions")
public class PromotionRedemption{
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="promotion_id",nullable=false)private Promotion promotion;
 @ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="user_id",nullable=false)private UserAccount user;
 @Column(name="reference_key",nullable=false,length=160)private String referenceKey;
 @Column(name="redeemed_at",nullable=false,updatable=false)private Instant redeemedAt;
 protected PromotionRedemption(){} public PromotionRedemption(Promotion p,UserAccount u,String ref){if(ref==null||ref.isBlank()||ref.trim().length()>160)throw new IllegalArgumentException();promotion=p;user=u;referenceKey=ref.trim();}
 @PrePersist void create(){redeemedAt=Instant.now();}public UUID getId(){return id;}public String getReferenceKey(){return referenceKey;}public Instant getRedeemedAt(){return redeemedAt;}
}
