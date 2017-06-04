package com.bitplay.market.okcoin;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.market.MarketService;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.utils.Utils;

import info.bitrich.xchangestream.okcoin.OkCoinStreamingExchange;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.okcoin.service.OkCoinTradeService;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import rx.Completable;

/**
 * Created by Sergey Shurmin on 3/21/17.
 */
@Service("okcoin")
public class OkCoinService extends MarketService {

    private static final Logger logger = LoggerFactory.getLogger(OkCoinService.class);
    private static final Logger tradeLogger = LoggerFactory.getLogger("OKCOIN_TRADE_LOG");

    private final static CurrencyPair CURRENCY_PAIR_BTC_USD = new CurrencyPair("BTC", "USD");

    private static final BigDecimal OKCOIN_STEP = new BigDecimal("0.01");
    private final static String NAME = "okcoin";

    ArbitrageService arbitrageService;

    @Autowired
    public void setArbitrageService(ArbitrageService arbitrageService) {
        this.arbitrageService = arbitrageService;
    }

    private OkCoinStreamingExchange exchange;

    Disposable orderBookSubscription;
    private Observable<OrderBook> orderBookObservable;
    private Map<String, Disposable> orderSubscriptions = new HashMap<>();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected Exchange getExchange() {
        return exchange;
    }

    @Override
    public void initializeMarket(String key, String secret) {
        exchange = initExchange(key, secret);

        initWebSocketAndAllSubscribers();
    }

    private void initWebSocketAndAllSubscribers() {
        initWebSocketConnection();

        createOrderBookObservable();

        subscribeOnOrderBook();

        subscribeOnOthers();

        startTradesListener(); // to remove openOrders

        fetchOpenOrdersWithDelay();
    }

    private void createOrderBookObservable() {
        orderBookObservable = exchange.getStreamingMarketDataService()
                .getOrderBook(CurrencyPair.BTC_USD, 20)
                .doOnDispose(() -> logger.info("okcoin subscription doOnDispose"))
                .doOnTerminate(() -> logger.info("okcoin subscription doOnTerminate"))
                .doOnError(throwable -> logger.error("okcoin onError orderBook", throwable))
                .retryWhen(throwableObservable -> throwableObservable.delay(5, TimeUnit.SECONDS))
                .share();
    }

    private OkCoinStreamingExchange initExchange(String key, String secret) {
        ExchangeSpecification spec = new ExchangeSpecification(OkCoinStreamingExchange.class);
        spec.setApiKey(key);
        spec.setSecretKey(secret);

        spec.setExchangeSpecificParametersItem("Use_Intl", true);

        OkCoinStreamingExchange exchange = (OkCoinStreamingExchange) ExchangeFactory.INSTANCE.createExchange(spec);
//        String metaDataFileName = ((BaseExchange) exchange).getMetaDataFileName(spec);
//        logger.info("OKCOING metaDataFileName=" + metaDataFileName);

        return exchange;
    }

    private void initWebSocketConnection() {
        // Connect to the Exchange WebSocket API. Blocking wait for the connection.
        exchange.connect().blockingAwait();

        // Retry on disconnect. (It's disconneced each 5 min)
        exchange.onDisconnect().doOnComplete(() -> {
            logger.warn("onClientDisconnect okCoinService");
            initWebSocketAndAllSubscribers();
        }).subscribe();
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
        orderBookSubscription = getOrderBookObservable()
                .subscribeOn(Schedulers.computation())
                .subscribe(orderBook -> {
                    final List<LimitOrder> bestAsks = Utils.getBestAsks(orderBook.getAsks(), 1);
                    final LimitOrder bestAsk = bestAsks.size() > 0 ? bestAsks.get(0) : null;
                    final List<LimitOrder> bestBids = Utils.getBestBids(orderBook.getBids(), 1);
                    final LimitOrder bestBid = bestBids.size() > 0 ? bestBids.get(0) : null;
                    this.bestAsk = bestAsk != null ? bestAsk.getLimitPrice() : BigDecimal.ZERO;
                    this.bestBid = bestBid != null ? bestBid.getLimitPrice() : BigDecimal.ZERO;
                    logger.debug("ask: {}, bid: {}", this.bestAsk, this.bestBid);
                    this.orderBook = orderBook;

                    CompletableFuture.runAsync(this::checkOpenOrdersForMoving)
                            .exceptionally(throwable -> {
                                logger.error("OnCheckOpenOrders", throwable);
                                return null;
                            });


                }, throwable -> logger.error("ERROR in getting order book: ", throwable));
    }

    @Override
    public Observable<OrderBook> getOrderBookObservable() {
        return orderBookObservable;
    }

