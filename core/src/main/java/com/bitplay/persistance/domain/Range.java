package com.bitplay.persistance.domain;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 10/31/17.
 */
public class Range {

    private BigDecimal min;
    private BigDecimal max;

    private Range(BigDecimal min, BigDecimal max) {
        this.min = min;
        this.max = max;
    }

    public Range() {
    }

    public static Range empty() {
        return new Range(BigDecimal.valueOf(10000), BigDecimal.valueOf(-10000));
    }

    public BigDecimal getMin() {
        return min;
    }

    public void setMin(BigDecimal min) {
        this.min = min;
    }

    public BigDecimal getMax() {
        return max;
    }

    public void setMax(BigDecimal max) {
        this.max = max;
    }
}
