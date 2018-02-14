package com.bitplay.persistance.domain;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 10/9/17.
 */
public class BordersV1 {

    private BigDecimal sumDelta = new BigDecimal(20);

    public BigDecimal getSumDelta() {
        return sumDelta;
    }

    public void setSumDelta(BigDecimal sumDelta) {
        this.sumDelta = sumDelta;
    }
}
