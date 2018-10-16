package com.bitplay.persistance.domain.fluent;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;

/**
 * Created by Sergey Shurmin on 12/23/17.
 */
public class FplayOrderConverter {

    public static OrderDetail convert(Order order) {
        final OrderDetail orderDetail = new OrderDetail();
        orderDetail.setId(order.getId());
        orderDetail.setOrderStatus(order.getStatus());
        orderDetail.setOrderType(order.getType());
        orderDetail.setAveragePrice(order.getAveragePrice());
        orderDetail.setCumulativeAmount(order.getCumulativeAmount());
        orderDetail.setTradableAmount(order.getTradableAmount());
        orderDetail.setTimestamp(order.getTimestamp());
        orderDetail.setCurrencyPair(order.getCurrencyPair());
        if (order instanceof LimitOrder) {
            orderDetail.setLimitPrice(((LimitOrder) order).getLimitPrice());
        }
        return orderDetail;
    }

    public static LimitOrder convert(OrderDetail orderDetail) {
        return new LimitOrder(orderDetail.getOrderType(),
                orderDetail.getTradableAmount(),
                orderDetail.getCurrencyPair(),
                orderDetail.getId(),
                orderDetail.getTimestamp(),
                orderDetail.getLimitPrice(),
                orderDetail.getAveragePrice(),
                orderDetail.getCumulativeAmount(),
                orderDetail.getOrderStatus());
    }
}
