package com.crypto.service;

import com.crypto.model.VisualTrade;

import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Trade;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Created by Sergey Shurmin on 3/25/17.
 */
public interface BitplayUIService {

    List<VisualTrade> fetchTrades();

    OrderBook fetchOrderBook();

    int getOrderBookDepth();




    default VisualTrade toVisualTrade(Trade trade) {
        return new VisualTrade(
                trade.getCurrencyPair().toString(),
                trade.getPrice().toString(),
                trade.getTradableAmount().toString(),
                trade.getType().toString(),
                LocalDateTime.ofInstant(trade.getTimestamp().toInstant(), ZoneId.systemDefault())
                        .toLocalTime().toString()
        );
    }
}
