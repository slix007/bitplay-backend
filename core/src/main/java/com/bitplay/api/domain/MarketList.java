package com.bitplay.api.domain;

/**
 * Created by Sergey Shurmin on 5/4/17.
 */
public class MarketList {
    String first;
    String second;

    public MarketList(String first, String second) {
        this.first = first;
        this.second = second;
    }

    public String getFirst() {
        return first;
    }

    public String getSecond() {
        return second;
    }
}
