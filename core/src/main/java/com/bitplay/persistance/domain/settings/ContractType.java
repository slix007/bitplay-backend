package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;
import org.knowm.xchange.currency.CurrencyPair;

public interface ContractType {
    BigDecimal getTickSize();
    Integer getScale();

    boolean isEth();

    CurrencyPair getCurrencyPair();

    String toString();
}
