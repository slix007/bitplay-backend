package com.bitplay.okex.v3.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FuturesDirectionEnum {

    LONG("long"), SHORT("short");
    private String direction;

    FuturesDirectionEnum(String direction) {
        this.direction = direction;
    }

    @JsonValue
    public String getDirection() {
        return direction;
    }
}
