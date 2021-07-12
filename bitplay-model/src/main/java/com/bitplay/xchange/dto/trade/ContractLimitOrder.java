package com.bitplay.xchange.dto.trade;

import java.math.BigDecimal;
import java.util.Date;
import com.bitplay.xchange.currency.CurrencyPair;

/**
 * Created by Sergey Shurmin on 6/23/17.
 */
public class ContractLimitOrder extends LimitOrder {

    public ContractLimitOrder(OrderType type, BigDecimal tradableAmount, CurrencyPair currencyPair, String id, Date timestamp, BigDecimal limitPrice) {
        super(type, tradableAmount, currencyPair, id, timestamp, limitPrice);
    }

    public ContractLimitOrder(OrderType type, BigDecimal tradableAmount, CurrencyPair currencyPair, String id, Date timestamp, BigDecimal limitPrice, BigDecimal averagePrice, BigDecimal cumulativeAmount, OrderStatus status) {
        super(type, tradableAmount, currencyPair, id, timestamp, limitPrice, averagePrice, cumulativeAmount, status);
    }

    private BigDecimal amountInBaseCurrency;

    public BigDecimal getAmountInBaseCurrency() {
        return amountInBaseCurrency;
    }

    public void setAmountInBaseCurrency(BigDecimal amountInBaseCurrency) {
        this.amountInBaseCurrency = amountInBaseCurrency;
    }
}
