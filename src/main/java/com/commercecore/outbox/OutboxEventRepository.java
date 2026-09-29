package com.commercecore.outbox;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value="""
        SELECT * FROM outbox_events
        WHERE published_at IS NULL AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
        ORDER BY occurred_at LIMIT :batch FOR UPDATE SKIP LOCKED
        """, nativeQuery=true)
    List<OutboxEvent> lockPublishable(@Param("now") Instant now, @Param("batch") int batch);

    long countByPublishedAtIsNull();

    @Modifying
    @Query("delete from OutboxEvent e where e.publishedAt is not null and e.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
