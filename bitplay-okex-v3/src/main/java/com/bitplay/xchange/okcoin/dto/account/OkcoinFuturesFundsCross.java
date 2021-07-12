package com.bitplay.xchange.okcoin.dto.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class OkcoinFuturesFundsCross {

  private final BigDecimal accountRights;
  private final BigDecimal keepDeposits;
  private final BigDecimal profitReal;
  private final BigDecimal profitUnreal;
  private final BigDecimal riskRate;

  public OkcoinFuturesFundsCross(@JsonProperty("account_rights") BigDecimal accountRights, @JsonProperty("keep_deposit") BigDecimal keepDeposits,
      @JsonProperty("profit_real") BigDecimal profitReal, @JsonProperty("profit_unreal") BigDecimal profitUnreal,
      @JsonProperty("risk_rate") BigDecimal riskRate) {

    this.accountRights = accountRights;
    this.keepDeposits = keepDeposits;
    this.profitReal = profitReal;
    this.profitUnreal = profitUnreal;
    this.riskRate = riskRate;

  }

  public BigDecimal getAccountRights() {

    return accountRights;
  }

  public BigDecimal getKeepDeposits() {

    return keepDeposits;
  }

  public BigDecimal getProfitReal() {

    return profitReal;
  }

  public BigDecimal getProfitUnreal() {

    return profitUnreal;
  }

  public BigDecimal getRiskRate() {

    return riskRate;
  }
}
