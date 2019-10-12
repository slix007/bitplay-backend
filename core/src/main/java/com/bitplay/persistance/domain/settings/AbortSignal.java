package com.bitplay.persistance.domain.settings;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AbortSignal {

    private Boolean abortSignalPtsEnabled; // only ArbScheme.CON_B_O_PORTIONS
    private BigDecimal abortSignalPts;
}
