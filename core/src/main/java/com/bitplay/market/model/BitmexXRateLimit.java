package com.bitplay.market.model;

import java.util.Date;

/**
 * Created by Sergey Shurmin on 3/4/18.
 */
public class BitmexXRateLimit {

    private final Integer xRateLimit;
    private final Date lastUpdate;

    public static BitmexXRateLimit initValue() {
        return new BitmexXRateLimit(301, new Date());
    }

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
