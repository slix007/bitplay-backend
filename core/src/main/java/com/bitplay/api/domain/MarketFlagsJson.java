package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 6/1/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketFlagsJson {
    Boolean firstMarket;
    Boolean secondMarket;

    public MarketFlagsJson(Boolean firstMarket, Boolean secondMarket) {
        this.firstMarket = firstMarket;
        this.secondMarket = secondMarket;
    }

    public Boolean getFirstMarket() {
        return firstMarket;
    }

    public void setFirstMarket(Boolean firstMarket) {
        this.firstMarket = firstMarket;
    }

    public Boolean getSecondMarket() {
        return secondMarket;
    }

    public void setSecondMarket(Boolean secondMarket) {
        this.secondMarket = secondMarket;
    }
}
