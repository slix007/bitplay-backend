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
public class PosAdjustment implements Cloneable {

    private BigDecimal posAdjustmentMin;
    private BigDecimal posAdjustmentMax;
    private PlacingType posAdjustmentPlacingType;
    private Integer posAdjustmentDelaySec;
    private Integer corrDelaySec;
    private Integer preliqDelaySec;

    public static PosAdjustment createDefault() {
        final PosAdjustment adj = new PosAdjustment();
        adj.setPosAdjustmentMin(BigDecimal.ZERO);
        adj.setPosAdjustmentMax(BigDecimal.ZERO);
        adj.setPosAdjustmentPlacingType(PlacingType.TAKER);
        adj.setPosAdjustmentDelaySec(25);
        adj.setCorrDelaySec(25);
        adj.setPreliqDelaySec(25);
        return adj;
    }

    @Override
    protected PosAdjustment clone() {
        PosAdjustment clone = new PosAdjustment();
        clone.setPosAdjustmentMin(this.posAdjustmentMin);
        clone.setPosAdjustmentMax(this.posAdjustmentMax);
        clone.setPosAdjustmentPlacingType(this.posAdjustmentPlacingType);
        clone.setPosAdjustmentDelaySec(this.posAdjustmentDelaySec);
        clone.setCorrDelaySec(this.corrDelaySec);
        clone.setPreliqDelaySec(this.preliqDelaySec);
        return clone;
    }
}
