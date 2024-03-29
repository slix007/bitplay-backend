package com.bitplay.xchange.dto.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class CurrencyPairMetaData {

  /**
   * Trading fee (fraction)
   */
  @JsonProperty("trading_fee")
  private final BigDecimal tradingFee;

  /**
   * Minimum trade amount, the scale indicates price step
   */
  @JsonProperty("min_amount")
  private final BigDecimal minimumAmount;

  /**
   * Minimum trade amount, the scale indicates price step
   */
  @JsonProperty("max_amount")
  private final BigDecimal maximumAmount;

  @JsonProperty("price_scale")
  private final Integer priceScale;

  /**
   * Constructor
   *
   * @param tradingFee
   * @param minimumAmount
   * @param maximumAmount
   * @param priceScale
   */
  public CurrencyPairMetaData(@JsonProperty("trading_fee") BigDecimal tradingFee, @JsonProperty("min_amount") BigDecimal minimumAmount,
      @JsonProperty("max_amount") BigDecimal maximumAmount, @JsonProperty("price_scale") Integer priceScale) {

    this.tradingFee = tradingFee;
    this.minimumAmount = minimumAmount;
    this.maximumAmount = maximumAmount;
    this.priceScale = priceScale;
  }

  public BigDecimal getTradingFee() {

    return tradingFee;
  }

  public BigDecimal getMinimumAmount() {

    return minimumAmount;
  }

  public BigDecimal getMaximumAmount() {

    return maximumAmount;
  }

  public Integer getPriceScale() {

    return priceScale;
  }

  @Override
  public String toString() {

    return "CurrencyPairMetaData [tradingFee=" + tradingFee + ", minimumAmount=" + minimumAmount + ", maximumAmount=" + maximumAmount
        + ", priceScale=" + priceScale + "]";
  }

}
