package com.bitplay.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SwapSettlement {
    @JsonSerialize(using = MyLocalDateTimeSerializer.class)
    private final LocalDateTime fundingTime;

    @JsonSerialize(using = BigDecimalSerializer.class)
    private final BigDecimal fundingRate;

    @JsonSerialize(using = BigDecimalSerializer.class)
    private final BigDecimal estimatedRate;

    @JsonSerialize(using = MyLocalDateTimeSerializer.class)
    private final LocalDateTime settlementTime;
}
