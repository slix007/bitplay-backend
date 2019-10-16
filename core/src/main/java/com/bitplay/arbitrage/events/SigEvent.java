package com.bitplay.arbitrage.events;

import com.bitplay.arbitrage.BordersService.TradingSignal;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.settings.TradingMode;
import lombok.Getter;
import lombok.Setter;
import org.knowm.xchange.dto.marketdata.OrderBook;

import java.time.Instant;

/**
 * Created by Sergey Shurmin on 6/6/17.
 */
@Getter
@Setter
public class SigEvent {

    private SigType sigType;
    private Instant startTime;

    // pre signal OB recheck
    private boolean preSignalReChecked = false;
    private DeltaName deltaName;
    private TradingMode tradingMode;
    private TradingSignal prevTradingSignal;
    private OrderBook btmOrderBook;
    private OrderBook okOrderBook;

    public SigEvent(SigType sigType, Instant startTime) {
        this.sigType = sigType;
        this.startTime = startTime;
    }

    public SigEvent(SigType sigType, Instant startTime, boolean preSignalReChecked, DeltaName deltaName, TradingMode tradingMode,
                    TradingSignal prevTradingSignal, OrderBook btmOrderBook, OrderBook okOrderBook) {
        this.sigType = sigType;
        this.startTime = startTime;
        this.preSignalReChecked = preSignalReChecked;
        this.deltaName = deltaName;
        this.tradingMode = tradingMode;
        this.prevTradingSignal = prevTradingSignal;
        this.btmOrderBook = btmOrderBook;
        this.okOrderBook = okOrderBook;
    }

    public Instant startTime() {
        return startTime;
    }
}
