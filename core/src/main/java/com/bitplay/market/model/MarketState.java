package com.bitplay.market.model;

/**
 * Created by Sergey Shurmin on 8/8/17.
 */
public enum MarketState {
    READY,
    ARBITRAGE,
    WAITING_ARB,
    MOVING,
    PLACING_ORDER,
    SWAP,
    SWAP_AWAIT,
    SYSTEM_OVERLOADED,
    FORBIDDEN,
    PRELIQ,
    ;

}
