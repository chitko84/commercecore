package com.commercecore.inventory;

import com.commercecore.user.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Phase4InventoryIT {
    private static final String PASSWORD="Inventory-Test-42!";
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired TestRestTemplate rest; @Autowired UserAccountRepository users; @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc; @Autowired InventoryService inventoryService;

    @Test void flywayV1ThroughV6ValidatesAndProductWithoutVariantsRemainsValid(){
        assertThat(jdbc.queryForList("select version from flyway_schema_history where success order by installed_rank",String.class))
                .containsExactly("1","2","3","4","5","6","7","8","9","10","11","12","13");
        String staff=access("p4.persistence.staff@example.com",UserRole.STAFF);
        JsonNode product=createProduct(staff,"Variantless","variantless-p4","P4-BASE-1","100.00");
        JsonNode publicDetail=get("/api/v1/products/variantless-p4");
        assertThat(publicDetail.path("variants")).isEmpty(); assertThat(publicDetail.path("availability").asText()).isEqualTo("UNKNOWN");
        JsonNode stock=initialize(staff,uuid(product),null,1,1);
        assertThat(stock.path("stockStatus").asText()).isEqualTo("LOW_STOCK");
        assertThat(post(staff,"/api/v1/staff/inventory/"+uuid(stock)+"/reservations",reservation(1,"status-out")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(get("/api/v1/products/variantless-p4").path("availability").asText()).isEqualTo("OUT_OF_STOCK");
    }

    @Test void variantsNormalizePersistAttributesPricesVisibilityAndRbac(){
        String staff=access("p4.variant.staff@example.com",UserRole.STAFF), customer=access("p4.variant.customer@example.com",UserRole.CUSTOMER), admin=access("p4.variant.admin@example.com",UserRole.ADMIN);
        JsonNode product=createProduct(staff,"Variant Product","variant-product-p4","P4-PROD-1","100.00");
        assertThat(post(customer,"/api/v1/staff/products/"+uuid(product)+"/variants",variant("blocked-1",null,true,attrs("Color","Red"))).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode first=post(staff,"/api/v1/staff/products/"+uuid(product)+"/variants",variant("p4-black-256","80.00",true,List.of(attr("Color","Black",0),attr("Storage","256GB",1)))).getBody();
        assertThat(first.path("id").asText()).isNotBlank();assertThat(first.path("sku").asText()).isEqualTo("P4-BLACK-256");assertThat(first.path("effectivePrice").decimalValue()).isEqualByComparingTo("80.00");assertThat(first.path("attributes")).hasSize(2);
        JsonNode fallback=post(admin,"/api/v1/staff/products/"+uuid(product)+"/variants",variant("p4-blue",null,true,attrs("Color","Blue"))).getBody();
        assertThat(fallback.path("effectivePrice").decimalValue()).isEqualByComparingTo("100.00");
        ResponseEntity<JsonNode> duplicate=post(staff,"/api/v1/staff/products/"+uuid(product)+"/variants",variant("P4-BLACK-256",null,true,List.of()));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(duplicate.getBody().path("code").asText()).isEqualTo("DUPLICATE_VARIANT_SKU");
        assertThat(post(staff,"/api/v1/staff/products/"+uuid(product)+"/variants",variant("bad-price","0.00",true,List.of())).getBody().path("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(post(staff,"/api/v1/staff/products/"+uuid(product)+"/variants",variant("dup-attr",null,true,List.of(attr("Color","Black",0),attr("color","Blue",1)))).getBody().path("code").asText()).isEqualTo("DUPLICATE_VARIANT_ATTRIBUTE");
        JsonNode updated=put(staff,"/api/v1/staff/variants/"+uuid(first),variant("p4-black-512","90.00",true,attrs("Storage","512GB"))).getBody();
        assertThat(updated.path("attributes")).hasSize(1);assertThat(updated.path("sku").asText()).isEqualTo("P4-BLACK-512");
        post(staff,"/api/v1/staff/variants/"+uuid(first)+"/deactivate",null);
        JsonNode detail=get("/api/v1/products/variant-product-p4");assertThat(detail.path("variants")).hasSize(1);assertThat(detail.toString()).doesNotContain("P4-BLACK-512");
    }

    @Test void inventoryOperationsAreAccountedIdempotentAndRecorded(){
        String staff=access("p4.inventory.staff@example.com",UserRole.STAFF);JsonNode product=createProduct(staff,"Stock Product","stock-product-p4","P4-STOCK-1","20.00");
        JsonNode stock=initialize(staff,uuid(product),null,2,1);UUID id=uuid(stock);
        stock=post(staff,"/api/v1/staff/inventory/"+id+"/restock",Map.of("quantity",3,"reference","receipt-1","reason","received")).getBody();assertQuantities(stock,5,0,0,5);
        stock=post(staff,"/api/v1/staff/inventory/"+id+"/adjust",Map.of("delta",2,"reference","count-up","reason","physical count")).getBody();assertQuantities(stock,7,0,0,7);
        stock=post(staff,"/api/v1/staff/inventory/"+id+"/adjust",Map.of("delta",-1,"reference","damage","reason","damaged")).getBody();assertQuantities(stock,6,0,0,6);
        JsonNode reservation=post(staff,"/api/v1/staff/inventory/"+id+"/reservations",reservation(2,"cart-one")).getBody();
        JsonNode retry=post(staff,"/api/v1/staff/inventory/"+id+"/reservations",reservation(2,"cart-one")).getBody();assertThat(retry.path("id").asText()).isEqualTo(reservation.path("id").asText());assertQuantities(getAuth(staff,"/api/v1/staff/inventory/"+id),6,2,0,4);
        post(staff,"/api/v1/staff/inventory/reservations/"+uuid(reservation)+"/release",null);assertQuantities(getAuth(staff,"/api/v1/staff/inventory/"+id),6,0,0,6);
        JsonNode sale=post(staff,"/api/v1/staff/inventory/"+id+"/reservations",reservation(2,"sale-one")).getBody();post(staff,"/api/v1/staff/inventory/reservations/"+uuid(sale)+"/confirm",null);assertQuantities(getAuth(staff,"/api/v1/staff/inventory/"+id),6,0,2,4);
        ResponseEntity<JsonNode> repeat=post(staff,"/api/v1/staff/inventory/reservations/"+uuid(sale)+"/confirm",null);assertThat(repeat.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(repeat.getBody().path("code").asText()).isEqualTo("INVALID_RESERVATION_STATE");
        JsonNode movements=getAuth(staff,"/api/v1/staff/inventory/"+id+"/history");assertThat(movements).hasSize(7);assertThat(movements.get(0).path("movementType").asText()).isEqualTo("RESTOCK");assertThat(movements.get(6).path("movementType").asText()).isEqualTo("SALE");
    }

    @Test void ownershipConstraintsAdjustmentsAndReservationStatesRejectInvalidData(){
        String staff=access("p4.constraints.staff@example.com",UserRole.STAFF);JsonNode product=createProduct(staff,"Constraint Product","constraint-product-p4","P4-CONSTRAINT-1","30.00");
        JsonNode variant=post(staff,"/api/v1/staff/products/"+uuid(product)+"/variants",variant("p4-owner-v",null,true,List.of())).getBody();
        ResponseEntity<JsonNode> dual=post(staff,"/api/v1/staff/inventory",Map.of("productId",uuid(product),"variantId",uuid(variant),"initialQuantity",1,"lowStockThreshold",0));assertThat(dual.getBody().path("code").asText()).isEqualTo("INVALID_INVENTORY_OWNER");
        JsonNode stock=initialize(staff,null,uuid(variant),2,0);assertThat(post(staff,"/api/v1/staff/inventory",Map.of("variantId",uuid(variant),"initialQuantity",1,"lowStockThreshold",0)).getBody().path("code").asText()).isEqualTo("INVENTORY_ALREADY_EXISTS");
        assertThat(post(staff,"/api/v1/staff/inventory/"+uuid(stock)+"/adjust",Map.of("delta",-3,"reason","invalid")).getBody().path("code").asText()).isEqualTo("INVALID_STOCK_ADJUSTMENT");
        JsonNode reservation=post(staff,"/api/v1/staff/inventory/"+uuid(stock)+"/reservations",reservation(2,"all-stock")).getBody();
        assertThat(post(staff,"/api/v1/staff/inventory/"+uuid(stock)+"/reservations",reservation(1,"too-much")).getBody().path("code").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThatThrownBy(()->jdbc.update("update inventory set total_quantity=-1 where id=?",uuid(stock))).hasMessageContaining("ck_inventory_");
        post(staff,"/api/v1/staff/inventory/reservations/"+uuid(reservation)+"/release",null);
    }

    @Test void expiryLifecycleReleasesStockAndRejectsRepeatedTransitions(){
        String staff=access("p4.expiry.staff@example.com",UserRole.STAFF);JsonNode product=createProduct(staff,"Expiry Product","expiry-product-p4","P4-EXPIRY-1","40.00");JsonNode stock=initialize(staff,uuid(product),null,3,0);
        Map<String,Object> request=new LinkedHashMap<>(reservation(2,"expiry-one"));request.put("expiresAt","2099-01-01T00:00:00Z");JsonNode reservation=post(staff,"/api/v1/staff/inventory/"+uuid(stock)+"/reservations",request).getBody();
        JsonNode expired=post(staff,"/api/v1/staff/inventory/reservations/"+uuid(reservation)+"/expire",null).getBody();assertThat(expired.path("status").asText()).isEqualTo("EXPIRED");assertQuantities(getAuth(staff,"/api/v1/staff/inventory/"+uuid(stock)),3,0,0,3);
        assertThat(post(staff,"/api/v1/staff/inventory/reservations/"+uuid(reservation)+"/release",null).getBody().path("code").asText()).isEqualTo("INVALID_RESERVATION_STATE");
        JsonNode history=getAuth(staff,"/api/v1/staff/inventory/"+uuid(stock)+"/history");assertThat(history.get(1).path("movementType").asText()).isEqualTo("RESERVATION_EXPIRED");
    }

    @Test void pessimisticLockAllowsExactlyOneConcurrentReservation(){
        String staff=access("p4.concurrent.staff@example.com",UserRole.STAFF);JsonNode product=createProduct(staff,"Concurrent Product","concurrent-product-p4","P4-CONCURRENT-1","50.00");UUID inventoryId=uuid(initialize(staff,uuid(product),null,1,0));
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);ExecutorService executor=Executors.newFixedThreadPool(2);
        Callable<Boolean> attempt=()->{ready.countDown();start.await(10,TimeUnit.SECONDS);try{inventoryService.reserve(inventoryId,new InventoryDtos.ReservationRequest(1,"concurrent-"+Thread.currentThread().getId(),null),null);return true;}catch(Exception expected){return false;}};
        try{Future<Boolean> one=executor.submit(attempt),two=executor.submit(attempt);assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();assertThat(List.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);}catch(Exception failure){throw new AssertionError(failure);}finally{executor.shutdownNow();}
        InventoryDtos.InventoryResponse finalState=inventoryService.get(inventoryId);assertThat(finalState.availableQuantity()).isZero();assertThat(finalState.reservedQuantity()).isEqualTo(1);assertThat(finalState.totalQuantity()).isEqualTo(1);
    }

    private JsonNode createProduct(String token,String name,String slug,String sku,String price){JsonNode category=post(token,"/api/v1/staff/categories",Map.of("name",name+" Category","slug",slug+"-category","description","test","active",true)).getBody();return post(token,"/api/v1/staff/products",Map.of("name",name,"slug",slug,"description","test product","sku",sku,"brand","Phase4","basePrice",new BigDecimal(price),"categoryId",uuid(category),"active",true)).getBody();}
    private JsonNode initialize(String token,UUID productId,UUID variantId,long quantity,long threshold){Map<String,Object> body=new LinkedHashMap<>();if(productId!=null)body.put("productId",productId);if(variantId!=null)body.put("variantId",variantId);body.put("initialQuantity",quantity);body.put("lowStockThreshold",threshold);ResponseEntity<JsonNode> response=post(token,"/api/v1/staff/inventory",body);assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);return response.getBody();}
    private Map<String,Object> variant(String sku,String price,boolean active,List<Map<String,Object>> attributes){Map<String,Object> body=new LinkedHashMap<>();body.put("sku",sku);if(price!=null)body.put("priceOverride",new BigDecimal(price));body.put("active",active);body.put("attributes",attributes);return body;}
    private List<Map<String,Object>> attrs(String name,String value){return List.of(attr(name,value,0));} private Map<String,Object> attr(String name,String value,int order){return Map.of("name",name,"value",value,"displayOrder",order);}
    private Map<String,Object> reservation(long quantity,String reference){return Map.of("quantity",quantity,"referenceKey",reference);}
    private String access(String email,UserRole role){UserAccount user=new UserAccount(email,encoder.encode(PASSWORD),"Phase Four",null,role);user.verifyEmail();users.saveAndFlush(user);return rest.postForObject("/api/v1/auth/login",Map.of("email",email,"password",PASSWORD),JsonNode.class).path("accessToken").asText();}
    private ResponseEntity<JsonNode> post(String token,String path,Object body){return exchange(token,HttpMethod.POST,path,body);}private ResponseEntity<JsonNode> put(String token,String path,Object body){return exchange(token,HttpMethod.PUT,path,body);}
    private ResponseEntity<JsonNode> exchange(String token,HttpMethod method,String path,Object body){HttpHeaders headers=new HttpHeaders();headers.setBearerAuth(token);headers.setContentType(MediaType.APPLICATION_JSON);return rest.exchange(path,method,new HttpEntity<>(body,headers),JsonNode.class);}
    private JsonNode get(String path){return rest.getForObject(path,JsonNode.class);}private JsonNode getAuth(String token,String path){return exchange(token,HttpMethod.GET,path,null).getBody();}
    private UUID uuid(JsonNode node){return UUID.fromString(node.path("id").asText());}private void assertQuantities(JsonNode value,long total,long reserved,long sold,long available){assertThat(value.path("totalQuantity").asLong()).isEqualTo(total);assertThat(value.path("reservedQuantity").asLong()).isEqualTo(reserved);assertThat(value.path("soldQuantity").asLong()).isEqualTo(sold);assertThat(value.path("availableQuantity").asLong()).isEqualTo(available);}
}

