package com.commercecore.inventory;

import com.commercecore.alert.AdminAlertService;
import com.commercecore.audit.AuditService;
import com.commercecore.common.exception.ApiException;
import com.commercecore.product.*;
import org.springframework.dao.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class InventoryService {
    private final InventoryRepository inventories; private final InventoryReservationRepository reservations;
    private final InventoryHistoryRepository history; private final ProductRepository products;
    private final ProductVariantRepository variants; private final AdminAlertService alerts;
    private final AuditService audits;
    public InventoryService(InventoryRepository inventories,InventoryReservationRepository reservations,
            InventoryHistoryRepository history,ProductRepository products,ProductVariantRepository variants,
            AdminAlertService alerts,AuditService audits){
        this.inventories=inventories;this.reservations=reservations;this.history=history;this.products=products;this.variants=variants;
        this.alerts=alerts;this.audits=audits;
    }

    @Transactional public InventoryDtos.InventoryResponse initialize(InventoryDtos.InitializeRequest request){
        if((request.productId()==null)==(request.variantId()==null))throw error(HttpStatus.BAD_REQUEST,"INVALID_INVENTORY_OWNER","Exactly one inventory owner is required.");
        Product product=null; ProductVariant variant=null;
        if(request.productId()!=null){
            product=products.findById(request.productId()).orElseThrow(InventoryService::productNotFound);
            if(!variants.findAllByProductIdOrderByCreatedAtAsc(product.getId()).isEmpty())throw error(HttpStatus.CONFLICT,"INVALID_INVENTORY_OWNER","Products with variants use variant inventory.");
            if(inventories.existsByProductId(product.getId()))throw alreadyExists();
        }else{
            variant=variants.findById(request.variantId()).orElseThrow(InventoryService::variantNotFound);
            if(inventories.existsByVariantId(variant.getId()))throw alreadyExists();
        }
        Inventory inventory=new Inventory(product,variant,request.initialQuantity(),request.lowStockThreshold());
        try{
            InventoryDtos.InventoryResponse created=response(inventories.saveAndFlush(inventory));
            alertOnLowCrossing(inventory,Long.MAX_VALUE);
            return created;
        }
        catch(IllegalArgumentException invalid){throw error(HttpStatus.BAD_REQUEST,"INVALID_STOCK_ADJUSTMENT","Inventory quantities are invalid.");}
        catch(DataIntegrityViolationException conflict){throw alreadyExists();}
    }
    @Transactional(readOnly=true) public List<InventoryDtos.InventoryResponse> list(){return inventories.findAll(Sort.by("createdAt").ascending()).stream().map(InventoryService::response).toList();}
    @Transactional(readOnly=true) public InventoryDtos.InventoryResponse get(UUID id){return response(required(id));}
    @Transactional public InventoryDtos.InventoryResponse restock(UUID id,InventoryDtos.RestockRequest request,UUID actor){
        Inventory i=locked(id); long beforeAvailable=i.availableQuantity();
        InventoryHistory.Snapshot before=InventoryHistory.snapshot(i);
        try{i.restock(request.quantity());}catch(RuntimeException invalid){throw invalidAdjustment();}
        record(i,MovementType.RESTOCK,request.quantity(),before,request.reference(),request.reason(),actor);
        audits.record(actor,"STOCK_RESTOCKED","INVENTORY","INVENTORY",i.getId().toString(),
                "sku="+ownerLabel(i)+"; quantity="+request.quantity()
                        +(request.reason()==null?"":"; reason="+request.reason()));
        alertOnLowCrossing(i,beforeAvailable);
        return response(i);
    }
    @Transactional public InventoryDtos.InventoryResponse adjust(UUID id,InventoryDtos.AdjustmentRequest request,UUID actor){
        Inventory i=locked(id); long beforeAvailable=i.availableQuantity();
        InventoryHistory.Snapshot before=InventoryHistory.snapshot(i);
        try{i.adjust(request.delta());}catch(RuntimeException invalid){throw invalidAdjustment();}
        MovementType type=request.delta()>0?MovementType.ADJUSTMENT_INCREASE:MovementType.ADJUSTMENT_DECREASE;
        record(i,type,request.delta(),before,request.reference(),request.reason(),actor);
        audits.record(actor,"STOCK_ADJUSTED","INVENTORY","INVENTORY",i.getId().toString(),
                "sku="+ownerLabel(i)+"; delta="+request.delta()+"; reason="+request.reason());
        alertOnLowCrossing(i,beforeAvailable);
        return response(i);
    }
    @Transactional public InventoryDtos.ReservationResponse reserve(UUID id,InventoryDtos.ReservationRequest request,UUID actor){
        Inventory i=locked(id); String reference=request.referenceKey().trim();
        Optional<InventoryReservation> existing=reservations.findByInventoryIdAndReferenceKey(id,reference);
        if(existing.isPresent()){
            InventoryReservation found=existing.get();
            if(found.getQuantity()==request.quantity()&&(found.getStatus()==ReservationStatus.ACTIVE||found.getStatus()==ReservationStatus.CONFIRMED))return reservationResponse(found);
            throw invalidReservationState();
        }
        if(request.expiresAt()!=null&&!request.expiresAt().isAfter(Instant.now()))throw invalidReservationState();
        InventoryHistory.Snapshot before=InventoryHistory.snapshot(i);
        long beforeAvailable=i.availableQuantity();
        try{i.reserve(request.quantity());}catch(IllegalArgumentException insufficient){throw error(HttpStatus.CONFLICT,"INSUFFICIENT_STOCK","Insufficient available stock.");}
        alertOnLowCrossing(i,beforeAvailable);
        InventoryReservation created=new InventoryReservation(i,request.quantity(),reference,request.expiresAt());
        try{reservations.saveAndFlush(created);}catch(DataIntegrityViolationException conflict){throw invalidReservationState();}
        record(i,MovementType.RESERVATION,request.quantity(),before,reference,null,actor);return reservationResponse(created);
    }
    @Transactional public InventoryDtos.ReservationResponse release(UUID reservationId,UUID actor){return transition(reservationId,ReservationStatus.RELEASED,actor);}
    @Transactional public InventoryDtos.ReservationResponse confirm(UUID reservationId,UUID actor){return transition(reservationId,ReservationStatus.CONFIRMED,actor);}
    @Transactional public InventoryDtos.InventoryResponse reverseSale(UUID inventoryId,long quantity,String reference,UUID actor){Inventory i=locked(inventoryId);if(history.existsByInventoryIdAndReferenceKeyAndMovementType(inventoryId,reference,MovementType.SALE_REVERSAL))return response(i);InventoryHistory.Snapshot before=InventoryHistory.snapshot(i);try{i.reverseSale(quantity);}catch(RuntimeException e){throw invalidAdjustment();}record(i,MovementType.SALE_REVERSAL,quantity,before,reference,"Paid-order cancellation",actor);audits.record(actor,"SALE_REVERSED","INVENTORY","INVENTORY",i.getId().toString(),"sku="+ownerLabel(i)+"; quantity="+quantity+"; reference="+reference);return response(i);}
    @Transactional public InventoryDtos.ReservationResponse expire(UUID reservationId,UUID actor){return transition(reservationId,ReservationStatus.EXPIRED,actor);}
    private InventoryDtos.ReservationResponse transition(UUID id,ReservationStatus target,UUID actor){
        InventoryReservation r=reservations.findLockedById(id).orElseThrow(InventoryService::reservationNotFound);
        if(r.getStatus()!=ReservationStatus.ACTIVE)throw invalidReservationState();
        Inventory i=locked(r.getInventory().getId());InventoryHistory.Snapshot before=InventoryHistory.snapshot(i);
        try{
            if(target==ReservationStatus.CONFIRMED){i.confirm(r.getQuantity());r.confirm();}
            else if(target==ReservationStatus.RELEASED){i.release(r.getQuantity());r.release();}
            else{i.release(r.getQuantity());r.expire();}
        }catch(RuntimeException invalid){throw invalidReservationState();}
        MovementType movement=target==ReservationStatus.CONFIRMED?MovementType.SALE:
                target==ReservationStatus.RELEASED?MovementType.RELEASE:MovementType.RESERVATION_EXPIRED;
        record(i,movement,r.getQuantity(),before,r.getReferenceKey(),null,actor);return reservationResponse(r);
    }
    @Transactional(readOnly=true) public List<InventoryDtos.HistoryResponse> history(UUID id){required(id);return history.findAllByInventoryIdOrderByOccurredAtAscIdAsc(id).stream().map(InventoryService::historyResponse).toList();}
    @Transactional(readOnly=true) public StockStatus productStatus(UUID productId){return inventories.findByProductId(productId).map(Inventory::stockStatus).orElse(StockStatus.UNKNOWN);}
    @Transactional(readOnly=true) public StockStatus variantStatus(UUID variantId){return inventories.findByVariantId(variantId).map(Inventory::stockStatus).orElse(StockStatus.UNKNOWN);}
    @Transactional(readOnly=true) public boolean hasProductInventory(UUID productId){return inventories.existsByProductId(productId);}
    private Inventory locked(UUID id){try{return inventories.findLockedById(id).orElseThrow(InventoryService::notFound);}catch(ConcurrencyFailureException conflict){throw error(HttpStatus.CONFLICT,"CONCURRENT_INVENTORY_UPDATE","Inventory is being updated concurrently.");}}
    private Inventory required(UUID id){return inventories.findById(id).orElseThrow(InventoryService::notFound);}
    private void record(Inventory i,MovementType type,long quantity,InventoryHistory.Snapshot before,String reference,String reason,UUID actor){history.save(new InventoryHistory(i,type,quantity,before,reference,reason,actor));}
    /**
     * Alerts only when a durable mutation crosses the stock level from above
     * the low-stock threshold to at or below it. Reads and mutations that do
     * not cross the threshold never produce an alert; the alert service
     * deduplicates per crossing.
     */
    private void alertOnLowCrossing(Inventory i,long beforeAvailable){
        long afterAvailable=i.availableQuantity();
        if(beforeAvailable>i.getLowStockThreshold()&&afterAvailable<=i.getLowStockThreshold()){
            alerts.recordLowStock(i.getId(),ownerLabel(i),afterAvailable,i.getLowStockThreshold());
        }
    }
    private static String ownerLabel(Inventory i){
        return i.getProduct()!=null?i.getProduct().getName()
                :i.getVariant()!=null?i.getVariant().getSku():"inventory";
    }
    static InventoryDtos.InventoryResponse response(Inventory i){return new InventoryDtos.InventoryResponse(i.getId(),i.getProduct()==null?null:i.getProduct().getId(),i.getVariant()==null?null:i.getVariant().getId(),i.getTotalQuantity(),i.getReservedQuantity(),i.getSoldQuantity(),i.availableQuantity(),i.getLowStockThreshold(),i.stockStatus(),i.getCreatedAt(),i.getUpdatedAt());}
    static InventoryDtos.ReservationResponse reservationResponse(InventoryReservation r){return new InventoryDtos.ReservationResponse(r.getId(),r.getInventory().getId(),r.getQuantity(),r.getStatus(),r.getReferenceKey(),r.getExpiresAt(),r.getCreatedAt(),r.getUpdatedAt());}
    static InventoryDtos.HistoryResponse historyResponse(InventoryHistory h){return new InventoryDtos.HistoryResponse(h.getId(),h.getMovementType(),h.getQuantity(),h.getBeforeTotal(),h.getBeforeReserved(),h.getBeforeSold(),h.getAfterTotal(),h.getAfterReserved(),h.getAfterSold(),h.getReferenceKey(),h.getReason(),h.getActorUserId(),h.getOccurredAt());}
    private static ApiException error(HttpStatus s,String c,String m){return new ApiException(s,c,m);} private static ApiException notFound(){return error(HttpStatus.NOT_FOUND,"INVENTORY_NOT_FOUND","Inventory was not found.");}
    private static ApiException productNotFound(){return error(HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND","The product was not found.");} private static ApiException variantNotFound(){return error(HttpStatus.NOT_FOUND,"VARIANT_NOT_FOUND","The variant was not found.");}
    private static ApiException reservationNotFound(){return error(HttpStatus.NOT_FOUND,"RESERVATION_NOT_FOUND","The reservation was not found.");} private static ApiException alreadyExists(){return error(HttpStatus.CONFLICT,"INVENTORY_ALREADY_EXISTS","Inventory already exists for this owner.");}
    private static ApiException invalidAdjustment(){return error(HttpStatus.BAD_REQUEST,"INVALID_STOCK_ADJUSTMENT","The stock adjustment would create an invalid inventory state.");} private static ApiException invalidReservationState(){return error(HttpStatus.CONFLICT,"INVALID_RESERVATION_STATE","The reservation cannot perform this transition.");}
}
