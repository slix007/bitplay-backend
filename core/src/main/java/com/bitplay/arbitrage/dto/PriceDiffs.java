package com.bitplay.arbitrage.dto;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 2/5/18.
 */
public class PriceDiffs {

    private volatile BigDecimal firstDiff = BigDecimal.ZERO;
    private volatile BigDecimal secondDiff = BigDecimal.ZERO;

    public synchronized BigDecimal getFirstDiff() {
        return firstDiff;
    }

    public synchronized void setFirstDiff(BigDecimal firstDiff) {
        this.firstDiff = firstDiff;
    }

    public synchronized BigDecimal getSecondDiff() {
        return secondDiff;
    }

    public synchronized void setSecondDiff(BigDecimal secondDiff) {
        this.secondDiff = secondDiff;
    }
}
