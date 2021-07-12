package com.bitplay.xchange.service.trade.params;

import com.bitplay.xchange.currency.CurrencyPair;

public interface TradeHistoryParamCurrencyPair extends TradeHistoryParams {

  void setCurrencyPair(CurrencyPair pair);

  CurrencyPair getCurrencyPair();
}
