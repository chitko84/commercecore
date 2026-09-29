package com.commercecore.cart;
import com.commercecore.common.exception.ApiException;import com.commercecore.inventory.*;import com.commercecore.product.*;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import java.math.BigDecimal;import java.util.*;
@Service public class SellableItemResolver{
 private final ProductRepository products;private final ProductVariantRepository variants;private final InventoryRepository inventories;
 public SellableItemResolver(ProductRepository p,ProductVariantRepository v,InventoryRepository i){products=p;variants=v;inventories=i;}
 @Transactional(readOnly=true)public Selection resolve(UUID productId,UUID variantId,int quantity,boolean stockRequired){
  if(quantity<1||quantity>99)throw error(HttpStatus.BAD_REQUEST,"INVALID_CART_QUANTITY","Cart quantity must be between 1 and 99.");
  Product p=products.findById(productId).orElseThrow(SellableItemResolver::unavailable);if(!p.isActive()||!p.getCategory().isActive())throw unavailable();
  List<ProductVariant> productVariants=variants.findAllByProductIdOrderByCreatedAtAsc(productId);ProductVariant v=null;
  if(productVariants.isEmpty()){if(variantId!=null)throw invalidSelection();}
  else{if(variantId==null)throw invalidSelection();v=variants.findById(variantId).orElseThrow(SellableItemResolver::invalidSelection);if(!v.getProduct().getId().equals(productId)||!v.isActive())throw invalidSelection();}
  Optional<Inventory> inventory=v==null?inventories.findByProductId(productId):inventories.findByVariantId(v.getId());
  if(stockRequired){if(inventory.isEmpty())throw unavailable();if(inventory.get().availableQuantity()<quantity)throw error(HttpStatus.CONFLICT,"INSUFFICIENT_STOCK","Requested quantity is not currently available.");}
  return new Selection(p,v,inventory.orElse(null),v==null?p.effectivePrice():v.effectivePrice());
 }
 @Transactional(readOnly=true)public Inspection inspect(Product p,ProductVariant v,int quantity){
  if(!p.isActive()||!p.getCategory().isActive()||v!=null&&(!v.isActive()||!v.getProduct().getId().equals(p.getId())))return new Inspection(CartLineStatus.UNAVAILABLE,0);
  boolean hasVariants=!variants.findAllByProductIdOrderByCreatedAtAsc(p.getId()).isEmpty();if(hasVariants!=(v!=null))return new Inspection(CartLineStatus.UNAVAILABLE,0);
  Optional<Inventory> stock=v==null?inventories.findByProductId(p.getId()):inventories.findByVariantId(v.getId());if(stock.isEmpty())return new Inspection(CartLineStatus.UNAVAILABLE,0);
  long available=stock.get().availableQuantity();return new Inspection(available==0?CartLineStatus.OUT_OF_STOCK:available<quantity?CartLineStatus.INSUFFICIENT_STOCK:CartLineStatus.AVAILABLE,available);
 }
 private static ApiException invalidSelection(){return error(HttpStatus.BAD_REQUEST,"INVALID_SELLABLE_ITEM","Product and variant selection is invalid.");}private static ApiException unavailable(){return error(HttpStatus.CONFLICT,"CART_ITEM_UNAVAILABLE","The sellable item is unavailable.");}private static ApiException error(HttpStatus s,String c,String m){return new ApiException(s,c,m);}
 public record Selection(Product product,ProductVariant variant,Inventory inventory,BigDecimal unitPrice){}public record Inspection(CartLineStatus status,long availableQuantity){}
}
