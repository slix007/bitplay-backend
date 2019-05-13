package com.bitplay.market.polonex;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.BalanceService;
import com.bitplay.market.LogService;
import com.bitplay.market.MarketService;
import com.bitplay.market.model.Affordable;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.utils.Utils;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingExchangeFactory;
import info.bitrich.xchangestream.poloniex.PoloniexStreamingExchange;
import info.bitrich.xchangestream.poloniex.PoloniexStreamingMarketDataService;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PreDestroy;
import jersey.repackaged.com.google.common.collect.Sets;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
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
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 3/21/17.
 */
@Service("poloniex")
public class PoloniexService extends MarketService {

    public final static CurrencyPair CURRENCY_PAIR_USDT_BTC = new CurrencyPair("BTC", "USDT");
    public final static Currency CURRENCY_USDT = new Currency("USDT");
    private static final Logger logger = LoggerFactory.getLogger(PoloniexService.class);
    private static final Logger tradeLogger = LoggerFactory.getLogger("POLONIEX_TRADE_LOG");
    private final static String NAME = "poloniex";
//    private List<PoloniexWebSocketDepth> updates = new ArrayList<>();
    Disposable orderBookSubscription;
    Disposable accountInfoSubscription;
    Observable<OrderBook> orderBookObservable;

    ArbitrageService arbitrageService;
    private StreamingExchange exchange;
    private Ticker ticker;

    //    @Autowired
//    WebSocketEndpoint webSocketEndpoint;
    private List<Long> latencyList = new ArrayList<>();

    @Override
    public PosDiffService getPosDiffService() {
        return null;
    }

    @Override
    public ArbitrageService getArbitrageService() {
        return arbitrageService;
    }

    @Override
    public BalanceService getBalanceService() {
        return null;
    }

    @Autowired
    public void setArbitrageService(ArbitrageService arbitrageService) {
        this.arbitrageService = arbitrageService;
    }

    @Override
    public Currency getSecondCurrency() {
        return CURRENCY_USDT;
    }

