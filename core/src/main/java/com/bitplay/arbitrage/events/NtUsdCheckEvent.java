package com.bitplay.arbitrage.events;

import org.springframework.context.ApplicationEvent;

public class NtUsdCheckEvent extends ApplicationEvent {

    @SuppressWarnings("ConstantConditions")
    public NtUsdCheckEvent() {
        super(new Object());
    }
}
