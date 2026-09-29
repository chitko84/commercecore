package com.commercecore.order;
import com.commercecore.config.CheckoutProperties;import org.springframework.stereotype.Service;import java.math.*;
@Service public class TaxCalculator{private final CheckoutProperties properties;public TaxCalculator(CheckoutProperties p){properties=p;}public BigDecimal calculate(BigDecimal discountedMerchandise){return discountedMerchandise.multiply(properties.taxRatePercent()).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);}}
