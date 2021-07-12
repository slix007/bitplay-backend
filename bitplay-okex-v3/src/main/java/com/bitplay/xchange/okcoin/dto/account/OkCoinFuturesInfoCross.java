package com.bitplay.xchange.okcoin.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OkCoinFuturesInfoCross {
  private final OkcoinFuturesFundsCross btcFunds;
  private final OkcoinFuturesFundsCross ltcFunds;
  private final OkcoinFuturesFundsCross ethFunds;

  public OkCoinFuturesInfoCross(@JsonProperty("btc") final OkcoinFuturesFundsCross btcFunds,
      @JsonProperty("ltc") final OkcoinFuturesFundsCross ltcFunds,
          @JsonProperty("eth") final OkcoinFuturesFundsCross ethFunds) {
    this.btcFunds = btcFunds;
    this.ltcFunds = ltcFunds;
    this.ethFunds = ethFunds;
  }

  public OkcoinFuturesFundsCross getBtcFunds() {
    return btcFunds;
  }

  public OkcoinFuturesFundsCross getLtcFunds() {
    return ltcFunds;
  }

  public OkcoinFuturesFundsCross getEthFunds() {
    return ethFunds;
  }
}
