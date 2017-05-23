package com.bitplay.arbitrage;

/**
 * Created by Sergey Shurmin on 5/22/17.
 */
public class FlagOpenOrder {
    private Boolean firstReady = true;
    private Boolean secondReady = true;

    public void setFirstReady(Boolean firstReady) {
        this.firstReady = firstReady;
    }

    public void setSecondReady(Boolean secondReady) {
        this.secondReady = secondReady;
    }

    public Boolean isReady() {
        return firstReady && secondReady;
    }
}
