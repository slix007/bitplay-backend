package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DeltaName;
import com.bitplay.persistance.domain.borders.BorderDelta;
import com.bitplay.persistance.domain.fluent.Dlt;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public interface AvgDelta {

    BigDecimal calcAvgDelta(DeltaName deltaName, BigDecimal instantDelta, Instant currTime, BorderDelta borderDelta,
            Instant begin_delta_hist_per);

    default void newDeltaEvent(Dlt dltJustAddedToDb, Instant begin_delta_hist_per) {
    }

    default void resetDeltasCache(Integer delta_hist_per, boolean clearData) {

    }

    default boolean isReadyForCalc(Instant currTime, Instant begin_delta_hist_per, Integer delta_hist_per) {
        long pastSeconds = Duration.between(begin_delta_hist_per, currTime).getSeconds();
        return pastSeconds > delta_hist_per;
    }


}
