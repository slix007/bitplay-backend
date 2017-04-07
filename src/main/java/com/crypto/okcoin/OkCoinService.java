package com.crypto.okcoin;

import com.crypto.utils.Utils;

import info.bitrich.xchangestream.okcoin.OkCoinStreamingExchange;

import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.annotation.PreDestroy;

import io.reactivex.disposables.Disposable;

/**
 * Created by Sergey Shurmin on 3/21/17.
 */
@Service
//@Scope("prototype")
public class OkCoinService { //extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(OkCoinService.class);

    private static String KEY = "d4566d08-4fef-49ac-8933-e51f8c873795";
    private static String SECRET = "3DB6AD75C7CD78392947A5D4CE8567D2";

    private final static CurrencyPair CURRENCY_PAIR_BTC_USD = new CurrencyPair("BTC", "USD");

    private OkCoinStreamingExchange exchange;

    OrderBook orderBook = null;

    Disposable orderBookSubscription;

    private OkCoinStreamingExchange initExchange() {
        ExchangeSpecification spec = new ExchangeSpecification(OkCoinStreamingExchange.class);
        spec.setApiKey(KEY);
        spec.setSecretKey(SECRET);

        spec.setExchangeSpecificParametersItem("Use_Intl", true);

        exchange = (OkCoinStreamingExchange) ExchangeFactory.INSTANCE.createExchange(spec);
        String metaDataFileName = ((BaseExchange) exchange).getMetaDataFileName(spec);
        logger.info("OKCOING metaDataFileName=" + metaDataFileName);

        return exchange;
    }

//    @Override
//    public void run() {
////        init();
////        fetchCurrencies();
////        fetchOrderBook();
////        fetchAccountInfo();
//
//    }

    public OkCoinService() {
        init();
//        fetchCurrencies();
//        fetchOrderBook();
//        fetchAccountInfo();
    }

    public void init() {
        exchange = initExchange();

        initWebSocketConnection();
    }

    private void initWebSocketConnection() {
        // Connect to the Exchange WebSocket API. Blocking wait for the connection.
        exchange.connect().blockingAwait();

        subscribeOnOrderBook();

        subscribeOnOthers();
    }

    private void subscribeOnOthers() {
        //        exchange.getStreamingMarketDataService().getTicker(CurrencyPair.BTC_USD).subscribe(ticker -> {
//            logger.info("TICKER: {}", ticker);
//        }, throwable -> logger.error("ERROR in getting ticker: ", throwable));

//        exchange.getStreamingMarketDataService().getTrades(CurrencyPair.BTC_USD).subscribe(trade -> {
//            logger.info("TRADE: {}", trade);
//        }, throwable -> logger.error("ERROR in getting trades: ", throwable));
    }

    private void subscribeOnOrderBook() {
        //TODO subscribe on updates only to increase the speed
        orderBookSubscription = exchange.getStreamingMarketDataService()
                .getOrderBook(CurrencyPair.BTC_USD, 20)
                .subscribe(orderBook -> {
                    final List<LimitOrder> bestAsks = Utils.getBestAsks(orderBook.getAsks(), 1);
                    final LimitOrder bestAsk = bestAsks.size() > 0 ? bestAsks.get(0) : null;
                    final List<LimitOrder> bestBids = Utils.getBestBids(orderBook.getBids(), 1);
                    final LimitOrder bestBid = bestBids.size() > 0 ? bestBids.get(0) : null;
                    logger.debug("First ask: {}", bestAsk);
                    logger.debug("First bid: {}", bestBid);
                    this.orderBook = orderBook;
                }, throwable -> logger.error("ERROR in getting order book: ", throwable));
    }

    @PreDestroy
    public void preDestroy() {
        // Unsubscribe from data order book.
        orderBookSubscription.dispose();

        // Disconnect from exchange (non-blocking)
        exchange.disconnect().subscribe(() -> logger.info("Disconnected from the Exchange"));
    }

    public String fetchCurrencies() {
        final List<CurrencyPair> exchangeSymbols = exchange.getExchangeSymbols();
        final String toString = Arrays.toString(exchangeSymbols.toArray());
        System.out.println(toString);
        return toString;
    }

    public AccountInfo fetchAccountInfo() {
        AccountInfo accountInfo = null;
        try {
            final Ticker ticker = exchange.getMarketDataService().getTicker(CURRENCY_PAIR_BTC_USD);
            logger.info("OKCOIN TICKER + " + ticker.toString());

            accountInfo = exchange.getAccountService().getAccountInfo();
            logger.info(accountInfo.toString());
            logger.info("Balance BTC {}", accountInfo.getWallet().getBalance(Currency.BTC).toString());
            logger.info("Balance USD {}", accountInfo.getWallet().getBalance(Currency.USD).toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return accountInfo;
    }

    public OrderBook fetchOrderBook() {
        try {
            orderBook = exchange.getMarketDataService().getOrderBook(CURRENCY_PAIR_BTC_USD);
            logger.info("Fetched orderBook: {} asks, {} bids. Timestamp {}", orderBook.getAsks().size(), orderBook.getBids().size(),
                    orderBook.getTimeStamp());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return orderBook;
    }

    public OrderBook getOrderBook() {
        return orderBook;
    }
}
