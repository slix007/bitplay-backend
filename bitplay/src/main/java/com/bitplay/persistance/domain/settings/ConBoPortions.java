package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ConBoPortions {

    private BigDecimal minNtUsdToStartOkex;
    private BigDecimal maxPortionUsdOkex;

    public static ConBoPortions createDefault() {
        final ConBoPortions conBoPortions = new ConBoPortions();
        conBoPortions.minNtUsdToStartOkex = BigDecimal.ZERO;
        conBoPortions.maxPortionUsdOkex = BigDecimal.ZERO;
        return conBoPortions;
    }
}
