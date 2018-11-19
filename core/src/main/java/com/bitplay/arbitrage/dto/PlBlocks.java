package com.bitplay.arbitrage.dto;

import com.bitplay.persistance.domain.settings.PlacingBlocks;
import java.math.BigDecimal;

/**
 * Blocks are in contracts only.
 * <br>
 * Created by Sergey Shurmin on 12/29/17.
 */
public class PlBlocks {
    final private BigDecimal blockBitmex;
    final private BigDecimal blockOkex;
    final private PlacingBlocks.Ver ver;
    final private String debugLog;

    public PlBlocks(BigDecimal blockBitmex, BigDecimal blockOkex, PlacingBlocks.Ver ver) {
        this.blockBitmex = blockBitmex;
        this.blockOkex = blockOkex;
        this.ver = ver;
        this.debugLog = "";
    }

    public PlBlocks(BigDecimal blockBitmex, BigDecimal blockOkex, PlacingBlocks.Ver ver, String debugLog) {
        this.blockBitmex = blockBitmex;
        this.blockOkex = blockOkex;
        this.ver = ver;
        this.debugLog = debugLog;
    }

    public BigDecimal getBlockBitmex() {
        return blockBitmex;
    }

    public BigDecimal getBlockOkex() {
        return blockOkex;
    }

    public boolean isDynamic() {
        return ver == PlacingBlocks.Ver.DYNAMIC;
    }

    public String getDebugLog() {
        return debugLog;
    }
}
