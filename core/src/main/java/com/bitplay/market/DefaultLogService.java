package com.bitplay.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultLogService implements LogService {

    private static final Logger tradeLogger = LoggerFactory.getLogger(MarketService.class);


    public void warn(String s) {
        tradeLogger.warn(s);
    }

    public void info(String s) {
        tradeLogger.info(s);
    }

    public void error(String s) {
        tradeLogger.error(s);
    }
}