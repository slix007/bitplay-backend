package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 4/7/18.
 */
@Getter
@Setter
public class Limits {
    private Boolean ignoreLimits;
    private BigDecimal bitmexLimitPrice;
    private BigDecimal okexLimitPrice;

    public static Limits createDefault() {
        final Limits limits = new Limits();
        limits.ignoreLimits = true;
        limits.bitmexLimitPrice = BigDecimal.ONE;
        limits.okexLimitPrice = BigDecimal.ONE;
        return limits;
    }
}