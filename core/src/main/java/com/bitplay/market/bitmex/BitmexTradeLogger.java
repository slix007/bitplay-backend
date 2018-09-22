package com.bitplay.market.bitmex;

import com.bitplay.market.LogService;
import com.bitplay.persistance.domain.settings.BitmexContractType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BitmexTradeLogger implements LogService {

    private static final Logger tradeLogger = LoggerFactory.getLogger("BITMEX_TRADE_LOG");

    @Autowired
    private BitmexService bitmexService;

    private String cont() {
        return String.format(" cont=%s", ((BitmexContractType) bitmexService.getContractType()).name());
    }

    public void warn(String s) {
        tradeLogger.warn(s + cont());
    }

    public void info(String s) {
        tradeLogger.info(s + cont());
    }

    public void error(String s) {
        tradeLogger.error(s + cont());
    }
}
