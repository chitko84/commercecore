package com.commercecore.order;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.time.Instant;import java.util.*;public interface OrderItemRepository extends JpaRepository<OrderItem,UUID>{
 @EntityGraph(attributePaths={"attributes","inventoryReservation"})List<OrderItem>findAllByOrderIdOrderByCreatedAtAscIdAsc(UUID orderId);
 boolean existsByOrder_Customer_IdAndOrder_StatusInAndSourceProduct_Id(UUID customerId,Collection<OrderStatus> statuses,UUID productId);
 @Query(value="select oi.product_name, oi.sku, sum(oi.quantity) as units_sold, coalesce(sum(oi.line_subtotal),0) as revenue from order_items oi join orders o on o.id = oi.order_id where o.status in (:statuses) and o.created_at >= :from and o.created_at < :toExclusive group by oi.product_name, oi.sku order by revenue desc",nativeQuery=true)
 List<Object[]> reportTopProducts(@Param("from")Instant from,@Param("toExclusive")Instant toExclusive,@Param("statuses")Collection<String> statuses);
}
