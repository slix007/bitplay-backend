package com.bitplay.api.dto.ob;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Created by Sergey Shurmin on 4/7/18.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LimitsJson {
    private BigDecimal limitPrice;
    private BigDecimal limitAsk;
    private BigDecimal limitBid;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Boolean insideLimits;
    private InsideLimitsEx insideLimitsEx;
    private Boolean ignoreLimits;
    private BigDecimal minPriceForTest;
    private BigDecimal maxPriceForTest;

    public LimitsJson(BigDecimal limitPrice, BigDecimal limitAsk, BigDecimal limitBid, BigDecimal minPrice, BigDecimal maxPrice, Boolean insideLimits,
            InsideLimitsEx insideLimitsEx, Boolean ignoreLimits) {
        this.limitPrice = limitPrice;
        this.limitAsk = limitAsk;
        this.limitBid = limitBid;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.insideLimits = insideLimits;
        this.insideLimitsEx = insideLimitsEx;
        this.ignoreLimits = ignoreLimits;
    }


}
