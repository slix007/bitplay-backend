package com.bitplay.arbitrage.dto;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class ThrottledWarn {

    private Instant warnTime = null;

    public boolean isReadyToSend() {
        final boolean isReadyToSend = warnTime == null || warnTime.plus(1, ChronoUnit.MINUTES).isBefore(Instant.now());
        if (isReadyToSend) {
            warnTime = Instant.now();
        }
        return isReadyToSend;
    }

    public void reset() {
        warnTime = null;
    }

}
