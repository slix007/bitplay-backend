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
    L_with_R,
    /** deprecated */
    CON_B_O,
    /**
     * Simultaneously set both orders.
     */
    R_wait_L,
    /** deprecated */
    SIM,
    /**
     * Consistently bitmex(one portion) then okex(multi-portions).
     */
    R_wait_L_portions,
    /** deprecated */
    CON_B_O_PORTIONS;

    public boolean isConBo() {
        return this == L_with_R || this == R_wait_L_portions;
    }
}
