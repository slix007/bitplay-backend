package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class Prem {

    private BigDecimal bcdPrem;
    private BigDecimal leftAddBorderPrem;
    private BigDecimal rightAddBorderPrem;

    public static Prem createDefault() {
        final Prem p = new Prem();
        p.bcdPrem = BigDecimal.ZERO;
        p.leftAddBorderPrem = BigDecimal.ZERO;
        p.rightAddBorderPrem = BigDecimal.ZERO;
        return p;
    }
}
