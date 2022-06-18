package com.bitplay.xchange.bitmex.service;

import com.bitplay.xchange.bitmex.dto.BitmexXRateLimit;
import com.bitplay.xchange.bitmex.dto.HeadersAware;
import si.mazi.rescu.HttpStatusIOException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class BitmexStateService {

    private final AtomicReference<BitmexXRateLimit> ref = new AtomicReference<>(BitmexXRateLimit.initValue());

    public BitmexXRateLimit getxRateLimit() {
        return ref.get();
    }

    public void setXrateLimit(HeadersAware responseWithHeader) {
        final Map<String, List<String>> headers = responseWithHeader.getResponseHeaders();

        setXrateLimit(headers);
    }

    public void setXrateLimit(HttpStatusIOException e) {
        final Map<String, List<String>> headers = e.getResponseHeaders();
        setXrateLimit(headers);
    }

    private void setXrateLimit(Map<String, List<String>> headers) {
        if (headers != null) {
//            final List<String> limit = headers.get("x-ratelimit-limit");
            final String remaining1 = "x-ratelimit-remaining";
            final String remaining2 = "X-RateLimit-Remaining";
            final String remaining1s1 = "x-ratelimit-remaining-1s";
            final String remaining1s2 = "X-RateLimit-Remaining-1s";
            final String reset1 = "x-ratelimit-reset";
            final String reset2 = "X-RateLimit-Reset";
            final List<String> remaining = headers.containsKey(remaining1)
                    ? headers.get(remaining1)
                    : headers.get(remaining2);
            final List<String> remaining1s = headers.containsKey(remaining1)
                    ? headers.get(remaining1s1)
                    : headers.get(remaining1s2);
            final List<String> reset = headers.containsKey(reset1)
                    ? headers.get(reset1)
                    : headers.get(reset2);

            final boolean hasRemaining = remaining != null && remaining.size() > 0;
            final boolean hasRemaining1s = remaining1s != null && remaining1s.size() > 0;
            final boolean hasResetTimestamp = reset != null && reset.size() > 0;

            final BitmexXRateLimit xRateLimit = ref.get();
            final int xRateLimitRemaining = hasRemaining
                    ? Integer.parseInt(remaining.get(0))
                    : xRateLimit.getxRateLimit();
            final int xRateLimitRemaining1s = hasRemaining1s
                    ? Integer.parseInt(remaining1s.get(0))
                    : xRateLimit.getxRateLimit1s();
            final Instant resetAt = hasResetTimestamp && hasRemaining
                    ? Instant.ofEpochSecond(Integer.parseInt(reset.get(0)))
                    : xRateLimit.getResetAt();
            final Instant lastUpdate = hasRemaining
                    ? Instant.now()
                    : xRateLimit.getLastUpdate();
            final Instant lastUpdate1s = hasRemaining1s
                    ? Instant.now()
                    : xRateLimit.getLastUpdate1s();

            final BitmexXRateLimit newValue = new BitmexXRateLimit(
                    xRateLimitRemaining,
                    resetAt,
                    lastUpdate,
                    xRateLimitRemaining1s,
                    lastUpdate1s
            );
            ref.set(newValue);
        }
    }

}
