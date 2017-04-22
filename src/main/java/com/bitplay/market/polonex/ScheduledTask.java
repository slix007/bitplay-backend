package com.bitplay.market.polonex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Created by Sergey Shurmin on 4/15/17.
 */
@Component
public class ScheduledTask {

    private final static Logger logger = LoggerFactory.getLogger("DEBUG_LOG");

    @Autowired
    PoloniexService poloniexService;

    @Scheduled(fixedRate = 100)
    public void reportCurrentTime() {
        poloniexService.fetchOrderBook();
    }
}
