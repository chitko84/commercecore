package com.commercecore.alert;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/v1/admin/alerts")
@SecurityRequirement(name = "bearerAuth")
public class AdminAlertController {

    private final AdminAlertService alerts;

    public AdminAlertController(AdminAlertService alerts) {
        this.alerts = alerts;
    }

    @GetMapping
    AdminAlertDtos.PageResponse<AdminAlertDtos.AdminAlertResponse> list(
            @RequestParam(defaultValue = "true") boolean unacknowledgedOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return alerts.page(unacknowledgedOnly, page, size);
    }

    @GetMapping("/unacknowledged-count")
    AdminAlertDtos.UnacknowledgedCountResponse unacknowledgedCount() {
        return alerts.unacknowledgedCount();
    }

    @PostMapping("/{id}/acknowledge")
    AdminAlertDtos.AdminAlertResponse acknowledge(@PathVariable UUID id) {
        return alerts.acknowledge(id);
    }
}