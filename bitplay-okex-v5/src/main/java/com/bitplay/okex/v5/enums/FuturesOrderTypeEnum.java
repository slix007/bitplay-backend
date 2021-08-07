package com.bitplay.okex.v5.enums;

public enum FuturesOrderTypeEnum {
    // Order type
    //market: market order
    //limit: limit order
    //post_only: Post-only order
    //fok: Fill-or-kill order
    //ioc: Immediate-or-cancel order
    //optimal_limit_ioc :Market order with immediate-or-cancel order (applicable only to Futures and Perpetual swap).

    MARKET("market"), //not in use
    LIMIT("limit"), //not in use
    POST_ONLY("post_only"),
    FILL_OR_KILL("fok"),
    IMMEDIATE_OR_CANCEL("ioc"),
    OPTIMAL_LIMIT_IOC("optimal_limit_ioc"),
    ;

    private String code;

    FuturesOrderTypeEnum(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
