package com.commercecore.cart;import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.time.Instant;import java.util.*;
public final class CartDtos{private CartDtos(){}public record AddItemRequest(@NotNull UUID productId,UUID variantId,@Min(1)@Max(99)int quantity){}public record UpdateItemRequest(@Min(1)@Max(99)int quantity){}
 public record CartLine(UUID id,UUID productId,UUID variantId,String productName,String sku,int quantity,BigDecimal unitPrice,BigDecimal lineSubtotal,CartLineStatus status,long availableQuantity,Instant updatedAt){}
 public record PromotionPricing(UUID id,String code,boolean valid,String errorCode,BigDecimal eligibleSubtotal,BigDecimal discount,boolean freeShippingEligible){}
 public record CartResponse(UUID id,List<CartLine> items,BigDecimal subtotal,BigDecimal promotionDiscount,BigDecimal discountedMerchandiseTotal,boolean freeShippingEligible,PromotionPricing promotion,boolean readyForCheckout,Instant updatedAt){}
}
