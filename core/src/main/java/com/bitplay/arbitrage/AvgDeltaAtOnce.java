package com.bitplay.arbitrage;

import com.bitplay.persistance.DeltaRepositoryService;
import com.bitplay.persistance.domain.borders.BorderDelta;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.fluent.Dlt;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.OptionalDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AvgDeltaAtOnce implements AvgDelta {

    private static final Logger logger = LoggerFactory.getLogger(AvgDeltaAtOnce.class);

    @Autowired
    private DeltaRepositoryService deltaRepositoryService;

    @Override
    public BigDecimal calcAvgDelta(DeltaName deltaName, BigDecimal instantDelta, Instant currTime, BorderDelta borderDelta,
            Instant begin_delta_hist_per) {
        final int scale = instantDelta.scale();
        return deltaName == DeltaName.B_DELTA
                ? BigDecimal.valueOf(getDeltaAvg1(instantDelta, borderDelta)).setScale(scale, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.valueOf(getDeltaAvg2(instantDelta, borderDelta)).setScale(scale, BigDecimal.ROUND_HALF_UP);
    }

    private Double getDeltaAvg1(BigDecimal defaultDelta1, BorderDelta borderDelta) {
        final Date fromDate = Date.from(Instant.now().minus(borderDelta.getDeltaCalcPast(), ChronoUnit.SECONDS));
        final Date toDate = new Date();

        final OptionalDouble average = deltaRepositoryService.streamDeltas(DeltaName.B_DELTA, fromDate, toDate)
                .mapToLong(Dlt::getValue)
//                .mapToDouble(BigDecimal::doubleValue)
//                .peek(val -> logger.info("Delta1Part: " + val))
                .average();

        if (average.isPresent()) {
            logger.debug("average Delta1=" + average);
            return average.getAsDouble() / 100;
        }

        final long count = deltaRepositoryService.streamDeltas(DeltaName.B_DELTA, fromDate, toDate).count();

        logger.warn("Can not calc average Delta1. count={} from {} to {}", count, fromDate, toDate);
        return defaultDelta1.doubleValue();
    }

    private Double getDeltaAvg2(BigDecimal defaultDelta2, BorderDelta borderDelta) {
        final Date fromDate = Date.from(Instant.now().minus(borderDelta.getDeltaCalcPast(), ChronoUnit.SECONDS));
        final Date toDate = new Date();

        final OptionalDouble average = deltaRepositoryService.streamDeltas(DeltaName.O_DELTA, fromDate, toDate)
                .mapToLong(Dlt::getValue)
//                .map(Delta::getoDelta)
//                .peek(val -> logger.info("Delta2Part: " + val))
//                .mapToDouble(BigDecimal::doubleValue)
                .average();
        if (average.isPresent()) {
            logger.debug("average Delta2=" + average);
            return average.getAsDouble() / 100;
        }

        final long count = deltaRepositoryService.streamDeltas(DeltaName.O_DELTA, fromDate, toDate).count();
        logger.warn("Can not calc average Delta2. count={} from {} to {}", count, fromDate, toDate);
        return defaultDelta2.doubleValue();
    }

}
