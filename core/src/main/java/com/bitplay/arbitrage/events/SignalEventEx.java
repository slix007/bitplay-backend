package com.bitplay.arbitrage.events;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 6/6/17.
 */
@Getter
@Setter
@AllArgsConstructor
public class SignalEventEx implements EventQuant {

    private SignalEvent signalEvent;
    private Instant startTime;

    @Override
    public Instant startTime() {
        return startTime;
    }
}
