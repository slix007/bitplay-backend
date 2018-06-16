package com.bitplay.arbitrage.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class DeltaWma {

    private long sumNumerator = 0;
    private long sumWeight = 0;

    private long lastDeltaTime = 0;


    public void addDelta(BigDecimal deltaToAdd) {
        addDelta(deltaToAdd, Instant.now().toEpochMilli());
    }

    synchronized void addDelta(BigDecimal deltaToAdd, long now) {
        if (lastDeltaTime != 0) {
            long weight = now - lastDeltaTime;
            sumNumerator += weight * (deltaToAdd.doubleValue() * 100);
            sumWeight += weight;
        }
        lastDeltaTime = now;
    }

    public BigDecimal getTheVal() {
        return BigDecimal.valueOf(sumNumerator / sumWeight, 2);
    }

}
