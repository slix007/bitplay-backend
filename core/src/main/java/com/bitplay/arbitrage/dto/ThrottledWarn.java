package com.bitplay.arbitrage.dto;

import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ThrottledWarn {

    private Logger log;
    private int throttleSec = 0;
    private final Map<String, Instant> sentLogs = new HashMap<>();

    public ThrottledWarn(Logger log, int throttleSec) {
        this.log = log;
        this.throttleSec = throttleSec;
    }

    public void info(String s) {
        add(s, (str) -> log.info(str));
    }

    public void warn(String s) {
        add(s, (str) -> log.warn(str));
    }

    public void error(String s) {
        add(s, (str) -> log.error(str));
    }

    public void error(String s, Throwable e) {
        add(s, (str) -> log.error(str, e));
    }

    public void add(String s, Consumer<String> logFunc) {
        final Instant lastSent = sentLogs.get(s);
        if (lastSent == null || Duration.between(lastSent, Instant.now()).getSeconds() > throttleSec) {
            sentLogs.put(s, Instant.now());
            logFunc.accept(s);
        }
    }
}
