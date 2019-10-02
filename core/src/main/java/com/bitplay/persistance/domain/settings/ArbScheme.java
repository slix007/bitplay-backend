package com.bitplay.persistance.domain.settings;

/**
 * Arbitrage trading scheme.
 *
 * Created by Sergey Shurmin on 11/28/17.
 */
public enum ArbScheme {
    /**
     * Consistently bitmex then okex.
     */
    CON_B_O,
    /**
     * Simultaneously set both orders.
     */
    SIM,
    /**
     * Consistently bitmex(one portion) then okex(multi-portions).
     */
    CON_B_O_PORTIONS;

    public boolean isConBo() {
        return this == CON_B_O || this == CON_B_O_PORTIONS;
    }
}
