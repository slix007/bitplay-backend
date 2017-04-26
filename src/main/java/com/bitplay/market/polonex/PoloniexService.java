package com.bitplay.market.polonex;

import com.bitplay.market.MarketService;
import com.bitplay.market.arbitrage.ArbitrageService;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.utils.Utils;

import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import info.bitrich.xchangestream.poloniex.PoloniexStreamingExchange;
import info.bitrich.xchangestream.poloniex.PoloniexStreamingMarketDataService;

import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.poloniex.dto.trade.PoloniexLimitOrder;
import org.knowm.xchange.poloniex.dto.trade.PoloniexMoveResponse;
import org.knowm.xchange.poloniex.dto.trade.PoloniexOrderFlags;
import org.knowm.xchange.poloniex.dto.trade.PoloniexTradeResponse;
import org.knowm.xchange.poloniex.service.PoloniexTradeService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsZero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.annotation.PreDestroy;

import io.reactivex.disposables.Disposable;
import jersey.repackaged.com.google.common.collect.Sets;

/**
 * Created by Sergey Shurmin on 3/21/17.
 */
@Service
public class PoloniexService extends MarketService {

    private static final Logger logger = LoggerFactory.getLogger(PoloniexService.class);
    private static final Logger tradeLogger = LoggerFactory.getLogger("POLONIEX_TRADE_LOG");
    private final static BigDecimal POLONIEX_STEP = new BigDecimal("0.00000001");

    @Autowired
    ArbitrageService arbitrageService;

//    @Autowired
//    WebSocketEndpoint webSocketEndpoint;

    // My api key for empty account
    //    private static String KEY = "2326PK47-9500PEQV-S64511G1-1HF2V48N";
    //    private static String SECRET = "2de990fecb2ca516a8cd40fa0ffc8f95f4fc8021e3f7ee681972493c10311c260d26b35c0f2e41adec027056711e2e7b1eaa6cde7d8f679aa871e0a1a801c8fa";

    // Shared api key
    private final static String KEY = "ER7VDOUY-6OL4ETGD-VC960XII-T128DA7I";
    private final static String SECRET = "77c9ccd1a34b389633ca1aa666c710ada9b37bd6ce0172818abdea9396658cb2bb552938b885dbb7c43c9f529b675ab428d5c1b21551540ff23703ace4056764";

    public final static CurrencyPair CURRENCY_PAIR_USDT_BTC = new CurrencyPair("BTC", "USDT");

    private List<Long> latencyList = new ArrayList<>();

    private StreamingExchange getExchange() {

        ExchangeSpecification spec = new ExchangeSpecification(PoloniexStreamingExchange.class);
        spec.setApiKey(KEY);
        spec.setSecretKey(SECRET);

        return StreamingExchangeFactory.INSTANCE.createExchange(spec);
    }

    private StreamingExchange exchange;

    private OrderBook orderBook;
    private AccountInfo accountInfo = null;
    private Ticker ticker;
//    private List<PoloniexWebSocketDepth> updates = new ArrayList<>();

    Disposable orderBookSubscription;

    public PoloniexService() {
        init();
    }

    public void init() {
        exchange = getExchange();
        fetchOrderBook();
        initWebSocketConnection();

//        initOrderBookSubscribers(logger);
    }



    private void initWebSocketConnection() {
        exchange.connect().blockingAwait();

        final PoloniexStreamingMarketDataService streamingMarketDataService =
                (PoloniexStreamingMarketDataService) exchange.getStreamingMarketDataService();

        // we don't need ticker
        subscribeOnTicker(streamingMarketDataService);
//TODO Contact okCoin. Some updates looks missing. They probably don't send all.
//        subscribeOnOrderBookUpdates(streamingMarketDataService);
    }

