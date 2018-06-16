package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DeltaName;
import com.bitplay.persistance.DeltaRepositoryService;
import com.bitplay.persistance.domain.borders.BorderDelta;
import com.bitplay.persistance.domain.fluent.Delta;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Getter
public class DeltasCalcService {

    private static final Logger logger = LoggerFactory.getLogger(BordersService.class);
    //    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");
//    private static final Logger signalLogger = LoggerFactory.getLogger("SIGNAL_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");
//    private static final Logger debugLog = LoggerFactory.getLogger("DEBUG_LOG");

    @Autowired
    private DeltaRepositoryService deltaRepositoryService;

    private Cache<Instant, Long> bDeltaCache;
    private Cache<Instant, Long> oDeltaCache;
    private Instant begin_delta_hist_per = Instant.now();

    private volatile BigDecimal bDeltaSma = BigDecimal.ZERO;
    private volatile BigDecimal oDeltaSma = BigDecimal.ZERO;
    private volatile BigDecimal bDeltaEveryCalc = BigDecimal.ZERO;
    private volatile BigDecimal oDeltaEveryCalc = BigDecimal.ZERO;

    public void resetDeltasCache(Integer delta_hist_per) {
        begin_delta_hist_per = Instant.now();
        bDeltaCache = CacheBuilder.newBuilder()
                .expireAfterWrite(delta_hist_per, TimeUnit.SECONDS)
                .build();
        oDeltaCache = CacheBuilder.newBuilder()
                .expireAfterWrite(delta_hist_per, TimeUnit.SECONDS)
                .build();
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

    public void addBDelta(BigDecimal bDelta) {
        bDeltaCache.put(Instant.now(), (bDelta.multiply(BigDecimal.valueOf(100))).longValue());
        bDeltaEveryCalc = calcDeltaSma(bDeltaCache);
    }

    public void addODelta(BigDecimal oDelta) {
        oDeltaCache.put(Instant.now(), (oDelta.multiply(BigDecimal.valueOf(100))).longValue());
        oDeltaEveryCalc = calcDeltaSma(oDeltaCache);
    }

    private synchronized BigDecimal calcDeltaSma(Cache<Instant, Long> bDeltaCache) {
        long sum = bDeltaCache.asMap().values().stream().mapToLong(Long::longValue).sum();
        return BigDecimal.valueOf(sum / bDeltaCache.size(), 2);
    }

    BigDecimal calcDelta1(BigDecimal instantDelta1, BorderDelta borderDelta) {
        if (bDeltaCache == null) {
            resetDeltasCache(borderDelta.getDeltaCalcPast());
        }

        switch (borderDelta.getDeltaCalcType()) {
            case DELTA:
                return instantDelta1;
            case AVG_DELTA:
                return BigDecimal.valueOf(getDeltaAvg1(instantDelta1, borderDelta)).setScale(2, BigDecimal.ROUND_HALF_UP);
            case AVG_DELTA_EVERY_BORDER_COMP:
            case AVG_DELTA_EVERY_NEW_DELTA:
                bDeltaSma = getEveryNewDelta(DeltaName.B_DELTA, borderDelta);
                return bDeltaSma;
        }
        throw new IllegalArgumentException("Unhandled deltaCalcType " + borderDelta.getDeltaCalcType());
    }

    BigDecimal calcDelta2(BigDecimal defaultDelta2, BorderDelta borderDelta) {
        switch (borderDelta.getDeltaCalcType()) {
            case DELTA:
                return defaultDelta2;
            case AVG_DELTA:
                return BigDecimal.valueOf(getDeltaAvg2(defaultDelta2, borderDelta)).setScale(2, BigDecimal.ROUND_HALF_UP);
            case AVG_DELTA_EVERY_BORDER_COMP:
            case AVG_DELTA_EVERY_NEW_DELTA:
                oDeltaSma = getEveryNewDelta(DeltaName.O_DELTA, borderDelta);
                return oDeltaSma;
        }
        throw new IllegalArgumentException("Unhandled deltaCalcType " + borderDelta.getDeltaCalcType());
    }

    private BigDecimal getEveryNewDelta(DeltaName deltaName, BorderDelta borderDelta) {
        Instant now = Instant.now();
        long pastSeconds = Duration.between(begin_delta_hist_per, now).getSeconds();
        Integer delta_hist_per = borderDelta.getDeltaCalcPast();
        if (pastSeconds < delta_hist_per) {
            // keep the last
            return deltaName == DeltaName.B_DELTA ? bDeltaSma : oDeltaSma;
        } else {
            return deltaName == DeltaName.B_DELTA ? bDeltaEveryCalc : oDeltaEveryCalc;
        }
    }

    private Double getDeltaAvg1(BigDecimal defaultDelta1, BorderDelta borderDelta) {
        final Date fromDate = Date.from(Instant.now().minus(borderDelta.getDeltaCalcPast(), ChronoUnit.SECONDS));

        final OptionalDouble average = deltaRepositoryService.streamDeltas(fromDate, new Date())
                .map(Delta::getbDelta)
                .mapToDouble(BigDecimal::doubleValue)
//                .peek(val -> logger.info("Delta1Part: " + val))
                .average();

        if (average.isPresent()) {
            logger.info("average Delta1=" + average);
            return average.getAsDouble();
        }

        logger.warn("Can not calc average Delta1");
        return defaultDelta1.doubleValue();
    }

    private Double getDeltaAvg2(BigDecimal defaultDelta2, BorderDelta borderDelta) {
        final Date fromDate = Date.from(Instant.now().minus(borderDelta.getDeltaCalcPast(), ChronoUnit.SECONDS));

        final OptionalDouble average = deltaRepositoryService.streamDeltas(fromDate, new Date())
                .map(Delta::getoDelta)
//                .peek(val -> logger.info("Delta2Part: " + val))
                .mapToDouble(BigDecimal::doubleValue)
                .average();
        if (average.isPresent()) {
            logger.info("average Delta2=" + average);
            return average.getAsDouble();
        }

        logger.warn("Can not calc average Delta2");
        return defaultDelta2.doubleValue();
    }


}
