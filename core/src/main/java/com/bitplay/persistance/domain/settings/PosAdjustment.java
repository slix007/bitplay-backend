package com.bitplay.persistance.domain.settings;

import com.bitplay.market.model.PlacingType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class PosAdjustment {

    private BigDecimal posAdjustmentMin;
    private BigDecimal posAdjustmentMax;
    private PlacingType posAdjustmentPlacingType;
    private Integer posAdjustmentDelaySec;
    private Integer corrDelaySec;

    public static PosAdjustment createDefault() {
        final PosAdjustment adj = new PosAdjustment();
        adj.setPosAdjustmentMin(BigDecimal.ZERO);
        adj.setPosAdjustmentMax(BigDecimal.ZERO);
        adj.setPosAdjustmentPlacingType(PlacingType.TAKER);
        adj.setPosAdjustmentDelaySec(25);
        adj.setCorrDelaySec(25);
        return adj;
    }

}
