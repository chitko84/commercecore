package com.commercecore.payment;

import com.commercecore.alert.AdminAlertService;
import com.commercecore.audit.AuditService;
import com.commercecore.common.exception.ApiException;
import com.commercecore.notification.NotificationService;
import com.commercecore.notification.NotificationType;
import com.commercecore.order.*;
import com.commercecore.user.UserAccountRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final PaymentRefundRepository refunds;
    private final PaymentEventRepository events;
    private final CustomerOrderRepository orders;
    private final UserAccountRepository users;
    private final OrderService orderService;
    private final PaymentProvider provider;
    private final PaymentReliabilityProperties reliability;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher publisher;
    private final NotificationService notifications;
    private final AdminAlertService alerts;
    private final AuditService audits;
    private final AtomicInteger transientFailures = new AtomicInteger();
    private volatile Instant circuitOpenedAt;

    public PaymentService(PaymentRepository payments, PaymentRefundRepository refunds,
                          PaymentEventRepository events, CustomerOrderRepository orders,
                          UserAccountRepository users, OrderService orderService,
                          PaymentProvider provider, PaymentReliabilityProperties reliability,
                          PlatformTransactionManager transactionManager,
                          ApplicationEventPublisher publisher, NotificationService notifications,
                          AdminAlertService alerts, AuditService audits) {
        this.payments=payments; this.refunds=refunds; this.events=events; this.orders=orders;
        this.users=users; this.orderService=orderService; this.provider=provider;
        this.reliability=reliability; this.transaction=new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.publisher=publisher; this.notifications=notifications; this.alerts=alerts;
        this.audits=audits;
    }

    public PaymentDtos.PaymentResponse pay(UUID userId, UUID orderId, String rawKey,
                                            PaymentDtos.PaymentRequest request) {
        String key=key(rawKey);
        String fingerprint=hash(request.paymentMethod()+"|"+
                (request.paymentMethodToken()==null?"":request.paymentMethodToken()));
        Payment existing=transaction.execute(status -> payments.findByOrderIdAndIdempotencyKey(orderId,key).orElse(null));
        if(existing!=null)return transaction.execute(status->samePayment(requiredLocked(existing.getId()),fingerprint));
        ensureCircuitAvailable();
        Payment created;
        try { created=transaction.execute(status -> createPayment(userId,orderId,key,fingerprint,request)); }
        catch(DataIntegrityViolationException concurrent){
            Payment winner=transaction.execute(status -> payments.findByOrderIdAndIdempotencyKey(orderId,key).orElseThrow(()->concurrent));
            return transaction.execute(status->samePayment(requiredLocked(winner.getId()),fingerprint));
        }
        UUID paymentId=created.getId();
        transaction.executeWithoutResult(status -> event(requiredLocked(paymentId),PaymentEventType.AUTHORIZATION_STARTED,
                PaymentStatus.PENDING,PaymentStatus.PENDING,null));
        PaymentProvider.Result authorization=execute(()->provider.authorize(request.paymentMethod(),created.getAmount(),
                created.getProviderIdempotencyKey(),request.paymentMethodToken()));
        if(!authorization.success())return fail(paymentId,PaymentStatus.PENDING,authorization);
        transaction.executeWithoutResult(status -> {
            Payment payment=requiredLocked(paymentId);
            payment.authorized(authorization.reference());
            event(payment,PaymentEventType.AUTHORIZED,PaymentStatus.PENDING,PaymentStatus.AUTHORIZED,authorization.reference());
            event(payment,PaymentEventType.CAPTURE_STARTED,PaymentStatus.AUTHORIZED,PaymentStatus.AUTHORIZED,authorization.reference());
        });
        PaymentProvider.Result capture=execute(()->provider.capture(created.getProviderIdempotencyKey()));
        if(!capture.success())return fail(paymentId,PaymentStatus.AUTHORIZED,capture);
        Payment paid=transaction.execute(status -> {
            Payment payment=requiredLocked(paymentId);
            payment.paid(capture.reference());
            event(payment,PaymentEventType.PAID,PaymentStatus.AUTHORIZED,PaymentStatus.PAID,capture.reference());
            notifications.notify(payment.getCustomer().getId(),NotificationType.PAYMENT_SUCCEEDED,
                    "Payment succeeded","Payment for order "+payment.getOrder().getOrderNumber()
                            +" was successful.","PAYMENT",payment.getId().toString());
            return payment;
        });
        return confirmOrCompensate(paid.getId(),orderId);
    }

    private Payment createPayment(UUID userId,UUID orderId,String key,String fingerprint,PaymentDtos.PaymentRequest request){
        CustomerOrder order=orders.findLockedById(orderId).filter(found->found.getCustomer().getId().equals(userId))
                .orElseThrow(()->err(HttpStatus.NOT_FOUND,"PAYMENT_NOT_ALLOWED","Order was not found."));
        Optional<Payment> prior=payments.findByOrderIdAndIdempotencyKey(orderId,key);
        if(prior.isPresent())return prior.get();
        if(order.getStatus()!=OrderStatus.PENDING||order.getGrandTotal().signum()<=0)
            throw err(HttpStatus.CONFLICT,"PAYMENT_NOT_ALLOWED","Order is not payable.");
        if(!Instant.now().isBefore(order.getReservationExpiresAt()))
            throw err(HttpStatus.CONFLICT,"RESERVATION_EXPIRED","The order reservation has expired.");
        Payment payment=payments.saveAndFlush(new Payment(order,order.getCustomer(),request.paymentMethod(),
                order.getGrandTotal(),key,fingerprint,"PAYMENT:"+orderId+":"+key));
        event(payment,PaymentEventType.CREATED,null,PaymentStatus.PENDING,null);
        return payment;
    }

    private PaymentDtos.PaymentResponse samePayment(Payment payment,String fingerprint){
        if(!payment.getRequestFingerprint().equals(fingerprint))throw err(HttpStatus.CONFLICT,
                "PAYMENT_IDEMPOTENCY_CONFLICT","Payment key was already used for another request.");
        return response(payment);
    }

    private PaymentDtos.PaymentResponse fail(UUID paymentId,PaymentStatus expectedFrom,PaymentProvider.Result result){
        return transaction.execute(status->{
            Payment payment=requiredLocked(paymentId); PaymentStatus from=payment.getStatus();
            if(from!=expectedFrom)return response(payment);
            payment.failed(result.code(),safe(result.code()),result.retryable());
            event(payment,PaymentEventType.FAILED,from,PaymentStatus.FAILED,result.reference());
            if(result.uncertain()){
                payment.reconciliation(true);
                event(payment,PaymentEventType.RECONCILIATION_REQUIRED,PaymentStatus.FAILED,PaymentStatus.FAILED,result.reference());
            }else{
                // Definitive failure (retries already exhausted or non-retryable).
                // Transient retries never reach this point, so they can never
                // produce the customer notification or the admin alert.
                notifications.notify(payment.getCustomer().getId(),NotificationType.PAYMENT_FAILED,
                        "Payment failed","The payment for order "+payment.getOrder().getOrderNumber()
                                +" could not be processed. Please try again.","PAYMENT",payment.getId().toString());
                alerts.recordFailedPayment(payment.getId(),payment.getOrder().getOrderNumber());
            }
            return response(payment);
        });
    }

    private PaymentDtos.PaymentResponse confirmOrCompensate(UUID paymentId,UUID orderId){
        Payment payment=transaction.execute(status->requiredLocked(paymentId));
        try{
            orderService.confirmAfterPayment(orderId,payment.getProviderPaymentReference());
            return transaction.execute(status->response(requiredLocked(paymentId)));
        }catch(RuntimeException failure){
            compensate(paymentId);
            throw err(HttpStatus.CONFLICT,"PAYMENT_RECONCILIATION_REQUIRED",
                    "Payment confirmation failed and durable compensation was started.");
        }
    }

    private void compensate(UUID paymentId){
        PaymentRefund compensation=transaction.execute(status->{
            Payment payment=requiredLocked(paymentId); String key="COMPENSATION:"+paymentId;
            Optional<PaymentRefund> prior=refunds.findByPaymentIdAndIdempotencyKey(paymentId,key);
            if(prior.isPresent())return prior.get();
            PaymentRefund created=refunds.saveAndFlush(new PaymentRefund(payment,payment.getAmount(),key,hash(payment.getAmount()),null));
            event(payment,PaymentEventType.COMPENSATION_REQUIRED,payment.getStatus(),payment.getStatus(),null);
            event(payment,PaymentEventType.REFUND_STARTED,payment.getStatus(),payment.getStatus(),null);
            return created;
        });
        if(compensation.getStatus()!=RefundStatus.PENDING)return;
        PaymentProvider.Result outcome=execute(()->provider.refund(compensation.getAmount(),providerRefundKey(compensation.getId())));
        completeRefund(compensation.getId(),outcome,null);
    }

    public PaymentDtos.RefundResponse refund(UUID actor,UUID paymentId,String rawKey,PaymentDtos.RefundRequest request){
        String idempotencyKey=key(rawKey); BigDecimal requestedInput=request.amount();
        PaymentRefund refund=transaction.execute(status->{
            Payment payment=requiredLocked(paymentId);
            Optional<PaymentRefund> prior=refunds.findByPaymentIdAndIdempotencyKey(paymentId,idempotencyKey);
            if(prior.isPresent()){
                if(requestedInput!=null&&!prior.get().getAmount().equals(money(requestedInput)))throw err(HttpStatus.CONFLICT,
                        "REFUND_IDEMPOTENCY_CONFLICT","Refund key was already used for another amount.");
                return prior.get();
            }
            if(payment.getStatus()!=PaymentStatus.PAID&&payment.getStatus()!=PaymentStatus.PARTIALLY_REFUNDED)
                throw err(HttpStatus.CONFLICT,"PAYMENT_NOT_ALLOWED","Payment is not refundable.");
            BigDecimal available=refundableBalance(paymentId);
            BigDecimal amount=requestedInput==null?available:money(requestedInput);
            if(amount.signum()<=0||amount.compareTo(available)>0)throw err(HttpStatus.CONFLICT,
                    "REFUND_EXCEEDS_PAYMENT","Refund exceeds the remaining paid amount.");
            PaymentRefund created=refunds.saveAndFlush(new PaymentRefund(payment,amount,idempotencyKey,hash(amount),users.findById(actor).orElse(null)));
            event(payment,PaymentEventType.REFUND_STARTED,payment.getStatus(),payment.getStatus(),null);
            audits.record(actor,"REFUND_CREATED","PAYMENT","REFUND",created.getId().toString(),
                    "amount="+created.getAmount()+"; order="+payment.getOrder().getOrderNumber());
            return created;
        });
        if(refund.getStatus()!=RefundStatus.PENDING||refund.isReconciliationRequired())return refundResponse(refund);
        PaymentProvider.Result outcome=execute(()->provider.refund(refund.getAmount(),providerRefundKey(refund.getId())));
        return completeRefund(refund.getId(),outcome,actor);
    }

    private PaymentDtos.RefundResponse completeRefund(UUID refundId,PaymentProvider.Result outcome,UUID actor){
        return transaction.execute(status->{
            PaymentRefund refund=refunds.findLocked(refundId).orElseThrow(()->err(HttpStatus.NOT_FOUND,"REFUND_NOT_FOUND","Refund was not found."));
            Payment payment=requiredLocked(refund.getPayment().getId());
            if(refund.getStatus()!=RefundStatus.PENDING||refund.isReconciliationRequired())return refundResponse(refund);
            PaymentStatus before=payment.getStatus();
            if(outcome.success()){
                refund.complete(outcome.reference()); recalculateRefundStatus(payment);
                event(payment,payment.getStatus()==PaymentStatus.REFUNDED?PaymentEventType.REFUNDED:PaymentEventType.PARTIALLY_REFUNDED,
                        before,payment.getStatus(),outcome.reference());
                publishRefundCompleted(payment,refund);
                audits.record(actor,"REFUND_COMPLETED","PAYMENT","REFUND",refund.getId().toString(),
                        "amount="+refund.getAmount()+"; order="+payment.getOrder().getOrderNumber());
            }else if(outcome.uncertain()){
                refund.reconcile(); payment.reconciliation(true);
                event(payment,PaymentEventType.RECONCILIATION_REQUIRED,before,before,outcome.reference());
                event(payment,PaymentEventType.REFUND_FAILED,before,before,outcome.reference());
            }else{
                refund.fail(); event(payment,PaymentEventType.REFUND_FAILED,before,before,outcome.reference());
            }
            return refundResponse(refund);
        });
    }

    public PaymentDtos.PaymentResponse reconcile(UUID actor,UUID paymentId){
        Payment snapshot=transaction.execute(status->requiredLocked(paymentId));
        if(!snapshot.isReconciliationRequired())return response(snapshot);
        PaymentProvider.Result lookup=execute(()->provider.lookup(snapshot.getProviderIdempotencyKey()));
        if(!lookup.success()){
            if(!lookup.uncertain())transaction.executeWithoutResult(status->requiredLocked(paymentId).reconciliation(false));
            return transaction.execute(status->response(requiredLocked(paymentId)));
        }
        UUID recoveredOrderId=transaction.execute(status->{
            Payment payment=requiredLocked(paymentId); PaymentStatus before=payment.getStatus();
            payment.recoverPaid(lookup.reference());
            event(payment,PaymentEventType.RECONCILED,before,PaymentStatus.PAID,lookup.reference());
            audits.record(actor,"PAYMENT_RECONCILED","PAYMENT","PAYMENT",payment.getId().toString(),
                    "order="+payment.getOrder().getOrderNumber()+"; reference="+lookup.reference());
            return payment.getOrder().getId();
        });
        return confirmOrCompensate(paymentId,recoveredOrderId);
    }

    public PaymentDtos.RefundResponse reconcileRefund(UUID actor,UUID refundId){
        PaymentRefund snapshot=transaction.execute(status->refunds.findLocked(refundId)
                .orElseThrow(()->err(HttpStatus.NOT_FOUND,"REFUND_NOT_FOUND","Refund was not found.")));
        if(snapshot.getStatus()==RefundStatus.COMPLETED||
                (snapshot.getStatus()==RefundStatus.FAILED&&!snapshot.isReconciliationRequired()))
            return transaction.execute(status->refundResponse(refunds.findLocked(refundId).orElseThrow()));
        PaymentProvider.Result lookup=execute(()->provider.lookup(providerRefundKey(refundId)));
        return transaction.execute(status->{
            PaymentRefund refund=refunds.findLocked(refundId).orElseThrow(()->err(HttpStatus.NOT_FOUND,"REFUND_NOT_FOUND","Refund was not found."));
            Payment payment=requiredLocked(refund.getPayment().getId()); PaymentStatus before=payment.getStatus();
            if(lookup.success()){
                refund.complete(lookup.reference()); recalculateRefundStatus(payment);
                if(!refunds.findAllByPaymentIdOrderByCreatedAtAsc(payment.getId()).stream()
                        .anyMatch(candidate -> candidate.isReconciliationRequired()))
                    payment.reconciliation(false);
                event(payment,PaymentEventType.RECONCILED,before,payment.getStatus(),lookup.reference());
                publishRefundCompleted(payment,refund);
                audits.record(actor,"REFUND_COMPLETED","PAYMENT","REFUND",refund.getId().toString(),
                        "amount="+refund.getAmount()+"; order="+payment.getOrder().getOrderNumber());
            }else if(lookup.uncertain()){
                refund.reconcile(); payment.reconciliation(true);
            }else{
                refund.fail(); payment.reconciliation(false);
                event(payment,PaymentEventType.REFUND_FAILED,before,before,lookup.reference());
            }
            return refundResponse(refund);
        });
    }

    public PaymentDtos.PaymentResponse get(UUID user,UUID id){
        return transaction.execute(status->response(payments.findByIdAndCustomerId(id,user)
                .orElseThrow(()->err(HttpStatus.NOT_FOUND,"PAYMENT_NOT_FOUND","Payment was not found."))));
    }
    public List<PaymentDtos.PaymentResponse> customerPayments(UUID customerId,UUID orderId){
        return transaction.execute(status->{
            orders.findByIdAndCustomerId(orderId,customerId)
                    .orElseThrow(()->err(HttpStatus.NOT_FOUND,"ORDER_NOT_FOUND","Order was not found."));
            return payments.findAllByOrderIdOrderByCreatedAtAsc(orderId).stream().map(this::response).toList();
        });
    }
    public List<PaymentDtos.PaymentResponse> staffPayments(UUID orderId){
        return transaction.execute(status->payments.findAllByOrderIdOrderByCreatedAtAsc(orderId).stream().map(this::response).toList());
    }
    public List<PaymentDtos.RefundResponse> refunds(UUID id){
        return transaction.execute(status->refunds.findAllByPaymentIdOrderByCreatedAtAsc(id).stream().map(this::refundResponse).toList());
    }

    private PaymentProvider.Result execute(Supplier<PaymentProvider.Result> call){
        Instant now=Instant.now();
        if(circuitOpenedAt!=null&&now.isBefore(circuitOpenedAt.plus(reliability.circuitOpenDuration())))
            throw err(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_PROVIDER_UNAVAILABLE","Payment provider is temporarily unavailable.");
        if(circuitOpenedAt!=null)circuitOpenedAt=null;
        PaymentProvider.Result result=null;
        for(int attempt=0;attempt<=reliability.maxRetries();attempt++){
            ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
            try {
                result=executor.submit(call::get).get(reliability.timeout().toMillis(),TimeUnit.MILLISECONDS);
            } catch(TimeoutException timeout){
                // Do not wait for a provider that ignores interruption. The remote
                // call is represented as uncertain and will be reconciled via its
                // stable provider idempotency key.
                result=new PaymentProvider.Result(false,true,true,null,"TIMEOUT");
            } catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
                result=new PaymentProvider.Result(false,true,true,null,"INTERRUPTED");
            } catch(ExecutionException failure){
                result=new PaymentProvider.Result(false,true,false,null,"PROVIDER_UNAVAILABLE");
            } finally {
                executor.shutdownNow();
            }
            if(result.success()||!result.retryable()||result.uncertain())break;
            if(!reliability.retryDelay().isZero())try{Thread.sleep(reliability.retryDelay().multipliedBy(attempt+1));}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();break;}
        }
        if(result!=null&&result.retryable()&&!result.success()){
            if(transientFailures.incrementAndGet()>=reliability.circuitFailureThreshold())circuitOpenedAt=Instant.now();
        }else transientFailures.set(0);
        return result;
    }

    private void ensureCircuitAvailable(){
        Instant opened=circuitOpenedAt;
        if(opened!=null&&Instant.now().isBefore(opened.plus(reliability.circuitOpenDuration())))
            throw err(HttpStatus.SERVICE_UNAVAILABLE,"PAYMENT_PROVIDER_UNAVAILABLE","Payment provider is temporarily unavailable.");
    }

    void resetReliabilityState(){transientFailures.set(0);circuitOpenedAt=null;}

    private void publishRefundCompleted(Payment payment,PaymentRefund refund){
        publisher.publishEvent(new RefundCompletedEvent(payment.getCustomer().getId(),
                payment.getOrder().getOrderNumber(),payment.getCurrencyCode(),refund.getAmount(),
                refund.getId(),payment.getId()));
    }
    private void recalculateRefundStatus(Payment payment){
        BigDecimal completed=completedRefundTotal(payment.getId());
        payment.refunded(completed.compareTo(payment.getAmount())<0);
    }
    private BigDecimal refundableBalance(UUID paymentId){
        Payment payment=requiredLocked(paymentId);
        BigDecimal committed=refunds.findAllByPaymentIdOrderByCreatedAtAsc(paymentId).stream()
                .filter(refund->refund.getStatus()!=RefundStatus.FAILED).map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        return payment.getAmount().subtract(committed).setScale(2,RoundingMode.HALF_UP);
    }
    private BigDecimal completedRefundTotal(UUID paymentId){return refunds.findAllByPaymentIdOrderByCreatedAtAsc(paymentId).stream()
            .filter(refund->refund.getStatus()==RefundStatus.COMPLETED).map(PaymentRefund::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private Payment requiredLocked(UUID id){return payments.findLocked(id).orElseThrow(()->err(HttpStatus.NOT_FOUND,"PAYMENT_NOT_FOUND","Payment was not found."));}
    private void event(Payment payment,PaymentEventType type,PaymentStatus from,PaymentStatus to,String reference){events.save(new PaymentEvent(payment,type,from,to,reference));}
    private static String providerRefundKey(UUID id){return "REFUND:"+id;}
    private static BigDecimal money(BigDecimal amount){return amount.setScale(2,RoundingMode.HALF_UP);}
    private static String key(String value){if(value==null||value.isBlank()||value.trim().length()<8||value.trim().length()>160)throw err(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Idempotency-Key must contain 8 to 160 characters.");return value.trim();}
    private static String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private static String safe(String value){return value==null?"PROVIDER_ERROR":value;}
    private PaymentDtos.PaymentResponse response(Payment payment){return new PaymentDtos.PaymentResponse(payment.getId(),payment.getOrder().getId(),payment.getPaymentMethod(),payment.getStatus(),payment.getAmount(),payment.getProviderPaymentReference(),payment.isReconciliationRequired(),payment.getCreatedAt(),payment.getUpdatedAt());}
    private PaymentDtos.RefundResponse refundResponse(PaymentRefund refund){return new PaymentDtos.RefundResponse(refund.getId(),refund.getPayment().getId(),refund.getAmount(),refund.getStatus(),refund.getProviderRefundReference(),refund.isReconciliationRequired(),refund.getCreatedAt());}
    private static ApiException err(HttpStatus status,String code,String message){return new ApiException(status,code,message);}
}
