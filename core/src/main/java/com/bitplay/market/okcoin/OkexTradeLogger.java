package com.bitplay.market.okcoin;

import com.bitplay.market.LogService;
import com.bitplay.persistance.domain.settings.OkexContractType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class OkexTradeLogger implements LogService {

    private static final Logger tradeLogger = LoggerFactory.getLogger("OKCOIN_TRADE_LOG");

    private final OkCoinService okCoinService;

    private String cont() {
        return String.format(" cont=%s", ((OkexContractType) okCoinService.getContractType()).name());
    }

    public void warn(String s, String... args) {
        tradeLogger.warn(s + cont());
    }

    public void info(String s, String... args) {
        tradeLogger.info(s + cont());
    }

    public void error(String s, String... args) {
        tradeLogger.error(s + cont());
    }
}
