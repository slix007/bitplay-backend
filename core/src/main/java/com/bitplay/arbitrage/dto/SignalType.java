package com.bitplay.arbitrage.dto;

/**
 * Created by Sergey Shurmin on 6/12/17.
 */
public enum SignalType {
    AUTOMATIC(""),
    ADJ_BTC("adj_btc"),
    B_ADJ_BTC("b_adj_btc"),
    B_ADJ_BTC_INCREASE_POS("b_adj_btc_increase_pos"),
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
    CORR_BTC("corr_btc"),
    B_CORR_BTC("b_corr_btc"),
    B_CORR_BTC_INCREASE_POS("b_corr_btc_increase_pos"),
    CORR_MDC("corr_mdc"),
    CORR_BTC_MDC("corr_btc_mdc"),
    CORR_TIMER("corr_timer"),
    RECOVERY_NTUSD("recovery_nt_usd"),
    RECOVERY_NTUSD_INCREASE_POS("recovery_nt_usd_increase_pos"),
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
                || this.isCorrBtc()
                || this.isAdj();
    }

    public boolean isCorrBtc() {
        return this == CORR_BTC || this == B_CORR_BTC || this == B_CORR_BTC_INCREASE_POS;
    }

    public boolean isAdj() {
        return this == ADJ || this == B_ADJ || this == O_ADJ || this == B_ADJ_INCREASE_POS || this == O_ADJ_INCREASE_POS
                || this.isAdjBtc();
    }

    public boolean isAdjBtc() {
        return this == ADJ_BTC || this == B_ADJ_BTC || this == B_ADJ_BTC_INCREASE_POS;
    }

    public boolean isDoubleTradingSignal() {
        return this == AUTOMATIC || isPreliq();
    }

    public boolean isPreliq() {
        return this == SignalType.B_PRE_LIQ || this == SignalType.O_PRE_LIQ;

    }

    public boolean isIncreasePos() {
        return getCounterName().contains("increase");
    }

    public boolean isManual() {
        return this == MANUAL_BUY || this == MANUAL_SELL || isRecoveryNtUsd();
    }

    public boolean isRecoveryNtUsd() {
        return this == RECOVERY_NTUSD || this == RECOVERY_NTUSD_INCREASE_POS;
    }

    public SignalType switchMarket() {
        final String inName = this.name().toUpperCase();
        String outName = inName;
        if (inName.startsWith("B_")) {
            outName = "O_" + inName.substring(2);
        }
        if (inName.startsWith("O_")) {
            outName = "B_" + inName.substring(2);
        }
        return SignalType.valueOf(outName);
    }

    public boolean isMainSet() {
        return !isExtraSet();
    }

    public boolean isExtraSet() {
        return getCounterName().contains("_btc");
    }

    @SuppressWarnings("DuplicatedCode")
    public SignalType toIncrease(boolean isBitmex) {
        SignalType res = this;
        if (this.isAdjBtc()) {
            res = B_ADJ_BTC_INCREASE_POS;
        } else if (this.isAdj()) {
            res = B_ADJ_INCREASE_POS;
        } else if (this.isCorrBtc()) {
            res = B_CORR_BTC_INCREASE_POS;
        } else if (this.isCorr()) {
            res = B_CORR_INCREASE_POS;
        } else if (this.isRecoveryNtUsd()) {
            return RECOVERY_NTUSD_INCREASE_POS;
        }
        if (isBitmex) {
            return res;
        }
        String outName = "O_" + res.name().substring(2);
        return SignalType.valueOf(outName);
    }

    @SuppressWarnings("DuplicatedCode")
    public SignalType toDecrease(boolean isBitmex) {
        SignalType res = this;
        if (this.isAdjBtc()) {
            res = B_ADJ_BTC;
        } else if (this.isAdj()) {
            res = B_ADJ;
        } else if (this.isCorrBtc()) {
            res = B_CORR_BTC;
        } else if (this.isCorr()) {
            res = B_CORR;
        } else if (this.isRecoveryNtUsd()) {
            return RECOVERY_NTUSD;
        }
        if (isBitmex) {
            return res;
        }
        String outName = "O_" + res.name().substring(2);
        return SignalType.valueOf(outName);
    }
}
