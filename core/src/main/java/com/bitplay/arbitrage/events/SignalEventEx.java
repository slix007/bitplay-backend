package com.bitplay.arbitrage.events;

import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.settings.TradingMode;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 6/6/17.
 */
@Getter
@Setter
public class SignalEventEx implements EventQuant {

    private SignalEvent signalEvent;
    private Instant startTime;
    private boolean preSignalReChecked = false;
    private DeltaName deltaName;
    private TradingMode tradingMode;

    public SignalEventEx(SignalEvent signalEvent, Instant startTime) {
        this.signalEvent = signalEvent;
        this.startTime = startTime;
    }

    public SignalEventEx(SignalEvent signalEvent, Instant startTime, boolean preSignalReChecked, DeltaName deltaName, TradingMode tradingMode) {
        this.signalEvent = signalEvent;
        this.startTime = startTime;
        this.preSignalReChecked = preSignalReChecked;
        this.deltaName = deltaName;
        this.tradingMode = tradingMode;
    }

    @Override
    public Instant startTime() {
        return startTime;
    }
}
