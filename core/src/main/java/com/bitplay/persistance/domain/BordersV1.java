package com.bitplay.persistance.domain;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 10/9/17.
 */
public class BordersV1 {

    private BigDecimal sumDelta = new BigDecimal(20);
    private Integer periodSec = 3600;

    public BigDecimal getSumDelta() {
        return sumDelta;
    }

    public void setSumDelta(BigDecimal sumDelta) {
        this.sumDelta = sumDelta;
    }

    public Integer getPeriodSec() {
        return periodSec;
    }

    public void setPeriodSec(Integer periodSec) {
        this.periodSec = periodSec;
    }
}
