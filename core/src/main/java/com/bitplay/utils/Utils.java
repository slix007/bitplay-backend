package com.bitplay.utils;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Sergey Shurmin on 4/4/17.
 */
public class Utils {

    public static String withSign(BigDecimal value) {
        return value.signum() < 0 ? value.toPlainString() : ("+" + value.toPlainString());
    }

    public static LimitOrder getBestBid(OrderBook orderBook) {
        return getBestBids(orderBook, 1).get(0);
    }

    public static LimitOrder getBestAsk(OrderBook orderBook) {
        return getBestAsks(orderBook, 1).get(0);
    }

    public static List<LimitOrder> getBestBids(List<LimitOrder> bids, int amount) {
        return bids.stream()
                .sorted((o1, o2) -> o2.getLimitPrice().compareTo(o1.getLimitPrice()))
                .limit(amount)
                .collect(Collectors.toList());
    }

    public static List<LimitOrder> getBestAsks(List<LimitOrder> asks, int amount) {
        return asks.stream()
                .sorted(Comparator.comparing(LimitOrder::getLimitPrice))
                .limit(amount)
                .collect(Collectors.toList());
    }

    public static List<LimitOrder> getBestBids(OrderBook orderBook, int amount) {
        return orderBook.getBids().stream()
                .sorted((o1, o2) -> o2.getLimitPrice().compareTo(o1.getLimitPrice()))
                .limit(amount)
                .collect(Collectors.toList());
    }

    public static List<LimitOrder> getBestAsks(OrderBook orderBook, int amount) {
        return orderBook.getAsks().stream()
                .sorted(Comparator.comparing(LimitOrder::getLimitPrice))
                .limit(amount)
                .collect(Collectors.toList());
    }

    public static String convertOrderTypeName(Order.OrderType orderType) {
        String theName = "undefined";
        switch (orderType) {
            case ASK:
                theName = "SELL";
                break;
            case BID:
                theName = "BUY";
                break;
            case EXIT_BID:
                theName = "CLOSE_BUY";
                break;
            case EXIT_ASK:
                theName = "CLOSE_SELL";
                break;
        }
        return theName;
    }

}
