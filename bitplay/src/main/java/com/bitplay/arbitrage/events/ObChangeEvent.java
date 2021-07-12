package com.bitplay.arbitrage.events;

import org.springframework.context.ApplicationEvent;

public class ObChangeEvent extends ApplicationEvent {
    public ObChangeEvent(SigEvent source) {
        super(source);
    }

    public SigEvent getSigEvent() {
        return (SigEvent) getSource();
    }
}