    private void subscribeOnTicker(PoloniexStreamingMarketDataService streamingMarketDataService) {
        streamingMarketDataService
                .getTicker(CURRENCY_PAIR_USDT_BTC)
                .subscribe(ticker -> {
                        this.ticker = ticker;
//                        logger.debug("Incoming ticker: {}", ticker);

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

                    orderBook = PoloniexOrderBookMerger.merge(orderBook, poloniexWebSocketDepth);
//                    updates.add(poloniexWebSocketDepth);

//                    updates.stream().filter(depth -> depth.getData().getRate().compareTo(new BigDecimal("1206.0"))==0).collect(Collectors.toList())
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

    @Scheduled(fixedRate = 2000)
    public AccountInfo fetchAccountInfo() {
        try {
            accountInfo = exchange.getAccountService().getAccountInfo();
            logger.info("Balance BTC={}, USD={}",
                    accountInfo.getWallet().getBalance(Currency.BTC).toString(),
                    accountInfo.getWallet().getBalance(Currency.USD).toString());
        } catch (Exception e) {
            logger.error("Can not fetchAccountInfo", e);
        }
        return accountInfo;
    }

    public AccountInfo getAccountInfo() {
        return accountInfo;
    }

    @Override
    public TradeService getTradeService() {
        return exchange.getTradeService();
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
            logger.error("Can not fetchTrades", e);
        } catch (Exception e) {
            logger.error("Unexpected error on fetchTrades", e);
        }
        return trades;
    }

//    @Scheduled(fixedRate = 5000)
//    public void check() {
//        checkOrderBook(orderBook);
//    }

    public OrderBook fetchOrderBook() {
        try {
//                orderBook = exchange.getMarketDataService().getOrderBook(CURRENCY_PAIR_USDT_BTC, -1);
            final long startFetch = System.nanoTime();
            orderBook = exchange.getMarketDataService().getOrderBook(CURRENCY_PAIR_USDT_BTC, 5);
            final long endFetch = System.nanoTime();

            latencyList.add(endFetch - startFetch);
            if (latencyList.size() > 100) {
                logger.debug("Average get orderBook(5) time is {} ms",
                        latencyList
                                .stream()
                                .mapToDouble(a -> a)
                                .average().orElse(0) / 1000 / 1000);
                latencyList.clear();
            }

//            logger.debug("Fetched orderBook: {} asks, {} bids. Timestamp {}", orderBook.getAsks().size(), orderBook.getBids().size(),
//                    orderBook.getTimeStamp());

//            orderBookChangedSubject.onNext(orderBook);

            CompletableFuture.runAsync(() -> {
                checkOrderBook(orderBook);
            });

        } catch (IOException e) {
            logger.error("Can not fetchOrderBook", e);
        } catch (Exception e) {
            logger.error("Unexpected error on fetchOrderBook", e);
        }
        return orderBook;
    }

    @Override
    public OrderBook getOrderBook() {
        return this.orderBook;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public OrderBook cleanOrderBook() {
        this.orderBook = PoloniexOrderBookMerger.cleanOrderBook(this.orderBook);
        return orderBook;
    }

    public TradeResponse placeTakerOrder(Order.OrderType orderType, BigDecimal amount) {
        final TradeResponse tradeResponse = new TradeResponse();
        try {
            BigDecimal thePrice = getBestPrice(orderType);

            final PoloniexLimitOrder theOrder = new PoloniexLimitOrder(orderType, amount, CURRENCY_PAIR_USDT_BTC,
                    null, new Date(), thePrice);
            theOrder.setOrderFlags(Sets.newHashSet(PoloniexOrderFlags.IMMEDIATE_OR_CANCEL));

            String orderId = exchange.getTradeService().placeLimitOrder(theOrder);
            tradeResponse.setOrderId(orderId);

            PoloniexTradeResponse response = theOrder.getResponse();
            tradeResponse.setSpecificResponse(response);

            // TODO save trading history into DB
            tradeLogger.info("taker {} {}, status={}",
                    orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                    theOrder.getResponse().getPoloniexPublicTrades().stream()
                            .map(t -> String.format("amount=%s,quote=%s", t.getAmount(), t.getRate()))
                            .reduce(" ", String::concat),
                    theOrder.getStatus().toString()
            );

            fetchAccountInfo();
        } catch (Exception e) {
            logger.error("Place market order error", e);
            tradeResponse.setOrderId(e.getMessage());
            tradeResponse.setErrorMessage(e.getMessage());
        }
        return tradeResponse;
    }

    private BigDecimal getBestPrice(Order.OrderType orderType) {
        BigDecimal thePrice = null;
        if (orderType == Order.OrderType.BID) {
            thePrice = Utils.getBestAsks(getOrderBook().getAsks(), 1)
                    .get(0)
                    .getLimitPrice();
        } else if (orderType == Order.OrderType.ASK) {
            thePrice = Utils.getBestBids(getOrderBook().getBids(), 1)
                    .get(0)
                    .getLimitPrice();
        }
        return thePrice;
    }

    public TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amount) {
        TradeResponse tradeResponse = new TradeResponse();

        int attemptCount = 0;
        Exception lastException = null;

        while (attemptCount < 5 && tradeResponse.getOrderId() == null) {
            attemptCount++;
            try {
                PoloniexLimitOrder theOrder = tryToPlaceMakerOrder(orderType, amount);

                final PoloniexTradeResponse response = theOrder.getResponse();
                final String orderId = response.getOrderNumber().toString();
                tradeResponse.setSpecificResponse(response);
                tradeResponse.setOrderId(orderId);
                // TODO save trading history into DB
                tradeLogger.info("maker {} with amount={},quote={}, id={}, attempt={}",
                        orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                        amount,
                        theOrder.getLimitPrice(),
                        orderId,
                        attemptCount
                );

                openOrders.add(new LimitOrder(theOrder.getType(), amount, theOrder.getCurrencyPair(),
                        orderId, new Date(), theOrder.getLimitPrice(), null, null,
                        theOrder.getStatus()));

            } catch (Exception e) {
                lastException = e;
                // Retry
            }
        }

        fetchAccountInfo();

        if (tradeResponse.getOrderId() == null && lastException != null) {
            logger.error("Place market order error", lastException);
            tradeLogger.info("maker error {}", lastException.toString());
            tradeResponse.setOrderId(lastException.getMessage());
            tradeResponse.setErrorMessage(lastException.getMessage());
        }
        return tradeResponse;
    }

    /**
     * Use when you're sure that order should be moved(has not the best price)
     * Use {@link MarketService#moveMakerOrderIfNotFirst(LimitOrder)} when you know that price is not the best.
     */
    public MoveResponse moveMakerOrder(LimitOrder limitOrder) {
        MoveResponse response;
        int attemptCount = 0;
        String lastExceptionMsg = "";
        boolean orderFinished = false;
        PoloniexMoveResponse moveResponse = null;
        BigDecimal bestMakerPrice = BigDecimal.ZERO;
//        while (attemptCount < 5) {
            attemptCount++;
            try {
                bestMakerPrice = createBestMakerPrice(limitOrder.getType(), true);
                final PoloniexTradeService poloniexTradeService = (PoloniexTradeService) exchange.getTradeService();
                moveResponse = poloniexTradeService.move(
                        limitOrder.getId(),
                        limitOrder.getTradableAmount(),
                        bestMakerPrice,
                        PoloniexOrderFlags.POST_ONLY);
//                if (moveResponse.success()) {
//                    break;
//                }

            } catch (ExchangeException e) {
                if (e.getMessage().equals("Invalid order number, or you are not the person who placed the order.")) {
                    logger.info(e.getMessage());
                    orderFinished = true;
                } else {
                    lastExceptionMsg = e.getMessage();
                    logger.error("{} attempt on move maker order", attemptCount, e);
                }

            } catch (Exception e) {
                lastExceptionMsg = e.getMessage();
                logger.error("{} attempt on move maker order", attemptCount, e);
            }
//        }

        if (moveResponse != null && moveResponse.success()) {
            tradeLogger.info("Moved {} amount={},quote={},id={},attempt={}",
                    limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                    limitOrder.getTradableAmount(),
                    bestMakerPrice.toPlainString(),
                    limitOrder.getId(),
                    attemptCount);
            response = new MoveResponse(true, "");

        } else {
            if (!orderFinished) {
                tradeLogger.info("Moving error {} amount={},oldQuote={},id={},attempt={}",
                        limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                        limitOrder.getTradableAmount(),
                        limitOrder.getLimitPrice().toPlainString(),
                        limitOrder.getId(),
                        attemptCount);
            }
//                logger.error("on moving", lastException);
            response = new MoveResponse(false, "Moving error " + lastExceptionMsg,
                    orderFinished ? MoveResponse.MoveOrderStatus.IS_FINISHED : null);
        }
        return response;
    }

    private PoloniexLimitOrder tryToPlaceMakerOrder(Order.OrderType orderType, BigDecimal amount) throws Exception {

        BigDecimal thePrice = createBestMakerPrice(orderType, false);

        final PoloniexLimitOrder theOrder = new PoloniexLimitOrder(orderType, amount,
                CURRENCY_PAIR_USDT_BTC, null, new Date(), thePrice);
        // consider , PoloniexOrderFlags.POST_ONLY
        theOrder.setOrderFlags(Sets.newHashSet(PoloniexOrderFlags.POST_ONLY));

        exchange.getTradeService().placeLimitOrder(theOrder);

        return theOrder;
    }

    @Override
    protected BigDecimal getMakerStep() {
        return POLONIEX_STEP;
    }

    @Override
    protected BigDecimal getMakerDelta() {
        return arbitrageService.getMakerDelta();
    }

    public UserTrades fetchMyTradeHistory() {
//        returnTradeHistory
        UserTrades tradeHistory = null;
        try {
            tradeHistory = exchange.getTradeService().getTradeHistory(TradeHistoryParamsZero.PARAMS_ZERO);
        } catch (Exception e) {
            logger.info("Exception on fetchMyTradeHistory", e);
        }
        return tradeHistory;

    }
}
