package com.crypto.service;

import com.crypto.model.OrderBookJson;
import com.crypto.model.VisualTrade;

import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Sergey Shurmin on 3/25/17.
 */
public abstract class AbstractBitplayUIService {

    public abstract List<VisualTrade> fetchTrades();

    public abstract OrderBookJson fetchOrderBook();

    public abstract int getOrderBookDepth();


    VisualTrade toVisualTrade(Trade trade) {
        return new VisualTrade(
                trade.getCurrencyPair().toString(),
                trade.getPrice().toString(),
                trade.getTradableAmount().toString(),
                trade.getType().toString(),
                LocalDateTime.ofInstant(trade.getTimestamp().toInstant(), ZoneId.systemDefault())
                        .toLocalTime().toString()
        );
    }


    protected OrderBookJson getBestOrderBookJson(OrderBook orderBook) {
        final OrderBookJson orderJson = new OrderBookJson();
        final List<LimitOrder> bestBids = getBestBids(orderBook.getBids(), 1);
        orderJson.setBid(bestBids.stream()
                .map(toOrderBookJson)
                .collect(Collectors.toList()));
        final List<LimitOrder> bestAsks = getBestAsks(orderBook.getAsks(), 1);
        orderJson.setAsk(bestAsks.stream()
                .map(toOrderBookJson)
                .collect(Collectors.toList()));
        return orderJson;
    }

    protected List<LimitOrder> getBestBids(List<LimitOrder> bids, int amount) {
        return bids.stream()
                .sorted(Comparator.comparing(LimitOrder::getLimitPrice))
                .limit(amount)
                .collect(Collectors.toList());
    }

    protected List<LimitOrder> getBestAsks(List<LimitOrder> asks, int amount) {
        return asks.stream()
                .sorted((o1, o2) -> o2.getLimitPrice().compareTo(o1.getLimitPrice()))
                .limit(amount)
                .collect(Collectors.toList());
    }

    Function<LimitOrder, VisualTrade> toVisual = limitOrder -> new VisualTrade(
            limitOrder.getCurrencyPair().toString(),
            limitOrder.getLimitPrice().toString(),
            limitOrder.getTradableAmount().toString(),
            limitOrder.getType().toString(),
            LocalDateTime.ofInstant(limitOrder.getTimestamp().toInstant(), ZoneId.systemDefault())
                    .toLocalTime().toString());

    Function<LimitOrder, OrderBookJson.OrderJson> toOrderBookJson = limitOrder -> {
        final OrderBookJson.OrderJson orderJson = new OrderBookJson.OrderJson();
        orderJson.setOrderType(limitOrder.getType().toString());
        orderJson.setPrice(limitOrder.getLimitPrice().toString());
        orderJson.setAmount(limitOrder.getTradableAmount().toString());
        orderJson.setCurrency(limitOrder.getCurrencyPair().toString());
        orderJson.setTimestamp(limitOrder.getTimestamp() != null
                ? LocalDateTime.ofInstant(limitOrder.getTimestamp().toInstant(), ZoneId.systemDefault()).toLocalTime().toString()
                : null);
        return orderJson;
    };


}
