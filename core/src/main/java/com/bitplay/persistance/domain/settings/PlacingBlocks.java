package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 12/29/17.
 */
public class PlacingBlocks {

    private static final BigDecimal BITMEX_BLOCK_FACTOR = BigDecimal.valueOf(100);
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
        return fixedBlockOkex.multiply(BITMEX_BLOCK_FACTOR);
    }

    public BigDecimal getFixedBlockOkex() {
        return fixedBlockOkex;
    }

    public void setFixedBlockOkex(BigDecimal fixedBlockOkex) {
        this.fixedBlockOkex = fixedBlockOkex;
    }

    public BigDecimal getDynMaxBlockBitmex() {
        return dynMaxBlockOkex.multiply(BITMEX_BLOCK_FACTOR);
    }

    public BigDecimal getDynMaxBlockOkex() {
        return dynMaxBlockOkex;
    }

    public void setDynMaxBlockOkex(BigDecimal dynMaxBlockOkex) {
        this.dynMaxBlockOkex = dynMaxBlockOkex;
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
}
