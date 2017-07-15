package com.bitplay.arbitrage;

/**
 * Created by Sergey Shurmin on 6/12/17.
 */
public enum SignalType {
    AUTOMATIC(""),
    CORRECTION("correction"),
    MANUAL_BUY("button_buy"),
    MANUAL_SELL("button_sell");

    private String counterName;

    SignalType(String counterName) {
        this.counterName = counterName;
    }

    public String getCounterName() {
        return counterName;
    }
}
