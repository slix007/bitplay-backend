package com.bitplay.okex.v5.dto.adapter;

import com.bitplay.okex.v5.dto.result.OrderDetail;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order.OrderStatus;
import com.bitplay.xchange.dto.Order.OrderType;
import com.bitplay.xchange.dto.trade.LimitOrder;
import com.bitplay.xchange.dto.trade.LimitOrder.Builder;
import com.bitplay.xchange.okcoin.OkCoinAdapters;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DtoToModelConverter {

    // WARN: may return null, if market does not update info yet and sends null fields
    public static LimitOrder convertOrder(OrderDetail orderDetail, CurrencyPair currencyPair) {
//        if (orderDetail.getOrdType() == null) {
//            log.info("DEBUG_okex_v5: " + orderDetail);
//        }
        LimitOrder order = null;
        try {
            final OrderType orderType = OkCoinAdapters.convertType(orderDetail.getSide());
            final OrderStatus orderStatus = OkCoinAdapters.convertStatus(orderDetail.getState());
            // we asked for this, don't spend resources to convert.

            // workaround for taker orders. Use averagePx as Px.
            final BigDecimal px = orderDetail.getPx() == null && orderDetail.getAvgPx() != null
                    ? orderDetail.getAvgPx()
                    : orderDetail.getPx();

            order = new Builder(orderType, currencyPair)
                    .tradableAmount(orderDetail.getSz())
                    .timestamp(orderDetail.getUTime())
                    .id(orderDetail.getOrdId())
                    .limitPrice(px)
                    .averagePrice(orderDetail.getAvgPx())
                    .orderStatus(orderStatus)
                    .build();
            order.setCumulativeAmount(orderDetail.getAccFillSz());
        } catch (Exception e) {
            log.info("DEBUG_okex_v5: " + orderDetail, e);
        }
        // There is also pnl and fee.
        return order;
    }

    public static List<LimitOrder> convertOrders(List<OrderDetail> orders, CurrencyPair currencyPair) {
        return orders.stream().map(ord -> convertOrder(ord, currencyPair)).collect(Collectors.toList());
    }

}
