package com.bitplay.arbitrage.events;

import java.time.Instant;

/**
 * Created by Sergey Shurmin on 6/6/17.
 */
public enum SignalEvent implements EventQuant {
    O_ORDERBOOK_CHANGED,
    B_ORDERBOOK_CHANGED,
    MT2_BITMEX_ORDER_FILLED,
    ;

    @Override
    public Instant startTime() {
        return null;
    }
}
