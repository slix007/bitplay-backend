package com.bitplay.api.domain;

/**
 * Created by Sergey Shurmin on 4/14/17.
 */
public class TickerJson {
    String value;

    public TickerJson(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
