package com.commercecore.notification;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/me/notifications")
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnProperty(name="app.notifications.async",havingValue="false",matchIfMissing=true)
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    NotificationDtos.PageResponse<NotificationDtos.NotificationResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return notifications.page(UUID.fromString(jwt.getSubject()), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    NotificationDtos.UnreadCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return notifications.unreadCount(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/{id}/read")
    NotificationDtos.NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable UUID id) {
        return notifications.markRead(UUID.fromString(jwt.getSubject()), id);
    }

    @PostMapping("/read-all")
    NotificationDtos.UnreadCountResponse markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return notifications.markAllRead(UUID.fromString(jwt.getSubject()));
    }
}
