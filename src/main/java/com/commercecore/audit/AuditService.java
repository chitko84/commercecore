package com.commercecore.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    /**
     * Records an append-only audit entry for an administrative or sensitive
     * action. Runs in the caller's transaction when one is active so the
     * audit entry commits or rolls back with the action itself.
     */
    @Transactional
    public void record(UUID actorUserId, String action, String category,
                       String resourceType, String resourceId, String details) {
        logs.save(new AuditLog(actorUserId, action, category, resourceType, resourceId, details));
    }

    @Transactional(readOnly = true)
    public AuditDtos.PageResponse<AuditDtos.AuditLogResponse> search(UUID actorId, String action,
            String category, String resourceType, String resourceId,
            Instant from, Instant to, int page, int size) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();
        if (actorId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorUserId"), actorId));
        }
        if (action != null && !action.isBlank()) {
            String pattern = "%" + action.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("action")), pattern));
        }
        if (category != null && !category.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase(Locale.ROOT)));
        }
        if (resourceType != null && !resourceType.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(cb.lower(root.get("resourceType")), resourceType.trim().toLowerCase(Locale.ROOT)));
        }
        if (resourceId != null && !resourceId.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(cb.lower(root.get("resourceId")), resourceId.trim().toLowerCase(Locale.ROOT)));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        Page<AuditLog> result = logs.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new AuditDtos.PageResponse<>(
                result.getContent().stream().map(this::response).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    private AuditDtos.AuditLogResponse response(AuditLog log) {
        return new AuditDtos.AuditLogResponse(log.getId(), log.getCreatedAt(), log.getActorUserId(),
                log.getAction(), log.getCategory(), log.getResourceType(), log.getResourceId(),
                log.getDetails());
    }
}
