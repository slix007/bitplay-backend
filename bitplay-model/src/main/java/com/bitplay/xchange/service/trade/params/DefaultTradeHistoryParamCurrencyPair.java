package com.bitplay.xchange.service.trade.params;

import com.bitplay.xchange.currency.CurrencyPair;

public class DefaultTradeHistoryParamCurrencyPair implements TradeHistoryParamCurrencyPair {

  private CurrencyPair pair;

  public DefaultTradeHistoryParamCurrencyPair() {
  }

  public DefaultTradeHistoryParamCurrencyPair(CurrencyPair pair) {
    this.pair = pair;
  }

  @Override
  public void setCurrencyPair(CurrencyPair pair) {

    this.pair = pair;
  }

  @Override
  public CurrencyPair getCurrencyPair() {

    return pair;
  }
}
