package com.bitplay.market.okcoin;

import com.bitplay.arbitrage.dto.ArbType;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class OOHangedCheckerService {

    private static final Logger logger = LoggerFactory.getLogger(OOHangedCheckerService.class);
    private final OkCoinService okCoinService;

    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("oo-hanged-checker-%d").build()
    );
    private volatile ScheduledFuture<?> future;
    private volatile int runCounter = 0;

    public void restartScheduler(ArbType arbType) {
        scheduler.shutdown();
        scheduler = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setNameFormat(arbType.s() + "_oo-hanged-checker-%d").build());
    }

    public String getStatus() {
        String statusString = "stopped";
        if (future != null && !future.isDone()) {
            statusString = String.format("<span style=\"color: green\">active(%s)<span>", runCounter);
        }
        return statusString;
    }

    void stopChecker() {
        try {
            if (future != null) { // with TAKER we don't even start it, but do Stop
                logger.info("stopChecker");
                future.cancel(false);
                runCounter = 0;
            }
        } catch (Exception e) {
            logger.error("stopChecker error ", e);
        }
    }

    void startChecker() {
        stopChecker();
        logger.info("startChecker");
        future = scheduler.scheduleWithFixedDelay(() -> {
            try {
                doRun();
            } catch (Exception e) {
                logger.error("Exception on openOrdersHangedChecker", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void doRun() {
        synchronized (this) {
            runCounter++;
        }
        okCoinService.openOrdersHangedChecker();
    }
}
