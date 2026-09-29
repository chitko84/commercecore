package com.commercecore.alert;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminAlertRepository extends JpaRepository<AdminAlert, UUID> {

    Optional<AdminAlert> findByDedupKey(String dedupKey);

    boolean existsByDedupKey(String dedupKey);

    Page<AdminAlert> findAllByAcknowledgedAtIsNullOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<AdminAlert> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    long countByAcknowledgedAtIsNull();

    long countByTypeAndResourceTypeAndResourceId(AdminAlertType type, String resourceType,
                                                 String resourceId);
}