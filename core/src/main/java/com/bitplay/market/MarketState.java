package com.bitplay.market;

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
    STOPPED,
    FORBIDDEN;

    public boolean isStopped() {
        return this == STOPPED || this == FORBIDDEN;
    }
}
