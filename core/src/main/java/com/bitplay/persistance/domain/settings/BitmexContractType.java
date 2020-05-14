package com.bitplay.persistance.domain.settings;

import com.bitplay.market.bitmex.BitmexService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.knowm.xchange.currency.CurrencyPair;

import java.math.BigDecimal;

@AllArgsConstructor
@Getter
public enum BitmexContractType implements ContractType {

    XBTUSD_Perpetual(BitmexContractTypeFirst.XBT.name(), BigDecimal.valueOf(0.5), 1),
    XBTUSD_Quarter(BitmexContractTypeFirst.XBT.name(), BigDecimal.valueOf(0.5), 1),
    XBTUSD_BiQuarter(BitmexContractTypeFirst.XBT.name(), BigDecimal.valueOf(0.5), 1),
    ETHUSD_Perpetual(BitmexContractTypeFirst.ETH.name(), BigDecimal.valueOf(0.05), 2),
    ETHUSD_Quarter(BitmexContractTypeFirst.ETH.name(), BigDecimal.valueOf(0.05), 2),
    ;

    //    private CurrencyPair currencyPair;
    private String firstCurrency; // XBT, ETH
    private BigDecimal tickSize;
    private Integer scale;

    public static BitmexContractType parse(CurrencyPair orderCurrencyPair, BitmexCtList bitmexContractTypes) {
        BitmexContractType resultType = null;
        if (orderCurrencyPair != null) {
            String first = orderCurrencyPair.base.getCurrencyCode();
            String second = orderCurrencyPair.counter.getCurrencyCode();
            String both = first + second;
            if (first.equals(BitmexContractTypeFirst.XBT.name())) {
                if (both.equals("XBTUSD")) {
                    resultType = BitmexContractType.XBTUSD_Perpetual;
                } else if (both.equals(bitmexContractTypes.getBtcUsdQuoter())) {
                    resultType = BitmexContractType.XBTUSD_Quarter;
                } else if (both.equals(bitmexContractTypes.getBtcUsdBiQuoter())) {
                    resultType = BitmexContractType.XBTUSD_BiQuarter;
                }
            } else if (first.equals(BitmexContractTypeFirst.ETH.name())) {
                if (both.equals("ETHUSD")) {
                    resultType = BitmexContractType.ETHUSD_Perpetual;
                } else if (both.equals(bitmexContractTypes.getEthUsdQuoter())) {
                    resultType = BitmexContractType.ETHUSD_Quarter;
                }
            }
        }
        return resultType;
    }

    public CurrencyPair getCurrencyPair() {
        throw new IllegalArgumentException("Use Settings.getBitmexCurrencyPair");
    }

    public String getSymbol() {
        if (this == XBTUSD_Perpetual) {
            return "XBTUSD";
        }
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

    public String getFirstCurrency() {
        return firstCurrency;
    }
}
