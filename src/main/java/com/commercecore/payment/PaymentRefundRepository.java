package com.commercecore.payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund,UUID>,JpaSpecificationExecutor<PaymentRefund>{
 Optional<PaymentRefund>findByPaymentIdAndIdempotencyKey(UUID p,String k);
 List<PaymentRefund>findAllByPaymentIdOrderByCreatedAtAsc(UUID p);
 long countByPaymentIdAndStatus(UUID p,RefundStatus s);
 @Lock(LockModeType.PESSIMISTIC_WRITE)@Query("select r from PaymentRefund r where r.id=:id")Optional<PaymentRefund>findLocked(@Param("id")UUID id);
 @Query(value="select date_trunc('day', created_at) as day, count(*) as refund_count, coalesce(sum(amount),0) as refund_amount from payment_refunds where status = 'COMPLETED' and created_at >= :from and created_at < :toExclusive group by date_trunc('day', created_at) order by date_trunc('day', created_at)",nativeQuery=true)
 List<Object[]> reportCompletedRefundsByDay(@Param("from")Instant from,@Param("toExclusive")Instant toExclusive);

 @Query("select coalesce(sum(r.amount), 0) from PaymentRefund r where (:status is null or r.status = :status) and r.createdAt >= :from and r.createdAt < :toExclusive")
 BigDecimal reportTotal(@Param("from")Instant from,@Param("toExclusive")Instant toExclusive,@Param("status")RefundStatus status);

 @Query("select count(r) from PaymentRefund r where (:status is null or r.status = :status) and r.createdAt >= :from and r.createdAt < :toExclusive")
 long reportCount(@Param("from")Instant from,@Param("toExclusive")Instant toExclusive,@Param("status")RefundStatus status);

 @Query("select coalesce(sum(r.amount), 0) from PaymentRefund r where r.status = 'COMPLETED'")
 BigDecimal reportCompletedRefundTotal();
}
