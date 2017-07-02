package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * "maker" or "taker".
 *
 * Created by Sergey Shurmin on 6/1/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlacingTypeJson {
    String firstMarket;
    String secondMarket;

    public PlacingTypeJson() {
    }

    public PlacingTypeJson(String firstMarket, String secondMarket) {
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
