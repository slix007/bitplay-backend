package com.bitplay.persistance.domain.settings;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.knowm.xchange.currency.CurrencyPair;

@AllArgsConstructor
@Getter
public enum BitmexContractType implements ContractType {

    XBTUSD(new CurrencyPair("XBT", "USD"), BigDecimal.valueOf(0.5)),
//    XBTH18(new CurrencyPair("XBT", "H18")),
    XBT7D_D95(new CurrencyPair("XBT", "7D_D95"), BigDecimal.valueOf(0.5)),
    XBT7D_U105(new CurrencyPair("XBT", "7D_U105"), BigDecimal.valueOf(0.5)),
    XBTU18(new CurrencyPair("XBT", "U18"), BigDecimal.valueOf(0.5)),
    XBTZ18(new CurrencyPair("XBT", "Z18"), BigDecimal.valueOf(0.5)),
    ETHUSD(new CurrencyPair("ETH", "USD"), BigDecimal.valueOf(0.05)),
    ETHU18(new CurrencyPair("ETH", "U18"), BigDecimal.valueOf(0.05)),
    ;

    private CurrencyPair currencyPair;
    private BigDecimal tickSize;

    public String getSymbol() {
        return this.name();
    }

    public boolean isEth() {
        return this.name().startsWith("ETH");
    }
}
