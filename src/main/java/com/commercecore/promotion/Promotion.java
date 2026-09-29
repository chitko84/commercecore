package com.commercecore.promotion;

import com.commercecore.category.Category;
import com.commercecore.product.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Entity @Table(name="promotions")
public class Promotion {
    private static final Pattern CODE=Pattern.compile("[A-Z0-9][A-Z0-9_-]*");
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false,unique=true,length=64) private String code;
    @Column(nullable=false,length=160) private String name;
    @Column(length=1000) private String description;
    @Enumerated(EnumType.STRING) @Column(name="promotion_type",nullable=false,length=24) private PromotionType type;
    @Enumerated(EnumType.STRING) @Column(name="promotion_scope",nullable=false,length=24) private PromotionScope scope;
    @Column(name="discount_value",precision=19,scale=4) private BigDecimal discountValue;
    @Column(name="minimum_spend",nullable=false,precision=19,scale=2) private BigDecimal minimumSpend;
    @Column(name="maximum_discount",precision=19,scale=2) private BigDecimal maximumDiscount;
    @Column(name="starts_at",nullable=false) private Instant startsAt; @Column(name="ends_at",nullable=false) private Instant endsAt;
    @Column(name="total_usage_limit") private Long totalUsageLimit; @Column(name="per_user_usage_limit") private Long perUserUsageLimit;
    @Column(nullable=false) private boolean active;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="target_product_id") private Product targetProduct;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="target_category_id") private Category targetCategory;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Version @Column(nullable=false) private long version;
    protected Promotion() { }
    public Promotion(String code,String name,String description,PromotionType type,PromotionScope scope,BigDecimal discountValue,
            BigDecimal minimumSpend,BigDecimal maximumDiscount,Instant startsAt,Instant endsAt,Long totalLimit,Long userLimit,
            boolean active,Product targetProduct,Category targetCategory){update(code,name,description,type,scope,discountValue,minimumSpend,maximumDiscount,startsAt,endsAt,totalLimit,userLimit,active,targetProduct,targetCategory);}
    public void update(String code,String name,String description,PromotionType type,PromotionScope scope,BigDecimal discountValue,
            BigDecimal minimumSpend,BigDecimal maximumDiscount,Instant startsAt,Instant endsAt,Long totalLimit,Long userLimit,
            boolean active,Product targetProduct,Category targetCategory){
        this.code=normalizeCode(code);this.name=text(name,160);this.description=optional(description,1000);this.type=Objects.requireNonNull(type);this.scope=Objects.requireNonNull(scope);
        validate(type,scope,discountValue,minimumSpend,maximumDiscount,startsAt,endsAt,totalLimit,userLimit,targetProduct,targetCategory);
        this.discountValue=discountValue;this.minimumSpend=minimumSpend;this.maximumDiscount=maximumDiscount;this.startsAt=startsAt;this.endsAt=endsAt;
        this.totalUsageLimit=totalLimit;this.perUserUsageLimit=userLimit;this.active=active;this.targetProduct=targetProduct;this.targetCategory=targetCategory;
    }
    private static void validate(PromotionType type,PromotionScope scope,BigDecimal value,BigDecimal min,BigDecimal max,Instant starts,Instant ends,Long total,Long user,Product product,Category category){
        boolean valueValid=type==PromotionType.FREE_SHIPPING?value==null:value!=null&&value.signum()>0&&(type!=PromotionType.PERCENTAGE||value.compareTo(BigDecimal.valueOf(100))<=0);
        boolean targetValid=scope==PromotionScope.CART?product==null&&category==null:scope==PromotionScope.PRODUCT?product!=null&&category==null:product==null&&category!=null;
        if(!valueValid||min==null||min.signum()<0||max!=null&&max.signum()<=0||starts==null||ends==null||!starts.isBefore(ends)||total!=null&&total<=0||user!=null&&user<=0||!targetValid)throw new IllegalArgumentException("invalid promotion");
    }
    public static String normalizeCode(String value){String code=text(value,64).toUpperCase(Locale.ROOT);if(!CODE.matcher(code).matches())throw new IllegalArgumentException("invalid code");return code;}
    private static String text(String v,int max){if(v==null||v.isBlank())throw new IllegalArgumentException();String t=v.trim();if(t.length()>max)throw new IllegalArgumentException();return t;}
    private static String optional(String v,int max){if(v==null||v.isBlank())return null;String t=v.trim();if(t.length()>max)throw new IllegalArgumentException();return t;}
    public void activate(){active=true;}public void deactivate(){active=false;}
    @PrePersist void create(){Instant now=Instant.now();createdAt=now;updatedAt=now;}@PreUpdate void touch(){updatedAt=Instant.now();}
    public UUID getId(){return id;}public String getCode(){return code;}public String getName(){return name;}public String getDescription(){return description;}
    public PromotionType getType(){return type;}public PromotionScope getScope(){return scope;}public BigDecimal getDiscountValue(){return discountValue;}
    public BigDecimal getMinimumSpend(){return minimumSpend;}public BigDecimal getMaximumDiscount(){return maximumDiscount;}public Instant getStartsAt(){return startsAt;}public Instant getEndsAt(){return endsAt;}
    public Long getTotalUsageLimit(){return totalUsageLimit;}public Long getPerUserUsageLimit(){return perUserUsageLimit;}public boolean isActive(){return active;}
    public Product getTargetProduct(){return targetProduct;}public Category getTargetCategory(){return targetCategory;}public Instant getCreatedAt(){return createdAt;}public Instant getUpdatedAt(){return updatedAt;}
}
