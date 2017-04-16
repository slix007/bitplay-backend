package com.bitplay.service;

import com.bitplay.business.BusinessService;
import com.bitplay.model.AccountInfoJson;
import com.bitplay.model.OrderBookJson;
import com.bitplay.model.TickerJson;
import com.bitplay.model.TradeRequest;
import com.bitplay.model.TradeResponse;
import com.bitplay.model.VisualTrade;
import com.bitplay.utils.Utils;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trade;
import org.knowm.xchange.dto.trade.LimitOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Sergey Shurmin on 3/25/17.
 */
public abstract class AbstractBitplayUIService<T extends BusinessService> {

    public abstract List<VisualTrade> fetchTrades();

    public abstract OrderBookJson fetchOrderBook();

    public abstract AccountInfoJson getAccountInfo();

    public abstract T getBusinessService();

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
        final List<LimitOrder> bestBids = Utils.getBestBids(orderBook.getBids(), 100);
        orderJson.setBid(bestBids.stream()
                .filter(limitOrder -> limitOrder.getTradableAmount().compareTo(BigDecimal.ZERO) != 0)
                .map(toOrderBookJson)
                .collect(Collectors.toList()));
        final List<LimitOrder> bestAsks = Utils.getBestAsks(orderBook.getAsks(), 100);
        orderJson.setAsk(bestAsks.stream()
                .filter(limitOrder -> limitOrder.getTradableAmount().compareTo(BigDecimal.ZERO) != 0)
                .map(toOrderBookJson)
                .collect(Collectors.toList()));
        return orderJson;
    }

    protected TickerJson convertTicker(Ticker ticker) {
        final String value = ticker != null ? ticker.toString() : "";
        return new TickerJson(value);
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

    protected AccountInfoJson convertAccountInfo(AccountInfo accountInfo) {
        final Wallet wallet = accountInfo.getWallet();
        return new AccountInfoJson(wallet.getBalance(Currency.BTC).getAvailable().toPlainString(),
                wallet.getBalance(Currency.USD).getAvailable().toPlainString(),
                accountInfo.toString());
    }

    public TradeResponse doTrade(TradeRequest tradeRequest) {
        final BigDecimal amount = new BigDecimal(tradeRequest.getAmount());
        Order.OrderType orderType;
        switch (tradeRequest.getType()) {
            case BUY:
                orderType = Order.OrderType.BID;
                break;
            case SELL:
                orderType = Order.OrderType.ASK;
                break;
            default:
                throw new IllegalArgumentException("No such order type " + tradeRequest.getType());
        }
        final String orderId = getBusinessService().placeMarketOrder(orderType, amount);
        return new TradeResponse(orderId);
    }
}
