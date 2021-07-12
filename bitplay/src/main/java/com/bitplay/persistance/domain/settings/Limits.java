package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Transient;

/**
 * Created by Sergey Shurmin on 4/7/18.
 */
@Getter
@Setter
public class Limits {
    private Boolean ignoreLimits;
    private BigDecimal bitmexLimitPrice;
    private BigDecimal okexLimitPrice;
    @Transient
    private BigDecimal okexMaxPriceForTest;
    @Transient
    private BigDecimal okexMinPriceForTest;

    public static Limits createDefault() {
        final Limits limits = new Limits();
        limits.ignoreLimits = true;
        limits.bitmexLimitPrice = BigDecimal.ONE;
        return limits;
    }
}