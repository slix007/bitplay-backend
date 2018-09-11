package com.bitplay.arbitrage.dto;

import com.bitplay.persistance.DeltaLogService;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class DeltaLogWriter {

    private final Long tradeId;
    private final String counterName;
    private final DeltaLogService deltaLogService;

    public void info(String msg) {
        deltaLogService.info(tradeId, counterName, msg);
    }

    public void warn(String msg) {
        deltaLogService.warn(tradeId, counterName, msg);
    }

    public void error(String msg) {
        deltaLogService.error(tradeId, counterName, msg);
    }
}
