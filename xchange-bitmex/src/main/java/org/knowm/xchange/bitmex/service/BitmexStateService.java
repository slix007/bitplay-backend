package org.knowm.xchange.bitmex.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.knowm.xchange.bitmex.dto.BitmexXRateLimit;
import org.knowm.xchange.bitmex.dto.HeadersAware;
import si.mazi.rescu.HttpStatusIOException;

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
            final List<String> remaining = headers.get("x-ratelimit-remaining");
            final List<String> remaining1s = headers.get("x-ratelimit-remaining-1s");
            final List<String> reset = headers.get("x-ratelimit-reset");

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

            ref.set(new BitmexXRateLimit(
                    xRateLimitRemaining,
                    resetAt,
                    lastUpdate,
                    xRateLimitRemaining1s,
                    lastUpdate1s
            ));
        }
    }

    public void resetXrateLimit() {
        ref.set(BitmexXRateLimit.initValue());
    }
}
