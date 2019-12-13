package com.bitplay.market.model;

/**
 * Created by Sergey Shurmin on 8/8/17.
 */
public enum MarketState {
    READY,
    STARTING_VERT,
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
