package com.commercecore.cart;import org.springframework.data.jpa.repository.*;import java.util.*;public interface CartItemRepository extends JpaRepository<CartItem,UUID>{
 @EntityGraph(attributePaths={"product","product.category","variant"})List<CartItem> findAllByCartIdOrderByCreatedAtAsc(UUID cartId);Optional<CartItem> findByIdAndCartUserId(UUID id,UUID userId);Optional<CartItem> findByCartIdAndProductIdAndVariantIsNull(UUID cartId,UUID productId);Optional<CartItem> findByCartIdAndVariantId(UUID cartId,UUID variantId);long deleteAllByCartId(UUID cartId);
}
