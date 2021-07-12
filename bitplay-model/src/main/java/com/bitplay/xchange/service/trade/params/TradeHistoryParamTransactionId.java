package com.bitplay.xchange.service.trade.params;

public interface TradeHistoryParamTransactionId extends TradeHistoryParams {
  void setTransactionId(String txId);

  String getTransactionId();
}
