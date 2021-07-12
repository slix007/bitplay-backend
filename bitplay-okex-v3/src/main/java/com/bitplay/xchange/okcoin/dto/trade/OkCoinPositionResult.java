package com.bitplay.xchange.okcoin.dto.trade;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OkCoinPositionResult extends OkCoinErrorResult {

  private final OkCoinPosition[] positions;
  private final String forceLiquPrice; // Example: "6,799.95"

  public OkCoinPositionResult(
      @JsonProperty("result") final boolean result,
      @JsonProperty("errorCode") final int errorCode,
      @JsonProperty("holding") final OkCoinPosition[] positions,
      @JsonProperty("force_liqu_price") final String forceLiquPrice) {

    super(result, errorCode);
    this.positions = positions;
    this.forceLiquPrice = forceLiquPrice;
  }

  public OkCoinPosition[] getPositions() {

    return positions;
  }

  public String getForceLiquPrice() {
    return forceLiquPrice;
  }
}
