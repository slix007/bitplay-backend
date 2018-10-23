package com.bitplay.persistance.domain.fluent;

import java.math.BigDecimal;
import java.util.Date;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;

/**
 * Created by Sergey Shurmin on 12/23/17.
 */
public class OrderDetail {
    private Order.OrderStatus orderStatus;
    private Order.OrderType orderType;
    private BigDecimal tradableAmount;
    private BigDecimal cumulativeAmount;
    private BigDecimal averagePrice;
    private CurrencyPair currencyPair;
    private String id;
    private Date timestamp;
    private BigDecimal limitPrice;

    public Order.OrderStatus getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(Order.OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }

    public Order.OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(Order.OrderType orderType) {
        this.orderType = orderType;
    }

    public BigDecimal getTradableAmount() {
        return tradableAmount;
    }

    public void setTradableAmount(BigDecimal tradableAmount) {
        this.tradableAmount = tradableAmount;
    }

    public BigDecimal getCumulativeAmount() {
        return cumulativeAmount;
    }

    public void setCumulativeAmount(BigDecimal cumulativeAmount) {
        this.cumulativeAmount = cumulativeAmount;
    }

    public BigDecimal getAveragePrice() {
        return averagePrice;
    }

    public void setAveragePrice(BigDecimal averagePrice) {
        this.averagePrice = averagePrice;
    }

    public CurrencyPair getCurrencyPair() {
        return currencyPair;
    }

    public void setCurrencyPair(CurrencyPair currencyPair) {
        this.currencyPair = currencyPair;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(BigDecimal limitPrice) {
        this.limitPrice = limitPrice;
    }

    @Override
    public String toString() {
        return "OrderDetail{" +
                "orderStatus=" + orderStatus +
                ", orderType=" + orderType +
                ", tradableAmount=" + tradableAmount +
                ", cumulativeAmount=" + cumulativeAmount +
                ", averagePrice=" + averagePrice +
                ", currencyPair=" + currencyPair +
                ", id='" + id + '\'' +
                ", timestamp=" + timestamp +
                ", limitPrice=" + limitPrice +
                '}';
    }

    public String getContractType() {
        return currencyPair != null ? currencyPair.toString() : "";
    }
}
