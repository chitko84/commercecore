package com.commercecore.alert;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdminAlertDtos {
    private AdminAlertDtos() { }

    public record AdminAlertResponse(UUID id, AdminAlertType type, String title, String body,
                                     String resourceType, String resourceId, boolean acknowledged,
                                     Instant createdAt) { }

    public record UnacknowledgedCountResponse(long unacknowledgedCount) { }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
                                  int totalPages, boolean first, boolean last) { }
}