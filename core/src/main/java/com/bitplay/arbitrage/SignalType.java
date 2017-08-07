package com.bitplay.arbitrage;

/**
 * Created by Sergey Shurmin on 6/12/17.
 */
public enum SignalType {
    AUTOMATIC(""),
    CORRECTION("correction"),
    B_PRE_LIQ("b_preliq"),
    O_PRE_LIQ("o_preliq"),
    MANUAL_BUY("button_buy"),
    MANUAL_SELL("button_sell"),
    SWAP_CLOSE_LONG("swap_close_long"),
    SWAP_CLOSE_SHORT("swap_open_short");

    private String counterName;

    SignalType(String counterName) {
        this.counterName = counterName;
    }

    public String getCounterName() {
        return counterName;
    }
}
