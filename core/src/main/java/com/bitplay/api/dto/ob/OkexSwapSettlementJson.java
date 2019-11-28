package com.bitplay.api.dto.ob;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OkexSwapSettlementJson {
    private final String fundingTime;
    private final BigDecimal fundingRate;
    private final BigDecimal estimatedRate;
    private final String settlementTime;
}
