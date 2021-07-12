package com.bitplay.arbitrage.events;

import org.springframework.context.ApplicationEvent;

public class ArbitrageReadyEvent extends ApplicationEvent {

    public ArbitrageReadyEvent() {
        super(new Object());
    }
}
