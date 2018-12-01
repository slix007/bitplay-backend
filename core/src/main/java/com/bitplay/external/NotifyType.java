package com.bitplay.external;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum NotifyType {
    //Список алармов для night:
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

    STOP_ALL_ACTIONS(false, true),
    PRELIQ(false, true),
    FORBIDDEN(true, true),
    E_BEST_VIOLATION(true, true),
    CORR_NOTIFY(false, true),
    BITMEX_BAN_403(true, true),
    OKEX_BAN_403(true, true),
    PRICE_CHANGE_10(false, true),
    BITMEX_DQL_OPEN_MIN(true, true),
    OKEX_DQL_OPEN_MIN(true, true),
    BITMEX_OUTSIDE_LIMITS(true, true),
    OKEX_OUTSIDE_LIMITS(true, true),
    BITMEX_RECONNECT(false, false),

    BUSY_6_MIN(false, false),
    AT_STARTUP(false, false),
    BITMEX_X_RATE_LIMIT(false, false),
    TIMESTAMP_OLD(false, false),
    TRADE_SIGNAL(false, false),
    ;

    private boolean throttled;
    private boolean night;
}
