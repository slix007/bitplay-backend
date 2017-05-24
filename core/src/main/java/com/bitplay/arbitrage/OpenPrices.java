package com.bitplay.arbitrage;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 5/24/17.
 */
public class OpenPrices {
    BigDecimal firstOpenPrice;
    BigDecimal secondOpenPrice;

    public BigDecimal getFirstOpenPrice() {
        return firstOpenPrice;
    }

    public void setFirstOpenPrice(BigDecimal firstOpenPrice) {
        this.firstOpenPrice = firstOpenPrice;
    }

    public BigDecimal getSecondOpenPrice() {
        return secondOpenPrice;
    }

    public void setSecondOpenPrice(BigDecimal secondOpenPrice) {
        this.secondOpenPrice = secondOpenPrice;
    }
}
