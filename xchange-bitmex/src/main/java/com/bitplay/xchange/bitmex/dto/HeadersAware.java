package com.bitplay.xchange.bitmex.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import si.mazi.rescu.HttpResponseAware;

public interface HeadersAware extends HttpResponseAware {

    Map<String, List<String>> headers = new HashMap<>();

    @Override
    default void setResponseHeaders(Map<String, List<String>> newHeaders) {
        headers.clear();
        headers.putAll(newHeaders);
    }

    // .get("X-RateLimit-Limit")   150 - not logged in
    // .get("X-RateLimit-Remaining") 149
    // .get("X-RateLimit-Reset") ?? 1549453508  - the UNIX timestamp
    // .get("Date")  Wed, 06 Feb 2019 11:45:07 GMT - the time string
    // x-ratelimit-remaining-1s
    @Override
    default Map<String, List<String>> getResponseHeaders() {
        return headers;
    }
}
