package com.crypto.service;

import com.crypto.polonex.PoloniexService;
import com.crypto.ui.VisualTrade;

import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Sergey Shurmin on 3/25/17.
 */
@Component("Poloniex")
public class BitplayUIServicePoloniex implements BitplayUIService {

    @Autowired
    PoloniexService poloniexService;

    int orderBookDepth = 0;

    OrderBook orderBook;

    @Override
    public List<VisualTrade> fetchTrades() {
        final Trades trades = poloniexService.fetchTrades();
        orderBookDepth = trades.getTrades().size();

        List<VisualTrade> askTrades = trades.getTrades().stream()
//                .filter(trade -> trade.getType() == Order.OrderType.ASK || trade.getType() == Order.OrderType.EXIT_ASK)
                .map(this::toVisualTrade)
                .collect(Collectors.toList());

//        List<VisualTrade> bidTrades = trades.getTrades().stream()
//                .filter(trade -> trade.getType() == Order.OrderType.BID || trade.getType() == Order.OrderType.EXIT_BID)
//                .map(BitplayUIService::toVisualTrade)
//                .collect(Collectors.toList());


        return askTrades;
    }

    @Override
    public OrderBook fetchOrderBook() {
        orderBook = poloniexService.fetchOrderBook();
        orderBookDepth = orderBook.getAsks().size();
        return orderBook;
    }

    public List<VisualTrade> getBids() {
        return orderBook.getBids().stream()
                .map(this::toVisualTrade)
                .collect(Collectors.toList());
    }
    public List<VisualTrade> getAsks() {
        return orderBook.getAsks().stream()
                .map(this::toVisualTrade)
                .collect(Collectors.toList());
    }

    private VisualTrade toVisualTrade(LimitOrder limitOrder) {
        return new VisualTrade(
                limitOrder.getCurrencyPair().toString(),
                limitOrder.getLimitPrice().toString(),
                limitOrder.getTradableAmount().toString(),
                limitOrder.getType().toString(),
                LocalDateTime.ofInstant(limitOrder.getTimestamp().toInstant(), ZoneId.systemDefault())
                        .toLocalTime().toString());
    }

    @Override
    public int getOrderBookDepth() {
        return orderBookDepth;
    }
}
