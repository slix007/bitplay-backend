package com.bitplay.okex.v5.enums;

public enum FuturesOrderTypeEnum {

    NORMAL_LIMIT("0"),
    POST_ONLY("1"),
    FILL_OR_KILL("2"),
    IMMEDIATE_OR_CANCEL("3"),
    ;

    private String code;

    FuturesOrderTypeEnum(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
