package com.bitplay.xchange.dto.trade;

import java.math.BigDecimal;
import java.util.Date;
import com.bitplay.xchange.dto.LoanOrder;
import com.bitplay.xchange.dto.Order;

/**
 * DTO representing a floating rate loan order
 * <p>
 * A floating rate loan order is a loan order whose rate is determined by the market. This type of loan order can be preferable for creditors when
 * loans have a callable provision (i.e. the debtor can choose to pay off the loan early and acquire another loan at a more favorable rate).
 */
public final class FloatingRateLoanOrder extends LoanOrder implements Comparable<FloatingRateLoanOrder> {

  private BigDecimal rate;

  /**
   * @param type Either BID (debtor) or ASK (creditor)
   * @param currency The loan currency code
   * @param tradableAmount Units of currency
   * @param dayPeriod Loan duration in days
   * @param id An id (usually provided by the exchange)
   * @param timestamp The absolute time for this order
   */
  public FloatingRateLoanOrder(Order.OrderType type, String currency, BigDecimal tradableAmount, int dayPeriod, String id, Date timestamp,
      BigDecimal rate) {

    super(type, currency, tradableAmount, dayPeriod, id, timestamp);
    this.rate = rate;
  }

  public BigDecimal getRate() {

    return rate;
  }

  public void setRate(BigDecimal rate) {

    this.rate = rate;
  }

  @Override
  public int compareTo(FloatingRateLoanOrder order) {

    return this.getDayPeriod() - order.getDayPeriod();
  }
}
