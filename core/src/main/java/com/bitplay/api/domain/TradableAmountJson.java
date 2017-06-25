package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 6/1/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradableAmountJson {
    private String blockSize1;
    private String blockSize2;

    public TradableAmountJson() {
    }

    public TradableAmountJson(String blockSize1, String blockSize2) {
        this.blockSize1 = blockSize1;
        this.blockSize2 = blockSize2;
    }

    public String getBlockSize1() {
        return blockSize1;
    }

    public String getBlockSize2() {
        return blockSize2;
    }
}
