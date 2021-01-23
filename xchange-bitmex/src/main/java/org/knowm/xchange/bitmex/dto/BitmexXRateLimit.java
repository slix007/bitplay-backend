package org.knowm.xchange.bitmex.dto;

import java.time.Instant;

/**
 * Created by Sergey Shurmin on 3/4/18.
 */
public class BitmexXRateLimit {

    private final int xRateLimit;
    private final int xRateLimit1s;
    private final Instant lastUpdate;
    public static final int UNDEFINED = 999;

    public static BitmexXRateLimit initValue() {
        return new BitmexXRateLimit(UNDEFINED, UNDEFINED, Instant.now());
    }

    public BitmexXRateLimit(int xRateLimit, int xRateLimit1s, Instant lastUpdate) {
        this.xRateLimit = xRateLimit;
        this.lastUpdate = lastUpdate;
        this.xRateLimit1s = xRateLimit1s;
    }

    public int getxRateLimit() {
        return xRateLimit;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public int getxRateLimit1s() {
        return xRateLimit1s;
    }

}
