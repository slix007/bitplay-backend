package com.bitplay.persistance.domain.settings;

import org.knowm.xchange.currency.CurrencyPair;

import java.math.BigDecimal;

public interface ContractType {
    BigDecimal getTickSize();

    Integer getScale();

    boolean isEth();

    CurrencyPair getCurrencyPair();

    String toString();

    String getMarketName();

    String getName();
}
