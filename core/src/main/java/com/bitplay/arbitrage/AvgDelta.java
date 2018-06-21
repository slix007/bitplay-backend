package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DeltaName;
import com.bitplay.arbitrage.events.DeltaChange;
import com.bitplay.persistance.domain.borders.BorderDelta;
import java.math.BigDecimal;

public interface AvgDelta {

    BigDecimal calcAvgDelta(DeltaName deltaName, BigDecimal instantDelta, BorderDelta borderDelta);

    default void newDeltaEvent(DeltaChange deltaChange) {
    }

    default void resetDeltasCache(Integer delta_hist_per, boolean clearData) {

    }

}
