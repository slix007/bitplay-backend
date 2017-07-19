package com.bitplay.market.dto;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 7/17/17.
 */
public class LiqInfo {

    private BigDecimal dqlCurr = BigDecimal.ZERO;
    private BigDecimal dqlMin = BigDecimal.valueOf(10000);
    private BigDecimal dqlMax = BigDecimal.valueOf(10000).negate();
    private BigDecimal dmrlCurr = BigDecimal.ZERO;
    private BigDecimal dmrlMin = BigDecimal.valueOf(10000);
    private BigDecimal dmrlMax = BigDecimal.valueOf(10000).negate();

    private String dqlString; // Diff Quote Liq.
    private String dmrlString;// Diff Margin Rate Liq.

    public LiqInfo(String dqlString, String dmrlString) {
        this.dqlString = dqlString;
        this.dmrlString = dmrlString;
    }

    public BigDecimal getDqlMin() {
        return dqlMin;
    }

    public void setDqlMin(BigDecimal dqlMin) {
        this.dqlMin = dqlMin;
    }

    public BigDecimal getDqlMax() {
        return dqlMax;
    }

    public void setDqlMax(BigDecimal dqlMax) {
        this.dqlMax = dqlMax;
    }

    public BigDecimal getDmrlMin() {
        return dmrlMin;
    }

    public void setDmrlMin(BigDecimal dmrlMin) {
        this.dmrlMin = dmrlMin;
    }

    public BigDecimal getDmrlMax() {
        return dmrlMax;
    }

    public void setDmrlMax(BigDecimal dmrlMax) {
        this.dmrlMax = dmrlMax;
    }

    public String getDqlString() {
        return dqlString;
    }

    public void setDqlString(String dqlString) {
        this.dqlString = dqlString;
    }

    public String getDmrlString() {
        return dmrlString;
    }

    public void setDmrlString(String dmrlString) {
        this.dmrlString = dmrlString;
    }

    public BigDecimal getDqlCurr() {
        return dqlCurr;
    }

    public void setDqlCurr(BigDecimal dqlCurr) {
        this.dqlCurr = dqlCurr;
    }

    public BigDecimal getDmrlCurr() {
        return dmrlCurr;
    }

    public void setDmrlCurr(BigDecimal dmrlCurr) {
        this.dmrlCurr = dmrlCurr;
    }
}
