package com.commercecore.payment;

import com.commercecore.user.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Phase7PaymentIT {
    private static final String PASSWORD="Payment-Test-42!";
    @Container @ServiceConnection static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired TestRestTemplate rest;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired SandboxPaymentProvider provider;
    @Autowired PaymentService paymentService;

    @BeforeEach void resetProvider(){provider.resetDiagnostics();paymentService.resetReliabilityState();}

    @Test void flywayV11AndPaymentSchemaValidate(){
        assertThat(jdbc.queryForObject("select version from flyway_schema_history where success order by installed_rank desc limit 1",String.class)).isEqualTo("13");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name in ('payments','payment_refunds','payment_events')",Integer.class)).isEqualTo(3);
    }

    @Test void allMethodsAuthorizeCaptureConfirmInventoryAndPromotionExactlyOnce(){
        String staff=access(UserRole.STAFF),customer=access(UserRole.CUSTOMER);
        for(PaymentMethod method:PaymentMethod.values()){
            Pending pending=pending(staff,customer,"method-"+method.name(),20,2,true);
            JsonNode paid=pay(customer,pending.orderId(),"pay-method-"+method.name(),method,null).getBody();
            assertThat(paid.path("status").asText()).isEqualTo("PAID");
            assertThat(order(customer,pending.orderId()).path("status").asText()).isEqualTo("CONFIRMED");
            assertQuantities(pending.inventoryId(),2,0,1);
            assertThat(jdbc.queryForObject("select count(*) from promotion_redemptions where reference_key=?",Long.class,"ORDER:"+pending.orderId())).isEqualTo(1);
            JsonNode retry=pay(customer,pending.orderId(),"pay-method-"+method.name(),method,null).getBody();
            assertThat(uuid(retry)).isEqualTo(uuid(paid));
            assertThat(jdbc.queryForObject("select count(*) from promotion_redemptions where reference_key=?",Long.class,"ORDER:"+pending.orderId())).isEqualTo(1);
        }
    }

    @Test void ownershipOrderStateAmountIdempotencyAndSecurityAreEnforced(){
        String staff=access(UserRole.STAFF),admin=access(UserRole.ADMIN),customer=access(UserRole.CUSTOMER),other=access(UserRole.CUSTOMER);
        Pending pending=pending(staff,customer,"guards",17,2,false);
        assertThat(pay(other,pending.orderId(),"cross-customer-01",PaymentMethod.CARD,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<JsonNode> first=pay(customer,pending.orderId(),"same-payment-key",PaymentMethod.CARD,null);
        UUID paymentId=uuid(first.getBody());
        assertThat(first.getBody().path("amount").decimalValue()).isEqualByComparingTo(order(customer,pending.orderId()).path("grandTotal").decimalValue());
        assertThat(uuid(pay(customer,pending.orderId(),"same-payment-key",PaymentMethod.CARD,null).getBody())).isEqualTo(paymentId);
        assertThat(pay(customer,pending.orderId(),"same-payment-key",PaymentMethod.E_WALLET,null).getBody().path("code").asText()).isEqualTo("PAYMENT_IDEMPOTENCY_CONFLICT");
        assertThat(pay(customer,pending.orderId(),"different-charge-key",PaymentMethod.CARD,null).getBody().path("code").asText()).isEqualTo("PAYMENT_NOT_ALLOWED");
        assertThat(get(customer,"/api/v1/orders/"+pending.orderId()+"/payments/"+paymentId).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(other,"/api/v1/orders/"+pending.orderId()+"/payments/"+paymentId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post(customer,"/api/v1/staff/payments/"+paymentId+"/refunds",Map.of("amount",1),"customer-refund").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(customer,"/api/v1/staff/payments/"+paymentId+"/reconcile",null,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(customer,"/api/v1/staff/orders/"+pending.orderId()+"/cancel-paid",null,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(staff,"/api/v1/staff/payments/"+paymentId+"/refunds").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(admin,"/api/v1/staff/payments/"+paymentId+"/refunds").getStatusCode()).isEqualTo(HttpStatus.OK);

        Pending cancelled=pending(staff,customer,"cancelled",10,1,false);
        post(customer,"/api/v1/orders/"+cancelled.orderId()+"/cancel",null,null);
        assertThat(pay(customer,cancelled.orderId(),"cancelled-payment",PaymentMethod.CARD,null).getBody().path("code").asText()).isEqualTo("PAYMENT_NOT_ALLOWED");
        Pending expired=pending(staff,customer,"expired",10,1,false);
        jdbc.update("update orders set reservation_expires_at=? where id=?",Timestamp.from(Instant.now().minusSeconds(1)),expired.orderId());
        int calls=provider.totalInvocations("AUTHORIZE");
        assertThat(pay(customer,expired.orderId(),"expired-payment",PaymentMethod.CARD,null).getBody().path("code").asText()).isEqualTo("RESERVATION_EXPIRED");
        assertThat(provider.totalInvocations("AUTHORIZE")).isEqualTo(calls);
    }

    @Test void declineDoesNotRetryOrMutateOrderInventoryOrPromotion(){
        String staff=access(UserRole.STAFF),customer=access(UserRole.CUSTOMER);Pending pending=pending(staff,customer,"decline",30,2,true);
        JsonNode payment=pay(customer,pending.orderId(),"decline-payment",PaymentMethod.CARD,"decline").getBody();
        String providerKey=providerKey(uuid(payment));
        assertThat(payment.path("status").asText()).isEqualTo("FAILED");
        assertThat(provider.invocationCount("AUTHORIZE",providerKey)).isEqualTo(1);
        assertThat(order(customer,pending.orderId()).path("status").asText()).isEqualTo("PENDING");
        assertQuantities(pending.inventoryId(),2,1,0);
        assertThat(jdbc.queryForObject("select count(*) from promotion_redemptions where reference_key=?",Long.class,"ORDER:"+pending.orderId())).isZero();
    }

    @Test void transientFailuresUseBoundedRetriesAndStableProviderIdentity(){
        String staff=access(UserRole.STAFF),customer=access(UserRole.CUSTOMER);Pending recovered=pending(staff,customer,"retry-ok",15,1,false);
        JsonNode paid=pay(customer,recovered.orderId(),"retry-payment-ok",PaymentMethod.ONLINE_BANKING,"transient-2").getBody();
        String key=providerKey(uuid(paid));
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        assertThat(provider.invocationCount("AUTHORIZE",key)).isEqualTo(3);
        assertThat(provider.invocationCount("EFFECTIVE_CHARGE",key)).isEqualTo(1);
        Pending exhausted=pending(staff,customer,"retry-stop",15,1,false);
        JsonNode failed=pay(customer,exhausted.orderId(),"retry-payment-stop",PaymentMethod.E_WALLET,"transient-always").getBody();
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");
        assertThat(provider.invocationCount("AUTHORIZE",providerKey(uuid(failed)))).isEqualTo(3);
    }

    @Test void timeoutIsUncertainAndReconciliationConvergesExactlyOnce(){
        String staff=access(UserRole.STAFF),customer=access(UserRole.CUSTOMER);Pending pending=pending(staff,customer,"timeout",22,2,true);
        JsonNode uncertain=pay(customer,pending.orderId(),"timeout-payment",PaymentMethod.CARD,"slow-timeout").getBody();
        UUID paymentId=uuid(uncertain);String key=providerKey(paymentId);
        assertThat(uncertain.path("status").asText()).isEqualTo("FAILED");assertThat(uncertain.path("reconciliationRequired").asBoolean()).isTrue();
        assertThat(provider.invocationCount("AUTHORIZE",key)).isEqualTo(1);assertThat(provider.invocationCount("EFFECTIVE_CHARGE",key)).isEqualTo(1);
        JsonNode reconciled=post(staff,"/api/v1/staff/payments/"+paymentId+"/reconcile",null,null).getBody();
        assertThat(reconciled.path("status").asText()).isEqualTo("PAID");assertThat(order(customer,pending.orderId()).path("status").asText()).isEqualTo("CONFIRMED");
        post(staff,"/api/v1/staff/payments/"+paymentId+"/reconcile",null,null);
        assertQuantities(pending.inventoryId(),2,0,1);
        assertThat(jdbc.queryForObject("select count(*) from promotion_redemptions where reference_key=?",Long.class,"ORDER:"+pending.orderId())).isEqualTo(1);
        assertThat(provider.invocationCount("LOOKUP",key)).isEqualTo(1);
    }

    @Test void circuitBreakerOpensBlocksProviderAndRecoversThroughHalfOpenProbe() throws Exception {
        String staff=access(UserRole.STAFF),customer=access(UserRole.CUSTOMER);
        for(int i=0;i<2;i++){
            Pending failure=pending(staff,customer,"circuit-fail-"+i,9,1,false);
            assertThat(pay(customer,failure.orderId(),"circuit-failure-"+i,PaymentMethod.CARD,"transient-always").getBody().path("status").asText()).isEqualTo("FAILED");
        }
        Pending blocked=pending(staff,customer,"circuit-blocked",9,1,false);int before=provider.totalInvocations("AUTHORIZE");
        ResponseEntity<JsonNode> unavailable=pay(customer,blocked.orderId(),"circuit-blocked-key",PaymentMethod.CARD,null);
        assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(unavailable.getBody().path("code").asText()).isEqualTo("PAYMENT_PROVIDER_UNAVAILABLE");
        assertThat(provider.totalInvocations("AUTHORIZE")).isEqualTo(before);
        Thread.sleep(2_100);
        Pending probe=pending(staff,customer,"circuit-probe",9,1,false);
        assertThat(pay(customer,probe.orderId(),"circuit-probe-key",PaymentMethod.CARD,null).getBody().path("status").asText()).isEqualTo("PAID");
        Pending closed=pending(staff,customer,"circuit-closed",9,1,false);
        assertThat(pay(customer,closed.orderId(),"circuit-closed-key",PaymentMethod.CARD,null).getBody().path("status").asText()).isEqualTo("PAID");
    }

    @Test void refundsArePartialFullIdempotentTruthfulAndAudited(){
        String staff=access(UserRole.STAFF),customer=access(UserRole.CUSTOMER);Pending pending=pending(staff,customer,"refund",50,1,false);
        UUID paymentId=uuid(pay(customer,pending.orderId(),"refund-payment",PaymentMethod.CARD,null).getBody());
        JsonNode partial=refund(staff,paymentId,"partial-refund-key","20.00").getBody();
        assertThat(partial.path("status").asText()).isEqualTo("COMPLETED");assertPayment(paymentId,"PARTIALLY_REFUNDED");
        assertThat(uuid(refund(staff,paymentId,"partial-refund-key","20.00").getBody())).isEqualTo(uuid(partial));
        assertThat(refund(staff,paymentId,"partial-refund-key","21.00").getBody().path("code").asText()).isEqualTo("REFUND_IDEMPOTENCY_CONFLICT");
        JsonNode full=refund(staff,paymentId,"remaining-refund",null).getBody();
        assertThat(full.path("status").asText()).isEqualTo("COMPLETED");assertPayment(paymentId,"REFUNDED");
        assertThat(jdbc.queryForObject("select count(*) from payment_refunds where payment_id=?",Long.class,paymentId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from payment_events where payment_id=? and event_type='REFUNDED' and from_status='PARTIALLY_REFUNDED' and to_status='REFUNDED'",Long.class,paymentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from payment_events where payment_id=? and from_status=to_status and event_type in ('PARTIALLY_REFUNDED','REFUNDED')",Long.class,paymentId)).isZero();
    }

    @Test void refundReconciliationHandlesCompletedFailedUncertainAndRepeatedCalls(){
        String staff=access(UserRole.STAFF),customer=access(UserRole.CUSTOMER);
        UUID paid=paid(staff,customer,"refund-reconcile-paid",40);
        provider.scriptRefund(new PaymentProvider.Result(false,true,true,"UNCERTAIN-REF", "STILL_UNCERTAIN"));
        JsonNode pending=refund(staff,paid,"uncertain-refund","10.00").getBody();UUID refundId=uuid(pending);
        assertThat(pending.path("reconciliationRequired").asBoolean()).isTrue();
        provider.scriptLookup(new PaymentProvider.Result(true,false,false,"SETTLED-REF",null));
        assertThat(post(staff,"/api/v1/staff/refunds/"+refundId+"/reconcile",null,null).getBody().path("status").asText()).isEqualTo("COMPLETED");
        int lookups=provider.totalInvocations("LOOKUP");
        post(staff,"/api/v1/staff/refunds/"+refundId+"/reconcile",null,null);
        assertThat(provider.totalInvocations("LOOKUP")).isEqualTo(lookups);assertPayment(paid,"PARTIALLY_REFUNDED");

        UUID failedPayment=paid(staff,customer,"refund-reconcile-failed",40);
        provider.scriptRefund(new PaymentProvider.Result(false,true,true,"UNCERTAIN-FAIL","STILL_UNCERTAIN"));
        UUID failedRefund=uuid(refund(staff,failedPayment,"failed-refund","10.00").getBody());
        provider.scriptLookup(new PaymentProvider.Result(false,false,false,null,"REFUND_NOT_COMPLETED"));
        JsonNode failed=post(staff,"/api/v1/staff/refunds/"+failedRefund+"/reconcile",null,null).getBody();
        assertThat(failed.path("status").asText()).isEqualTo("FAILED");assertPayment(failedPayment,"PAID");

        UUID uncertainPayment=paid(staff,customer,"refund-reconcile-still",40);
        provider.scriptRefund(new PaymentProvider.Result(false,true,true,"UNCERTAIN-STILL","STILL_UNCERTAIN"));
        UUID uncertainRefund=uuid(refund(staff,uncertainPayment,"still-refund","10.00").getBody());
        provider.scriptLookup(new PaymentProvider.Result(false,true,true,"UNCERTAIN-STILL","STILL_UNCERTAIN"));
        JsonNode still=post(staff,"/api/v1/staff/refunds/"+uncertainRefund+"/reconcile",null,null).getBody();
        assertThat(still.path("reconciliationRequired").asBoolean()).isTrue();
    }

    @Test void concurrentRefundsUseRealPostgresLockAndNeverOverRefund() throws Exception {
        String staff=access(UserRole.STAFF),customer=access(UserRole.CUSTOMER);Pending pending=pending(staff,customer,"concurrent-refund",10,1,false);
        jdbc.update("update orders set subtotal=100.00, merchandise_discount=0.00, discounted_merchandise_total=100.00, " +
                "shipping_fee_before_discount=0.00, shipping_discount=0.00, shipping_fee=0.00, tax_amount=0.00, grand_total=100.00 where id=?",
                pending.orderId());
        UUID paymentId=uuid(pay(customer,pending.orderId(),"concurrent-payment",PaymentMethod.CARD,null).getBody());
        CountDownLatch ready=new CountDownLatch(2),start=new CountDownLatch(1);CyclicBarrier overlap=new CyclicBarrier(2);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        Callable<ResponseEntity<JsonNode>> one=()->{ready.countDown();start.await();overlap.await();return refund(staff,paymentId,"concurrent-refund-one","80.00");};
        Callable<ResponseEntity<JsonNode>> two=()->{ready.countDown();start.await();overlap.await();return refund(staff,paymentId,"concurrent-refund-two","80.00");};
        Future<ResponseEntity<JsonNode>> first=pool.submit(one),second=pool.submit(two);ready.await();start.countDown();
        List<ResponseEntity<JsonNode>> outcomes=List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));pool.shutdown();
        assertThat(outcomes.stream().filter(r->r.getStatusCode().is2xxSuccessful()).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(r->r.getStatusCode()==HttpStatus.CONFLICT).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select coalesce(sum(amount),0) from payment_refunds where payment_id=? and status='COMPLETED'",BigDecimal.class,paymentId)).isEqualByComparingTo("80.00");
        assertPayment(paymentId,"PARTIALLY_REFUNDED");
        assertThat(refund(staff,paymentId,"concurrent-final-20","20.00").getBody().path("status").asText()).isEqualTo("COMPLETED");
        assertPayment(paymentId,"REFUNDED");
        assertThat(jdbc.queryForObject("select coalesce(sum(amount),0) from payment_refunds where payment_id=? and status='COMPLETED'",BigDecimal.class,paymentId)).isEqualByComparingTo("100.00");
    }

    @Test void durableCompensationPersistsSuccessFailureAndUncertainty(){
        compensationScenario("success",new PaymentProvider.Result(true,false,false,"COMP-OK",null),"COMPLETED","REFUNDED",false);
        compensationScenario("failure",new PaymentProvider.Result(false,false,false,null,"REFUND_DECLINED"),"FAILED","PAID",false);
        compensationScenario("uncertain",new PaymentProvider.Result(false,true,true,"COMP-MAYBE","STILL_UNCERTAIN"),"PENDING","PAID",true);
    }

    @Test void paidCancellationRefundsThenReversesSaleAndIsIdempotent(){
        String staff=access(UserRole.STAFF),customer=access(UserRole.CUSTOMER);Pending pending=pending(staff,customer,"paid-cancel",35,3,false);
        UUID paymentId=uuid(pay(customer,pending.orderId(),"paid-cancel-payment",PaymentMethod.CARD,null).getBody());
        ResponseEntity<JsonNode> cancelled=post(staff,"/api/v1/staff/orders/"+pending.orderId()+"/cancel-paid",null,null);
        assertThat(cancelled.getBody().path("status").asText()).isEqualTo("CANCELLED");assertPayment(paymentId,"REFUNDED");assertQuantities(pending.inventoryId(),3,0,0);
        long refunds=count("payment_refunds","payment_id",paymentId),reversals=jdbc.queryForObject("select count(*) from inventory_history where inventory_id=? and movement_type='SALE_REVERSAL'",Long.class,pending.inventoryId());
        assertThat(reversals).isEqualTo(1);
        assertThat(post(staff,"/api/v1/staff/orders/"+pending.orderId()+"/cancel-paid",null,null).getBody().path("status").asText()).isEqualTo("CANCELLED");
        assertThat(count("payment_refunds","payment_id",paymentId)).isEqualTo(refunds);
        assertThat(jdbc.queryForObject("select count(*) from inventory_history where inventory_id=? and movement_type='SALE_REVERSAL'",Long.class,pending.inventoryId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from order_status_history where order_id=? and reason='PAID_ORDER_CANCELLED'",Long.class,pending.orderId())).isEqualTo(1);

        Pending processing=pending(staff,customer,"processing-cancel",25,2,false);pay(customer,processing.orderId(),"processing-pay",PaymentMethod.CARD,null);
        post(staff,"/api/v1/staff/orders/"+processing.orderId()+"/processing",null,null);
        assertThat(post(staff,"/api/v1/staff/orders/"+processing.orderId()+"/cancel-paid",null,null).getBody().path("status").asText()).isEqualTo("CANCELLED");
    }

    @Test void failedOrUncertainRefundNeverCancelsOrReversesAndShippedIsRejected(){
        String staff=access(UserRole.STAFF),customer=access(UserRole.CUSTOMER);
        Pending failed=pending(staff,customer,"cancel-fail",18,2,false);UUID failedPayment=uuid(pay(customer,failed.orderId(),"cancel-fail-pay",PaymentMethod.CARD,null).getBody());
        provider.scriptRefund(new PaymentProvider.Result(false,false,false,null,"REFUND_DECLINED"));
        assertThat(post(staff,"/api/v1/staff/orders/"+failed.orderId()+"/cancel-paid",null,null).getBody().path("code").asText()).isEqualTo("PAYMENT_RECONCILIATION_REQUIRED");
        assertThat(order(customer,failed.orderId()).path("status").asText()).isEqualTo("CONFIRMED");assertQuantities(failed.inventoryId(),2,0,1);
        assertThat(jdbc.queryForObject("select status from payment_refunds where payment_id=?",String.class,failedPayment)).isEqualTo("FAILED");

        Pending uncertain=pending(staff,customer,"cancel-uncertain",18,2,false);UUID uncertainPayment=uuid(pay(customer,uncertain.orderId(),"cancel-uncertain-pay",PaymentMethod.CARD,null).getBody());
        provider.scriptRefund(new PaymentProvider.Result(false,true,true,"CANCEL-MAYBE","STILL_UNCERTAIN"));
        post(staff,"/api/v1/staff/orders/"+uncertain.orderId()+"/cancel-paid",null,null);
        assertThat(order(customer,uncertain.orderId()).path("status").asText()).isEqualTo("CONFIRMED");assertQuantities(uncertain.inventoryId(),2,0,1);
        assertThat(jdbc.queryForObject("select reconciliation_required from payments where id=?",Boolean.class,uncertainPayment)).isTrue();

        Pending shipped=pending(staff,customer,"cancel-shipped",18,2,false);pay(customer,shipped.orderId(),"cancel-shipped-pay",PaymentMethod.CARD,null);
        post(staff,"/api/v1/staff/orders/"+shipped.orderId()+"/processing",null,null);
        post(staff,"/api/v1/staff/orders/"+shipped.orderId()+"/shipment",Map.of("carrier","Carrier","trackingNumber","TRACK-"+shortId(),"estimatedDeliveryAt",Instant.now().plusSeconds(3600)),null);
        post(staff,"/api/v1/staff/orders/"+shipped.orderId()+"/ship",null,null);
        assertThat(post(staff,"/api/v1/staff/orders/"+shipped.orderId()+"/cancel-paid",null,null).getBody().path("code").asText()).isEqualTo("PAID_ORDER_CANCELLATION_NOT_ALLOWED");
    }

    private void compensationScenario(String tag,PaymentProvider.Result refundResult,String refundStatus,String paymentStatus,boolean reconciliation){
        String staff=access(UserRole.STAFF),firstCustomer=access(UserRole.CUSTOMER),secondCustomer=access(UserRole.CUSTOMER);
        JsonNode setup=directSellable(staff,"comp-"+tag,30,2);String code="COMP"+shortId();promotion(staff,code,"PERCENTAGE","10",1L);
        Pending first=checkoutExisting(staff,firstCustomer,setup,code,"comp-first-"+tag);Pending second=checkoutExisting(staff,secondCustomer,setup,code,"comp-second-"+tag);
        pay(firstCustomer,first.orderId(),"comp-first-pay-"+tag,PaymentMethod.CARD,null);
        provider.scriptRefund(refundResult);
        JsonNode error=pay(secondCustomer,second.orderId(),"comp-second-pay-"+tag,PaymentMethod.CARD,null).getBody();
        assertThat(error.path("code").asText()).isEqualTo("PAYMENT_RECONCILIATION_REQUIRED");
        UUID paymentId=jdbc.queryForObject("select id from payments where order_id=?",UUID.class,second.orderId());
        Map<String,Object> refund=jdbc.queryForMap("select status,reconciliation_required from payment_refunds where payment_id=?",paymentId);
        assertThat(refund.get("status")).isEqualTo(refundStatus);assertThat(refund.get("reconciliation_required")).isEqualTo(reconciliation);
        assertPayment(paymentId,paymentStatus);
        assertThat(jdbc.queryForObject("select count(*) from payment_refunds where payment_id=?",Long.class,paymentId)).isEqualTo(1);
    }

    private UUID paid(String staff,String customer,String tag,int price){Pending pending=pending(staff,customer,tag,price,1,false);return uuid(pay(customer,pending.orderId(),tag+"-payment",PaymentMethod.CARD,null).getBody());}
    private Pending pending(String staff,String customer,String tag,int price,int quantity,boolean withPromotion){
        JsonNode setup=directSellable(staff,tag,price,quantity);String code=null;
        if(withPromotion){code="P7"+shortId();promotion(staff,code,"PERCENTAGE","10",null);}
        return checkoutExisting(staff,customer,setup,code,"checkout-"+tag+"-"+shortId());
    }
    private Pending checkoutExisting(String staff,String customer,JsonNode setup,String code,String checkoutKey){
        UUID product=uuid(setup.path("product")),inventory=uuid(setup.path("inventory"));UUID address=address(customer,"Street "+shortId());
        post(customer,"/api/v1/cart/items",Map.of("productId",product,"quantity",1),null);
        if(code!=null)post(customer,"/api/v1/cart/promotion",Map.of("code",code),null);
        JsonNode order=postCheckout(customer,checkoutKey,address).getBody();return new Pending(uuid(order),inventory);
    }
    private JsonNode directSellable(String staff,String tag,int price,int quantity){JsonNode category=category(staff,tag),product=product(staff,category,tag,price),inventory=post(staff,"/api/v1/staff/inventory",Map.of("productId",uuid(product),"initialQuantity",quantity,"lowStockThreshold",1),null).getBody();ObjectNode result=JsonNodeFactory.instance.objectNode();result.set("product",product);result.set("inventory",inventory);return result;}
    private JsonNode category(String staff,String tag){return post(staff,"/api/v1/staff/categories",Map.of("name",tag,"slug","p7-"+slugPart(tag)+"-"+shortId().toLowerCase(),"description","phase7","active",true),null).getBody();}
    private JsonNode product(String staff,JsonNode category,String tag,int price){String suffix=shortId();return post(staff,"/api/v1/staff/products",Map.of("name","Product "+tag,"slug","p7-product-"+slugPart(tag)+"-"+suffix.toLowerCase(),"description","phase7","sku","P7-"+suffix,"brand","Phase7","basePrice",new BigDecimal(price),"categoryId",uuid(category),"active",true),null).getBody();}
    private JsonNode promotion(String staff,String code,String type,String value,Long total){Map<String,Object> body=new LinkedHashMap<>();body.put("code",code);body.put("name",code);body.put("type",type);body.put("scope","CART");if(value!=null)body.put("discountValue",new BigDecimal(value));body.put("minimumSpend",BigDecimal.ZERO);body.put("startsAt",Instant.now().minusSeconds(60));body.put("endsAt",Instant.now().plusSeconds(3600));if(total!=null)body.put("totalUsageLimit",total);body.put("active",true);return post(staff,"/api/v1/staff/promotions",body,null).getBody();}
    private UUID address(String token,String line){return uuid(post(token,"/api/v1/me/addresses",Map.of("label","Home","recipientName","Phase Seven","phone","+60123456789","addressLine1",line,"city","Kuala Lumpur","stateRegion","KL","postalCode","50000","countryCode","MY","defaultShipping",true,"defaultBilling",true),null).getBody());}
    private String access(UserRole role){String email="p7."+role.name().toLowerCase()+"."+UUID.randomUUID()+"@example.com";UserAccount user=new UserAccount(email,encoder.encode(PASSWORD),"Phase Seven",null,role);user.verifyEmail();users.saveAndFlush(user);return rest.postForObject("/api/v1/auth/login",Map.of("email",email,"password",PASSWORD),JsonNode.class).path("accessToken").asText();}
    private ResponseEntity<JsonNode> postCheckout(String token,String key,UUID address){return post(token,"/api/v1/checkout",Map.of("shippingAddressId",address,"shippingServiceLevel","STANDARD"),key);}
    private ResponseEntity<JsonNode> pay(String token,UUID order,String key,PaymentMethod method,String methodToken){Map<String,Object> body=new LinkedHashMap<>();body.put("paymentMethod",method);if(methodToken!=null)body.put("paymentMethodToken",methodToken);return post(token,"/api/v1/orders/"+order+"/payments",body,key);}
    private ResponseEntity<JsonNode> refund(String token,UUID payment,String key,String amount){Map<String,Object> body=new LinkedHashMap<>();if(amount!=null)body.put("amount",new BigDecimal(amount));return post(token,"/api/v1/staff/payments/"+payment+"/refunds",body,key);}
    private JsonNode order(String token,UUID id){return get(token,"/api/v1/orders/"+id).getBody();}
    private ResponseEntity<JsonNode> post(String token,String path,Object body,String key){return exchange(token,HttpMethod.POST,path,body,key);}
    private ResponseEntity<JsonNode> get(String token,String path){return exchange(token,HttpMethod.GET,path,null,null);}
    private ResponseEntity<JsonNode> exchange(String token,HttpMethod method,String path,Object body,String key){HttpHeaders headers=new HttpHeaders();if(token!=null)headers.setBearerAuth(token);if(key!=null)headers.set("Idempotency-Key",key);headers.setContentType(MediaType.APPLICATION_JSON);return rest.exchange(path,method,new HttpEntity<>(body,headers),JsonNode.class);}
    private String providerKey(UUID payment){return jdbc.queryForObject("select provider_idempotency_key from payments where id=?",String.class,payment);}
    private void assertPayment(UUID id,String status){assertThat(jdbc.queryForObject("select status from payments where id=?",String.class,id)).isEqualTo(status);}
    private long count(String table,String column,UUID id){return jdbc.queryForObject("select count(*) from "+table+" where "+column+"=?",Long.class,id);}
    private void assertQuantities(UUID id,long total,long reserved,long sold){Map<String,Object> row=jdbc.queryForMap("select total_quantity,reserved_quantity,sold_quantity from inventory where id=?",id);assertThat(((Number)row.get("total_quantity")).longValue()).isEqualTo(total);assertThat(((Number)row.get("reserved_quantity")).longValue()).isEqualTo(reserved);assertThat(((Number)row.get("sold_quantity")).longValue()).isEqualTo(sold);}
    private static UUID uuid(JsonNode node){return UUID.fromString(node.path("id").asText());}
    private static String slugPart(String value){return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-");}
    private static String shortId(){return UUID.randomUUID().toString().replace("-","").substring(0,8);}
    @Test void customerPaymentList_isOwnershipScopedAndStaffListByOrderWorks(){
        String staff=access(UserRole.STAFF),customer=access(UserRole.CUSTOMER),other=access(UserRole.CUSTOMER);
        Pending pending=pending(staff,customer,"paylist",12,2,false);
        // Empty before payment, owned by the customer.
        JsonNode before=get(customer,"/api/v1/orders/"+pending.orderId()+"/payments").getBody();
        assertThat(before.isArray()).isTrue();
        assertThat(before.size()).isZero();
        // A different customer cannot list another customer's order payments.
        assertThat(get(other,"/api/v1/orders/"+pending.orderId()+"/payments").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Pay and verify the list reflects the real payment record.
        JsonNode paid=pay(customer,pending.orderId(),"paylist-key",PaymentMethod.CARD,null).getBody();
        JsonNode list=get(customer,"/api/v1/orders/"+pending.orderId()+"/payments").getBody();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).path("id").asText()).isEqualTo(paid.path("id").asText());
        assertThat(list.get(0).path("status").asText()).isEqualTo("PAID");
        // Staff can list payments for an order; customers cannot.
        JsonNode staffList=get(staff,"/api/v1/staff/payments/order/"+pending.orderId()).getBody();
        assertThat(staffList.size()).isEqualTo(1);
        assertThat(staffList.get(0).path("orderId").asText()).isEqualTo(pending.orderId().toString());
        assertThat(get(customer,"/api/v1/staff/payments/order/"+pending.orderId()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    private record Pending(UUID orderId,UUID inventoryId){}
}
