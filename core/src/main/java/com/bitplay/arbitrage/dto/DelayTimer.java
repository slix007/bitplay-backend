package com.bitplay.arbitrage.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class DelayTimer {

    private Instant firstStart;

    public long secToReady(int delaySec) {
        final long activateMs = firstStart.toEpochMilli();
        final long nowMs = Instant.now().toEpochMilli();
        return -(nowMs - activateMs - delaySec * 1000) / 1000;
    }

    public boolean isReadyByTime(int delaySec) {
        final long activateMs = firstStart.toEpochMilli();
        return isReadyByTime(activateMs, delaySec);
    }

    public static boolean isReadyByTime(long activateMs, int delaySec) {
        final long nowMs = Instant.now().toEpochMilli();
        return nowMs - activateMs > delaySec * 1000;
    }


    public void activate() {
        if (firstStart == null) {
            firstStart = Instant.now();
        }
    }

    public void stop() {
        firstStart = null;
    }

}
