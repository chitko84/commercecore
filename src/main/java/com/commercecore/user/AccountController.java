package com.commercecore.user;

import com.commercecore.auth.AuthDtos;
import com.commercecore.auth.PasswordService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {
    private final AccountService accounts;
    private final PasswordService passwords;
    public AccountController(AccountService accounts, PasswordService passwords) {
        this.accounts = accounts; this.passwords = passwords;
    }

    @GetMapping
    UserDtos.ProfileResponse get(@AuthenticationPrincipal Jwt jwt) { return accounts.get(userId(jwt)); }

    @PatchMapping
    UserDtos.ProfileResponse update(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        return accounts.update(userId(jwt), request);
    }

    @PutMapping("/password")
    AuthDtos.MessageResponse changePassword(@AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody UserDtos.ChangePasswordRequest request) {
        passwords.changePassword(userId(jwt), request);
        return new AuthDtos.MessageResponse("Password changed successfully.");
    }

    private static UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
