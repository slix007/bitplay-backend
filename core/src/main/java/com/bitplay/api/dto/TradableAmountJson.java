package com.bitplay.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 6/1/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradableAmountJson {
    private String block1;
    private String block2;

    public TradableAmountJson() {
    }

    public TradableAmountJson(String block1, String block2) {
        this.block1 = block1;
        this.block2 = block2;
    }

    public String getBlock1() {
        return block1;
    }

    public String getBlock2() {
        return block2;
    }
}
