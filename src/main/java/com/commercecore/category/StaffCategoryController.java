package com.commercecore.category;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/categories")
@SecurityRequirement(name = "bearerAuth")
public class StaffCategoryController {
    private final CategoryService categories;
    public StaffCategoryController(CategoryService categories) { this.categories = categories; }

    @PostMapping
    ResponseEntity<CategoryDtos.CategoryResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody CategoryDtos.CategoryRequest request) {
        CategoryDtos.CategoryResponse created = categories.create(actor(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/staff/categories/" + created.id())).body(created);
    }
    @PutMapping("/{id}")
    CategoryDtos.CategoryResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @Valid @RequestBody CategoryDtos.CategoryRequest request) {
        return categories.update(actor(jwt), id, request);
    }
    @PostMapping("/{id}/activate")
    CategoryDtos.CategoryResponse activate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return categories.activate(actor(jwt), id);
    }
    @PostMapping("/{id}/deactivate")
    CategoryDtos.CategoryResponse deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return categories.deactivate(actor(jwt), id);
    }

    private static UUID actor(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getSubject());
    }
}
