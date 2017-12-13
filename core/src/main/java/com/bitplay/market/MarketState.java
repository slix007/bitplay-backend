package com.bitplay.market;

/**
 * Created by Sergey Shurmin on 8/8/17.
 */
public enum MarketState {
    READY,
    ARBITRAGE,
    WAITING_ARB,
    MOVING,
    TAKER_IN_PROGRESS,
    SWAP,
    SWAP_AWAIT,
    SYSTEM_OVERLOADED,
    STOPPED,
}
