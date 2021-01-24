package org.knowm.xchange.bitmex.dto;

import java.time.Instant;

/**
 * Created by Sergey Shurmin on 3/4/18.
 */
public class BitmexXRateLimit {

    private final int xRateLimit;
    private final Instant resetAt;
    private final Instant lastUpdate;
    private final int xRateLimit1s;
    private final Instant resetAt1s;
    private final Instant lastUpdate1s;
    public static final int UNDEFINED = 999;

    public static BitmexXRateLimit initValue() {
        return new BitmexXRateLimit(UNDEFINED, Instant.now(), Instant.now(), UNDEFINED, Instant.now(), Instant.now());
    }

    public BitmexXRateLimit(int xRateLimit,
            Instant resetAt,
            Instant lastUpdate,
            int xRateLimit1s,
            Instant resetAt1s,
            Instant lastUpdate1s
    ) {
        this.xRateLimit = xRateLimit;
        this.resetAt = resetAt;
        this.lastUpdate = lastUpdate;
        this.xRateLimit1s = xRateLimit1s;
        this.resetAt1s = resetAt1s;
        this.lastUpdate1s = lastUpdate1s;
    }

    public int getxRateLimit() {
        return xRateLimit;
    }

    public int getxRateLimit1s() {
        return xRateLimit1s;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public Instant getResetAt() {
        return resetAt;
    }

    public Instant getResetAt1s() {
        return resetAt1s;
    }

    public Instant getLastUpdate1s() {
        return lastUpdate1s;
    }
}
