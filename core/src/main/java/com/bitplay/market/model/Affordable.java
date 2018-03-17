package com.bitplay.market.model;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 2/25/18.
 */
public class Affordable {
    private volatile BigDecimal forShort = BigDecimal.ZERO;
    private volatile BigDecimal forLong = BigDecimal.ZERO;

    public BigDecimal getForShort() {
        return forShort;
    }

    public void setForShort(BigDecimal forShort) {
        this.forShort = forShort;
    }

    public BigDecimal getForLong() {
        return forLong;
    }

    public void setForLong(BigDecimal forLong) {
        this.forLong = forLong;
    }

    @Override
    public String toString() {
        return "Affordable{" +
                "lg=" + forLong +
                ", st=" + forShort +
                '}';
    }
}
