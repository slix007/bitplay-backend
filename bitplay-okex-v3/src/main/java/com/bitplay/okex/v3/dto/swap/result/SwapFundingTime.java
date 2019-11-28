package com.bitplay.okex.v3.dto.swap.result;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SwapFundingTime {
    private String instrumentId;
    private LocalDateTime funding_time;
    private BigDecimal funding_rate;
    private BigDecimal estimated_rate;
    private LocalDateTime settlement_time;
}
