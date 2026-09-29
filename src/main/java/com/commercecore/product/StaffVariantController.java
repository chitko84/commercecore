package com.commercecore.product;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;

@RestController @RequestMapping("/api/v1/staff") @SecurityRequirement(name="bearerAuth")
public class StaffVariantController {
    private final ProductVariantService variants;
    public StaffVariantController(ProductVariantService variants){this.variants=variants;}
    @PostMapping("/products/{productId}/variants") ResponseEntity<VariantDtos.VariantResponse> create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID productId,@Valid @RequestBody VariantDtos.VariantRequest request){VariantDtos.VariantResponse created=variants.create(actor(jwt),productId,request);return ResponseEntity.created(URI.create("/api/v1/staff/variants/"+created.id())).body(created);}
    @GetMapping("/products/{productId}/variants") List<VariantDtos.VariantResponse> list(@PathVariable UUID productId){return variants.staffList(productId);}
    @GetMapping("/variants/{id}") VariantDtos.VariantResponse get(@PathVariable UUID id){return variants.staffGet(id);}
    @PutMapping("/variants/{id}") VariantDtos.VariantResponse update(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody VariantDtos.VariantRequest request){return variants.update(actor(jwt),id,request);}
    @PostMapping("/variants/{id}/activate") VariantDtos.VariantResponse activate(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return variants.activate(actor(jwt),id);}
    @PostMapping("/variants/{id}/deactivate") VariantDtos.VariantResponse deactivate(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return variants.deactivate(actor(jwt),id);}
    private static UUID actor(Jwt jwt){return jwt==null?null:UUID.fromString(jwt.getSubject());}
}
