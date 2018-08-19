package com.bitplay.persistance.domain.settings;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.knowm.xchange.currency.CurrencyPair;

@AllArgsConstructor
@Getter
public enum BitmexContractType implements ContractType {

    XBTUSD(new CurrencyPair("XBT", "USD")),
//    XBTH18(new CurrencyPair("XBT", "H18")),
    XBT7D_D95(new CurrencyPair("XBT", "7D_D95")),
    XBT7D_U105(new CurrencyPair("XBT", "7D_U105")),
    XBTU18(new CurrencyPair("XBT", "U18")),
    XBTZ18(new CurrencyPair("XBT", "Z18")),
    ETHUSD(new CurrencyPair("ETH", "USD")),
    ETHU18(new CurrencyPair("ETH", "U18")),
    ;

    private CurrencyPair currencyPair;

    public String getSymbol() {
        return this.name();
    }
}
