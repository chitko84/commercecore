package com.commercecore.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final RegistrationService registration;
    private final AuthenticationService authentication;
    private final PasswordService passwords;

    public AuthController(RegistrationService registration, AuthenticationService authentication,
                          PasswordService passwords) {
        this.registration = registration; this.authentication = authentication; this.passwords = passwords;
    }

    @PostMapping("/register")
    ResponseEntity<AuthDtos.RegistrationResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registration.register(request));
    }

    @PostMapping("/verify-email")
    AuthDtos.MessageResponse verifyEmail(@Valid @RequestBody AuthDtos.TokenRequest request) {
        registration.verifyEmail(request.token());
        return new AuthDtos.MessageResponse("Email verified.");
    }

    @PostMapping("/resend-verification")
    ResponseEntity<AuthDtos.MessageResponse> resend(@Valid @RequestBody AuthDtos.EmailRequest request) {
        registration.resendVerification(request.email());
        return ResponseEntity.accepted().body(new AuthDtos.MessageResponse(
                "If the account is eligible, a verification email will be sent."));
    }

    @PostMapping("/login")
    AuthDtos.AuthTokensResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authentication.login(request);
    }

    @PostMapping("/refresh")
    AuthDtos.AuthTokensResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return authentication.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody AuthDtos.LogoutRequest request) {
        authentication.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    ResponseEntity<AuthDtos.MessageResponse> forgotPassword(@Valid @RequestBody AuthDtos.EmailRequest request) {
        passwords.forgotPassword(request.email());
        return ResponseEntity.accepted().body(new AuthDtos.MessageResponse(
                "If the account is eligible, a password reset email will be sent."));
    }

    @PostMapping("/reset-password")
    AuthDtos.MessageResponse resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        passwords.resetPassword(request);
        return new AuthDtos.MessageResponse("Password reset successfully.");
    }
}
