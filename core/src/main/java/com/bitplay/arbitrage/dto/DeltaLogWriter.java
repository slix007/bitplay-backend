package com.bitplay.arbitrage.dto;

import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.fluent.TradeMStatus;
import com.bitplay.persistance.domain.fluent.TradeStatus;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class DeltaLogWriter {

    private final Long tradeId;
    private final String counterName;
    private final TradeService tradeService;

    public void info(String msg) {
        tradeService.info(tradeId, counterName, msg);
    }

    public void warn(String msg) {
        tradeService.warn(tradeId, counterName, msg);
    }

    public void error(String msg) {
        tradeService.error(tradeId, counterName, msg);
    }

    public void setEndStatus(TradeStatus tradeStatus) {
        tradeService.setEndStatus(tradeId, tradeStatus);
    }

    public void setBitmexStatus(TradeMStatus tradeStatus) {
        tradeService.setBitmexStatus(tradeId, tradeStatus);
    }

    public void setOkexStatus(TradeMStatus tradeStatus) {
        tradeService.setOkexStatus(tradeId, tradeStatus);
    }
}
