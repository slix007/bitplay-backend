package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DeltaName;
import com.bitplay.arbitrage.events.DeltaChange;
import com.bitplay.persistance.domain.borders.BorderDelta;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public interface AvgDelta {

    BigDecimal calcAvgDelta(DeltaName deltaName, BigDecimal instantDelta, BorderDelta borderDelta, Instant begin_delta_hist_per);

    default void newDeltaEvent(DeltaChange deltaChange, Instant begin_delta_hist_per) {
    }

    default void resetDeltasCache(Integer delta_hist_per, boolean clearData) {

    }

    default boolean isReadyForCalc(Instant currTime, Instant begin_delta_hist_per, Integer delta_hist_per) {
        Instant now = Instant.now();
        long pastSeconds = Duration.between(begin_delta_hist_per, now).getSeconds();
        return pastSeconds > delta_hist_per;
    }


}
