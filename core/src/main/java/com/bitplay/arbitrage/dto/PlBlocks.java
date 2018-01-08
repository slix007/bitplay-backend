package com.bitplay.arbitrage.dto;

import com.bitplay.persistance.domain.settings.PlacingBlocks;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 12/29/17.
 */
public class PlBlocks {
    final private BigDecimal blockBitmex;
    final private BigDecimal blockOkex;
    final private PlacingBlocks.Ver ver;

    public PlBlocks(BigDecimal blockBitmex, BigDecimal blockOkex, PlacingBlocks.Ver ver) {
        this.blockBitmex = blockBitmex;
        this.blockOkex = blockOkex;
        this.ver = ver;
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

}
