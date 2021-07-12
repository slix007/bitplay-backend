package com.bitplay.xchange.service.trade.params.orders;

import com.bitplay.xchange.currency.CurrencyPair;

public interface OpenOrdersParamCurrencyPair extends OpenOrdersParams {
  void setCurrencyPair(CurrencyPair pair);

  CurrencyPair getCurrencyPair();
}
