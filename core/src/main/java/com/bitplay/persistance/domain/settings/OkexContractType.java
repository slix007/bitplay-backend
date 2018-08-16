package com.bitplay.persistance.domain.settings;

import info.bitrich.xchangestream.okex.dto.Tool;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.okcoin.FuturesContract;

@AllArgsConstructor
@Getter
public enum OkexContractType implements ContractType {

    BTC_ThisWeek(FuturesContract.ThisWeek, CurrencyPair.BTC_USD),
    BTC_NextWeek(FuturesContract.NextWeek, CurrencyPair.BTC_USD),
    BTC_Quarter(FuturesContract.Quarter, CurrencyPair.BTC_USD),
    ETH_ThisWeek(FuturesContract.ThisWeek, CurrencyPair.ETH_USD),
    ETH_NextWeek(FuturesContract.NextWeek, CurrencyPair.ETH_USD),
    ETH_Quarter(FuturesContract.Quarter, CurrencyPair.ETH_USD),
    ;

    private FuturesContract futuresContract;
    private CurrencyPair currencyPair;

    public Tool getBaseTool() {
        String baseTool = currencyPair.base.getCurrencyCode();
        return Tool.valueOf(baseTool);
    }
}
