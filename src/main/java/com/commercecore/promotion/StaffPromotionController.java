package com.commercecore.promotion;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;import jakarta.validation.Valid;import org.springframework.http.ResponseEntity;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.security.oauth2.jwt.Jwt;import org.springframework.web.bind.annotation.*;import java.net.URI;import java.util.*;
@RestController@RequestMapping("/api/v1/staff/promotions")@SecurityRequirement(name="bearerAuth")
public class StaffPromotionController{private final PromotionService promotions;public StaffPromotionController(PromotionService p){promotions=p;}
 @PostMapping ResponseEntity<PromotionDtos.PromotionResponse> create(@AuthenticationPrincipal Jwt jwt,@Valid@RequestBody PromotionDtos.PromotionRequest r){var created=promotions.create(actor(jwt),r);return ResponseEntity.created(URI.create("/api/v1/staff/promotions/"+created.id())).body(created);}
 @GetMapping List<PromotionDtos.PromotionResponse> list(){return promotions.list();}@GetMapping("/{id}")PromotionDtos.PromotionResponse get(@PathVariable UUID id){return promotions.get(id);}
 @PutMapping("/{id}")PromotionDtos.PromotionResponse update(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid@RequestBody PromotionDtos.PromotionRequest r){return promotions.update(actor(jwt),id,r);}
 @PostMapping("/{id}/activate")PromotionDtos.PromotionResponse activate(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return promotions.activate(actor(jwt),id);}@PostMapping("/{id}/deactivate")PromotionDtos.PromotionResponse deactivate(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return promotions.deactivate(actor(jwt),id);}
 private static UUID actor(Jwt jwt){return jwt==null?null:UUID.fromString(jwt.getSubject());}
}
