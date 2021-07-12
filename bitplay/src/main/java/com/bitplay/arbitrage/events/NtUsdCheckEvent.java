package com.bitplay.arbitrage.events;

import org.springframework.context.ApplicationEvent;

public class NtUsdCheckEvent extends ApplicationEvent {

    public NtUsdCheckEvent() {
        super(new Object());
    }
}
