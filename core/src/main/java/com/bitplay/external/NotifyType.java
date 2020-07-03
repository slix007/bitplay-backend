package com.bitplay.external;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public enum NotifyType {
    // Stop all actions (MDC и signal limit) - saa;
    STOP_ALL_ACTIONS_BY_MDC_TIMER("saa", false),
    // Совершился любой Preliq - plq;
    PRELIQ("plq", false),
    // S_e_best = lower - low;
    SEBEST_LOWER("low", true, 60 * 30), // 30 min
    // e_best bitmex / e_best okex = res; 0.4 < res < 1 - eqt;
    E_BEST_VIOLATION("eqt", true, 60 * 60), // 60 min
    // Сделалась любая коррекция - cor;
    CORR_NOTIFY("cor", false),
    // Сделалась любая подгонка - adj;
    ADJ_NOTIFY("adj", false),
    // Бан на бирже (HTTP status was not ok: 403) - ban;
    BITMEX_BAN_403("ban", true, 60 * 30), // 30 min
    OKEX_BAN_403("ban", true, 60 * 30), // 30 min - not in use
    // Last price deviation - lpd;
    LAST_PRICE_DEVIATION("lpd", false),
    // DQL < DQL_open_min - dql;
    BITMEX_DQL_OPEN_MIN("dql", true, 60 * 30), // 30 min
    OKEX_DQL_OPEN_MIN("dql", true, 60 * 30), // 30 min
    // Любая из бирж Outside limits - out;
    BITMEX_OUTSIDE_LIMITS("out", true, 60 * 30), // 30 min
    OKEX_OUTSIDE_LIMITS("out", true, 60 * 30), // 30 min
    // Reconnect / resubscribe Bitmex - rec;
    BITMEX_RECONNECT("rec", false),

    // BusyIsBusy for 6 min любой биржи, Arbitrage state reset - bsy;
    BUSY_6_MIN("bsy", false),
    // Started: First delta calculated - str.
    AT_STARTUP("str", false),
    // Любой флаг перешел в Stopped - stp;
    STOPPED("stp", false),
    // Bitmex дошли до stop! по X-rate limits - xrt;
    BITMEX_X_RATE_LIMIT("xrt", false),
    // Запрос на ребут по зависанию стакана - reb;
    REBOOT_TIMESTAMP_OLD("reb", false),
    // TRADE_SIGNAL - sig.
    TRADE_SIGNAL("sig", false),
    // Round is not done - rnd;
    ROUND_IS_NOT_DONE("rnd", false),

    // ADJ_CORR_MAX_TOTAL (раз в 30сек) - act;
    ADJ_CORR_MAX_TOTAL("act", true, 30),
    //ADJ_CORR_MAX_ATTEMPT (раз в 30 сек) - aca;
    ADJ_CORR_MAX_ATTEMPT("aca", true, 30),
    //PRELIQ_MAX_TOTAL (раз в 30 сек) - pmt;
    PRELIQ_MAX_TOTAL("pmt", true, 30),
    //PRELIQ_MAX_ATTEMPT (раз в 30 сек) - pma;
    PRELIQ_MAX_ATTEMPT("pma", true, 30),
    //RESET_TO_FREE - rtf;
    RESET_TO_FREE("rtf", false),
    //MAX_DELTA_VIOLATED (b_delta >= b_max_delta и/или o_delta >= o_max_delta, раз в 30 сек)) - mdv;
    MAX_DELTA_VIOLATED("mdv", true, 30), // b_delta >= b_max_delta и/или o_delta >= o_max_delta. (раз в 30 сек).
    //SETTINGS_ERRORS (раз в 30 сек) - err.
    SETTINGS_ERRORS("err", true, 30), // initiated from developer
    ;

    private final String shortName;
    private final boolean throttled;
    private Integer throttleSec = 60 * 30; // 30 min
}
