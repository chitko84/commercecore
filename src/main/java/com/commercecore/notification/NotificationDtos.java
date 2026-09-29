package com.commercecore.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NotificationDtos {
    private NotificationDtos() { }

    public record NotificationResponse(UUID id, NotificationType type, String title, String body,
                                       String resourceType, String resourceId, boolean read,
                                       Instant createdAt) { }

    public record UnreadCountResponse(long unreadCount) { }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements,
                                  int totalPages, boolean first, boolean last) { }
}
