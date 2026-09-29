package com.commerceplatform.notification;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface NotificationRecordRepository extends JpaRepository<NotificationRecord,UUID> {
    Page<NotificationRecord> findAllByRecipientIdOrderByCreatedAtDescIdDesc(UUID recipientId,Pageable pageable);
    Page<NotificationRecord> findAllByRecipientIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(UUID recipientId,Pageable pageable);
    Optional<NotificationRecord> findByIdAndRecipientId(UUID id,UUID recipientId);
    long countByRecipientIdAndReadAtIsNull(UUID recipientId);
}
