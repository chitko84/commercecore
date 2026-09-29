package com.commercecore.user;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/addresses")
@SecurityRequirement(name = "bearerAuth")
public class AddressController {
    private final AddressService addresses;
    public AddressController(AddressService addresses) { this.addresses = addresses; }

    @GetMapping
    List<UserDtos.AddressResponse> list(@AuthenticationPrincipal Jwt jwt) { return addresses.list(userId(jwt)); }

    @PostMapping
    ResponseEntity<UserDtos.AddressResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody UserDtos.AddressRequest request) {
        UserDtos.AddressResponse response = addresses.create(userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/me/addresses/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    UserDtos.AddressResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                    @Valid @RequestBody UserDtos.AddressRequest request) {
        return addresses.update(userId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        addresses.delete(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/default-shipping")
    UserDtos.AddressResponse defaultShipping(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return addresses.makeDefaultShipping(userId(jwt), id);
    }

    @PutMapping("/{id}/default-billing")
    UserDtos.AddressResponse defaultBilling(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return addresses.makeDefaultBilling(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
