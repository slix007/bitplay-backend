package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.ArbType;
import com.bitplay.arbitrage.events.ObChangeEvent;
import com.bitplay.arbitrage.events.SigEvent;
import com.bitplay.arbitrage.events.SigType;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.market.MarketServicePreliq;
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

    @Async("movingExecutor")
    @EventListener(ObChangeEvent.class)
    public void movingCheck(ObChangeEvent obChangeEvent) {
        if (!arbitrageService.isInitialized()) {
            return;
        }

        final SigEvent sigEvent = obChangeEvent.getSigEvent();
        final SigType sigType = sigEvent.getSigType();
        final ArbType arbType = sigEvent.getArbType();
        try {
            final MarketServicePreliq market = arbType == ArbType.LEFT
                    ? arbitrageService.getLeftMarketService()
                    : arbitrageService.getRightMarketService();

            market.checkOpenOrdersForMoving(sigEvent.startTime());
        } catch (NotYetInitializedException e) {
            // do nothing
        } catch (Exception e) {
            log.error("{} openOrdersMovingSubscription error", sigType, e);
        }
    }


}
