package com.bitplay.xchangestream.bitmex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import lombok.Getter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
public class BitmexDepth {

  private final String symbol;
  private final BigDecimal[][] asks;
  private final BigDecimal[][] bids;
  private final Date timestamp;
  private final Date receiveTimestamp;

  public BitmexDepth(
          @JsonProperty("symbol") final String symbol,
          @JsonProperty("asks") final BigDecimal[][] asks,
          @JsonProperty("bids") final BigDecimal[][] bids,
          @JsonProperty("timestamp") Date timestamp) {
    this.symbol = symbol;
    this.asks = asks;
    this.bids = bids;
    this.timestamp = timestamp;
    this.receiveTimestamp = new Date();
  }

  @Override
  public String toString() {

    return "OkCoinDepth [asks=" + Arrays.toString(asks) + ", bids=" + Arrays.toString(bids) + "]";
  }
}
