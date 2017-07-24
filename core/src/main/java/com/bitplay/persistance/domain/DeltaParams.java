package com.bitplay.persistance.domain;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection = "deltaParamsCollection")
@TypeAlias("deltaParams")
public class DeltaParams extends AbstractDocument {
    private BigDecimal bDeltaMin = BigDecimal.valueOf(-10000);
    private BigDecimal oDeltaMin = BigDecimal.valueOf(-10000);
    private BigDecimal bDeltaMax = BigDecimal.valueOf(10000);
    private BigDecimal oDeltaMax = BigDecimal.valueOf(10000);

    public DeltaParams() {
    }

    public BigDecimal getbDeltaMin() {
        return bDeltaMin;
    }

    public void setbDeltaMin(BigDecimal bDeltaMin) {
        this.bDeltaMin = bDeltaMin;
    }

    public BigDecimal getoDeltaMin() {
        return oDeltaMin;
    }

    public void setoDeltaMin(BigDecimal oDeltaMin) {
        this.oDeltaMin = oDeltaMin;
    }

    public BigDecimal getbDeltaMax() {
        return bDeltaMax;
    }

    public void setbDeltaMax(BigDecimal bDeltaMax) {
        this.bDeltaMax = bDeltaMax;
    }

    public BigDecimal getoDeltaMax() {
        return oDeltaMax;
    }

    public void setoDeltaMax(BigDecimal oDeltaMax) {
        this.oDeltaMax = oDeltaMax;
    }
}
