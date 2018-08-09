package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DeltaName;
import com.bitplay.persistance.DeltaRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.DeltaParams;
import com.bitplay.persistance.domain.fluent.Dlt;
import com.bitplay.persistance.repository.DeltaParamsRepository;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Getter
@Service
public class DeltaMinService {

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private DeltaRepositoryService deltaRepositoryService;

    @Autowired
    private DeltaParamsRepository deltaParamsRepository;

    private Disposable deltaChangeSubscriber;

    private static final BigDecimal DEFAULT_VALUE = BigDecimal.valueOf(9999);
    private volatile BigDecimal lastBtmDelta = DEFAULT_VALUE;
    private volatile BigDecimal lastOkDelta = DEFAULT_VALUE;
    private volatile BigDecimal btmDeltaMinInstant = DEFAULT_VALUE;
    private volatile BigDecimal okDeltaMinInstant = DEFAULT_VALUE;
    private volatile BigDecimal btmDeltaMinFixed = DEFAULT_VALUE;
    private volatile BigDecimal okDeltaMinFixed = DEFAULT_VALUE;

    private volatile Instant lastFix;
    private volatile int lastRateInSec = 99999;
    private volatile long fixCounter = 0;

    private ScheduledFuture<?> scheduledFuture;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1,
            new ThreadFactoryBuilder().setNameFormat("delta-min-thread-%d").build());

    @PostConstruct
    private void init() {
        // fix deltaMin scheduler
        Integer deltaMinRateOfFixSec = persistenceService.fetchBorders().getDeltaMinFixPeriodSec();
        if (deltaMinRateOfFixSec != null) {
            restartScheduler(deltaMinRateOfFixSec);
        }
//        final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("deltas-calc-%d").build();
//        final ExecutorService executor = Executors.newSingleThreadExecutor(namedThreadFactory);

        // collect deltaMin listener
        deltaChangeSubscriber = deltaRepositoryService.getDltSaveObservable()
                .doOnError(e -> log.error("Error in dlt change listener", e))
                .observeOn(Schedulers.computation())
                .retry()
                .subscribe(this::dltChangeListener,
                        e -> log.error("Error in deltaMinSubscriber on delta change", e));

    }

    @PreDestroy
    private void preDestory() {
        scheduler.shutdown();
        deltaChangeSubscriber.dispose();
    }

    private void dltChangeListener(Dlt dlt) {
        try {
            if (dlt.getName() == DeltaName.B_DELTA) {
                lastBtmDelta = dlt.getDelta();
                if (btmDeltaMinInstant.compareTo(DEFAULT_VALUE) == 0
                        || dlt.getDelta().compareTo(btmDeltaMinInstant) < 0) {
                    btmDeltaMinInstant = dlt.getDelta();
                }
            } else {
                lastOkDelta = dlt.getDelta();
                if (okDeltaMinInstant.compareTo(DEFAULT_VALUE) == 0
                        || dlt.getDelta().compareTo(okDeltaMinInstant) < 0) {
                    okDeltaMinInstant = dlt.getDelta();
                }
            }
        } catch (Exception e) {
            log.error("ERROR in dltChangeListener", e);
        }
    }

    public void restartScheduler(int rateInSec) {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(false);
        }

        btmDeltaMinFixed = DEFAULT_VALUE;
        okDeltaMinFixed = DEFAULT_VALUE;

        if (rateInSec < lastRateInSec) {
            btmDeltaMinInstant = DEFAULT_VALUE;
            okDeltaMinInstant = DEFAULT_VALUE;
        }
        this.lastRateInSec = rateInSec;

        scheduledFuture = scheduler.scheduleAtFixedRate(this::fixDeltaMin,
                rateInSec, rateInSec, TimeUnit.SECONDS);
    }


    private long getNextFixDelay() {
        return scheduledFuture == null ? 0
                : scheduledFuture.getDelay(TimeUnit.SECONDS);
    }

    public String getTimerString() {
        long lastUpdate = this.lastFix == null
                ? 0
                : Instant.now().getEpochSecond() - this.lastFix.getEpochSecond();

        return String.format("delta_min updated %s sec ago. Next (%s) in %s sec",
                lastUpdate,
                fixCounter,
                getNextFixDelay());
    }

    private void fixDeltaMin() {
        lastFix = Instant.now();
        fixCounter++;

        BigDecimal btmDeltaMin = this.btmDeltaMinInstant;
        this.btmDeltaMinInstant = lastBtmDelta;
        BigDecimal okDeltaMin = this.okDeltaMinInstant;
        this.okDeltaMinInstant = lastOkDelta;

        this.btmDeltaMinFixed = btmDeltaMin;
        this.okDeltaMinFixed = okDeltaMin;
        storeDeltaMinParams(btmDeltaMin, okDeltaMin);
    }

    private synchronized void storeDeltaMinParams(BigDecimal btmDeltaMin, BigDecimal okDeltaMin) {
        DeltaParams deltaParams = fetchDeltaMinParams();
        if (btmDeltaMin.compareTo(DEFAULT_VALUE) != 0) {
            if (btmDeltaMin.compareTo(deltaParams.getBDeltaMax()) > 0) {
                deltaParams.setBDeltaMax(btmDeltaMin);
            }
            if (btmDeltaMin.compareTo(deltaParams.getBDeltaMin()) < 0) {
                deltaParams.setBDeltaMin(btmDeltaMin);
            }
        }
        if (okDeltaMin.compareTo(DEFAULT_VALUE) != 0) {
            if (okDeltaMin.compareTo(deltaParams.getODeltaMax()) > 0) {
                deltaParams.setODeltaMax(okDeltaMin);
            }
            if (okDeltaMin.compareTo(deltaParams.getODeltaMin()) < 0) {
                deltaParams.setODeltaMin(okDeltaMin);
            }
        }
        deltaParamsRepository.save(deltaParams);
    }

    public DeltaParams fetchDeltaMinParams() {
        return deltaParamsRepository.findFirstByDocumentId(2L);
    }

    public synchronized void resetDeltaMinParams() {
        DeltaParams deltaParams = fetchDeltaMinParams();
        deltaParams.setBDeltaMax(btmDeltaMinFixed);
        deltaParams.setBDeltaMin(btmDeltaMinFixed);
        deltaParams.setODeltaMax(okDeltaMinFixed);
        deltaParams.setODeltaMin(okDeltaMinFixed);
        deltaParamsRepository.save(deltaParams);
    }

    public BigDecimal getDeltaMinFixed(DeltaName deltaName) {
        return deltaName == DeltaName.B_DELTA ? btmDeltaMinFixed : okDeltaMinFixed;
    }

}
