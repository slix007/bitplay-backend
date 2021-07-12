package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class Implied {

    private BigDecimal volUsd;
    private BigDecimal usdQuIni;
    private BigDecimal sebestIniUsd;


    public static Implied createDefaults() {
        final Implied im = new Implied();
        im.volUsd = BigDecimal.ZERO;
        im.usdQuIni = BigDecimal.ZERO;
        im.sebestIniUsd = BigDecimal.ZERO;
        return im;
    }
}
