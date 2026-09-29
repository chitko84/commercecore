package com.commercecore.product;

import com.commercecore.audit.AuditService;
import com.commercecore.common.exception.ApiException;
import com.commercecore.inventory.InventoryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ProductVariantService {
    private final ProductVariantRepository variants; private final ProductRepository products; private final InventoryService inventory;
    private final AuditService audits;
    public ProductVariantService(ProductVariantRepository variants,ProductRepository products,InventoryService inventory,AuditService audits){this.variants=variants;this.products=products;this.inventory=inventory;this.audits=audits;}
    @Transactional public VariantDtos.VariantResponse create(UUID actorId,UUID productId,VariantDtos.VariantRequest request){
        Product product=products.findById(productId).orElseThrow(ProductVariantService::productNotFound);
        if(inventory.hasProductInventory(productId))throw new ApiException(HttpStatus.CONFLICT,"INVALID_INVENTORY_OWNER","Remove direct product inventory before adding variants.");
        String sku=normalizeSku(request.sku()); if(variants.existsBySku(sku)||products.existsBySku(sku))throw duplicateSku(); validatePrice(request.priceOverride());
        ProductVariant variant=new ProductVariant(product,sku,request.priceOverride(),request.active());replaceAttributes(variant,request.attributes());
        try{variants.saveAndFlush(variant);}catch(DataIntegrityViolationException conflict){throw catalogConflict();}
        audits.record(actorId,"VARIANT_CREATED","VARIANT","VARIANT",variant.getId().toString(),"sku="+sku+"; product="+product.getSlug());
        return response(variant);
    }
    @Transactional public VariantDtos.VariantResponse update(UUID actorId,UUID id,VariantDtos.VariantRequest request){
        ProductVariant variant=required(id);String sku=normalizeSku(request.sku());
        if(variants.existsBySkuAndIdNot(sku,id)||products.existsBySku(sku))throw duplicateSku();validatePrice(request.priceOverride());
        validateAttributes(request.attributes());
        try{variant.update(sku,request.priceOverride(),request.active());variant.replaceAttributes(List.of());variants.flush();replaceAttributes(variant,request.attributes());variants.flush();}
        catch(DataIntegrityViolationException conflict){throw catalogConflict();}
        audits.record(actorId,"VARIANT_UPDATED","VARIANT","VARIANT",variant.getId().toString(),"sku="+sku+"; active="+variant.isActive());
        return response(variant);
    }
    @Transactional public VariantDtos.VariantResponse activate(UUID actorId,UUID id){ProductVariant v=required(id);if(!v.getProduct().isActive())throw new ApiException(HttpStatus.CONFLICT,"PRODUCT_INACTIVE","A variant cannot be activated for an inactive product.");v.activate();audits.record(actorId,"VARIANT_ACTIVATED","VARIANT","VARIANT",v.getId().toString(),"sku="+v.getSku());return response(v);}
    @Transactional public VariantDtos.VariantResponse deactivate(UUID actorId,UUID id){ProductVariant v=required(id);v.deactivate();audits.record(actorId,"VARIANT_DEACTIVATED","VARIANT","VARIANT",v.getId().toString(),"sku="+v.getSku());return response(v);}
    @Transactional(readOnly=true) public List<VariantDtos.VariantResponse> staffList(UUID productId){if(!products.existsById(productId))throw productNotFound();return variants.findAllByProductIdOrderByCreatedAtAsc(productId).stream().map(this::response).toList();}
    @Transactional(readOnly=true) public VariantDtos.VariantResponse staffGet(UUID id){return response(required(id));}
    @Transactional(readOnly=true) public List<VariantDtos.VariantResponse> publicList(Product product){if(!product.isActive())return List.of();return variants.findAllByProductIdAndActiveTrueOrderByCreatedAtAsc(product.getId()).stream().map(this::response).toList();}
    @Transactional(readOnly=true) public ProductVariant required(UUID id){return variants.findById(id).orElseThrow(ProductVariantService::notFound);}
    @Transactional(readOnly=true) public boolean skuExists(String sku){return variants.existsBySku(sku);}
    private void replaceAttributes(ProductVariant v,List<VariantDtos.AttributeRequest> attrs){
        try{v.replaceAttributes(attrs.stream().map(a->new ProductVariant.AttributeValue(a.name(),a.value(),a.displayOrder())).toList());}
        catch(IllegalArgumentException invalid){String code=invalid.getMessage()!=null&&invalid.getMessage().contains("duplicate")?"DUPLICATE_VARIANT_ATTRIBUTE":"INVALID_VARIANT_ATTRIBUTE";throw new ApiException(HttpStatus.BAD_REQUEST,code,"Variant attributes are invalid or duplicated.");}
    }
    private void validateAttributes(List<VariantDtos.AttributeRequest> attrs){
        Set<String> names=new HashSet<>();
        for(VariantDtos.AttributeRequest attribute:attrs)
            if(!names.add(attribute.name().trim().toLowerCase(Locale.ROOT)))
                throw new ApiException(HttpStatus.BAD_REQUEST,"DUPLICATE_VARIANT_ATTRIBUTE","Variant attributes are invalid or duplicated.");
    }
    private VariantDtos.VariantResponse response(ProductVariant v){return new VariantDtos.VariantResponse(v.getId(),v.getProduct().getId(),v.getSku(),v.getPriceOverride(),v.effectivePrice(),v.isActive(),v.getAttributes().stream().map(a->new VariantDtos.AttributeResponse(a.getName(),a.getValue(),a.getDisplayOrder())).toList(),inventory.variantStatus(v.getId()),v.getCreatedAt(),v.getUpdatedAt());}
    private static void validatePrice(java.math.BigDecimal p){if(p!=null&&p.signum()<=0)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_VARIANT_PRICE","The variant price override is invalid.");}
    private static String normalizeSku(String value){try{return Product.normalizeSku(value);}catch(IllegalArgumentException e){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_VARIANT_SKU","The variant SKU is invalid.");}}
    private static ApiException duplicateSku(){return new ApiException(HttpStatus.CONFLICT,"DUPLICATE_VARIANT_SKU","The variant SKU is already in use.");}
    private static ApiException catalogConflict(){return new ApiException(HttpStatus.CONFLICT,"VARIANT_CONFLICT","The variant conflicts with existing data.");}
    private static ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"VARIANT_NOT_FOUND","The variant was not found.");}
    private static ApiException productNotFound(){return new ApiException(HttpStatus.NOT_FOUND,"PRODUCT_NOT_FOUND","The product was not found.");}
}
