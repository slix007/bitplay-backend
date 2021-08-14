package com.bitplay.okex.v5.dto.adapter;

import com.bitplay.okex.v5.dto.result.OrderDetail;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order.OrderStatus;
import com.bitplay.xchange.dto.Order.OrderType;
import com.bitplay.xchange.dto.trade.LimitOrder;
import com.bitplay.xchange.dto.trade.LimitOrder.Builder;
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
            final OrderType orderType = convertType(orderDetail.getSide());
            final OrderStatus orderStatus = convertStatus(orderDetail.getState());
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

    private static OrderStatus convertStatus(String state) {
        // for OPEN ORDERS:
        //live
        //partially_filled

        // for DETAILS:
        //State
        //canceled
        //live
        //partially_filled
        //filled
        switch (state) {
            case "canceled":
                return OrderStatus.CANCELED;
            case "live":
                return OrderStatus.NEW;
            case "partially_filled":
                return OrderStatus.PARTIALLY_FILLED;
            case "filled":
                return OrderStatus.FILLED;
        }
        throw new IllegalArgumentException("wrong orderStatus " + state);
    }

    private static OrderType convertType(String orderType) {
        if (orderType == null) {
            return null;
        }
        // Type (1: open long 2: open short 3: close long 4: close short)
        switch (orderType) {
            case "buy"://OPEN_LONG:
                return OrderType.BID;
            case "sell"://OPEN_SHORT:
                return OrderType.ASK;
//            case "3"://CLOSE_LONG:
//                return OrderType.EXIT_BID;
//            case "4"://CLOSE_SHORT:
//                return OrderType.EXIT_ASK;
            default:
                throw new IllegalArgumentException("enum is wrong");
        }
    }

    public static List<LimitOrder> convertOrders(List<OrderDetail> orders, CurrencyPair currencyPair) {
        return orders.stream().map(ord -> convertOrder(ord, currencyPair)).collect(Collectors.toList());
    }

}
