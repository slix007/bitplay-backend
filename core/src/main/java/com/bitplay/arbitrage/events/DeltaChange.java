package com.bitplay.arbitrage.events;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DeltaChange {

    private BigDecimal btmDelta;
    private BigDecimal okDelta;
}
