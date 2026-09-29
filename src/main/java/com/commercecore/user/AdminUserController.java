package com.commercecore.user;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/admin/users")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    private final AdminUserService users;

    public AdminUserController(AdminUserService users) {
        this.users = users;
    }

    @GetMapping
    UserDtos.PageResponse<UserDtos.AdminUserResponse> list(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return users.list(email, role, status, page, size);
    }

    @GetMapping("/{id}")
    UserDtos.AdminUserResponse get(@PathVariable UUID id) {
        return users.get(id);
    }

    @PostMapping("/{id}/lock")
    UserDtos.AdminUserResponse lock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return users.lock(actor(jwt), id);
    }

    @PostMapping("/{id}/unlock")
    UserDtos.AdminUserResponse unlock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return users.unlock(actor(jwt), id);
    }

    @PostMapping("/{id}/activate")
    UserDtos.AdminUserResponse activate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return users.activate(actor(jwt), id);
    }

    @PostMapping("/{id}/deactivate")
    UserDtos.AdminUserResponse deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return users.deactivate(actor(jwt), id);
    }

    @PutMapping("/{id}/role")
    UserDtos.AdminUserResponse changeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @Valid @RequestBody UserDtos.ChangeRoleRequest request) {
        return users.changeRole(actor(jwt), id, request.role());
    }

    private static UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
