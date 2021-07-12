package com.bitplay.market.model;

public enum DqlState {
    ANY_ORDERS,
    CLOSE_ONLY,
    PRELIQ,
    KILLPOS,
    ;

    public boolean isActiveClose() {
        return this == PRELIQ || this == KILLPOS;
    }

    public boolean isClose() {
        return this == CLOSE_ONLY || this == PRELIQ || this == KILLPOS;
    }

}
