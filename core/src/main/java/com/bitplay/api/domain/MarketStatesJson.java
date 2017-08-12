package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 6/1/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketStatesJson {
    String firstMarket;
    String secondMarket;

    public MarketStatesJson(String firstMarket, String secondMarket) {
        this.firstMarket = firstMarket;
        this.secondMarket = secondMarket;
    }

    public String getFirstMarket() {
        return firstMarket;
    }

    public void setFirstMarket(String firstMarket) {
        this.firstMarket = firstMarket;
    }

    public String getSecondMarket() {
        return secondMarket;
    }

    public void setSecondMarket(String secondMarket) {
        this.secondMarket = secondMarket;
    }
}
