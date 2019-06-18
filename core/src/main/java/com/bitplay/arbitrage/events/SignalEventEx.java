package com.bitplay.arbitrage.events;

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
    private boolean orderBookReFetched = false;

    public SignalEventEx(SignalEvent signalEvent, Instant startTime) {
        this.signalEvent = signalEvent;
        this.startTime = startTime;
    }

    public SignalEventEx(SignalEvent signalEvent, Instant startTime, boolean orderBookReFetched) {
        this.signalEvent = signalEvent;
        this.startTime = startTime;
        this.orderBookReFetched = orderBookReFetched;
    }

    @Override
    public Instant startTime() {
        return startTime;
    }
}
