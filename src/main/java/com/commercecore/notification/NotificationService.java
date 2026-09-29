package com.commercecore.notification;

import com.commercecore.common.exception.ApiException;
import com.commercecore.outbox.OutboxService;
import com.commercecore.user.UserAccount;
import com.commercecore.user.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserAccountRepository users;
    private final Clock clock;
    private final OutboxService outbox;
    private final boolean asynchronous;

    public NotificationService(NotificationRepository notifications,
                               UserAccountRepository users, Clock clock, OutboxService outbox,
                               @Value("${app.notifications.async:false}") boolean asynchronous) {
        this.notifications = notifications;
        this.users = users;
        this.clock = clock;
        this.outbox = outbox;
        this.asynchronous = asynchronous;
    }

    /** Creates an unread in-app notification for a customer. */
    @Transactional
    public void notify(UUID recipientId, NotificationType type, String title, String body,
                       String resourceType, String resourceId) {
        if (asynchronous) {
            Map<String,Object> payload=new LinkedHashMap<>();
            payload.put("recipientId",recipientId); payload.put("notificationType",type.name());
            payload.put("title",title); payload.put("body",body);
            payload.put("resourceType",resourceType); payload.put("resourceId",resourceId);
            String eventType=switch(type){case WELCOME->"USER_REGISTERED"; default->type.name();};
            String aggregateType=switch(type){
                case WELCOME, PASSWORD_CHANGED, PASSWORD_RESET -> "USER";
                case PAYMENT_SUCCEEDED, PAYMENT_FAILED, REFUND_COMPLETED -> "PAYMENT";
                default -> "ORDER";
            };
            outbox.append(eventType,aggregateType,resourceId==null?recipientId.toString():resourceId,payload);
            return;
        }
        UserAccount recipient = users.findById(recipientId).orElse(null);
        if (recipient == null) {
            return;
        }
        notifications.save(new Notification(recipient, type, title, body, resourceType, resourceId));
    }

    @Transactional(readOnly = true)
    public NotificationDtos.PageResponse<NotificationDtos.NotificationResponse> page(
            UUID recipientId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = pageRequest(page, size);
        Page<Notification> result = unreadOnly
                ? notifications.findAllByRecipientIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(
                        recipientId, pageable)
                : notifications.findAllByRecipientIdOrderByCreatedAtDescIdDesc(recipientId, pageable);
        return new NotificationDtos.PageResponse<>(
                result.getContent().stream().map(this::response).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Transactional(readOnly = true)
    public NotificationDtos.UnreadCountResponse unreadCount(UUID recipientId) {
        return new NotificationDtos.UnreadCountResponse(
                notifications.countByRecipientIdAndReadAtIsNull(recipientId));
    }

    @Transactional
    public NotificationDtos.NotificationResponse markRead(UUID recipientId, UUID notificationId) {
        Notification notification = notifications.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND",
                        "Notification was not found."));
        notification.markRead(clock.instant());
        return response(notification);
    }

    @Transactional
    public NotificationDtos.UnreadCountResponse markAllRead(UUID recipientId) {
        int page = 0;
        while (true) {
            Page<Notification> unread = notifications
                    .findAllByRecipientIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(
                            recipientId, PageRequest.of(page, 100));
            if (!unread.hasContent()) {
                break;
            }
            unread.forEach(notification -> notification.markRead(clock.instant()));
            if (unread.isLast()) {
                break;
            }
            page++;
        }
        return unreadCount(recipientId);
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Page parameters are invalid.");
        }
        return PageRequest.of(page, size);
    }

    private NotificationDtos.NotificationResponse response(Notification notification) {
        return new NotificationDtos.NotificationResponse(notification.getId(), notification.getType(),
                notification.getTitle(), notification.getBody(), notification.getResourceType(),
                notification.getResourceId(), notification.isRead(), notification.getCreatedAt());
    }

    private static ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}