    @PreDestroy
    public void preDestroy() {
        // Unsubscribe from data order book.
        orderBookSubscription.dispose();
        orderSubscriptions.forEach((s, disposable) -> disposable.dispose());

        // Disconnect from exchange (non-blocking)
        exchange.disconnect().subscribe(() -> logger.info("Disconnected from the Exchange"));
    }

    @Override
    protected BigDecimal getMakerPriceStep() {
        return OKCOIN_STEP;
    }

    @Override
    protected BigDecimal getMakerDelta() {
        return arbitrageService.getMakerDelta();
    }

    public String fetchCurrencies() {
        final List<CurrencyPair> exchangeSymbols = exchange.getExchangeSymbols();
        final String toString = Arrays.toString(exchangeSymbols.toArray());
        logger.info(toString);
        return toString;
    }

    @Scheduled(fixedRate = 1000)
    public AccountInfo fetchAccountInfo() {
        try {
            accountInfo = exchange.getAccountService().getAccountInfo();
            logger.debug(accountInfo.toString());
        } catch (IOException e) {
            logger.error("AccountInfo error", e);
        }
        return accountInfo;
    }

//    @Scheduled(fixedRate = 2000)
    public void fetchOpenOrdersWithDelay() {
        isMovingInProgress = true;
        Completable.timer(2000, TimeUnit.MILLISECONDS)
                .doOnCompleted(() -> {
                    fetchOpenOrders(); // Synchronous
                    isMovingInProgress = false;
                })
                .subscribe();
//        this.fetchOpenOrders();
    }
/*
    private Disposable startOrderListener(String orderId) {
        return exchange.getStreamingTradingService()
                //TODO use different method like getOrderObservable
                .getOpenOrdersObservable("btc_usd", orderId)
                .doOnError(throwable -> logger.error("onOrder onError", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.computation())
                .subscribe(updatedOrder -> {
                    logger.info("Order update: " + updatedOrder.toString());
                    this.openOrders = this.openOrders.stream()
                            .flatMap(existingInMemory -> {
                                // merge if the update of an existingInMemory
                                LimitOrder order = existingInMemory;
                                final Optional<LimitOrder> optionalMatch = updatedOrder.getOpenOrders().stream()
                                        .filter(existing -> existingInMemory.getId().equals(existing.getId()))
                                        .findFirst();
                                if (optionalMatch.isPresent()) {
                                    final LimitOrder existing = optionalMatch.get();
                                    order = new LimitOrder(
                                            existing.getType(),
                                            existingInMemory.getTradableAmount() != null ? existingInMemory.getTradableAmount() : existing.getTradableAmount(),
                                            existing.getCurrencyPair(),
                                            existing.getId(),
                                            existingInMemory.getTimestamp(),
                                            existingInMemory.getLimitPrice() != null ? existingInMemory.getLimitPrice() : existing.getLimitPrice()
                                    );
                                }
                                final List<LimitOrder> optionalOrder = new ArrayList<>();
                                if (order.getStatus() != Order.OrderStatus.CANCELED
                                        && order.getStatus() != Order.OrderStatus.EXPIRED
                                        && order.getStatus() != Order.OrderStatus.FILLED
                                        && order.getStatus() != Order.OrderStatus.REJECTED
                                        && order.getStatus() != Order.OrderStatus.REPLACED
                                        && order.getStatus() != Order.OrderStatus.STOPPED) {
                                    optionalOrder.add(order);
                                } else {
                                    orderSubscriptions.computeIfPresent(orderId, (s, disposable) -> {
                                        disposable.dispose();
                                        return disposable;
                                    });
                                    orderSubscriptions.remove(orderId);
                                }
                                return optionalOrder.stream();
                            }).collect(Collectors.toList());

                }, throwable -> {
                    logger.error("OO.Exception: ", throwable);
                });
    }*/

