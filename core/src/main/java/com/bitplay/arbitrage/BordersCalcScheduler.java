package com.bitplay.arbitrage;

import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.borders.BorderParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PostConstruct;

import io.reactivex.disposables.Disposable;

/**
 * Recalculate scheduler and executor of the table bordersV2.
 *
 * Created by Sergey Shurmin on 2/14/18.
 */
@Service
public class BordersCalcScheduler {

//    private static final Logger logger = LoggerFactory.getLogger(BordersService.class);
    //    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");
//    private static final Logger signalLogger = LoggerFactory.getLogger("SIGNAL_LOG");
//    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");
//    private static final Logger debugLog = LoggerFactory.getLogger("DEBUG_LOG");
//
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile ScheduledFuture<?> scheduledRecalc;

    private Instant lastRecalcTime;
//    private Disposable schdeduleUpdateBorders;

    @Autowired
    private BordersRecalcService bordersCalcService;

    @Autowired
    private PersistenceService persistenceService;

    private AtomicInteger updateBordersCounter = new AtomicInteger(0);

    public BordersCalcScheduler() {
        ((ScheduledThreadPoolExecutor) scheduler).setRemoveOnCancelPolicy(true);
    }

    @PostConstruct
    public void init() {
        scheduler.schedule(this::firstInit, 10, TimeUnit.SECONDS);
    }

    private void firstInit() {
        final BorderParams borderParams = persistenceService.fetchBorders();
        final Integer recalcPeriodSec = borderParams.getRecalcPeriodSec();
        resetTimerToRecalc(recalcPeriodSec);
    }

    public synchronized void resetTimerToRecalc(Integer recalcPeriodSec) {
        if (scheduledRecalc != null && !scheduledRecalc.isDone()) {
            scheduledRecalc.cancel(false);
        }
        scheduledRecalc = scheduler.scheduleWithFixedDelay(this::recalc,
                recalcPeriodSec, recalcPeriodSec, TimeUnit.SECONDS);
    }

    private void recalc() {
        updateBordersCounter.incrementAndGet();
        lastRecalcTime = Instant.now();

        bordersCalcService.recalc();
    }

    public String getUpdateBordersTimerString() {
        String lastUpdate = "()";
        if (lastRecalcTime != null) {
            lastUpdate = String.valueOf(Duration.between(lastRecalcTime, Instant.now())
                    .getSeconds());
        }
        final long nextInSec = scheduledRecalc == null ? -1L
                : scheduledRecalc.getDelay(TimeUnit.SECONDS);

        return String.format("Borders updated %s sec ago. Next (%s) in %s sec",
                lastUpdate,
                updateBordersCounter.get(),
                String.valueOf(nextInSec));
    }


}
