package com.bitplay.okex.v5.dto.adapter;

import com.bitplay.okex.v5.dto.result.Book;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order.OrderType;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import com.bitplay.xchange.dto.trade.LimitOrder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BookAdapter {

    public static OrderBook convertBook(Book depth, CurrencyPair currencyPair) {
        List<LimitOrder> asks = adaptLimitOrders(OrderType.ASK, depth.getAsks(), currencyPair, depth.getTimestamp());
//        Collections.reverse(asks);
        List<LimitOrder> bids = adaptLimitOrders(OrderType.BID, depth.getBids(), currencyPair, depth.getTimestamp());
        return new OrderBook(depth.getTimestamp(), asks, bids);
    }

    private static List<LimitOrder> adaptLimitOrders(OrderType type, BigDecimal[][] list, CurrencyPair currencyPair, Date timestamp) {
        List<LimitOrder> limitOrders = new ArrayList<>(list.length);
        for (int i = 0; i < list.length; ++i) {
            BigDecimal[] data = list[i];
            limitOrders.add(adaptLimitOrder(type, data, currencyPair, (String) null, timestamp));
        }

        return limitOrders;
    }

    private static LimitOrder adaptLimitOrder(OrderType type, BigDecimal[] data, CurrencyPair currencyPair, String id, Date timestamp) {
        //[411.8,6,8,4][double ,int ,int ,int]
        // 411.8 is the price,
        // 6 is the size of the price,
        // 8 is the number of force-liquidated orders,
        // 4 is the number of orders of the price，
        // timestamp is the timestamp of the orderbook.
        BigDecimal tradableAmount = data[1].setScale(0, RoundingMode.HALF_UP);
        return new LimitOrder(type, tradableAmount, currencyPair, id, timestamp, data[0]);
    }


}
