package com.bitplay.persistance.domain.settings;

import com.bitplay.market.bitmex.BitmexService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.knowm.xchange.currency.CurrencyPair;

import java.math.BigDecimal;

@AllArgsConstructor
@Getter
public enum BitmexContractType implements ContractType {

    XBTUSD_Perpetual("XBT", BigDecimal.valueOf(0.5), 1),
    XBTUSD_NextWeek("XBT", BigDecimal.valueOf(0.5), 1),
    XBTUSD_Quoter("XBT", BigDecimal.valueOf(0.5), 1),
    XBTUSD_BiQuoter("XBT", BigDecimal.valueOf(0.5), 1),
    ETHUSD_Perpetual("XBT", BigDecimal.valueOf(0.05), 2),
    ETHUSD_NextWeek("XBT", BigDecimal.valueOf(0.05), 2),
    ;

//    private CurrencyPair currencyPair;
    private String firstCurrency; // XBT, ETH
    private BigDecimal tickSize;
    private Integer scale;

    public static BitmexContractType parse(CurrencyPair currencyPair) {
        BitmexContractType resultType = null;
        if (currencyPair != null) {
            String first = currencyPair.base.getCurrencyCode();
            String second = currencyPair.counter.getCurrencyCode();
            for (BitmexContractType type : BitmexContractType.values()) {
                CurrencyPair pair = type.getCurrencyPair();
                if (first.equals(pair.base.getCurrencyCode()) && second.equals(pair.counter.getCurrencyCode())) {
                    resultType = type;
                    break;
                }
            }
        }
        return resultType;
    }

    public CurrencyPair getCurrencyPair() {
        throw new IllegalArgumentException("Use Settings.getBitmexCurrencyPair");
    }

    public String getSymbol() {
        return this.name();
    }

    public boolean isEth() {
        return this.name().startsWith("ETH");
    }

    @Override
    public String getMarketName() {
        return BitmexService.NAME;
    }

    @Override
    public String getName() {
        return name();
    }


}
