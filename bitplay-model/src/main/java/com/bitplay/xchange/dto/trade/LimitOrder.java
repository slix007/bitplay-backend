package com.bitplay.xchange.dto.trade;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Set;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order;

/**
 * <p>
 * DTO representing a limit order
 * </p>
 * <p>
 * A limit order lets you set a minimum or maximum price before your trade will be treated by the exchange as a {@link MarketOrder}. There is no guarantee that
 * your conditions will be met on the exchange, so your order may not be executed. However, until you become very experienced, almost all orders should be limit
 * orders to protect yourself.
 * </p>
 */
public class LimitOrder extends Order implements Comparable<LimitOrder> {

    /**
     * The limit price
     */
    protected final BigDecimal limitPrice;

    /**
     * @param type Either BID (buying) or ASK (selling)
     * @param tradableAmount The amount to trade
     * @param currencyPair The identifier (e.g. BTC/USD)
     * @param id An id (usually provided by the exchange)
     * @param timestamp a Date object representing the order's timestamp according to the exchange's server, null if not provided
     * @param limitPrice In a BID this is the highest acceptable price, in an ASK this is the lowest acceptable price
     */
    public LimitOrder(OrderType type, BigDecimal tradableAmount, CurrencyPair currencyPair, String id, Date timestamp, BigDecimal limitPrice) {

        super(type, tradableAmount, currencyPair, id, timestamp);
        this.limitPrice = limitPrice;
    }

    /**
     * @param type Either BID (buying) or ASK (selling)
     * @param tradableAmount The amount to trade
     * @param currencyPair The identifier (e.g. BTC/USD)
     * @param id An id (usually provided by the exchange)
     * @param timestamp a Date object representing the order's timestamp according to the exchange's server, null if not provided
     * @param limitPrice In a BID this is the highest acceptable price, in an ASK this is the lowest acceptable price
     * @param averagePrice the weighted average price of any fills belonging to the order
     * @param status the status of the order at the exchange or broker
     */
    public LimitOrder(OrderType type, BigDecimal tradableAmount, CurrencyPair currencyPair, String id, Date timestamp, BigDecimal limitPrice,
            BigDecimal averagePrice, BigDecimal cumulativeAmount, OrderStatus status) {

        super(type, tradableAmount, currencyPair, id, timestamp, averagePrice, cumulativeAmount, status);
        this.limitPrice = limitPrice;
    }

    /**
     * @return The limit price
     */
    public BigDecimal getLimitPrice() {

        return limitPrice;
    }

    @Override
    public String toString() {

        return "LimitOrder [limitPrice=" + limitPrice + ", " + super.toString() + "]";
    }

    @Override
    public int compareTo(LimitOrder limitOrder) {

        final int ret;

        if (this.getType() == limitOrder.getType()) {
            // Same side
            ret = this.getLimitPrice().compareTo(limitOrder.getLimitPrice()) * (getType() == OrderType.BID ? -1 : 1);
        } else {
            // Keep bid side be less than ask side
            ret = this.getType() == OrderType.BID ? -1 : 1;
        }

        return ret;
    }

    @Override
    public int hashCode() {

        int hash = super.hashCode();
        hash = 59 * hash + (this.limitPrice != null ? this.limitPrice.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {

        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LimitOrder other = (LimitOrder) obj;
        if (this.limitPrice == null ? (other.limitPrice != null) : this.limitPrice.compareTo(other.limitPrice) != 0) {
            return false;
        }
        return super.equals(obj);
    }

    public static class Builder extends Order.Builder {

        protected BigDecimal limitPrice;

        public Builder(OrderType orderType, CurrencyPair currencyPair) {

            super(orderType, currencyPair);
        }

        public static Builder from(Order order) {

            Builder builder = (Builder) new Builder(order.getType(), order.getCurrencyPair()).tradableAmount(order.getTradableAmount())
                    .timestamp(order.getTimestamp()).id(order.getId()).flags(order.getOrderFlags()).orderStatus(order.getStatus())
                    .averagePrice(order.getAveragePrice());
            if (order instanceof LimitOrder) {
                LimitOrder limitOrder = (LimitOrder) order;
                builder.limitPrice(limitOrder.getLimitPrice());
            }
            return builder;
        }

        @Override
        public Builder orderType(OrderType orderType) {

            return (Builder) super.orderType(orderType);
        }

        @Override
        public Builder tradableAmount(BigDecimal tradableAmount) {

            return (Builder) super.tradableAmount(tradableAmount);
        }

        @Override
        public Builder currencyPair(CurrencyPair currencyPair) {

            return (Builder) super.currencyPair(currencyPair);
        }

        @Override
        public Builder id(String id) {

            return (Builder) super.id(id);
        }

        @Override
        public Builder timestamp(Date timestamp) {

            return (Builder) super.timestamp(timestamp);
        }

        @Override
        public Builder orderStatus(OrderStatus status) {

            return (Builder) super.orderStatus(status);
        }

        @Override
        public Builder averagePrice(BigDecimal averagePrice) {

            return (Builder) super.averagePrice(averagePrice);
        }

        @Override
        public Builder flag(IOrderFlags flag) {

            return (Builder) super.flag(flag);
        }

        @Override
        public Builder flags(Set<IOrderFlags> flags) {

            return (Builder) super.flags(flags);
        }

        public Builder limitPrice(BigDecimal limitPrice) {

            this.limitPrice = limitPrice;
            return this;
        }

        public LimitOrder build() {

            LimitOrder order = new LimitOrder(orderType, tradableAmount, currencyPair, id,
                    timestamp, limitPrice, averagePrice, null, status);
            order.setOrderFlags(flags);
            return order;
        }
    }
}
