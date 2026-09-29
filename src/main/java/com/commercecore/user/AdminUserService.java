package com.commercecore.user;

import com.commercecore.audit.AuditService;
import com.commercecore.auth.RefreshTokenRepository;
import com.commercecore.common.exception.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

@Service
public class AdminUserService {

    private final UserAccountRepository users;
    private final AuditService audits;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public AdminUserService(UserAccountRepository users, AuditService audits,
                            RefreshTokenRepository refreshTokens, Clock clock) {
        this.users = users;
        this.audits = audits;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserDtos.PageResponse<UserDtos.AdminUserResponse> list(String email, UserRole role,
            AccountStatus status, int page, int size) {
        Specification<UserAccount> spec = (root, query, cb) -> cb.conjunction();
        if (email != null && !email.isBlank()) {
            String pattern = "%" + email.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("email")), pattern));
        }
        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("accountStatus"), status));
        }
        Page<UserAccount> result = users.findAll(spec, pageRequest(page, size)
                .withSort(Sort.by(Sort.Direction.DESC, "createdAt")));
        return new UserDtos.PageResponse<>(
                result.getContent().stream().map(UserDtos.AdminUserResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Transactional(readOnly = true)
    public UserDtos.AdminUserResponse get(UUID id) {
        return UserDtos.AdminUserResponse.from(find(id));
    }

    @Transactional
    public UserDtos.AdminUserResponse lock(UUID actorId, UUID id) {
        UserAccount user = find(id);
        if (user.getAccountStatus() == AccountStatus.LOCKED) {
            return UserDtos.AdminUserResponse.from(user);
        }
        requireNotSelf(actorId, id, "lock");
        requireOperational(user, "lock");
        AccountStatus from = user.getAccountStatus();
        user.changeStatus(AccountStatus.LOCKED);
        refreshTokens.revokeActiveForUser(id, clock.instant());
        audit(actorId, "USER_LOCKED", user, statusName(from), "LOCKED");
        return UserDtos.AdminUserResponse.from(user);
    }

    @Transactional
    public UserDtos.AdminUserResponse unlock(UUID actorId, UUID id) {
        UserAccount user = find(id);
        if (user.getAccountStatus() == AccountStatus.ACTIVE) {
            return UserDtos.AdminUserResponse.from(user);
        }
        if (user.getAccountStatus() != AccountStatus.LOCKED) {
            throw error(HttpStatus.CONFLICT, "ACCOUNT_NOT_LOCKED", "The account is not locked.");
        }
        user.changeStatus(AccountStatus.ACTIVE);
        user.resetFailedLogins();
        audit(actorId, "USER_UNLOCKED", user, "LOCKED", "ACTIVE");
        return UserDtos.AdminUserResponse.from(user);
    }

    @Transactional
    public UserDtos.AdminUserResponse activate(UUID actorId, UUID id) {
        UserAccount user = find(id);
        if (user.getAccountStatus() == AccountStatus.ACTIVE) {
            return UserDtos.AdminUserResponse.from(user);
        }
        AccountStatus from = user.getAccountStatus();
        user.changeStatus(AccountStatus.ACTIVE);
        audit(actorId, "USER_ACTIVATED", user, statusName(from), "ACTIVE");
        return UserDtos.AdminUserResponse.from(user);
    }

    @Transactional
    public UserDtos.AdminUserResponse deactivate(UUID actorId, UUID id) {
        UserAccount user = find(id);
        if (user.getAccountStatus() == AccountStatus.DEACTIVATED) {
            return UserDtos.AdminUserResponse.from(user);
        }
        AccountStatus from = user.getAccountStatus();
        requireNotSelf(actorId, id, "deactivate");
        user.changeStatus(AccountStatus.DEACTIVATED);
        refreshTokens.revokeActiveForUser(id, clock.instant());
        audit(actorId, "USER_DEACTIVATED", user, statusName(from), "DEACTIVATED");
        return UserDtos.AdminUserResponse.from(user);
    }

    @Transactional
    public UserDtos.AdminUserResponse changeRole(UUID actorId, UUID id, UserRole newRole) {
        UserAccount user = find(id);
        if (user.getRole() == newRole) {
            return UserDtos.AdminUserResponse.from(user);
        }
        if (id.equals(actorId) && newRole != UserRole.ADMIN) {
            throw error(HttpStatus.CONFLICT, "ADMIN_ROLE_CHANGE_NOT_ALLOWED",
                    "An administrator cannot remove their own ADMIN role.");
        }
        UserRole from = user.getRole();
        user.changeRole(newRole);
        refreshTokens.revokeActiveForUser(id, clock.instant());
        audit(actorId, "USER_ROLE_CHANGED", user, roleName(from), roleName(newRole));
        return UserDtos.AdminUserResponse.from(user);
    }

    private void requireNotSelf(UUID actorId, UUID id, String operation) {
        if (actorId != null && id.equals(actorId)) {
            throw error(HttpStatus.CONFLICT, "ADMIN_SELF_OPERATION_NOT_ALLOWED",
                    "An administrator cannot " + operation + " their own account.");
        }
    }

    private void requireOperational(UserAccount user, String operation) {
        if (user.getAccountStatus() == AccountStatus.DEACTIVATED) {
            throw error(HttpStatus.CONFLICT, "ACCOUNT_OPERATION_NOT_ALLOWED",
                    "The account is deactivated and cannot be " + operation + "ed.");
        }
    }

    private UserAccount find(UUID id) {
        return users.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,
                "USER_NOT_FOUND", "The user was not found."));
    }

    private void audit(UUID actorId, String action, UserAccount user, String from, String to) {
        audits.record(actorId, action, "ACCOUNT", "USER", user.getId().toString(),
                "email=" + user.getEmail() + "; from=" + from + "; to=" + to);
    }

    private static String statusName(AccountStatus status) {
        return status == null ? "null" : status.name();
    }

    private static String roleName(UserRole role) {
        return role == null ? "null" : role.name();
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
