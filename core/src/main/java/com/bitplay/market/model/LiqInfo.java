package com.bitplay.market.model;

import com.bitplay.persistance.domain.LiqParams;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 7/17/17.
 */
public class LiqInfo {

    private LiqParams liqParams;

    private BigDecimal dqlCurr;
    private BigDecimal dmrlCurr;
    private String dqlString; // Diff Quote Liq.
    private String dmrlString;// Diff Margin Rate Liq.

    public LiqParams getLiqParams() {
        return liqParams;
    }

    public void setLiqParams(LiqParams liqParams) {
        this.liqParams = liqParams;
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
}
