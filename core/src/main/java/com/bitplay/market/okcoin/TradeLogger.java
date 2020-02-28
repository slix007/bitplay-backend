package com.bitplay.market.okcoin;

import com.bitplay.arbitrage.dto.ArbType;
import com.bitplay.market.LogService;
import com.bitplay.persistance.domain.settings.ContractType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeLogger implements LogService {

    private final Logger tradeLogger;
    private final String contName;

    public TradeLogger(ContractType contractType, ArbType arbType) {
        if (arbType == ArbType.LEFT) {
            tradeLogger = LoggerFactory.getLogger("LEFT_TRADE_LOG");
        } else {
            tradeLogger = LoggerFactory.getLogger("RIGHT_TRADE_LOG");
        }
        contName = contractType.getName();
    }

    private String getMsg(String s, String[] args) {
        return s;
    }


    private String cont(String... args) {
        if (args != null && args.length > 0) {
            return " cont=" + args[0];
        }
        return " cont=" + contName;
    }

    public void warn(String s, String... args) {
        tradeLogger.warn(s + cont(args));
    }

    public void info(String s, String... args) {
        tradeLogger.info(s + cont(args));
    }

    public void error(String s, String... args) {
        tradeLogger.error(s + cont(args));
    }
}
