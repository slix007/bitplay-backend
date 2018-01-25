package com.bitplay.arbitrage;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 5/24/17.
 */
public class OpenPrices {
    BigDecimal firstOpenPrice = BigDecimal.ZERO;
    BigDecimal secondOpenPrice = BigDecimal.ZERO;
    BigDecimal border = BigDecimal.ZERO;

    public BigDecimal getFirstOpenPrice() {
        return firstOpenPrice;
    }

    public void setFirstOpenPrice(BigDecimal firstOpenPrice) {
        this.firstOpenPrice = firstOpenPrice.setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    public BigDecimal getSecondOpenPrice() {
        return secondOpenPrice;
    }

    public void setSecondOpenPrice(BigDecimal secondOpenPrice) {
        this.secondOpenPrice = secondOpenPrice.setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    public BigDecimal getDelta1Fact() {
        return (firstOpenPrice != null && secondOpenPrice != null)
                ? firstOpenPrice.subtract(secondOpenPrice).setScale(2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
    }

    public BigDecimal getDelta2Fact() {
        return (firstOpenPrice != null && secondOpenPrice != null)
                ? secondOpenPrice.subtract(firstOpenPrice).setScale(2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
    }

    public BigDecimal getBorder() {
        return border;
    }

    public void setBorder(BigDecimal border) {
        this.border = border;
    }
}
