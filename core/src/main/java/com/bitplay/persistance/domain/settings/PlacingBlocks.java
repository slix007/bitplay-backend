package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;
import org.springframework.data.annotation.Transient;

/**
 * Created by Sergey Shurmin on 12/29/17.
 */
public class PlacingBlocks {

    @Transient
    private BigDecimal bitmexBlockFactor = BigDecimal.valueOf(100);
    private Ver activeVersion;
    private BigDecimal fixedBlockOkex;
    private BigDecimal dynMaxBlockOkex;

    public static PlacingBlocks createDefault() {
        final PlacingBlocks placingBlocks = new PlacingBlocks();
        placingBlocks.setActiveVersion(Ver.FIXED);
        placingBlocks.setFixedBlockOkex(BigDecimal.valueOf(1));
        placingBlocks.setDynMaxBlockOkex(BigDecimal.valueOf(1));
        return placingBlocks;
    }

    public Ver getActiveVersion() {
        return activeVersion;
    }

    public void setActiveVersion(Ver activeVersion) {
        this.activeVersion = activeVersion;
    }

    public BigDecimal getFixedBlockBitmex() {
        return fixedBlockOkex.multiply(bitmexBlockFactor);
    }

    public BigDecimal getFixedBlockOkex() {
        return fixedBlockOkex;
    }

    public void setFixedBlockOkex(BigDecimal fixedBlockOkex) {
        this.fixedBlockOkex = fixedBlockOkex;
    }

    public BigDecimal getDynMaxBlockBitmex() {
        return dynMaxBlockOkex.multiply(bitmexBlockFactor);
    }

    public BigDecimal getDynMaxBlockOkex() {
        return dynMaxBlockOkex;
    }

    public void setDynMaxBlockOkex(BigDecimal dynMaxBlockOkex) {
        this.dynMaxBlockOkex = dynMaxBlockOkex;
    }

    public BigDecimal getBitmexBlockFactor() {
        return bitmexBlockFactor;
    }

    public void setBitmexBlockFactor(BigDecimal bitmexBlockFactor) {
        this.bitmexBlockFactor = bitmexBlockFactor;
    }

    @Override
    public String toString() {
        return "PlacingBlocks{" +
                "activeVersion=" + activeVersion +
                ", fixedBlockOkex=" + fixedBlockOkex +
                ", dynMaxBlockOkex=" + dynMaxBlockOkex +
                '}';
    }

    public enum Ver {FIXED, DYNAMIC,}
    public enum DeltaBase {B_DELTA, O_DELTA,}
}
