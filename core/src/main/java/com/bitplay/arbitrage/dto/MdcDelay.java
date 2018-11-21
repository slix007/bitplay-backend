package com.bitplay.arbitrage.dto;

import java.time.Instant;
import java.util.Date;
import lombok.Data;

@Data
public class MdcDelay {

    private volatile Date firstSignalMainSet;
    private volatile Date firstSignalExtraSet;

    public static boolean isMdcReadyByTime(Date firstSignalTime, int delaySec) {
        final long activateMs = firstSignalTime.toInstant().toEpochMilli();
        return isMdcReadyByTime(activateMs, delaySec);
    }

    public static boolean isMdcReadyByTime(long activateMs, int delaySec) {
        final long nowMs = Instant.now().toEpochMilli();
        return nowMs - activateMs > delaySec * 1000;
    }
}
