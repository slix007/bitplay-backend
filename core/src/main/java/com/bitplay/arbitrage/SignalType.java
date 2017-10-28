package com.bitplay.arbitrage;

/**
 * Created by Sergey Shurmin on 6/12/17.
 */
public enum SignalType {
    AUTOMATIC(""),
    CORR("corr"),
    B_CORR("b_corr"),
    O_CORR("o_corr"),
    CORR_MDC("corr_mdc"),
    CORR_PERIOD("corr_period"),
    B_PRE_LIQ("b_preliq"),
    O_PRE_LIQ("o_preliq"),
    MANUAL_BUY("button_buy"),
    MANUAL_SELL("button_sell"),
    SWAP_NONE("swap_none"),
    SWAP_CLOSE_LONG("swap_close_long"),
    SWAP_CLOSE_SHORT("swap_close_short"),
    SWAP_REVERT_LONG("swap_revert_long"),
    SWAP_REVERT_SHORT("swap_revert_short"),
    SWAP_OPEN("swap_open");

    private String counterName;

    SignalType(String counterName) {
        this.counterName = counterName;
    }

    public String getCounterName() {
        return counterName;
    }
}
