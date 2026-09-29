package com.commercecore.alert;

import com.commercecore.common.exception.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class AdminAlertService {

    private final AdminAlertRepository alerts;
    private final Clock clock;

    public AdminAlertService(AdminAlertRepository alerts, Clock clock) {
        this.alerts = alerts;
        this.clock = clock;
    }

    /**
     * Records a low-stock alert for a durable transition into low stock.
     * Each distinct crossing produces its own alert (the count of prior
     * alerts for the inventory is part of the dedup key), so a stock level
     * that drops, is restocked above the threshold and drops again alerts
     * afresh, while repeated evaluations of the same crossing never
     * duplicate an alert.
     */
    @Transactional
    public void recordLowStock(UUID inventoryId, String ownerLabel, long available,
                               long threshold) {
        String resourceId = inventoryId.toString();
        long prior = alerts.countByTypeAndResourceTypeAndResourceId(
                AdminAlertType.LOW_STOCK_ALERT, "INVENTORY", resourceId);
        save(AdminAlertType.LOW_STOCK_ALERT,
                "Low stock: " + ownerLabel,
                "Available stock (" + available + ") has dropped to or below the threshold of "
                        + threshold + " for " + ownerLabel + ".",
                "INVENTORY", resourceId,
                "LOW_STOCK:" + resourceId + ":" + (prior + 1));
    }

    /**
     * Records a failed-payment alert for a definitive payment failure.
     * Transient retries never reach this point (the payment is only marked
     * FAILED after retries are exhausted or on a non-retryable error), and
     * the dedup key is the payment itself so one payment produces at most
     * one alert.
     */
    @Transactional
    public void recordFailedPayment(UUID paymentId, String orderNumber) {
        save(AdminAlertType.FAILED_PAYMENT_ALERT,
                "Payment failed",
                "Payment for order " + orderNumber + " failed and needs attention.",
                "PAYMENT", paymentId.toString(),
                "FAILED_PAYMENT:" + paymentId);
    }

    @Transactional(readOnly = true)
    public AdminAlertDtos.PageResponse<AdminAlertDtos.AdminAlertResponse> page(
            boolean unacknowledgedOnly, int page, int size) {
        PageRequest pageable = pageRequest(page, size);
        Page<AdminAlert> result = unacknowledgedOnly
                ? alerts.findAllByAcknowledgedAtIsNullOrderByCreatedAtDescIdDesc(pageable)
                : alerts.findAllByOrderByCreatedAtDescIdDesc(pageable);
        return new AdminAlertDtos.PageResponse<>(
                result.getContent().stream().map(this::response).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Transactional(readOnly = true)
    public AdminAlertDtos.UnacknowledgedCountResponse unacknowledgedCount() {
        return new AdminAlertDtos.UnacknowledgedCountResponse(alerts.countByAcknowledgedAtIsNull());
    }

    @Transactional
    public AdminAlertDtos.AdminAlertResponse acknowledge(UUID id) {
        AdminAlert alert = alerts.findById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND",
                        "Alert was not found."));
        alert.acknowledge(clock.instant());
        return response(alert);
    }

    private void save(AdminAlertType type, String title, String body,
                      String resourceType, String resourceId, String dedupKey) {
        if (alerts.existsByDedupKey(dedupKey)) {
            return;
        }
        try {
            alerts.saveAndFlush(new AdminAlert(type, title, body, resourceType, resourceId, dedupKey));
        } catch (DataIntegrityViolationException duplicate) {
            // Concurrent duplicate: the alert already exists, which is exactly the
            // deduplication guarantee we want.
        }
    }

    private AdminAlertDtos.AdminAlertResponse response(AdminAlert alert) {
        return new AdminAlertDtos.AdminAlertResponse(alert.getId(), alert.getType(), alert.getTitle(),
                alert.getBody(), alert.getResourceType(), alert.getResourceId(),
                alert.isAcknowledged(), alert.getCreatedAt());
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Page parameters are invalid.");
        }
        return PageRequest.of(page, size);
    }

    private static ApiException error(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }
}