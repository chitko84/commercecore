package com.commercecore.order;import jakarta.persistence.LockModeType;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;public interface CustomerOrderRepository extends JpaRepository<CustomerOrder,UUID>,JpaSpecificationExecutor<CustomerOrder>{Optional<CustomerOrder>findByCustomerIdAndIdempotencyKey(UUID customerId,String key);Optional<CustomerOrder>findByIdAndCustomerId(UUID id,UUID customerId);@Lock(LockModeType.PESSIMISTIC_WRITE)@Query("select o from CustomerOrder o where o.id=:id")Optional<CustomerOrder>findLockedById(@Param("id")UUID id);List<CustomerOrder>findAllByStatusAndReservationExpiresAtLessThanEqual(OrderStatus status,Instant now);
 @Query(value="select date_trunc('day', created_at) as day, count(*) as order_count, coalesce(sum(grand_total),0) as gross_sales from orders where status in (:statuses) and created_at >= :from and created_at < :toExclusive group by date_trunc('day', created_at) order by date_trunc('day', created_at)",nativeQuery=true)
 List<Object[]> reportSalesByDay(@Param("from")Instant from,@Param("toExclusive")Instant toExclusive,@Param("statuses")Collection<String> statuses);

 @Query("select coalesce(sum(o.grandTotal), 0) from CustomerOrder o where (:status is null or o.status = :status) and o.createdAt >= :from and o.createdAt < :toExclusive")
 BigDecimal reportOrderTotal(@Param("from")Instant from,@Param("toExclusive")Instant toExclusive,@Param("status")OrderStatus status);

 @Query("select count(o) from CustomerOrder o where (:status is null or o.status = :status) and o.createdAt >= :from and o.createdAt < :toExclusive")
 long reportOrderCount(@Param("from")Instant from,@Param("toExclusive")Instant toExclusive,@Param("status")OrderStatus status);

 @Query("select o.status, count(o) from CustomerOrder o group by o.status")
 List<Object[]> countByStatusAll();

 @Query("select coalesce(sum(o.grandTotal), 0) from CustomerOrder o where o.status in :statuses")
 BigDecimal reportCapturedTotal(@Param("statuses")Collection<OrderStatus> statuses);

 @Query("select coalesce(sum(o.grandTotal), 0) from CustomerOrder o where o.status in :statuses " +
         "and o.createdAt >= :from and o.createdAt < :toExclusive")
 BigDecimal reportCapturedTotalBetween(@Param("from")Instant from,@Param("toExclusive")Instant toExclusive,
         @Param("statuses")Collection<OrderStatus> statuses);

 List<CustomerOrder> findTop10ByOrderByCreatedAtDesc();}
