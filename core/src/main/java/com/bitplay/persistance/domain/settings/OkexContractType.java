package com.bitplay.persistance.domain.settings;

import info.bitrich.xchangestream.okex.dto.Tool;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.okcoin.FuturesContract;

@AllArgsConstructor
@Getter
public enum OkexContractType implements ContractType {

    BTC_ThisWeek(FuturesContract.ThisWeek, CurrencyPair.BTC_USD, BigDecimal.valueOf(0.1)),
    BTC_NextWeek(FuturesContract.NextWeek, CurrencyPair.BTC_USD, BigDecimal.valueOf(0.1)),
    BTC_Quarter(FuturesContract.Quarter, CurrencyPair.BTC_USD, BigDecimal.valueOf(0.1)),
    ETH_ThisWeek(FuturesContract.ThisWeek, CurrencyPair.ETH_USD, BigDecimal.valueOf(0.1)),
    ETH_NextWeek(FuturesContract.NextWeek, CurrencyPair.ETH_USD, BigDecimal.valueOf(0.1)),
    ETH_Quarter(FuturesContract.Quarter, CurrencyPair.ETH_USD, BigDecimal.valueOf(0.1)),
    ;

    private FuturesContract futuresContract;
    private CurrencyPair currencyPair;
    private BigDecimal tickSize;

    public Tool getBaseTool() {
        String baseTool = currencyPair.base.getCurrencyCode();
        return Tool.valueOf(baseTool);
    }
}
