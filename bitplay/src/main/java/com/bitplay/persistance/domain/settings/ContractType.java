package com.bitplay.persistance.domain.settings;

import com.bitplay.xchange.currency.CurrencyPair;
import java.math.BigDecimal;

public interface ContractType {

    BigDecimal getTickSize();

    Integer getScale();

    boolean isBtc();

    /**
     * Quanto - works only with the main tool(BTC)<br>
     *     https://www.bitmex.com/app/perpetualContractsGuide#What-is-a-Quanto-Contract
     *
     * Bitmex: XBTUSD
     * <br>
     * Okex: {Currency}_BTC (ETH_BTC,XRP_BTC,...)
     */
    boolean isQuanto();

    CurrencyPair getCurrencyPair();

    String toString();

    String getMarketName();

    String getName();

    BigDecimal defaultLeverage();
}
