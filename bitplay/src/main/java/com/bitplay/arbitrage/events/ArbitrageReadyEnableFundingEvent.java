package com.bitplay.arbitrage.events;

import org.springframework.context.ApplicationEvent;

public class ArbitrageReadyEnableFundingEvent extends ApplicationEvent {

    public ArbitrageReadyEnableFundingEvent() {
        super(new Object());
    }
}
