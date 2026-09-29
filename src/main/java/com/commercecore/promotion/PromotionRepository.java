package com.commercecore.promotion;
import jakarta.persistence.LockModeType;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.util.*;
public interface PromotionRepository extends JpaRepository<Promotion,UUID>{boolean existsByCode(String code);boolean existsByCodeAndIdNot(String code,UUID id);Optional<Promotion> findByCode(String code);@Lock(LockModeType.PESSIMISTIC_WRITE)@Query("select p from Promotion p where p.id=:id")Optional<Promotion> findLockedById(@Param("id")UUID id);}
