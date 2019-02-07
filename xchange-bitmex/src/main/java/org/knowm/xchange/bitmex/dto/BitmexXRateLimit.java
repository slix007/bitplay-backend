package org.knowm.xchange.bitmex.dto;

import java.time.Instant;

/**
 * Created by Sergey Shurmin on 3/4/18.
 */
public class BitmexXRateLimit {

    private final int xRateLimit;
    private final Instant lastUpdate;

    public static BitmexXRateLimit initValue() {
        return new BitmexXRateLimit(301, Instant.now());
    }

    public BitmexXRateLimit(Integer xRateLimit, Instant lastUpdate) {
        this.xRateLimit = xRateLimit;
        this.lastUpdate = lastUpdate;
    }

    public int getxRateLimit() {
        return xRateLimit;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }
}
