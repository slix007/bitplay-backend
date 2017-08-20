package com.bitplay.arbitrage;

/**
 * Created by Sergey Shurmin on 6/12/17.
 */
public enum SignalType {
    AUTOMATIC(""),
    B_CORR("b_corr"),
    O_CORR("o_corr"),
    B_CORR_MDC("b_corr_mdc"),
    O_CORR_MDC("o_corr_mdc"),
    B_CORR_PERIOD("b_corr_period"),
    O_CORR_PERIOD("o_corr_period"),
    B_PRE_LIQ("b_preliq"),
    O_PRE_LIQ("o_preliq"),
    MANUAL_BUY("button_buy"),
    MANUAL_SELL("button_sell"),
    SWAP_NONE("swap_none"),
    SWAP_CLOSE_LONG("swap_close_long"),
    SWAP_CLOSE_SHORT("swap_close_short"),
    SWAP_REVERT_LONG("swap_revert_long"),
    SWAP_REVERT_SHORT("swap_revert_short");

    private String counterName;

    SignalType(String counterName) {
        this.counterName = counterName;
    }

    public String getCounterName() {
        return counterName;
    }
}
