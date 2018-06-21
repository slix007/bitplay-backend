package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DeltaName;
import com.bitplay.arbitrage.events.DeltaChange;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.borders.BorderDelta;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
//    private static final Logger debugLog = LoggerFactory.getLogger("DEBUG_LOG");

    @Autowired
    private PersistenceService persistenceService;
    @Autowired
    private ArbitrageService arbitrageService;
    @Autowired
    private BordersRecalcService bordersRecalcService;
    @Autowired
    private AvgDeltaFromDb avgDeltaFromDb;
    @Autowired
    private AvgDeltaInMemory avgDeltaInMemory;

    /**
     * local copy of the settings
     */
    private BorderDelta borderDelta;

    //    private Cache<Instant, Long> bDeltaCache;
//    private Cache<Instant, Long> oDeltaCache;
    private Instant begin_delta_hist_per = Instant.now();

    private volatile BigDecimal bDeltaSma = BigDecimal.ZERO;
    private volatile BigDecimal oDeltaSma = BigDecimal.ZERO;
    private volatile BigDecimal bDeltaEveryCalc = BigDecimal.ZERO;
    private volatile BigDecimal oDeltaEveryCalc = BigDecimal.ZERO;
    private volatile boolean started;

    private Disposable deltaChangeSubscriber;

    public void initDeltasCache(BorderDelta borderDelta) {
        resetDeltasCache(borderDelta, true);

        final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("deltas-calc-%d").build();
        final ExecutorService executor = Executors.newSingleThreadExecutor(namedThreadFactory);

        deltaChangeSubscriber = arbitrageService.getDeltaChangesPublisher()
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.from(executor))
                .subscribe(this::deltaChangeListener);
    }

    private void deltaChangeListener(DeltaChange deltaChange) {
        long startMs = Instant.now().toEpochMilli();

        avgDeltaInMemory.newDeltaEvent(deltaChange);

        bordersRecalcService.newDeltaAdded();

        long endMs = Instant.now().toEpochMilli();
        arbitrageService.getDeltaMon().setDeltaMs(endMs - startMs);

//        logger.info("CalcEnded. " + arbitrageService.getDeltaMon());
    }

    public void resetDeltasCache(BorderDelta borderDelta, boolean clearData) {
        this.borderDelta = borderDelta;
        Integer delta_hist_per = borderDelta.getDeltaCalcPast();

        if (clearData) {
            begin_delta_hist_per = Instant.now();
        }

        // the only impl
        avgDeltaInMemory.resetDeltasCache(delta_hist_per, clearData);

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

        switch (borderDelta.getDeltaCalcType()) {
            case DELTA:
                return instantDelta;
            case AVG_DELTA:
                return avgDeltaFromDb.calcAvgDelta(deltaName, instantDelta, borderDelta);
            case AVG_DELTA_EVERY_BORDER_COMP_FROM_DB:
            case AVG_DELTA_EVERY_NEW_DELTA_FROM_DB:
                return calcAvgDelta(avgDeltaFromDb, deltaName, instantDelta, borderDelta);
            case AVG_DELTA_EVERY_BORDER_COMP:
            case AVG_DELTA_EVERY_NEW_DELTA:
                return calcAvgDelta(avgDeltaInMemory, deltaName, instantDelta, borderDelta);
        }
        throw new IllegalArgumentException("Unhandled deltaCalcType " + borderDelta.getDeltaCalcType());
    }

    private BigDecimal calcAvgDelta(AvgDelta avgDeltaService, DeltaName deltaName, BigDecimal instantDelta, BorderDelta borderDelta) {
        BigDecimal deltaSma = deltaName == DeltaName.B_DELTA ? bDeltaSma : oDeltaSma;
        if (isReadyForCalc(borderDelta.getDeltaCalcPast())) {
            if (deltaName == DeltaName.B_DELTA) {
                deltaSma = avgDeltaService.calcAvgDelta(deltaName, instantDelta, borderDelta);
            } else {
                deltaSma = avgDeltaService.calcAvgDelta(deltaName, instantDelta, borderDelta);
            }
        }
        return deltaSma;
    }
//
//    private BigDecimal getEveryNewDelta(DeltaName deltaName, BorderDelta borderDelta) {
//        if (!isReadyForCalc(borderDelta.getDeltaCalcPast())) {
//            // keep the last
//            return deltaName == DeltaName.B_DELTA ? bDeltaSma : oDeltaSma;
//        } else {
//            return deltaName == DeltaName.B_DELTA ? bDeltaEveryCalc : oDeltaEveryCalc;
//        }
//    }

    private boolean isReadyForCalc(Integer deltaCalcPast) {
        Instant now = Instant.now();
        long pastSeconds = Duration.between(begin_delta_hist_per, now).getSeconds();
        return pastSeconds > deltaCalcPast;
    }

    public void setBorderDelta(BorderDelta borderDelta) {
        this.borderDelta = borderDelta;
    }
}
