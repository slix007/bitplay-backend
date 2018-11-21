package com.bitplay.arbitrage.dto;

import java.time.Instant;
import java.util.Date;
import lombok.Data;

@Data
public class MdcDelay {

    private volatile Date firstSignalMainSet;
    private volatile Date firstSignalExtraSet;

    public static boolean isMdcReadyByTime(Date firstSignalTime, int delaySec) {
        final long startedMs = firstSignalTime.toInstant().toEpochMilli();
        final long nowMs = Instant.now().toEpochMilli();

        return nowMs - startedMs > delaySec * 1000;
    }
}
