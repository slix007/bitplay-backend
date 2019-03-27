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
    SIM
}
