package com.commerceplatform.notification;

import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Validated @RestController @RequestMapping("/api/v1/me/notifications")
public class NotificationApiController {
    private final NotificationQueryService service;
    NotificationApiController(NotificationQueryService service){this.service=service;}
    @GetMapping NotificationQueryService.PageResponse list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="false") boolean unreadOnly,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return service.page(user(jwt),unreadOnly,page,size);}
    @GetMapping("/unread-count") NotificationQueryService.CountResponse count(@AuthenticationPrincipal Jwt jwt){return service.count(user(jwt));}
    @PostMapping("/{id}/read") NotificationQueryService.NotificationResponse read(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return service.read(user(jwt),id);}
    @PostMapping("/read-all") NotificationQueryService.CountResponse readAll(@AuthenticationPrincipal Jwt jwt){return service.readAll(user(jwt));}
    private static UUID user(Jwt jwt){return UUID.fromString(jwt.getSubject());}
}
