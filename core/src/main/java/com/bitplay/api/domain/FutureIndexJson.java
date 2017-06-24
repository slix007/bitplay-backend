package com.bitplay.api.domain;

/**
 * Created by Sergey Shurmin on 4/14/17.
 */
public class FutureIndexJson {
    private String index;
    private String timestamp;

    public FutureIndexJson(String index, String timestamp) {
        this.index = index;
        this.timestamp = timestamp;
    }

    public String getIndex() {
        return index;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
