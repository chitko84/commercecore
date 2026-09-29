package com.commercecore.promotion;
import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemption,UUID>{long countByPromotionId(UUID id);long countByPromotionIdAndUserId(UUID promotionId,UUID userId);Optional<PromotionRedemption> findByPromotionIdAndReferenceKey(UUID promotionId,String referenceKey);}
