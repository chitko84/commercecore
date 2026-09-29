package com.commercecore.inventory;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;

@RestController @RequestMapping("/api/v1/staff/inventory") @SecurityRequirement(name="bearerAuth")
public class StaffInventoryController {
    private final InventoryService inventory; public StaffInventoryController(InventoryService inventory){this.inventory=inventory;}
    @GetMapping List<InventoryDtos.InventoryResponse> list(){return inventory.list();}
    @GetMapping("/{id}") InventoryDtos.InventoryResponse get(@PathVariable UUID id){return inventory.get(id);}
    @PostMapping ResponseEntity<InventoryDtos.InventoryResponse> initialize(@Valid @RequestBody InventoryDtos.InitializeRequest request){InventoryDtos.InventoryResponse created=inventory.initialize(request);return ResponseEntity.created(URI.create("/api/v1/staff/inventory/"+created.id())).body(created);}
    @PostMapping("/{id}/restock") InventoryDtos.InventoryResponse restock(@PathVariable UUID id,@Valid @RequestBody InventoryDtos.RestockRequest request,@AuthenticationPrincipal Jwt jwt){return inventory.restock(id,request,actor(jwt));}
    @PostMapping("/{id}/adjust") InventoryDtos.InventoryResponse adjust(@PathVariable UUID id,@Valid @RequestBody InventoryDtos.AdjustmentRequest request,@AuthenticationPrincipal Jwt jwt){return inventory.adjust(id,request,actor(jwt));}
    @PostMapping("/{id}/reservations") ResponseEntity<InventoryDtos.ReservationResponse> reserve(@PathVariable UUID id,@Valid @RequestBody InventoryDtos.ReservationRequest request,@AuthenticationPrincipal Jwt jwt){InventoryDtos.ReservationResponse created=inventory.reserve(id,request,actor(jwt));return ResponseEntity.created(URI.create("/api/v1/staff/inventory/reservations/"+created.id())).body(created);}
    @PostMapping("/reservations/{id}/release") InventoryDtos.ReservationResponse release(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return inventory.release(id,actor(jwt));}
    @PostMapping("/reservations/{id}/confirm") InventoryDtos.ReservationResponse confirm(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return inventory.confirm(id,actor(jwt));}
    @PostMapping("/reservations/{id}/expire") InventoryDtos.ReservationResponse expire(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt){return inventory.expire(id,actor(jwt));}
    @GetMapping("/{id}/history") List<InventoryDtos.HistoryResponse> history(@PathVariable UUID id){return inventory.history(id);}
    private static UUID actor(Jwt jwt){return jwt==null?null:UUID.fromString(jwt.getSubject());}
}
