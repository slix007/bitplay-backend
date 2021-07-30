package com.bitplay.okex.v5.dto.adapter.adapter;

import com.bitplay.model.SwapSettlement;
import com.bitplay.okex.v5.dto.result.SwapFundingTime.SwapFundingTimeData;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class SwapConverter {

    public static SwapSettlement convertFunding(SwapFundingTimeData t) {
        if (t == null) {
            return new SwapSettlement(
                    LocalDateTime.MIN, BigDecimal.ZERO, BigDecimal.ZERO, LocalDateTime.MIN
            );
        }
        final LocalDateTime localDate = t.getFundingTime() == null
                ? LocalDateTime.MIN
                : t.getFundingTime().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
        final LocalDateTime localDateNext = t.getNextFundingTime() == null
                ? LocalDateTime.MIN
                : t.getNextFundingTime().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
        return new SwapSettlement(localDate,
                t.getFundingRate(), t.getNextFundingRate(),
                localDateNext);
    }
}
