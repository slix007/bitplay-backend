package com.bitplay.market.model;

import java.util.Date;

/**
 * Created by Sergey Shurmin on 3/4/18.
 */
public class BitmexXRateLimit {

    private volatile Integer xRateLimit;
    private volatile Date lastUpdate;

    public BitmexXRateLimit(Integer xRateLimit, Date lastUpdate) {
        this.xRateLimit = xRateLimit;
        this.lastUpdate = lastUpdate;
    }

    public Integer getxRateLimit() {
        return xRateLimit;
    }

    public Date getLastUpdate() {
        return lastUpdate;
    }
}
