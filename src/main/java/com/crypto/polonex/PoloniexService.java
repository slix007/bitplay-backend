package com.crypto.polonex;

import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import info.bitrich.xchangestream.poloniex.PoloniexStreamingExchange;
import info.bitrich.xchangestream.poloniex.PoloniexStreamingMarketDataService;
import info.bitrich.xchangestream.poloniex.incremental.PoloniexOrderBookMerger;
import info.bitrich.xchangestream.poloniex.incremental.PoloniexWebSocketDepth;

import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.trade.UserTrades;
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

import io.reactivex.disposables.Disposable;

/**
 * Created by Sergey Shurmin on 3/21/17.
 */
@Service
public class PoloniexService {

    Logger logger = LoggerFactory.getLogger(PoloniexService.class);

//    @Autowired
//    WebSocketEndpoint webSocketEndpoint;

    // My api key for empty account
    //    private static String KEY = "2326PK47-9500PEQV-S64511G1-1HF2V48N";
    //    private static String SECRET = "2de990fecb2ca516a8cd40fa0ffc8f95f4fc8021e3f7ee681972493c10311c260d26b35c0f2e41adec027056711e2e7b1eaa6cde7d8f679aa871e0a1a801c8fa";

    // Shared api key
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

    private OrderBook orderBook;
    private Ticker ticker;
    private List<PoloniexWebSocketDepth> updates = new ArrayList<>();

    Disposable orderBookSubscription;

    public PoloniexService() {
        init();
    }

    public void init() {
        exchange = getExchange();
        fetchOrderBook();
        initWebSocketConnection();
    }

    private void initWebSocketConnection() {
        exchange.connect().blockingAwait();

        final PoloniexStreamingMarketDataService streamingMarketDataService =
                (PoloniexStreamingMarketDataService) exchange.getStreamingMarketDataService();

        // we don't need ticker
        subscribeOnTicker(streamingMarketDataService);
//TODO fix it. Looks strange
        subscribeOnOrderBookUpdates(streamingMarketDataService);
    }

    private void subscribeOnTicker(PoloniexStreamingMarketDataService streamingMarketDataService) {
        streamingMarketDataService
                .getTicker(CURRENCY_PAIR_USDT_BTC)
                .subscribe(ticker -> {
                    this.ticker = ticker;
                    logger.debug("Incoming ticker: {}", ticker);
                }, throwable -> {
                    logger.error("Error in subscribing tickers.", throwable);
                });
    }

    private void subscribeOnOrderBookUpdates(PoloniexStreamingMarketDataService streamingMarketDataService) {
        orderBookSubscription = streamingMarketDataService
                .getOrderBookUpdate(CURRENCY_PAIR_USDT_BTC)
                .subscribe(poloniexWebSocketDepth -> {
                    // Do something
                    logger.debug(poloniexWebSocketDepth.toString());
                    synchronized (this) {
                        orderBook = PoloniexOrderBookMerger.merge(orderBook, poloniexWebSocketDepth);
                    }
                    updates.add(poloniexWebSocketDepth);

//                    webSocketEndpoint.sendLogMessage(poloniexWebSocketDepth.toString());

                    //IT DOSN"T WORK WELL orderBook.update(poloniexWebSocketDepth);
                });
    }

    @PreDestroy
    public void preDestroy() {
        // Unsubscribe from data order book.
        orderBookSubscription.dispose();

        // Disconnect from exchange (non-blocking)
        exchange.disconnect().subscribe(() -> logger.info("Disconnected from the Exchange"));
    }


    public AccountInfo fetchAccountInfo() {
        AccountInfo accountInfo = null;
        try {
            final TradeService tradeService = exchange.getTradeService();
            final UserTrades tradeHistory = tradeService.getTradeHistory(TradeHistoryParamsZero.PARAMS_ZERO);
            logger.info(tradeHistory.toString());
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
        Trades trades = null;

        try {
            List<CurrencyPairMetaData> selected = new ArrayList<>();

            Map<CurrencyPair, CurrencyPairMetaData> selectedMeta = new HashMap<>();

            exchange.getExchangeMetaData().getCurrencyPairs()
                    .forEach((pair, currencyPairMetaData) -> {
                if (pair.base == Currency.BTC
                    //&& pair.counter == Currency.USD
                        ) {
                    selected.add(currencyPairMetaData);
                    selectedMeta.put(pair, currencyPairMetaData);
                }
            });

            final MarketDataService marketDataService = exchange.getMarketDataService();
            trades = marketDataService.getTrades(selectedMeta.keySet().iterator().next());
            logger.info("Fetched {} trades", trades.getTrades().size());

        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return trades;
    }

    public OrderBook fetchOrderBook() {
        try {
            synchronized (this) {
                orderBook = exchange.getMarketDataService().getOrderBook(CURRENCY_PAIR_USDT_BTC, -1);
            }
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
        return this.orderBook;
    }

    public Ticker getTicker() {
        return ticker;
    }
}