    @Override
    public boolean isMarketStopped() {
        return false;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public LogService getTradeLogger() {
        return null;
    }

    @Override
    public LogService getLogger() {
        return null;
    }

    @Override
    public String fetchPosition() {
        return null;
    }

    @Override
    protected void onReadyState() {
        // nothing for now
    }

    @Override
    public ContractType getContractType() {
        return null;
    }

    @Override
    protected void iterateOpenOrdersMove(Object... iterateArgs) {

    }

    @Override
    protected void postOverload() {
    }

    @Override
    protected Exchange getExchange() {
        return exchange;
    }

    private StreamingExchange initExchange(String key, String secret) {

        ExchangeSpecification spec = new ExchangeSpecification(PoloniexStreamingExchange.class);
        spec.setApiKey(key);
        spec.setSecretKey(secret);

        return StreamingExchangeFactory.INSTANCE.createExchange(spec);
    }

    @Override
    public void initializeMarket(String key, String secret, ContractType contractType, Object... exArgs) {
        exchange = initExchange(key, secret);
//        fetchOrderBook();
        initWebSocketConnection();

        createOrderBookObservable();
//        initOrderBookSubscribers(logger);
//        initLocalSubscribers();
//        startAccountInfoListener();
        logger.info("POLONIEX INIT FINISHED");
    }

//    private void initLocalSubscribers() {
//        accountInfoSubscription = fetchAccountInfo()
//                .subscribe(accountInfo1 -> {
//                    this.accountInfo = accountInfo1;
//                    logger.info("Balance BTC={}, USD={}",
//                            accountInfo.getWallet().getBalance(Currency.BTC).toString(),
//                            accountInfo.getWallet().getBalance(Currency.USD).toString());
//                }, throwable -> logger.error("Can not fetchAccountInfo", throwable));
//
//    }

    private void initWebSocketConnection() {
        exchange.connect().blockingAwait();

        final PoloniexStreamingMarketDataService streamingMarketDataService =
                (PoloniexStreamingMarketDataService) exchange.getStreamingMarketDataService();

        subscribeOnTicker(streamingMarketDataService);
//        subscribeOnOrderBookUpdates(streamingMarketDataService);
    }

    private void subscribeOnTicker(PoloniexStreamingMarketDataService streamingMarketDataService) {
        streamingMarketDataService
                .getTicker(CURRENCY_PAIR_USDT_BTC)
                .subscribe((Ticker ticker) -> {
                        this.ticker = ticker;
                }, throwable -> {
                    logger.error("Error in subscribing tickers.", throwable);
                });
    }

//    private void subscribeOnOrderBookUpdates(PoloniexStreamingMarketDataService streamingMarketDataService) {
//        orderBookSubscription = streamingMarketDataService
//                .getOrderBookUpdate(CURRENCY_PAIR_USDT_BTC)
//                .subscribe(poloniexWebSocketDepth -> {
//                    // Do something
//                    logger.debug(poloniexWebSocketDepth.toString());
//
//                    orderBook = PoloniexOrderBookMerger.merge(orderBook, poloniexWebSocketDepth);
//                    bestAsk = Utils.getBestAsks(getOrderBook(), 1).get(0).getLimitPrice();
//                    bestBid = Utils.getBestBids(getOrderBook(), 1).get(0).getLimitPrice();
//
//
////                    updates.add(poloniexWebSocketDepth);
//
////                    updates.stream().filter(depth -> depth.getData().getRate().compareTo(new BigDecimal("1206.0"))==0).collect(Collectors.toList())
////                    webSocketEndpoint.sendLogMessage(poloniexWebSocketDepth.toString());
//
//                    //IT DOSN"T WORK WELL orderBook.update(poloniexWebSocketDepth);
//                });
//    }

    @PreDestroy
    public void preDestroy() {
        // Unsubscribe from data order book.
        orderBookSubscription.dispose();
        accountInfoSubscription.dispose();

        // Disconnect from exchange (non-blocking)
        exchange.disconnect()
                .subscribe(() -> logger.info("Disconnected from the Exchange"));
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
//        checkOpenOrdersForMoving(orderBook);
//    }

//    @Scheduled(fixedRate = 2000)
    public void fetchOpenOrdersSchedule() {
        this.fetchOpenOrders();
    }

    private void createOrderBookObservable() {
        final Observable<OrderBook> orderBookObservable = Observable.create(observableOnSubscribe -> {
            while (!observableOnSubscribe.isDisposed()) {
                boolean noSleep = false;
                try {
                    final OrderBook orderBook = fetchOrderBook();
                    if (orderBook != null) {
                        observableOnSubscribe.onNext(orderBook);
                    }
                } catch (ExchangeException e) {
                    if (e.getMessage().startsWith("Nonce must be greater than")) {
                        noSleep = true;
                        logger.warn(e.getMessage());
                    } else {
                        observableOnSubscribe.onError(e);
                    }
                }

                if (noSleep) sleep(10);
                else sleep(500);
            }
        });

        this.orderBookObservable = orderBookObservable.share();
    }

    public OrderBook fetchOrderBook() {
        try {
//                orderBook = exchange.getMarketDataService().getOrderBook(CURRENCY_PAIR_USDT_BTC, -1);
            final long startFetch = System.nanoTime();
            orderBook = exchange.getMarketDataService().getOrderBook(CURRENCY_PAIR_USDT_BTC, 5);
            bestAsk = Utils.getBestAsks(orderBook, 1).get(0).getLimitPrice();
            bestBid = Utils.getBestBids(orderBook, 1).get(0).getLimitPrice();
            logger.debug("ask: {}, bid: {}", this.bestAsk, this.bestBid);

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

//            CompletableFuture.runAsync(this::checkOpenOrdersForMoving)
//                    .exceptionally(throwable -> {
//                        logger.error("OnCheckOpenOrders", throwable);
//                        return null;
//                    });

        } catch (IOException e) {
            logger.error("Can not fetchOrderBook", e);
        } catch (Exception e) {
            logger.error("Unexpected error on fetchOrderBook", e);
        }
        return orderBook;
    }

    public boolean isAffordable(Order.OrderType orderType, BigDecimal tradableAmount) {
        return false;
    }

    @Override
    public Affordable recalcAffordable() {
        return null;
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

        } catch (Exception e) {
            logger.error("Place market order error", e);
            tradeResponse.setOrderId(e.getMessage());
            tradeResponse.setErrorCode(e.getMessage());
        }
        return tradeResponse;
    }

    private BigDecimal getBestPrice(Order.OrderType orderType) {
        BigDecimal thePrice = null;
        if (orderType == Order.OrderType.BID) {
            thePrice = Utils.getBestAsk(getOrderBook()).getLimitPrice();
        } else if (orderType == Order.OrderType.ASK) {
            thePrice = Utils.getBestBid(getOrderBook()).getLimitPrice();
        }
        return thePrice;
    }

    @Override
    public TradeResponse placeOrder(PlaceOrderArgs placeOrderArgs) {
        return null;
    }

    public TradeResponse placeOrderOnSignal(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes, SignalType signalType) {
        TradeResponse tradeResponse = new TradeResponse();
        BigDecimal amountInBtc = amountInContracts; //TODO convert to btc

        int attemptCount = 0;
        Exception lastException = null;

        while (attemptCount < 5 && tradeResponse.getOrderId() == null) {
            attemptCount++;
            try {
                PoloniexLimitOrder theOrder = tryToPlaceMakerOrder(orderType, amountInBtc);

                final PoloniexTradeResponse response = theOrder.getResponse();
                final String orderId = response.getOrderNumber().toString();
                tradeResponse.setSpecificResponse(response);
                tradeResponse.setOrderId(orderId);

                String diffWithSignal = "";
                if (bestQuotes != null) {
                    diffWithSignal = orderType.equals(Order.OrderType.BID)
                            ? String.format("diff2__buy_p = ask_p[1] - order_price_buy_p = %s", bestQuotes.getAsk1_p().subtract(theOrder.getLimitPrice()).toPlainString()) //"BUY"
                            : String.format("diff1_sell_p = order_price_sell_p - bid_p[1] = %s",theOrder.getLimitPrice().subtract(bestQuotes.getBid1_p()).toPlainString()); //"SELL"
                }

                tradeLogger.info("maker {} with amount={},quote={}, id={}, attempt={}. {}",
                        orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                        amountInBtc,
                        theOrder.getLimitPrice(),
                        orderId,
                        attemptCount,
                        diffWithSignal
                );

//                openOrders.add(new LimitOrder(theOrder.getType(), amount, theOrder.getCurrencyPair(),
//                        orderId, new Date(), theOrder.getLimitPrice(), null, null,
//                        theOrder.getStatus()));
                orderIdToSignalInfo.put(orderId, bestQuotes);

            } catch (Exception e) {
                lastException = e;
                // Retry
            }
        }

        //fetchAccountInfo();

        if (tradeResponse.getOrderId() == null && lastException != null) {
            logger.error("Place market order error", lastException);
            tradeLogger.info("maker error {}", lastException.toString());
            tradeResponse.setOrderId(lastException.getMessage());
            tradeResponse.setErrorCode(lastException.getMessage());
        }
        return tradeResponse;
    }

    /**
     * Use when you're sure that order should be moved(has not the best price)
     * Use {@link MarketService#moveMakerOrderIfNotFirst(FplayOrder, Object...)}} when you know that price is not the best.
     */
    public MoveResponse moveMakerOrder(FplayOrder fplayOrder, BigDecimal bestMarketPrice, Object... reqMovingArgs) {
        final LimitOrder limitOrder = (LimitOrder) fplayOrder.getOrder();

        MoveResponse response;
        int attemptCount = 0;
        String lastExceptionMsg = "";
        PoloniexMoveResponse moveResponse = null;
        BigDecimal bestMakerPrice = BigDecimal.ZERO;
        BestQuotes bestQuotes = orderIdToSignalInfo.get(limitOrder.getId());

        while (attemptCount < 2) {
            attemptCount++;
            try {
                bestMakerPrice = createBestMakerPrice(limitOrder.getType(), getOrderBook());
                final PoloniexTradeService poloniexTradeService = (PoloniexTradeService) exchange.getTradeService();
                moveResponse = poloniexTradeService.move(
                        limitOrder.getId(),
                        limitOrder.getTradableAmount(),
                        bestMakerPrice,
                        PoloniexOrderFlags.POST_ONLY);
                if (moveResponse.success()) {
                    break;
                }

//            } catch (ExchangeException e) {
//                if (e.getMessage().equals("Invalid order number, or you are not the person who placed the order.")) {
//                    logger.info(e.getMessage());
////                    orderFinished = true;
//                    return new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, e.getMessage());
//                } else {
//                    lastExceptionMsg = e.getMessage();
//                    logger.error("{} attempt on move maker order", attemptCount, e);
//                }

            } catch (Exception e) {
                lastExceptionMsg = e.getMessage();
                logger.error("{} attempt on move maker order {}", attemptCount, e.getMessage());
            }
        }

        if (moveResponse != null && moveResponse.success()) {
            String diffWithSignal = "";
            if (bestQuotes != null) {
                diffWithSignal = limitOrder.getType().equals(Order.OrderType.BID)
                        ? String.format("diff2__buy_p = ask_p[1] - order_price_buy_p = %s", bestQuotes.getAsk1_p().subtract(bestMakerPrice).toPlainString()) //"BUY"
                        : String.format("diff1_sell_p = order_price_sell_p - bid_p[1] = %s",bestMakerPrice.subtract(bestQuotes.getBid1_p()).toPlainString()); //"SELL"
            }

            final String logString = String.format("Moved %s amount=%s,quote=%s,id=%s,attempt=%s. %s",
                    limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                    limitOrder.getTradableAmount(),
                    bestMakerPrice.toPlainString(),
                    limitOrder.getId(),
                    attemptCount,
                    diffWithSignal);

            orderIdToSignalInfo.put(limitOrder.getId(), bestQuotes);

            tradeLogger.info(logString);
            response = new MoveResponse(MoveResponse.MoveOrderStatus.MOVED, logString);
        } else {
            final String logString = String.format("Moving error %s amount=%s,oldQuote=%s,id=%s,attempt=%s(%s)",
                        limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                        limitOrder.getTradableAmount(),
                        limitOrder.getLimitPrice().toPlainString(),
                        limitOrder.getId(),
                    attemptCount,
                    lastExceptionMsg);
            tradeLogger.info(logString);
            sleep(200);
            response = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, logString);
        }
        return response;
    }

    private PoloniexLimitOrder tryToPlaceMakerOrder(Order.OrderType orderType, BigDecimal amount) throws Exception {

        BigDecimal thePrice = createBestMakerPrice(orderType, getOrderBook());

        final PoloniexLimitOrder theOrder = new PoloniexLimitOrder(orderType, amount,
                CURRENCY_PAIR_USDT_BTC, null, new Date(), thePrice);
        // consider , PoloniexOrderFlags.POST_ONLY
        theOrder.setOrderFlags(Sets.newHashSet(PoloniexOrderFlags.POST_ONLY));

        exchange.getTradeService().placeLimitOrder(theOrder);

        return theOrder;
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

    @Override
    public String getPositionAsString() {
        return null;
    }

    @Override
    public PersistenceService getPersistenceService() {
        return null;
    }

    @Override
    public boolean checkLiquidationEdge(Order.OrderType orderType) {
        return false;
    }
}
