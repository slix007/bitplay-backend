package com.crypto.service;

import com.crypto.model.OrderBookJson;
import com.crypto.model.TickerJson;
import com.crypto.model.VisualTrade;
import com.crypto.utils.Utils;

import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Sergey Shurmin on 3/25/17.
 */
public abstract class AbstractBitplayUIService {

    public abstract List<VisualTrade> fetchTrades();

    public abstract OrderBookJson fetchOrderBook();

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


    protected OrderBookJson convertOrderBookAndFilter(OrderBook orderBook) {
        final OrderBookJson orderJson = new OrderBookJson();
        final List<LimitOrder> bestBids = Utils.getBestBids(orderBook.getBids(), 200);
        orderJson.setBid(bestBids.stream()
                .map(toOrderBookJson)
                .collect(Collectors.toList()));
        final List<LimitOrder> bestAsks = Utils.getBestAsks(orderBook.getAsks(), 200);
        orderJson.setAsk(bestAsks.stream()
                .map(toOrderBookJson)
                .collect(Collectors.toList()));
        return orderJson;
    }

    protected TickerJson convertTicker(Ticker ticker) {
        return new TickerJson(ticker.toString());
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
