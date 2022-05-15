package com.bitplay.persistance.domain.settings;

import com.bitplay.xchange.currency.CurrencyPair;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BitmexContractTypeEx implements ContractType {

    private final BitmexContractType bitmexContractType;
    private final CurrencyPair currencyPair;

    public CurrencyPair getCurrencyPair() {
        return currencyPair;
    }

    public String getSymbol() {
        return currencyPair.base.getCurrencyCode() + currencyPair.counter.getCurrencyCode();
    }

    @Override
    public boolean isBtc() {
        return bitmexContractType.isBtc();
    }

    @Override
    public boolean isQuanto() {
        return bitmexContractType.isQuanto();
    }

    @Override
    public String getMarketName() {
        return bitmexContractType.getMarketName();
    }

    @Override
    public String getName() {
        return bitmexContractType.getName();
    }

    @Override
    public BigDecimal getTickSize() {
        return bitmexContractType.getTickSize();
    }

    @Override
    public Integer getScale() {
        return bitmexContractType.getScale();
    }

    @Override
    public BigDecimal defaultLeverage() {
        return bitmexContractType.defaultLeverage();
    }

    static public Integer getFundingScale(String currencyCode) {
        int scale;
        switch (currencyCode) {
            case "BTC":
            case "XBT":
                scale = 0;
                break;
//            case "ETH":
//            case "BCH":
//            case "LTC":
//                scale = 2;
//                break;
            case "LINK":
            case "XRP":
            case "LTC":
                scale = 4;
                break;
            default:
                scale = 2;
        }
        return scale;
    }

}