    private Disposable startTradesListener() {
        return exchange.getStreamingMarketDataService()
                .getTrades(CurrencyPair.BTC_USD, 20)
                .doOnError(throwable -> logger.error("onTrades", throwable))
                .retryWhen(throwables -> throwables.delay(1, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.computation())
                .subscribe(trades -> {
                    if (this.openOrders == null) {
                        this.openOrders = new ArrayList<>();
                    }
                    this.openOrders.stream()
                            .filter(limitOrder -> trades.getId().equals(limitOrder.getId()))
                            .forEach(limitOrder -> debugLog.info("Trades: " + trades.toString()));
//                    this.openOrders.removeIf(limitOrder ->
//                            trades.getId().equals(limitOrder.getId()));
                }, throwable -> logger.error("Trades.Exception: ", throwable));
    }

    public OrderBook fetchOrderBook() {
        try {
            orderBook = exchange.getMarketDataService().getOrderBook(CURRENCY_PAIR_BTC_USD);

            bestBid = Utils.getBestBids(getOrderBook(), 1).get(0).getLimitPrice();
            bestAsk = Utils.getBestAsks(getOrderBook(), 1).get(0).getLimitPrice();

            logger.info("Fetched orderBook: {} asks, {} bids. Timestamp {}", orderBook.getAsks().size(), orderBook.getBids().size(),
                    orderBook.getTimeStamp());
        } catch (Exception e) {
            logger.error("fetchOrderBook error", e);
        }
        return orderBook;
    }

    @Override
    public OrderBook getOrderBook() {
        return orderBook;
    }

    public String placeTakerOrder(Order.OrderType orderType, BigDecimal amount) {
        String orderId;
        try {
            final TradeService tradeService = exchange.getTradeService();
            BigDecimal tradingDigit;
            BigDecimal theBestPrice;

            if (orderType.equals(Order.OrderType.BID)) {
                // The price is to total amount you want to buy, and it must be higher than the current price of 0.01 BTC
                tradingDigit = getTotalPriceOfAmountToBuy(amount);
                theBestPrice = bestAsk;
            } else { // orderType.equals(Order.OrderType.ASK)
                tradingDigit = amount;
                theBestPrice = bestBid;
            }

//          TODO  Place unclear logic to BitplayOkCoinTradeService.placeTakerOrder()
            final MarketOrder marketOrder = new MarketOrder(orderType,
                    tradingDigit,
                    CURRENCY_PAIR_BTC_USD, new Date());
            orderId = tradeService.placeMarketOrder(marketOrder);

//            final Order successfulOrder = fetchOrderInfo(orderId);

            // TODO save trading history into DB
            tradeLogger.info("taker {} amount={} with theBestPrice={}",
                    orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                    amount.toPlainString(),
                    theBestPrice);

            fetchAccountInfo();
        } catch (Exception e) {
            logger.error("Place market order error", e);
            orderId = e.getMessage();
        }
        return orderId;
    }

    @Override
    public TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, boolean fromGui) {
        return placeMakerOrder(orderType, amount, bestQuotes, false, fromGui);
    }

