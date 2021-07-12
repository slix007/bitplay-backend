package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.ArbType;
import com.bitplay.arbitrage.events.ObChangeEvent;
import com.bitplay.arbitrage.events.SigEvent;
import com.bitplay.arbitrage.events.SigType;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ObChangeListener {

    @Autowired
    private ArbitrageService arbitrageService;

    @Async("signalCheckExecutor")
    @EventListener(ObChangeEvent.class)
    public void doCheckObChangeEvent(ObChangeEvent obChangeEvent) {
        final SigEvent sigEvent = obChangeEvent.getSigEvent();
        arbitrageService.sigEventCheck(sigEvent);
    }

    @Async("movingExecutorLeft")
    @EventListener(ObChangeEvent.class)
    public void movingCheckLeft(ObChangeEvent obChangeEvent) {
        if (!arbitrageService.isInitialized()) {
            return;
        }

        final SigEvent sigEvent = obChangeEvent.getSigEvent();
        final SigType sigType = sigEvent.getSigType();
        final ArbType arbType = sigEvent.getArbType();
        Instant startTime = sigEvent.startTime();
        if (arbType == ArbType.RIGHT) {
            return;
        }

        try {
            try {
                arbitrageService.getLeftMarketService().checkOpenOrdersForMoving(startTime);
            } catch (NotYetInitializedException e) {
                // do nothing
            } catch (Exception e) {
                log.error("{} openOrdersMovingSubscription error", sigType, e);
            }
        } catch (NotYetInitializedException e) {
            // do nothing
        } catch (Exception e) {
            log.error("{} openOrdersMovingSubscription error", sigType, e);
        }
    }

    @Async("movingExecutorRight")
    @EventListener(ObChangeEvent.class)
    public void movingCheckRight(ObChangeEvent obChangeEvent) {
        if (!arbitrageService.isInitialized()) {
            return;
        }

        final SigEvent sigEvent = obChangeEvent.getSigEvent();
        final SigType sigType = sigEvent.getSigType();
        final ArbType arbType = sigEvent.getArbType();
        Instant startTime = sigEvent.startTime();
        if (arbType == ArbType.LEFT) {
            return;
        }
        try {
            arbitrageService.getRightMarketService().checkOpenOrdersForMoving(startTime);
        } catch (NotYetInitializedException e) {
            // do nothing
        } catch (Exception e) {
            log.error("{} openOrdersMovingSubscription error", sigType, e);
        }
    }

}
