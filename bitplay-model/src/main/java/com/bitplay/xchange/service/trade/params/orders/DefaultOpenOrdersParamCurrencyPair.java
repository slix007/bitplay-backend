package com.bitplay.xchange.service.trade.params.orders;

import com.bitplay.xchange.currency.CurrencyPair;

public class DefaultOpenOrdersParamCurrencyPair implements OpenOrdersParamCurrencyPair {

  private CurrencyPair pair;

  public DefaultOpenOrdersParamCurrencyPair() {
  }

  public DefaultOpenOrdersParamCurrencyPair(CurrencyPair pair) {
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
