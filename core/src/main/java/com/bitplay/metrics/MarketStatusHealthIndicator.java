package com.bitplay.metrics;

import com.bitplay.arbitrage.ArbitrageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

@Component
public class MarketStatusHealthIndicator extends AbstractHealthIndicator {

    @Autowired
    private ArbitrageService arbitrageService;

    @Override
    protected void doHealthCheck(Builder builder) {
        final boolean bitmexUp = arbitrageService.getFirstMarketService() != null &&
                arbitrageService.getFirstMarketService().isStarted();
        final boolean okexUp = arbitrageService.getSecondMarketService() != null &&
                arbitrageService.getSecondMarketService().isStarted();

        builder.withDetail("bitmexService", bitmexUp ? Status.UP : Status.DOWN);
        builder.withDetail("okexService", okexUp ? Status.UP : Status.DOWN);
        builder.status(bitmexUp && okexUp ? Status.UP : Status.OUT_OF_SERVICE);
    }

}
