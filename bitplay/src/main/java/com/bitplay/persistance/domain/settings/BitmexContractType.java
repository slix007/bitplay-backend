package com.bitplay.persistance.domain.settings;

import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.xchange.currency.CurrencyPair;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@AllArgsConstructor
@Getter
public enum BitmexContractType implements ContractType {

    // primary tools
    XBTUSD_Perpetual(BitmexContractTypeFirst.XBT.name(), BigDecimal.valueOf(0.5), 1, null),
    XBTUSD_Quarter(BitmexContractTypeFirst.XBT.name(), BigDecimal.valueOf(0.5), 1, null),
    XBTUSD_BiQuarter(BitmexContractTypeFirst.XBT.name(), BigDecimal.valueOf(0.5), 1, null),
    // secondary tools
    ETHUSD_Perpetual(BitmexContractTypeFirst.ETH.name(), BigDecimal.valueOf(0.05), 2, BigDecimal.valueOf(0.000001)),
    ETHUSD_Quarter(BitmexContractTypeFirst.ETH.name(), BigDecimal.valueOf(0.05), 2, BigDecimal.valueOf(0.000001)),
    LINKUSDT_Perpetual(BitmexContractTypeFirst.LINK.name(), BigDecimal.valueOf(0.001), 3, BigDecimal.valueOf(0.00001)),
    XRPUSD_Perpetual(BitmexContractTypeFirst.XRP.name(), BigDecimal.valueOf(0.0001), 4, BigDecimal.valueOf(0.0002)),
    LTCUSD_Perpetual(BitmexContractTypeFirst.LTC.name(), BigDecimal.valueOf(0.01), 2, BigDecimal.valueOf(0.000002)),
    BCHUSD_Perpetual(BitmexContractTypeFirst.BCH.name(), BigDecimal.valueOf(0.05), 2, BigDecimal.valueOf(0.000001)),
    ;

    //    private CurrencyPair currencyPair;
    private String firstCurrency; // XBT, ETH
    private BigDecimal tickSize;
    private Integer scale;
    private BigDecimal bm; // bitcoin multiplier

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
            } else if (first.equals(BitmexContractTypeFirst.LTC.name())) {
                resultType = BitmexContractType.LTCUSD_Perpetual;
            } else if (first.equals(BitmexContractTypeFirst.LINK.name())) {
                resultType = BitmexContractType.LINKUSDT_Perpetual;
            } else if (first.equals(BitmexContractTypeFirst.XRP.name())) {
                resultType = BitmexContractType.XRPUSD_Perpetual;
            } else if (first.equals(BitmexContractTypeFirst.BCH.name())) {
                resultType = BitmexContractType.BCHUSD_Perpetual;
            }
        }
        return resultType;
    }

    public CurrencyPair getCurrencyPair() {
        throw new IllegalArgumentException("Use Settings.getBitmexCurrencyPair");
    }

    public String getSymbol() {
        switch (this) {
            case XBTUSD_Perpetual:
                return "XBTUSD";
            case ETHUSD_Perpetual:
                return "ETHUSD";
            case LINKUSDT_Perpetual:
                return "LINKUSD";
            case XRPUSD_Perpetual:
                return "XRPUSD";
            case LTCUSD_Perpetual:
                return "LTCUSD";
            case BCHUSD_Perpetual:
                return "BCHUSD";
        }
        return "";
    }


    @Override
    public boolean isBtc() {
        return this.name().startsWith("XBT");
    }

    public boolean isQuanto() {
        return this.name().startsWith("ETH")
                || this == LINKUSDT_Perpetual
                || this == XRPUSD_Perpetual
                || this == LTCUSD_Perpetual
                || this == BCHUSD_Perpetual;
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

    @Override
    public BigDecimal defaultLeverage() {
        switch (this) {
            case XBTUSD_Perpetual:
            case XBTUSD_BiQuarter:
            case XBTUSD_Quarter:
                return BigDecimal.valueOf(100);
            case LINKUSDT_Perpetual:
            case XRPUSD_Perpetual:
            case ETHUSD_Perpetual:
            case ETHUSD_Quarter:
                return BigDecimal.valueOf(50);
            case LTCUSD_Perpetual:
                return BigDecimal.valueOf(33.33);
            case BCHUSD_Perpetual:
                return BigDecimal.valueOf(25);
        }
        return BigDecimal.valueOf(100);
    }

    /**
     * Bitcoin multiplier.
     *
     * @see <a href="https://docs.google.com/spreadsheets/d/1kE6vmLo3XugGwrHSKETuAAbvZclL4iOlmQGijrjzKj8/edit#gid=0">
     * new instruments of Dec 2020
     * </a>
     * <p>
     * @see <a href="https://trello.com/c/MXeeFiNO/1165-%D0%BD%D0%BE%D0%B2%D1%8B%D0%B5-%D0%B8%D0%BD%D1%81%D1%82%D1%80%D1%83%D0%BC%D0%B5%D0%BD%D1%82%D1%8B-bitmex-%D0%BA%D0%B2%D0%B0%D0%BD%D1%82%D0%BE">
     * trello task
     * </a>
     * </p>
     */
    public BigDecimal getBm() {
        return bm;
    }

    public boolean isSwap() {
        return name().endsWith("Perpetual");
    }
}
