package com.bitplay.persistance.domain.settings;

import com.bitplay.market.model.PlacingType;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Transient;

/**
 * Created by Sergey Shurmin on 12/29/17.
 */
@Getter
@Setter
public class PlacingBlocks {

    private Ver activeVersion;
    private BigDecimal fixedBlockOkex;
    private BigDecimal dynMaxBlockOkex;

    @Transient
    private BigDecimal bitmexBlockFactor = BigDecimal.valueOf(100);
    private BigDecimal posAdjustment;
    private PlacingType posAdjustmentPlacingType;

    public static PlacingBlocks createDefault() {
        final PlacingBlocks placingBlocks = new PlacingBlocks();
        placingBlocks.setActiveVersion(Ver.FIXED);
        placingBlocks.setFixedBlockOkex(BigDecimal.valueOf(1));
        placingBlocks.setDynMaxBlockOkex(BigDecimal.valueOf(1));
        placingBlocks.setPosAdjustment(BigDecimal.ZERO);
        return placingBlocks;
    }

    public BigDecimal getFixedBlockBitmex() {
        return fixedBlockOkex.multiply(bitmexBlockFactor);
    }

    public BigDecimal getDynMaxBlockBitmex() {
        return dynMaxBlockOkex.multiply(bitmexBlockFactor);
    }

    @Override
    public String toString() {
        return "PlacingBlocks{" +
                "activeVersion=" + activeVersion +
                ", fixedBlockOkex=" + fixedBlockOkex +
                ", dynMaxBlockOkex=" + dynMaxBlockOkex +
                ", bitmexBlockFactor=" + bitmexBlockFactor +
                ", posAdjustment=" + posAdjustment +
                '}';
    }

    public enum Ver {FIXED, DYNAMIC,}
}
