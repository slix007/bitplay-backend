package com.bitplay.persistance.domain.settings;

/**
 * Created by Sergey Shurmin on 11/19/17.
 */
public enum PlacingType {
    MAKER,
    TAKER,
    TAKER_FOK, // taker fill or kill
    TAKER_IOC, // taker ImmediateOrCancel
    HYBRID,
    MAKER_TICK,
    HYBRID_TICK,
    ;

    public boolean isTaker() {
        return this == TAKER || this == TAKER_FOK || this == TAKER_IOC;
    }

    public boolean isNonTaker() {
        return !this.isTaker();
    }
}
