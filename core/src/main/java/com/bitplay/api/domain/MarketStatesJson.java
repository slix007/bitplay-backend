package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by Sergey Shurmin on 6/1/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@Getter
public class MarketStatesJson {
    String firstMarket;
    String secondMarket;
    String firstTimeToReset;
    String secondTimeToReset;
    String signalDelay;
    String timeToSignal;
    String arbState;
    String bitmexReconnectState;

    public MarketStatesJson() {
    }

    public MarketStatesJson(String firstMarket, String secondMarket, String firstTimeToReset, String secondTimeToReset) {
        this.firstMarket = firstMarket;
        this.secondMarket = secondMarket;
        this.firstTimeToReset = firstTimeToReset;
        this.secondTimeToReset = secondTimeToReset;
    }
}
