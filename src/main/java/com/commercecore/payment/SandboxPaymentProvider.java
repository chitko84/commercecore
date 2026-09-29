package com.commercecore.payment;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SandboxPaymentProvider implements PaymentProvider {
    private final Map<String,Result> results=new ConcurrentHashMap<>();
    private final Map<String,String> tokens=new ConcurrentHashMap<>();
    private final Map<String,AtomicInteger> calls=new ConcurrentHashMap<>();
    private final Map<String,AtomicInteger> transientRemaining=new ConcurrentHashMap<>();
    private final Queue<Result> refundScripts=new ConcurrentLinkedQueue<>();
    private final Queue<Result> lookupScripts=new ConcurrentLinkedQueue<>();

    @Override public Result authorize(PaymentMethod method,BigDecimal amount,String key,String token){
        count("AUTHORIZE:"+key);tokens.putIfAbsent(key,token==null?"":token.toLowerCase(Locale.ROOT));
        Result prior=results.get("AUTH:"+key);if(prior!=null&&!prior.uncertain())return prior;
        String behavior=tokens.get(key);
        if(behavior.contains("slow-timeout")){
            Result uncertain=new Result(false,true,true,"SANDBOX-UNCERTAIN-"+UUID.randomUUID(),"TIMEOUT");
            results.putIfAbsent("AUTH:"+key,uncertain);count("EFFECTIVE_CHARGE:"+key);
            try{Thread.sleep(5_000);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            return uncertain;
        }
        if(behavior.contains("decline"))return store("AUTH:"+key,new Result(false,false,false,null,"DECLINED"));
        if(behavior.contains("transient")){
            int configured=behavior.contains("always")?Integer.MAX_VALUE:trailingNumber(behavior,1);
            AtomicInteger remaining=transientRemaining.computeIfAbsent(key,ignored->new AtomicInteger(configured));
            if(remaining.getAndDecrement()>0)return new Result(false,true,false,null,"TRANSIENT");
        }
        count("EFFECTIVE_CHARGE:"+key);
        return store("AUTH:"+key,new Result(true,false,false,"SANDBOX-"+UUID.randomUUID(),null));
    }

    @Override public Result capture(String key){
        count("CAPTURE:"+key);
        return results.computeIfAbsent("CAP:"+key,ignored->new Result(true,false,false,"CAP-"+UUID.randomUUID(),null));
    }

    @Override public Result refund(BigDecimal amount,String key){
        count("REFUND:"+key);Result prior=results.get(key);if(prior!=null)return prior;
        Result scripted=refundScripts.poll();
        Result result=scripted==null?new Result(true,false,false,"REF-"+UUID.randomUUID(),null):scripted;
        if(result.success())count("EFFECTIVE_REFUND:"+key);else if(result.uncertain())count("POSSIBLE_REFUND:"+key);
        return store(key,result);
    }

    @Override public Result lookup(String key){
        count("LOOKUP:"+key);Result scripted=lookupScripts.poll();if(scripted!=null){results.put(key,scripted);return scripted;}
        Result result=results.get(key);if(result==null)result=results.get("AUTH:"+key);
        if(result==null)return new Result(false,false,false,null,"NOT_FOUND");
        if(result.uncertain()&&!"STILL_UNCERTAIN".equals(result.code())){
            Result settled=new Result(true,false,false,result.reference()==null?"LOOKUP-"+UUID.randomUUID():result.reference(),null);
            results.put(key,settled);results.put("AUTH:"+key,settled);return settled;
        }
        return result;
    }

    public void scriptRefund(Result result){refundScripts.add(result);}
    public void scriptLookup(Result result){lookupScripts.add(result);}
    public int invocationCount(String operation,String key){AtomicInteger count=calls.get(operation+":"+key);return count==null?0:count.get();}
    public int totalInvocations(String operation){return calls.entrySet().stream().filter(entry->entry.getKey().startsWith(operation+":")).mapToInt(entry->entry.getValue().get()).sum();}
    public void resetDiagnostics(){calls.clear();transientRemaining.clear();refundScripts.clear();lookupScripts.clear();}
    private void count(String key){calls.computeIfAbsent(key,ignored->new AtomicInteger()).incrementAndGet();}
    private Result store(String key,Result value){results.putIfAbsent(key,value);return results.get(key);}
    private static int trailingNumber(String value,int fallback){try{int dash=value.lastIndexOf('-');return dash<0?fallback:Integer.parseInt(value.substring(dash+1));}catch(NumberFormatException ignored){return fallback;}}
}
