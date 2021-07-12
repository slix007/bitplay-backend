package com.bitplay.arbitrage;

import com.bitplay.persistance.DeltaRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.borders.BorderDelta;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.fluent.Dlt;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
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
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    public static final BigDecimal NONE_VALUE = BigDecimal.valueOf(99999);
    private volatile BigDecimal b_delta_sma = NONE_VALUE;
    private volatile BigDecimal o_delta_sma = NONE_VALUE;

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
    @Autowired
    private DeltaMinService deltaMinService;

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
                .observeOn(Schedulers.from(executor))
                .subscribeOn(Schedulers.from(executor))
                .doOnError(e -> logger.error("Error in observer on delta change", e))
                .retry()
                .subscribe(this::dltChangeListener,
                        e -> logger.error("Error in subscriber on delta change", e));
    }

    private void dltChangeListener(Dlt dlt) { //TODO move into borderRecalcService
        if (borderDelta != null && borderDelta.getDeltaSmaCalcOn() != null && !borderDelta.getDeltaSmaCalcOn()) {
            return;
        }

        try {
            long startMs = System.nanoTime();

            // 1. summirize only while _sma_init();
            avgDeltaInParts.newDeltaEvent(dlt, begin_delta_hist_per);

            // 2. check starting border recalc after each delta
            bordersRecalcService.newDeltaAdded();

            long endMs = System.nanoTime();
            arbitrageService.getDeltaMon().setAddNewDeltaMs((endMs - startMs) / 1000);

            arbitrageService.getDeltaMon().setItmes(
                    (long) avgDeltaInParts.getB_delta_sma_map().size(),
                    (long) avgDeltaInParts.getO_delta_sma_map().size());
        } catch (Exception e) {
            logger.error("ERROR in dltChangeListener", e);
        }
    }

    public void resetDeltasCache(BorderDelta borderDelta, boolean clearData) {
        this.borderDelta = borderDelta; //sets new settings of 'delta_hist_per'

        if (clearData) {
            begin_delta_hist_per = Instant.now();
            avgDeltaInParts.resetDeltasCache();
        }

        if (!started) {
            started = true;
        }
    }

    public boolean isDataResetNeeded(Integer histPerUpdate) {
        Instant now = Instant.now();
        long pastSeconds = Duration.between(begin_delta_hist_per, now).getSeconds();

        boolean shouldClearData = pastSeconds >= histPerUpdate;

        return shouldClearData;
    }

    public String getDeltaHistPerStartedSec() {
        if (borderDelta != null && borderDelta.getDeltaSmaCalcOn() != null && !borderDelta.getDeltaSmaCalcOn()) {
            return "";
        }

        Instant now = Instant.now();
        long pastSeconds = Duration.between(begin_delta_hist_per, now).getSeconds();
        return String.valueOf(pastSeconds);
    }

    public String getDeltaSmaUpdateIn(Integer delta_hist_per) {
        if (borderDelta != null && borderDelta.getDeltaSmaCalcOn() != null && !borderDelta.getDeltaSmaCalcOn()) {
            return "";
        }

        Instant now = Instant.now();
        long pastSeconds = Duration.between(begin_delta_hist_per, now).getSeconds();
        long toStart = delta_hist_per - pastSeconds;
        return String.valueOf(toStart > 0 ? toStart : 0);
    }

    BigDecimal calcDelta(DeltaName deltaName, BigDecimal instantDelta) {
        if (borderDelta == null) {
            borderDelta = persistenceService.fetchBorders().getBorderDelta();
        }

        BigDecimal theDelta = getDelta(deltaName, instantDelta);

        if (borderDelta.getDeltaCalcType().isSMA()) {
            if (theDelta == null || theDelta.equals(NONE_VALUE) ) {
                // Not initialized (or Error?) -> Do Nothing!
                b_delta_sma = NONE_VALUE;
                b_delta_sma = NONE_VALUE;
                theDelta = NONE_VALUE;
            }
            if (deltaName == DeltaName.B_DELTA) {
                b_delta_sma = theDelta;
            } else {
                o_delta_sma = theDelta;
            }
        }

        return theDelta;
    }

    private BigDecimal getDelta(DeltaName deltaName, BigDecimal instantDelta) {
        Instant currTime = Instant.now();
        switch (borderDelta.getDeltaCalcType()) {
            case DELTA:
                return instantDelta;
            case AVG_DELTA: // calc every call
                return avgDeltaAtOnce.calcAvgDelta(deltaName, instantDelta, currTime, borderDelta, begin_delta_hist_per);
            case DELTA_MIN:
                return deltaMinService.getDeltaMinFixed(deltaName);
            case AVG_DELTA_EVERY_BORDER_COMP_AT_ONCE:
            case AVG_DELTA_EVERY_NEW_DELTA_AT_ONCE:
                return calcIfDeltaHistPeriodIsDone(avgDeltaAtOnce, deltaName, instantDelta, currTime, borderDelta);
            case AVG_DELTA_EVERY_BORDER_COMP_IN_PARTS:
            case AVG_DELTA_EVERY_NEW_DELTA_IN_PARTS:
                if (borderDelta.getDeltaSmaCalcOn() != null && !borderDelta.getDeltaSmaCalcOn()) {
                    return NONE_VALUE;
                }
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

    public String getBDeltaEveryCalc() {
        final int scale = arbitrageService.getToolsScale();
        return String.format("<span style=\"color: %s;\">%s / %s = %s</span>",
               avgDeltaInParts.isHasErrorsBtm() ? "red" : "black",
               BigDecimal.valueOf(avgDeltaInParts.getNum_sma_btm(), scale).toPlainString(),
               avgDeltaInParts.getDen_sma_btm(),
               avgDeltaInParts.getCurrSmaBtmDelta());
    }

    public String getODeltaEveryCalc() {
        final int scale = arbitrageService.getToolsScale();
        return String.format("<span style=\"color: %s;\">%s / %s = %s</span>",
                avgDeltaInParts.isHasErrorsOk() ? "red" : "black",
                BigDecimal.valueOf(avgDeltaInParts.getNum_sma_ok(), scale).toPlainString(),
                avgDeltaInParts.getDen_sma_ok(),
                avgDeltaInParts.getCurrSmaOkDelta());
    }

    public Map<Instant, BigDecimal> getCurrBtmDeltasInCalc() {
        return new TreeMap<>(avgDeltaInParts.getB_delta_sma_map());
    }

    public Map<Instant, BigDecimal> getCurrOkDeltasInCalc() {
        return new TreeMap<>(avgDeltaInParts.getO_delta_sma_map());
    }

    public Map<Instant, BigDecimal> getCurrBtmDeltas() {

        TreeMap<Instant, BigDecimal> map = new TreeMap<>();
        for (Dlt dlt: avgDeltaInParts.getB_delta()) {
            map.put(dlt.getTimestamp().toInstant(), dlt.getDelta());
        }

        return map;
    }

    public Map<Instant, BigDecimal> getCurrOkDeltas() {

        TreeMap<Instant, BigDecimal> map = new TreeMap<>();
        for (Dlt dlt: avgDeltaInParts.getO_delta()) {
            map.put(dlt.getTimestamp().toInstant(), dlt.getDelta());
        }

        return map;
    }

}
