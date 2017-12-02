package com.bitplay.persistance.domain.settings;

/**
 * Arbitrage trading scheme.
 *
 * Created by Sergey Shurmin on 11/28/17.
 */
public enum ArbScheme {
    /**
     * Maker - Taker
     */
    MT,
    /**
     * Maker - Taker, but the second only when the first has filled
     */
    MT2,
    /**
     * Taker - Taker
     */
    TT
}
