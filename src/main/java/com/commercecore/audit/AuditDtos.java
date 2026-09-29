package com.commercecore.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuditDtos {
    private AuditDtos() { }

    public record AuditLogResponse(UUID id, Instant occurredAt, UUID actorUserId,
                                   String action, String category, String resourceType,
                                   String resourceId, String details) { }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
                                  int totalPages, boolean first, boolean last) { }
}
