package com.bitplay.external;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public enum NotifyType {
    //Список алармов для passive:
    //1. Stop all actions (MDC и signal limit) *
    //2. Preliq
    //3. Forbidden
    //4. e_best bitmex / e_best okex > 1; e_best bitmex / e_best okex < 2; *
    //5. Любая коррекция, подгонка
    //6. Бан на бирже (HTTP status was not ok: 403).
    //7. Изменение цены BTC и ETH более, чем на 10% в час *
    //8. DQL < DQL_open_min
    //9. Любая из бирж Outside limits
    //* - добавить также в дневной режим.

    STOP_ALL_ACTIONS_BY_MDC_TIMER(false),
    PRELIQ(false),
    FORBIDDEN(true, 60 * 30), // 30 min
    E_BEST_VIOLATION(true, 60 * 60), // 60 min
    CORR_NOTIFY(false),
    ADJ_NOTIFY(false),
    BITMEX_BAN_403(true, 60 * 30), // 30 min
    OKEX_BAN_403(true, 60 * 30), // 30 min
    LAST_PRICE_DEVIATION(false),
    BITMEX_DQL_OPEN_MIN(true, 60 * 30), // 30 min
    OKEX_DQL_OPEN_MIN(true, 60 * 30), // 30 min
    BITMEX_OUTSIDE_LIMITS(true, 60 * 30), // 30 min
    OKEX_OUTSIDE_LIMITS(true, 60 * 30), // 30 min
    BITMEX_RECONNECT(false),

    BUSY_6_MIN(false),
    AT_STARTUP(false),
    STOPPED(false),
    BITMEX_X_RATE_LIMIT(false),
    REBOOT_TIMESTAMP_OLD(false),
    TRADE_SIGNAL(false),
    ROUND_IS_NOT_DONE(false),
    ;

    private final boolean throttled;
    private Integer throttleSec = 60 * 30; // 30 min
}
