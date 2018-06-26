package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DeltaName;
import com.bitplay.persistance.DeltaRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.borders.BorderDelta;
import com.bitplay.persistance.domain.fluent.Dlt;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Getter
public class DeltasCalcService {

    private static final Logger logger = LoggerFactory.getLogger(DeltasCalcService.class);
    //    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");
//    private static final Logger signalLogger = LoggerFactory.getLogger("SIGNAL_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private PersistenceService persistenceService;
    @Autowired
    private DeltaRepositoryService deltaRepositoryService;
    @Autowired
    private ArbitrageService arbitrageService;
    @Autowired
    private BordersRecalcService bordersRecalcService;
    @Autowired
    private AvgDeltaAtOnce avgDeltaAtOnce;
    @Autowired
    private AvgDeltaInParts avgDeltaInParts;

    /**
     * local copy of the settings
     */
    private BorderDelta borderDelta;

    private Instant begin_delta_hist_per = Instant.now();

    private volatile boolean started;

    private Disposable deltaChangeSubscriber;

    public void initDeltasCache(BorderDelta borderDelta) {
        resetDeltasCache(borderDelta, true);

        final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("deltas-calc-%d").build();
        final ExecutorService executor = Executors.newSingleThreadExecutor(namedThreadFactory);

        deltaChangeSubscriber = deltaRepositoryService.getDltSaveObservable()
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.from(executor))
                .subscribe(this::dltChangeListener);
    }

    private void dltChangeListener(Dlt dlt) { //TODO move into borderRecalcService
        long startMs = Instant.now().toEpochMilli();

        // 1. summirize only while _sma_init();
        avgDeltaInParts.newDeltaEvent(dlt, begin_delta_hist_per);

        // 2. check starting border recalc after each delta
        bordersRecalcService.newDeltaAdded();

        long endMs = Instant.now().toEpochMilli();
        arbitrageService.getDeltaMon().setDeltaMs(endMs - startMs);

        arbitrageService.getDeltaMon().setBorderMs(endMs - startMs);

        arbitrageService.getDeltaMon().setItmes(
                (long) avgDeltaInParts.getB_delta_sma_map().size(),
                (long) avgDeltaInParts.getO_delta_sma_map().size());

//        logger.info("CalcEnded. " + arbitrageService.getDeltaMon());
    }

    public void resetDeltasCache(BorderDelta borderDelta, boolean clearData) {
        this.borderDelta = borderDelta; //sets new settings of 'delta_hist_per'
        Integer delta_hist_per = borderDelta.getDeltaCalcPast();

        if (clearData) {
            begin_delta_hist_per = Instant.now();
            avgDeltaInParts.resetDeltasCache(delta_hist_per, clearData);
        }

        if (!started) {
            started = true;
        }
    }

    public String getDeltaHistPerStartedSec() {
        Instant now = Instant.now();
        long pastSeconds = Duration.between(begin_delta_hist_per, now).getSeconds();
        return String.valueOf(pastSeconds);
    }

    public String getDeltaSmaUpdateIn(Integer delta_hist_per) {
        Instant now = Instant.now();
        long pastSeconds = Duration.between(begin_delta_hist_per, now).getSeconds();
        long toStart = delta_hist_per - pastSeconds;
        return String.valueOf(toStart > 0 ? toStart : 0);
    }

    BigDecimal calcDelta(DeltaName deltaName, BigDecimal instantDelta) {
        if (borderDelta == null) {
            borderDelta = persistenceService.fetchBorders().getBorderDelta();
        }

        Instant currTime = Instant.now();
        switch (borderDelta.getDeltaCalcType()) {
            case DELTA:
                return instantDelta;
            case AVG_DELTA: // calc every call
                return avgDeltaAtOnce.calcAvgDelta(deltaName, instantDelta, currTime, borderDelta, begin_delta_hist_per);
            case AVG_DELTA_EVERY_BORDER_COMP_AT_ONCE:
            case AVG_DELTA_EVERY_NEW_DELTA_AT_ONCE:
                return calcIfDeltaHistPeriodIsDone(avgDeltaAtOnce, deltaName, instantDelta, currTime, borderDelta);
            case AVG_DELTA_EVERY_BORDER_COMP_IN_PARTS:
            case AVG_DELTA_EVERY_NEW_DELTA_IN_PARTS:
                return calcIfDeltaHistPeriodIsDone(avgDeltaInParts, deltaName, instantDelta, currTime, borderDelta);
        }
        throw new IllegalArgumentException("Unhandled deltaCalcType " + borderDelta.getDeltaCalcType());
    }

    private BigDecimal calcIfDeltaHistPeriodIsDone(AvgDelta avgDeltaService, DeltaName deltaName, BigDecimal instantDelta, Instant currTime,
            BorderDelta borderDelta) {
        if (!avgDeltaService.isReadyForCalc(currTime, begin_delta_hist_per, borderDelta.getDeltaCalcPast())) {
            return null;
        }
        return avgDeltaService.calcAvgDelta(deltaName, instantDelta, currTime, borderDelta, begin_delta_hist_per);
    }

    public void setBorderDelta(BorderDelta borderDelta) {
        this.borderDelta = borderDelta;
    }

    public BigDecimal getBDeltaEveryCalc() {
        return avgDeltaInParts.getCurrSmaBtmDelta();
    }

    public BigDecimal getODeltaEveryCalc() {
        return avgDeltaInParts.getCurrSmaOkDelta();
    }


    public BigDecimal getBDeltaSma() {
        return avgDeltaInParts.getB_delta_sma().getSecond();
    }

    public BigDecimal getODeltaSma() {
        return avgDeltaInParts.getO_delta_sma().getSecond();
    }

    public Map<Instant, BigDecimal> getCurrBtmDeltasInCalc() {
        return avgDeltaInParts.getB_delta_sma_map();
    }

    public Map<Instant, BigDecimal> getCurrOkDeltasInCalc() {
        return avgDeltaInParts.getO_delta_sma_map();
    }
}
