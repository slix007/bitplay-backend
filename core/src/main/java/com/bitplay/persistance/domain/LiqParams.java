package com.bitplay.persistance.domain;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection="liqParamsCollection")
@TypeAlias("liqParams")
public class LiqParams extends MarketDocument {
    private BigDecimal dqlMin;
    private BigDecimal dqlMax;
    private BigDecimal dmrlMin;
    private BigDecimal dmrlMax;

    public LiqParams(BigDecimal dqlMin, BigDecimal dqlMax, BigDecimal dmrlMin, BigDecimal dmrlMax) {
        this.dqlMin = dqlMin;
        this.dqlMax = dqlMax;
        this.dmrlMin = dmrlMin;
        this.dmrlMax = dmrlMax;
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
}
