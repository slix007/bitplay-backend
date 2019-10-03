package com.bitplay.api.dto.states;

import com.bitplay.api.dto.pos.PosDiffJson;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Created by Sergey Shurmin on 6/1/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class MarketStatesJson {
    String firstMarket;
    String secondMarket;
    String firstTimeToReset;
    String secondTimeToReset;
    String signalDelay;
    String timeToSignal;
    Long timeToResetTradingMode;
    Long timeToVolatileMode;
    Long timeToResetBitmexChangeOnSo;
    String arbState;
    String bitmexReconnectState;
    DelayTimerJson corrDelay;
    DelayTimerJson posAdjustmentDelay;
    DelayTimerJson preliqDelay;
    SignalPartsJson signalParts;
    PosDiffJson posDiffJson;
    OrderPortionsJson orderPortionsJson;
    Boolean okexSettlementMode;
    String nowMomentStr;

    public MarketStatesJson(String firstMarket, String secondMarket, String firstTimeToReset, String secondTimeToReset) {
        this.firstMarket = firstMarket;
        this.secondMarket = secondMarket;
        this.firstTimeToReset = firstTimeToReset;
        this.secondTimeToReset = secondTimeToReset;
    }
}