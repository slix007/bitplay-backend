package com.crypto.polonex;

import info.bitrich.xchangestgream.poloniex.PoloniexStreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;

import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.meta.ExchangeMetaData;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.poloniex.service.PoloniexAccountServiceRaw;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsZero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PreDestroy;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

/**
 * Created by Sergey Shurmin on 3/21/17.
 */
@Service
public class PoloniexService {

    Logger logger = LoggerFactory.getLogger(PoloniexService.class);

    //    private static String KEY = "2326PK47-9500PEQV-S64511G1-1HF2V48N";
    //    private static String SECRET = "2de990fecb2ca516a8cd40fa0ffc8f95f4fc8021e3f7ee681972493c10311c260d26b35c0f2e41adec027056711e2e7b1eaa6cde7d8f679aa871e0a1a801c8fa";

    private final static String KEY = "7HQHK3EL-40SJE5G8-1L9ZHPK6-7R6IEBIB";
    private final static String SECRET = "1cd1b572fe1bbfd3f0920ea2df364b74b3c07efedf838df245602cf55e52d7441d14464f4a116cbc431bfa0320f299045aa563497fb57c2e7c6fc78d5c703ea2";

    private final static CurrencyPair CURRENCY_PAIR_USDT_BTC = new CurrencyPair("BTC", "USDT");


    private StreamingExchange getExchange() {

        ExchangeSpecification spec = new ExchangeSpecification(PoloniexStreamingExchange.class);
        spec.setApiKey(KEY);
        spec.setSecretKey(SECRET);

        return StreamingExchangeFactory.INSTANCE.createExchange(spec);
    }

    private StreamingExchange exchange;
    private ExchangeMetaData exchangeMetaData;
    private MarketDataService marketDataService;
    private AccountService accountService; // account and wallets. Current
    private TradeService tradeService; // Create/check orders

    Disposable subscription;

    public PoloniexService() {
        init();
    }

    public void init() {
        exchange = getExchange();
        exchangeMetaData = exchange.getExchangeMetaData();

        marketDataService = exchange.getMarketDataService();
        accountService  = exchange.getAccountService();
        tradeService = exchange.getTradeService();
//        initStreaming();
//        // Subscribe order book data with the reference to the subscription.
//        subscription = exchange.getStreamingMarketDataService()
//                .getOrderBook(CURRENCY_PAIR_USDT_BTC)
//                .subscribe(orderBook -> {
//                    // Do something
//                    logger.info("orderBook changed!");
//
//                });
    }

    public Observable<Ticker> initStreaming() {
        // Connect to the Exchange WebSocket API. Blocking wait for the connection.
        exchange.connect().blockingAwait();
        // Subscribe to live trades update.
        final Observable<Ticker> tickerObservable = exchange.getStreamingMarketDataService()
                .getTicker(CURRENCY_PAIR_USDT_BTC);

//        final Disposable subscribe = tickerObservable
//                .subscribe(ticker -> {
//                    logger.info("Incoming ticker: {}", ticker);
//                }, throwable -> {
//                    logger.error("Error in subscribing tickers.", throwable);
//                });
        return tickerObservable;
    }

    @PreDestroy
    public void preDestroy() {
        // Unsubscribe from data order book.
//        subscription.dispose();

        // Disconnect from exchange (non-blocking)
        exchange.disconnect().subscribe(() -> logger.info("Disconnected from the Exchange"));
    }

    public AccountInfo fetchAccountInfo() {
        AccountInfo accountInfo = null;
        try {
            final MarketDataService marketDataService = exchange.getMarketDataService();
            final TradeService tradeService = exchange.getTradeService();
            final UserTrades tradeHistory = tradeService.getTradeHistory(TradeHistoryParamsZero.PARAMS_ZERO);


            logger.info(tradeHistory.toString());
//            final OrderBook orderBook = marketDataService.getOrderBook(new CurrencyPair("BTC", "USDT"));
//            logger.info(orderBook.toString());



            accountInfo = accountService.getAccountInfo();
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

            exchangeMetaData.getCurrencyPairs().forEach((pair, currencyPairMetaData) -> {
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
            orderBook = marketDataService.getOrderBook(CURRENCY_PAIR_USDT_BTC);
            logger.info("Fetched orderBook: {} asks, {} bids. Timestamp {}", orderBook.getAsks().size(), orderBook.getBids().size(),
                    orderBook.getTimeStamp());
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return orderBook;
    }






    public void doWork() {


        try {
//            CertHelper.trustAllCerts();
            StreamingExchange poloniex = getExchange();
            final ExchangeMetaData exchangeMetaData = poloniex.getExchangeMetaData();
            final Map<CurrencyPair, CurrencyPairMetaData> currencyPairs = exchangeMetaData.getCurrencyPairs();
            System.out.println("Pair0=" + currencyPairs.get(0));

            System.out.println("Currency0=" + exchangeMetaData.getCurrencies().get(0));
            List<CurrencyPairMetaData> selected = new ArrayList<>();

            Map<CurrencyPair, CurrencyPairMetaData> selectedMeta = new HashMap<>();

            exchangeMetaData.getCurrencyPairs().forEach((pair, currencyPairMetaData) -> {
                if (pair.base == Currency.BTC
                        //&& pair.counter == Currency.USD
                        ) {
                    selected.add(currencyPairMetaData);
                    selectedMeta.put(pair, currencyPairMetaData);
                }
            });

//            selected.forEach(currencyPairMetaData -> {
//                System.out.println("currencyPairMetaData=" + currencyPairMetaData);
//            });

//            System.out.println("exchangeMetaData=" + selectedMeta);



            final MarketDataService marketDataService = poloniex.getMarketDataService();
            final Trades trades = marketDataService.getTrades(selectedMeta.keySet().iterator().next());
            System.out.println(trades);


            AccountService accountService = poloniex.getAccountService();
//            generic(accountService);
//            raw((PoloniexAccountServiceRaw) accountService);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void generic(AccountService accountService) throws IOException {
        System.out.println("----------GENERIC----------");
//        System.out.println(accountService.requestDepositAddress(Currency.BTC, new String[0]));
        final AccountInfo accountInfo = accountService.getAccountInfo();
//        System.out.println(accountInfo);
//        System.out.println(accountService.withdrawFunds(Currency.BTC, new BigDecimal("0.03"), "13ArNKUYZ4AmXP4EUzSHMAUsvgGok74jWu"));
    }

    private static void raw(PoloniexAccountServiceRaw accountService) throws IOException {
        System.out.println("------------RAW------------");
//        System.out.println(accountService.getDepositAddress("BTC"));
        System.out.println(accountService.getWallets());
    }
}
