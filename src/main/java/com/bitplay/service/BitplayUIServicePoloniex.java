package com.bitplay.service;

import com.bitplay.model.AccountInfoJson;
import com.bitplay.model.TradeRequest;
import com.bitplay.model.OrderBookJson;
import com.bitplay.model.TickerJson;
import com.bitplay.model.TradeResponse;
import com.bitplay.business.polonex.PoloniexService;
import com.bitplay.model.VisualTrade;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Wallet;
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
public class BitplayUIServicePoloniex extends AbstractBitplayUIService<PoloniexService> {

    @Autowired
    PoloniexService poloniexService;

    @Override
    public PoloniexService getBusinessService() {
        return poloniexService;
    }

    OrderBook orderBook;

    @Override
    public List<VisualTrade> fetchTrades() {
        final Trades trades = poloniexService.fetchTrades();

        List<VisualTrade> askTrades = trades.getTrades().stream()
                .sorted((o1, o2) -> o1.getTimestamp().before(o2.getTimestamp()) ? 1 : -1)
                .map(this::toVisualTrade)
                .collect(Collectors.toList());
        return askTrades;
    }

    @Override
    public OrderBookJson fetchOrderBook() {
        orderBook = poloniexService.fetchOrderBook();

        return convertOrderBookAndFilter(orderBook);
    }

    @Override
    public AccountInfoJson getAccountInfo() {
        return convertAccountInfo(poloniexService.fetchAccountInfo());
    }

    @Override
    protected AccountInfoJson convertAccountInfo(AccountInfo accountInfo) {
        final Wallet wallet = accountInfo.getWallet();
        return new AccountInfoJson(wallet.getBalance(Currency.BTC).getAvailable().toPlainString(),
                wallet.getBalance(new Currency("USDT")).getAvailable().toPlainString(),
                accountInfo.toString());
    }

    public OrderBookJson getOrderBook() {
        return convertOrderBookAndFilter(poloniexService.getOrderBook());
    }

    public TickerJson getTicker() {
        return convertTicker(poloniexService.getTicker());
    }

    public List<VisualTrade> getBestBids() {
        return orderBook.getBids().stream()
                .map(toVisual)
                .collect(Collectors.toList());
    }
    public List<VisualTrade> getAsks() {
        return orderBook.getAsks().stream()
                .map(toVisual)
                .collect(Collectors.toList());
    }

    private VisualTrade toVisualTrade(LimitOrder limitOrder) {
        final VisualTrade apply = toVisual.apply(limitOrder);
        return new VisualTrade(
                limitOrder.getCurrencyPair().toString(),
                limitOrder.getLimitPrice().toString(),
                limitOrder.getTradableAmount().toString(),
                limitOrder.getType().toString(),
                LocalDateTime.ofInstant(limitOrder.getTimestamp().toInstant(), ZoneId.systemDefault())
                        .toLocalTime().toString());
    }

    public OrderBookJson cleanOrderBook() {
        return convertOrderBookAndFilter(poloniexService.cleanOrderBook());
    }
}
