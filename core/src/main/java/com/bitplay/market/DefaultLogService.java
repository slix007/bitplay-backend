package com.bitplay.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultLogService implements LogService {

    private final Logger tradeLogger;

    public DefaultLogService() {
        tradeLogger = LoggerFactory.getLogger(MarketService.class);
    }

    public DefaultLogService(Logger tradeLogger) {
        this.tradeLogger = tradeLogger;
    }


    public void warn(String s, String... args) {
        tradeLogger.warn(s);
    }

    public void info(String s, String... args) {
        tradeLogger.info(s);
    }

    public void error(String s, String... args) {
        tradeLogger.error(s);
    }
}
