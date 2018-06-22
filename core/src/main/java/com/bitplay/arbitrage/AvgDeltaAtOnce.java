package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DeltaName;
import com.bitplay.persistance.DeltaRepositoryService;
import com.bitplay.persistance.domain.borders.BorderDelta;
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
    public BigDecimal calcAvgDelta(DeltaName deltaName, BigDecimal instantDelta, BorderDelta borderDelta, Instant begin_delta_hist_per) {
        return deltaName == DeltaName.B_DELTA
                ? BigDecimal.valueOf(getDeltaAvg1(instantDelta, borderDelta)).setScale(2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.valueOf(getDeltaAvg2(instantDelta, borderDelta)).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    private Double getDeltaAvg1(BigDecimal defaultDelta1, BorderDelta borderDelta) {
        final Date fromDate = Date.from(Instant.now().minus(borderDelta.getDeltaCalcPast(), ChronoUnit.SECONDS));

        final OptionalDouble average = deltaRepositoryService.streamDeltas(DeltaName.B_DELTA, fromDate, new Date())
                .mapToLong(Dlt::getValue)
//                .mapToDouble(BigDecimal::doubleValue)
//                .peek(val -> logger.info("Delta1Part: " + val))
                .average();

        if (average.isPresent()) {
            logger.info("average Delta1=" + average);
            return average.getAsDouble() / 100;
        }

        logger.warn("Can not calc average Delta1");
        return defaultDelta1.doubleValue();
    }

    private Double getDeltaAvg2(BigDecimal defaultDelta2, BorderDelta borderDelta) {
        final Date fromDate = Date.from(Instant.now().minus(borderDelta.getDeltaCalcPast(), ChronoUnit.SECONDS));

        final OptionalDouble average = deltaRepositoryService.streamDeltas(DeltaName.O_DELTA, fromDate, new Date())
                .mapToLong(Dlt::getValue)
//                .map(Delta::getoDelta)
//                .peek(val -> logger.info("Delta2Part: " + val))
//                .mapToDouble(BigDecimal::doubleValue)
                .average();
        if (average.isPresent()) {
            logger.info("average Delta2=" + average);
            return average.getAsDouble() / 100;
        }

        logger.warn("Can not calc average Delta2");
        return defaultDelta2.doubleValue();
    }

}
