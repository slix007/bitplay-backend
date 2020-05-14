package com.bitplay.persistance.domain.settings;

import lombok.Data;
import org.knowm.xchange.currency.CurrencyPair;

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

    public boolean isEth() {
        return bitmexContractType.isEth();
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
}
