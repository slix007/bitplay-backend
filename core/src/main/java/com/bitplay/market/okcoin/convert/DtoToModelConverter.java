package com.bitplay.market.okcoin.convert;

import com.bitplay.okex.v3.dto.futures.result.OrderDetail;
import com.bitplay.okex.v3.enums.FuturesTransactionTypeEnum;
import java.util.List;
import java.util.stream.Collectors;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.LimitOrder.Builder;

public class DtoToModelConverter {

    public static LimitOrder convertOrder(OrderDetail orderDetail, CurrencyPair currencyPair) {
        final OrderType orderType = convertType(orderDetail.getType());
        final OrderStatus orderStatus = convertStatus(orderDetail.getState());
        // we asked for this, don't spend resources to convert.
        final LimitOrder order = new Builder(orderType, currencyPair)
                .tradableAmount(orderDetail.getSize())
                .timestamp(orderDetail.getTimestamp())
                .id(orderDetail.getOrder_id())
                .limitPrice(orderDetail.getPrice())
                .averagePrice(orderDetail.getPrice_avg())
                .orderStatus(orderStatus)
                .build();
        order.setCumulativeAmount(orderDetail.getFilled_qty());
        // There is also pnl and fee.
        return order;
    }

    private static OrderStatus convertStatus(String state) {
        // -2:Failed,
        // -1:Canceled,
        // 0:Open ,
        // 1:Partially Filled,
        // 2:Fully Filled,
        // 3:Submitting,
        // 4:Canceling
        switch (state) {
            case "-2": // no value of using OrderStatus.REJECTED so far.
            case "-1":
                return OrderStatus.CANCELED;
            case "0":
                return OrderStatus.NEW;
            case "1":
                return OrderStatus.PARTIALLY_FILLED;
            case "2":
                return OrderStatus.FILLED;
            case "3":
                return OrderStatus.PENDING_NEW;
            case "4":
                return OrderStatus.PENDING_CANCEL;
        }
        throw new IllegalArgumentException("wrong orderStatus " + state);
    }

    private static OrderType convertType(String orderType) {
        // Type (1: open long 2: open short 3: close long 4: close short)
        switch (orderType) {
            case "1"://OPEN_LONG:
                return OrderType.BID;
            case "2"://OPEN_SHORT:
                return OrderType.ASK;
            case "3"://CLOSE_LONG:
                return OrderType.EXIT_BID;
            case "4"://CLOSE_SHORT:
                return OrderType.EXIT_ASK;
            default:
                throw new IllegalArgumentException("enum is wrong");
        }
    }

    public static List<LimitOrder> convertOrders(List<OrderDetail> orders, CurrencyPair currencyPair) {
        return orders.stream().map(ord -> convertOrder(ord, currencyPair)).collect(Collectors.toList());
    }

}
