package org.knowm.xchange.bitmex.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.knowm.xchange.bitmex.dto.BitmexXRateLimit;
import org.knowm.xchange.bitmex.dto.HeadersAware;
import si.mazi.rescu.HttpStatusIOException;

public class BitmexStateService {

    private volatile BitmexXRateLimit xRateLimit = BitmexXRateLimit.initValue();
//    protected volatile BitmexXRateLimit xRateLimitPublic = BitmexXRateLimit.initValue();

    public BitmexXRateLimit getxRateLimit() {
        return xRateLimit;
    }

//    public BitmexXRateLimit getxRateLimitPublic() {
//        return xRateLimitPublic;
//    }

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
            final List<String> remaining = headers.get("X-RateLimit-Remaining");
            if (remaining != null && remaining.size() > 0) {
                final Integer xRateLimitRemaining = Integer.valueOf(remaining.get(0));
                final List<String> reset = headers.get("X-RateLimit-Reset");
                final Instant timestamp = (reset != null && reset.size() > 0)
                        ? Instant.ofEpochSecond(Integer.parseInt(reset.get(0)))
                        : null;
                xRateLimit = new BitmexXRateLimit(xRateLimitRemaining, timestamp);
            }
        }
    }

    public void resetXrateLimit() {
        xRateLimit = BitmexXRateLimit.initValue();
    }
}
