package com.bitplay.arbitrage;

import com.bitplay.arbitrage.events.ObChangeEvent;
import com.bitplay.arbitrage.events.SigEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ArbitrageSignalCheckListener {

    @Autowired
    private ArbitrageService arbitrageService;

    @Async("signalCheckExecutor")
    @EventListener(ObChangeEvent.class)
    public void doCheckObChangeEvent(ObChangeEvent obChangeEvent) {
        final SigEvent sigEvent = obChangeEvent.getSigEvent();
        arbitrageService.sigEventCheck(sigEvent);
    }


}
