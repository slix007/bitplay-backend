package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    /**
     * CM - correlation multiplier.
     */
    @Transient
    private BigDecimal bitmexBlockFactor = BigDecimal.valueOf(100);

    public static PlacingBlocks createDefault() {
        final PlacingBlocks placingBlocks = new PlacingBlocks();
        placingBlocks.setActiveVersion(Ver.FIXED);
        placingBlocks.setFixedBlockOkex(BigDecimal.valueOf(1));
        placingBlocks.setDynMaxBlockOkex(BigDecimal.valueOf(1));
        return placingBlocks;
    }

    public BigDecimal getFixedBlockBitmex() {
        return fixedBlockOkex.multiply(bitmexBlockFactor).setScale(0, RoundingMode.HALF_UP);
    }

    public BigDecimal getDynMaxBlockBitmex() {
        return dynMaxBlockOkex.multiply(bitmexBlockFactor).setScale(0, RoundingMode.HALF_UP);
    }

    @Override
    public String toString() {
        return "PlacingBlocks{" +
                "activeVersion=" + activeVersion +
                ", fixedBlockOkex=" + fixedBlockOkex +
                ", dynMaxBlockOkex=" + dynMaxBlockOkex +
                ", bitmexBlockFactor=" + bitmexBlockFactor +
                '}';
    }

    public enum Ver {FIXED, DYNAMIC,}
}
