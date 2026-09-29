package com.commercecore.product;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/staff/products")
@SecurityRequirement(name = "bearerAuth")
public class StaffProductController {
    private final ProductService products;
    private final ProductMediaService media;
    public StaffProductController(ProductService products, ProductMediaService media) {
        this.products = products; this.media = media;
    }

    @GetMapping
    ProductDtos.PageResponse<ProductDtos.ProductSummary> search(
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) @Size(max = 140) String category,
            @RequestParam(required = false) @Size(max = 120) String brand,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal maxPrice,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "newest") String sort) {
        return products.staffSearch(q, category, brand, minPrice, maxPrice, active, page, size, sort);
    }

    @GetMapping("/{id}")
    ProductDtos.ProductResponse detail(@PathVariable UUID id) { return products.staffDetail(id); }

    @PostMapping
    ResponseEntity<ProductDtos.ProductResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody ProductDtos.ProductRequest request) {
        ProductDtos.ProductResponse created = products.create(actor(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/staff/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    ProductDtos.ProductResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                      @Valid @RequestBody ProductDtos.ProductRequest request) {
        return products.update(actor(jwt), id, request);
    }
    @PostMapping("/{id}/activate")
    ProductDtos.ProductResponse activate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return products.activate(actor(jwt), id);
    }
    @PostMapping("/{id}/deactivate")
    ProductDtos.ProductResponse deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return products.deactivate(actor(jwt), id);
    }

    private static UUID actor(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getSubject());
    }

    @PostMapping("/{id}/media")
    ResponseEntity<ProductDtos.MediaResponse> addMedia(@PathVariable UUID id,
                                                       @Valid @RequestBody ProductDtos.MediaRequest request) {
        ProductDtos.MediaResponse created = media.create(id, request);
        return ResponseEntity.created(URI.create("/api/v1/staff/products/" + id + "/media/" + created.id())).body(created);
    }
    @PutMapping("/{id}/media/{mediaId}")
    ProductDtos.MediaResponse updateMedia(@PathVariable UUID id, @PathVariable UUID mediaId,
                                          @Valid @RequestBody ProductDtos.MediaRequest request) {
        return media.update(id, mediaId, request);
    }
    @DeleteMapping("/{id}/media/{mediaId}")
    ResponseEntity<Void> deleteMedia(@PathVariable UUID id, @PathVariable UUID mediaId) {
        media.delete(id, mediaId); return ResponseEntity.noContent().build();
    }
}
