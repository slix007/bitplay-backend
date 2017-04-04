package com.crypto.okcoin;

import info.bitrich.xchangestream.okcoin.OkCoinStreamingExchange;

import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PreDestroy;

import io.reactivex.disposables.Disposable;

/**
 * Created by Sergey Shurmin on 3/21/17.
 */
@Service
public class OkCoinService {

    private static final Logger logger = LoggerFactory.getLogger(OkCoinService.class);

    private static String KEY = "d4566d08-4fef-49ac-8933-e51f8c873795";
    private static String SECRET = "3DB6AD75C7CD78392947A5D4CE8567D2";

    private final static CurrencyPair CURRENCY_PAIR_BTC_USD = new CurrencyPair("BTC", "USD");

    private OkCoinStreamingExchange exchange;
//    private ExchangeMetaData exchangeMetaData;
//    private MarketDataService marketDataService;
//    private AccountService accountService; // account and wallets. Current
//    private TradeService tradeService; // Create/check orders

    Disposable subscription;

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


    public OkCoinService() {
        init();
        fetchCurrencies();
        fetchOrderBook();
        fetchAccountInfo();
    }

    public void init() {
        exchange = initExchange();

        // Connect to the Exchange WebSocket API. Blocking wait for the connection.
        exchange.connect().blockingAwait();
        // Subscribe to live trades update.
//        exchange.getStreamingMarketDataService()
//                .getTicker(CURRENCY_PAIR_BTC_USD)
//                .subscribe(ticker -> {
//                    logger.info("Incoming ticker: {}", ticker);
//                }, throwable -> {
//                    logger.error("Error in subscribing tickers.", throwable);
//                });

        // Subscribe order book data with the reference to the subscription.
//        subscription = exchange.getStreamingMarketDataService()
//                .getOrderBook(CURRENCY_PAIR_BTC_USD)
//                .subscribe(orderBook -> {
//                    // Do something
//                    logger.info("orderBook changed!");
//                });

//        exchange.getStreamingMarketDataService().getOrderBook(CurrencyPair.BTC_USD).subscribe(orderBook -> {
//            logger.info("First ask: {}", orderBook.getAsks().get(0));
//            logger.info("First bid: {}", orderBook.getBids().get(0));
//        }, throwable -> logger.error("ERROR in getting order book: ", throwable));

        exchange.getStreamingMarketDataService().getTicker(CurrencyPair.BTC_USD).subscribe(ticker -> {
            logger.info("TICKER: {}", ticker);
        }, throwable -> logger.error("ERROR in getting ticker: ", throwable));

//        exchange.getStreamingMarketDataService().getTrades(CurrencyPair.BTC_USD).subscribe(trade -> {
//            logger.info("TRADE: {}", trade);
//        }, throwable -> logger.error("ERROR in getting trades: ", throwable));
    }

    public String fetchCurrencies() {
        final List<CurrencyPair> exchangeSymbols = exchange.getExchangeSymbols();
        final String toString = Arrays.toString(exchangeSymbols.toArray());
        System.out.println(toString);
        return toString;
    }

    @PreDestroy
    public void preDestroy() {
        // Unsubscribe from data order book.
        subscription.dispose();

        // Disconnect from exchange (non-blocking)
        exchange.disconnect().subscribe(() -> logger.info("Disconnected from the Exchange"));
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

    public Trades fetchTrades() {
        fetchAccountInfo();
        Trades trades = null;

        try {

//            final Map<CurrencyPair, CurrencyPairMetaData> currencyPairs = exchangeMetaData.getCurrencyPairs();
            List<CurrencyPairMetaData> selected = new ArrayList<>();

            Map<CurrencyPair, CurrencyPairMetaData> selectedMeta = new HashMap<>();

            exchange.getExchangeMetaData().getCurrencyPairs().forEach((pair, currencyPairMetaData) -> {
                if (pair.base == Currency.BTC
                    //&& pair.counter == Currency.USD
                        ) {
                    selected.add(currencyPairMetaData);
                    selectedMeta.put(pair, currencyPairMetaData);
                }
            });

            final MarketDataService marketDataService = exchange.getMarketDataService();
            trades = marketDataService.getTrades(selectedMeta.keySet().iterator().next());
//            System.out.println(trades);
            logger.info("Fetched {} trades", trades.getTrades().size());

//            AccountService accountService = exchange.getAccountService();
//            generic(accountService);
//            raw((PoloniexAccountServiceRaw) accountService);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return trades;
    }

    public OrderBook fetchOrderBook() {
        OrderBook orderBook = null;
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

    private static void generic(AccountService accountService) throws IOException {
        System.out.println("----------GENERIC----------");
//        System.out.println(accountService.requestDepositAddress(Currency.BTC, new String[0]));
        final AccountInfo accountInfo = accountService.getAccountInfo();
//        System.out.println(accountInfo);
//        System.out.println(accountService.withdrawFunds(Currency.BTC, new BigDecimal("0.03"), "13ArNKUYZ4AmXP4EUzSHMAUsvgGok74jWu"));
    }

}