    private TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes,
                                          boolean isMoving, boolean fromGui) {
        final TradeResponse tradeResponse = new TradeResponse();
        try {
            final TradeService tradeService = exchange.getTradeService();
            BigDecimal thePrice;

            thePrice = createBestMakerPrice(orderType, false)
                    .setScale(2, BigDecimal.ROUND_HALF_UP);

            BigDecimal tradeableAmount = adjustAmount(amount);
            if (tradeableAmount.compareTo(BigDecimal.ZERO) == 0) {
                tradeResponse.setErrorMessage("Not enough amount left");
            } else {
                final LimitOrder limitOrder = new LimitOrder(orderType,
                        tradeableAmount, CURRENCY_PAIR_BTC_USD, "123", new Date(),
                        thePrice);

                String orderId = tradeService.placeLimitOrder(limitOrder);
                tradeResponse.setOrderId(orderId);

                String diffWithSignal = "";
                if (bestQuotes != null) {
                    final BigDecimal diff1 = bestQuotes.getAsk1_o().subtract(thePrice);
                    final BigDecimal diff2 = thePrice.subtract(bestQuotes.getBid1_o());
                    diffWithSignal = orderType.equals(Order.OrderType.BID)
                            ? String.format("diff1_buy_o = ask_o[1] - order_price_buy_o = %s", diff1.toPlainString()) //"BUY"
                            : String.format("diff2_sell_o = order_price_sell_o - bid_o[1] = %s", diff2.toPlainString()); //"SELL"
                    arbitrageService.getOpenDiffs().setSecondOpenPrice(orderType.equals(Order.OrderType.BID)
                            ? diff1 : diff2);
                }
                tradeLogger.info("{} {} amount={} with quote={} was placed.orderId={}. {}",
                        isMoving ? "Moved" : "maker",
                        orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                        tradeableAmount.toPlainString(),
                        thePrice,
                        orderId,
                        diffWithSignal);

//                final Disposable orderListener = startOrderListener(orderId);
//                orderSubscriptions.put(orderId, orderListener);
//                final LimitOrder limitOrderWithId = new LimitOrder(orderType,
//                        tradeableAmount, CURRENCY_PAIR_BTC_USD, orderId, new Date(),
//                        thePrice);
//                openOrders.add(limitOrderWithId); - java.util.ConcurrentModificationException with checkOpenOrdersForMoving
                fetchOpenOrdersWithDelay();

                if (!fromGui) {
                    arbitrageService.getOpenPrices().setSecondOpenPrice(thePrice);
                }
                orderIdToSignalInfo.put(orderId, bestQuotes);
            }

        } catch (Exception e) {
            logger.error("Place market order error", e);
            tradeLogger.info("maker error {}", e.toString());
            tradeResponse.setOrderId(e.getMessage());
            tradeResponse.setErrorMessage(e.getMessage());
        }
        return tradeResponse;
    }

    private BigDecimal adjustAmount(BigDecimal initialAmount) {
        BigDecimal amount = initialAmount.setScale(3, BigDecimal.ROUND_HALF_UP);
        if (amount.compareTo(OKCOIN_STEP) == -1) {
            amount = amount.setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        if (amount.compareTo(OKCOIN_STEP) == -1) {
            amount = BigDecimal.ZERO;
        }

        if (amount.compareTo(initialAmount) != 0) {
            tradeLogger.info(String.format("Amount change %s -> %s", initialAmount.toPlainString(), amount.toPlainString()));
        }
        return amount;
    }

    private Order fetchOrderInfo(String orderId) {
        Order order = null;
        try {
            //NOT implemented yet
            final Collection<Order> orderCollection = exchange.getTradeService().getOrder(orderId);
            if (!orderCollection.isEmpty()) {
                order = orderCollection.iterator().next();
            }
        } catch (Exception e) {
            logger.error("on fetch order info by id=" + orderId, e);
        }
        return order;
    }

    @Override
    public UserTrades fetchMyTradeHistory() {
//        returnTradeHistory
        UserTrades tradeHistory = null;
        try {
            tradeHistory = exchange.getTradeService()
                    .getTradeHistory(new OkCoinTradeService.OkCoinTradeHistoryParams(
                            10, 1, CURRENCY_PAIR_BTC_USD));
        } catch (Exception e) {
            logger.info("Exception on fetchMyTradeHistory", e);
        }
        return tradeHistory;

    }

    @Override
    public TradeService getTradeService() {
        return exchange.getTradeService();
    }

    @Override
    public MoveResponse moveMakerOrder(LimitOrder limitOrder, boolean fromGui) {
        // IT doesn't support moving
        // Do cancel ant place
        final OkCoinTradeService tradeService = (OkCoinTradeService) exchange.getTradeService();
        MoveResponse response;
        BestQuotes bestQuotes = orderIdToSignalInfo.get(limitOrder.getId());

        int attemptCount = 0;
        Exception lastException = null;
        boolean cancelledSuccessfully = false;
        while (attemptCount < 2) {
            attemptCount++;
            try {
                cancelledSuccessfully = tradeService.cancelOrder(limitOrder.getId());
                if (cancelledSuccessfully) {
                    break;
                }
            } catch (Exception e) {
                lastException = e;
                logger.error("{} attempt on cancel maker order", attemptCount, e);
            }
        }

        if (cancelledSuccessfully) {
            tradeLogger.info("Cancelled {} amount={},quote={},id={},attempt={}",
                    limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                    limitOrder.getTradableAmount(),
                    limitOrder.getLimitPrice().toPlainString(),
                    limitOrder.getId(),
                    attemptCount);

            // Place order
            while (attemptCount < 5) {
                attemptCount++;
                final TradeResponse tradeResponse = placeMakerOrder(limitOrder.getType(), limitOrder.getTradableAmount(), bestQuotes, true, fromGui);
                if (tradeResponse.getErrorCode() == null) {
                    break;
                }
            }

//            String diffWithSignal = "";
//            if (bestQuotes != null) {
//                diffWithSignal = limitOrder.getType().equals(Order.OrderType.BID)
//                        ? String.format("diff1_buy_o = ask_o[1] - order_price_buy_o = %s", bestQuotes.getAsk1_o().subtract(thePrice).toPlainString()) //"BUY"
//                        : String.format("diff2_sell_o = order_price_sell_o - bid_o[1] = %s",thePrice.subtract(bestQuotes.getBid1_o()).toPlainString()); //"SELL"
//            }
//            final String logString = String.format("Moving finished %s amount=%s,oldQuote=%s,id=%s,attempt=%s",
//                    limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
//                    limitOrder.getTradableAmount(),
//                    limitOrder.getLimitPrice(),
//                    limitOrder.getId(),
//                    attemptCount);
//            tradeLogger.info(logString);
            response = new MoveResponse(MoveResponse.MoveOrderStatus.NEED_TO_DELETE, "");
        } else {
            final String logString = String.format("Cancel failed %s amount=%s,quote=%s,id=%s,attempt=%s,lastException=%s",
                    limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                    limitOrder.getTradableAmount(),
                    limitOrder.getLimitPrice().toPlainString(),
                    limitOrder.getId(),
                    attemptCount,
                    lastException != null ? lastException.getMessage() : null);
            tradeLogger.info(logString);

            fetchOpenOrdersWithDelay();

            response = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, logString);
        }
        return response;
    }
}
