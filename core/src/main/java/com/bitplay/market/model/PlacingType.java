package com.bitplay.market.model;

/**
 * Created by Sergey Shurmin on 11/19/17.
 */
public enum PlacingType {
    MAKER,
    TAKER,
    TAKER_FOK, // taker fill or kill
    HYBRID,
    MAKER_TICK,
    HYBRID_TICK,
    ;

    public boolean isTaker() {
        return this == TAKER || this == TAKER_FOK;
    }

    public boolean isNonTaker() {
        return !this.isTaker();
    }
}
