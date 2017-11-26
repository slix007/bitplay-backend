package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 6/1/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketStatesJson {
    String firstMarket;
    String secondMarket;
    String firstTimeToReset;
    String secondTimeToReset;

    public MarketStatesJson() {
    }

    public MarketStatesJson(String firstMarket, String secondMarket, String firstTimeToReset, String secondTimeToReset) {
        this.firstMarket = firstMarket;
        this.secondMarket = secondMarket;
        this.firstTimeToReset = firstTimeToReset;
        this.secondTimeToReset = secondTimeToReset;
    }

    public void setFirstMarket(String firstMarket) {
        this.firstMarket = firstMarket;
    }

    public void setSecondMarket(String secondMarket) {
        this.secondMarket = secondMarket;
    }

    public void setFirstTimeToReset(String firstTimeToReset) {
        this.firstTimeToReset = firstTimeToReset;
    }

    public void setSecondTimeToReset(String secondTimeToReset) {
        this.secondTimeToReset = secondTimeToReset;
    }

    public String getFirstMarket() {
        return firstMarket;
    }

    public String getSecondMarket() {
        return secondMarket;
    }

    public String getFirstTimeToReset() {
        return firstTimeToReset;
    }

    public String getSecondTimeToReset() {
        return secondTimeToReset;
    }
}
