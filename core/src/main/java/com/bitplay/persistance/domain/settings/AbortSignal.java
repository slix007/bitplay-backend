package com.bitplay.persistance.domain.settings;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AbortSignal {

    private Boolean abortSignalPtsEnabled; // only ArbScheme.R_wait_L_portions
    private BigDecimal abortSignalPts;
}
