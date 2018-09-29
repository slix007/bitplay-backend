package com.bitplay.arbitrage.dto;

/**
 * Created by Sergey Shurmin on 6/12/17.
 */
public enum SignalType {
    AUTOMATIC(""),
    ADJ("adj"),
    B_ADJ("b_adj"),
    B_ADJ_INCREASE_POS("b_adj_increase_pos"),
    O_ADJ("o_adj"),
    O_ADJ_INCREASE_POS("o_adj_increase_pos"),
    CORR("corr"),
    B_CORR("b_corr"),
    B_CORR_INCREASE_POS("b_corr_increase_pos"),
    O_CORR("o_corr"),
    O_CORR_INCREASE_POS("o_corr_increase_pos"),
    CORR_MDC("corr_mdc"),
    CORR_TIMER("corr_timer"),
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

    public boolean isCorr() {
        return this == CORR || this == CORR_MDC || this == CORR_TIMER || this == B_CORR || this == O_CORR || this == B_CORR_INCREASE_POS
                || this == O_CORR_INCREASE_POS
                || this.isAdj();
    }

    public boolean isAdj() {
        return this == ADJ || this == B_ADJ || this == O_ADJ || this == B_ADJ_INCREASE_POS || this == O_ADJ_INCREASE_POS;
    }

    public boolean isDoubleTradingSignal() {
        return this == AUTOMATIC || isPreliq();
    }

    public boolean isPreliq() {
        return this == SignalType.B_PRE_LIQ || this == SignalType.O_PRE_LIQ;

    }
}
