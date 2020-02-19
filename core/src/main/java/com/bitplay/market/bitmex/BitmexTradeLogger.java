package com.bitplay.market.bitmex;

import com.bitplay.market.LogService;
import com.bitplay.persistance.domain.settings.BitmexContractType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BitmexTradeLogger implements LogService {

    private static final Logger tradeLogger = LoggerFactory.getLogger("BITMEX_TRADE_LOG");

    private String cont(BitmexContractType bitmexContractType) {
        return String.format(" cont=%s", bitmexContractType.name());
    }

    private String getMsg(String s, String[] args) {
        if (args != null && args.length > 0) {
            String contName = args[0];
            return String.format("%s cont=%s", s, contName);
        }
        return s;
    }

    public void warn(String s, String... args) {
        tradeLogger.warn(getMsg(s, args));
    }

    public void info(String s, String... args) {
        tradeLogger.info(getMsg(s, args));
    }

    public void error(String s, String... args) {
        tradeLogger.error(getMsg(s, args));
    }

}
