package com.commercecore.payment;
import com.commercecore.user.UserAccount;
import jakarta.persistence.*;
import java.math.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="payment_refunds") public class PaymentRefund {
 @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="payment_id",nullable=false) Payment payment;
 @Column(nullable=false,precision=19,scale=2) BigDecimal amount;
 @Enumerated(EnumType.STRING) @Column(nullable=false) RefundStatus status;
 @Column(name="idempotency_key",nullable=false) String idempotencyKey;
 @Column(name="request_fingerprint",nullable=false) String requestFingerprint;
 @Column(name="provider_refund_reference") String providerRefundReference;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by") UserAccount createdBy;
 @Column(name="reconciliation_required",nullable=false) boolean reconciliationRequired;
 @Column(name="created_at",nullable=false,updatable=false) Instant createdAt;
 @Column(name="updated_at",nullable=false) Instant updatedAt;
 @Version long version;
 protected PaymentRefund(){}
 public PaymentRefund(Payment p,BigDecimal a,String k,String fp,UserAccount u){payment=p;amount=a.setScale(2,RoundingMode.HALF_UP);idempotencyKey=k;requestFingerprint=fp;createdBy=u;status=RefundStatus.PENDING;}
 public void complete(String ref){status=RefundStatus.COMPLETED;providerRefundReference=ref;reconciliationRequired=false;}
 public void fail(){status=RefundStatus.FAILED;reconciliationRequired=false;}
 public void reconcile(){reconciliationRequired=true;}
 @PrePersist void pre(){createdAt=updatedAt=Instant.now();}
 @PreUpdate void up(){updatedAt=Instant.now();}    public UUID getId(){return id;} public Payment getPayment(){return payment;} public BigDecimal getAmount(){return amount;} public RefundStatus getStatus(){return status;} public String getIdempotencyKey(){return idempotencyKey;} public String getRequestFingerprint(){return requestFingerprint;} public String getProviderRefundReference(){return providerRefundReference;} public UserAccount getCreatedBy(){return createdBy;} public boolean isReconciliationRequired(){return reconciliationRequired;} public Instant getCreatedAt(){return createdAt;}
}
