package com.bitplay.arbitrage;

import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.borders.BorderParams;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Recalculate scheduler and executor of the table bordersV2.
 *
 * Created by Sergey Shurmin on 2/14/18.
 */
@Service
public class BordersCalcScheduler {

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
        boolean isRecalcEveryNewDelta = borderParams.getBorderDelta().getDeltaCalcType().isEveryNewDelta();
        resetTimerToRecalc(recalcPeriodSec, isRecalcEveryNewDelta);
    }

    public synchronized void resetTimerToRecalc(Integer recalcPeriodSec, boolean isRecalcEveryNewDelta) {
        if (scheduledRecalc != null && !scheduledRecalc.isDone()) {
            scheduledRecalc.cancel(false);
        }
        if (!isRecalcEveryNewDelta && recalcPeriodSec > 0) {
            scheduledRecalc = scheduler.scheduleWithFixedDelay(this::recalc,
                    recalcPeriodSec, recalcPeriodSec, TimeUnit.SECONDS);
        }
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
        long nextInSec = (scheduledRecalc == null || scheduledRecalc.getDelay(TimeUnit.SECONDS) < 0)
                ? -1L
                : scheduledRecalc.getDelay(TimeUnit.SECONDS);

        if (bordersCalcService.isRecalcEveryNewDelta()) {
            return "Borders update every new delta";
        }
        return String.format("Borders updated %s sec ago. Next (%s) in %s sec",
                lastUpdate,
                updateBordersCounter.get(),
                String.valueOf(nextInSec));
    }


}
