package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class SettingsTransient {

    private BigDecimal leftOkexLeverage;
    private BigDecimal rightOkexLeverage;

}
