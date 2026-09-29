package com.commercecore.promotion;
import jakarta.validation.constraints.*;import java.math.BigDecimal;import java.time.Instant;import java.util.UUID;
public final class PromotionDtos{private PromotionDtos(){}
 public record PromotionRequest(@NotBlank@Size(max=64)String code,@NotBlank@Size(max=160)String name,@Size(max=1000)String description,
  @NotNull PromotionType type,@NotNull PromotionScope scope,@Digits(integer=17,fraction=4)BigDecimal discountValue,
  @NotNull@DecimalMin("0.00")@Digits(integer=17,fraction=2)BigDecimal minimumSpend,@DecimalMin("0.01")@Digits(integer=17,fraction=2)BigDecimal maximumDiscount,
  @NotNull Instant startsAt,@NotNull Instant endsAt,@Positive Long totalUsageLimit,@Positive Long perUserUsageLimit,boolean active,UUID targetProductId,UUID targetCategoryId){}
 public record PromotionResponse(UUID id,String code,String name,String description,PromotionType type,PromotionScope scope,BigDecimal discountValue,BigDecimal minimumSpend,BigDecimal maximumDiscount,Instant startsAt,Instant endsAt,Long totalUsageLimit,Long perUserUsageLimit,boolean active,UUID targetProductId,UUID targetCategoryId,long totalRedemptions,Instant createdAt,Instant updatedAt){}
 public record CouponRequest(@NotBlank@Size(max=64)String code){}
}
