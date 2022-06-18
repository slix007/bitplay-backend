package com.bitplay.xchange.bitmex.service;

import com.bitplay.xchange.bitmex.dto.BitmexXRateLimit;
import com.bitplay.xchange.bitmex.dto.HeadersAware;
import si.mazi.rescu.HttpStatusIOException;

import java.time.Instant;
import java.util.ArrayList;
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
            final String nameRemaining1 = "x-ratelimit-remaining";
            final String nameRemaining1s1 = "x-ratelimit-remaining-1s";
            final String nameReset1 = "x-ratelimit-reset";
            final List<String> remaining = new ArrayList<>();
            final List<String> remaining1s = new ArrayList<>();
            final List<String> reset = new ArrayList<>();
            headers.forEach((k, v) -> {
                        if (v != null && v.size() > 0) {
                            if (nameRemaining1.equalsIgnoreCase(k)) {
                                remaining.add(v.get(0));
                            }
                            if (nameRemaining1s1.equalsIgnoreCase(k)) {
                                remaining1s.add(v.get(0));
                            }
                            if (nameReset1.equalsIgnoreCase(k)) {
                                reset.add(v.get(0));
                            }
                        }
                    }

            );
            final boolean hasRemaining = remaining.size() > 0;
            final boolean hasRemaining1s = remaining1s.size() > 0;
            final boolean hasResetTimestamp = reset.size() > 0;

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
