package com.bitplay.market.model;

public enum DqlState {
    ANY_ORDERS,
    CLOSE_ONLY,
    PRELIQ,
    KILLPOS,
    ;

    public boolean isClose() {
        return this == PRELIQ || this == KILLPOS;
    }
}
