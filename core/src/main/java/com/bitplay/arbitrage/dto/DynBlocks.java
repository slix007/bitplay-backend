package com.bitplay.arbitrage.dto;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 12/29/17.
 */
public class DynBlocks {
    final private BigDecimal blockBitmex;
    final private BigDecimal blockOkex;

    public DynBlocks(BigDecimal blockBitmex, BigDecimal blockOkex) {
        this.blockBitmex = blockBitmex;
        this.blockOkex = blockOkex;
    }

    public BigDecimal getBlockBitmex() {
        return blockBitmex;
    }

    public BigDecimal getBlockOkex() {
        return blockOkex;
    }
}
