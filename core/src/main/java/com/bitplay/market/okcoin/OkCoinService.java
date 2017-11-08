package com.bitplay.market.okcoin;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.MarketService;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.SignalEvent;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.utils.Utils;

import info.bitrich.xchangestream.okex.OkExStreamingExchange;
import info.bitrich.xchangestream.okex.OkExStreamingMarketDataService;
import info.bitrich.xchangestream.service.exception.NotConnectedException;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.ContractIndex;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.okcoin.FuturesContract;
import org.knowm.xchange.okcoin.dto.trade.OkCoinPosition;
import org.knowm.xchange.okcoin.dto.trade.OkCoinPositionResult;
import org.knowm.xchange.okcoin.dto.trade.OkCoinTradeResult;
import org.knowm.xchange.okcoin.service.OkCoinFuturesTradeService;
import org.knowm.xchange.okcoin.service.OkCoinTradeService;
import org.knowm.xchange.okcoin.service.OkCoinTradeServiceRaw;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;
import javax.validation.constraints.NotNull;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import si.mazi.rescu.HttpStatusIOException;

/**
 * Created by Sergey Shurmin on 3/21/17.
 */
@Service("okcoin")
public class OkCoinService extends MarketService {

    public static final String TAKER_WAS_CANCELLED_MESSAGE = "Taker wasn't filled. Cancelled";
    private static final Logger logger = LoggerFactory.getLogger(OkCoinService.class);
    private static final Logger tradeLogger = LoggerFactory.getLogger("OKCOIN_TRADE_LOG");

    private final static CurrencyPair CURRENCY_PAIR_BTC_USD = new CurrencyPair("BTC", "USD");
    private static final BigDecimal OKCOIN_STEP = new BigDecimal("0.01");
    private final static String NAME = "okcoin";
    private static final int MAX_ATTEMPTS = 10;
//    protected State state = State.READY;
    ArbitrageService arbitrageService;
    @Autowired
    private PosDiffService posDiffService;
    @Autowired
    private PersistenceService persistenceService;
    private OkExStreamingExchange exchange;
    private Disposable orderBookSubscription;
    private Disposable privateDataSubscription;
    private Disposable accountInfoSubscription;
    private Disposable futureIndexSubscription;
    private Observable<OrderBook> orderBookObservable;

    @Override
    public PosDiffService getPosDiffService() {
        return posDiffService;
    }

    @Override
    public ArbitrageService getArbitrageService() {
        return arbitrageService;
    }

    @Autowired
    public void setArbitrageService(ArbitrageService arbitrageService) {
        this.arbitrageService = arbitrageService;
    }

    @Override
    public PersistenceService getPersistenceService() {
        return persistenceService;
    }
//    private Map<String, Disposable> orderSubscriptions = new HashMap<>();

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
        this.usdInContract = 100;
        exchange = initExchange(key, secret);
        loadLiqParams();

        initWebSocketAndAllSubscribers();
    }

    private void initWebSocketAndAllSubscribers() {
        initWebSocketConnection();

        createOrderBookObservable();
        subscribeOnOrderBook();

//        startTradesListener(); // to remove openOrders

        privateDataSubscription = startPrivateDataListener();
        accountInfoSubscription = startAccountInfoSubscription();
        futureIndexSubscription = startFutureIndexListener();

        fetchOpenOrders();

//        fetchAccountInfo();
        try {
            fetchPosition();
        } catch (Exception e) {
            logger.error("FetchPositionError", e);
        }
    }

    private Completable closeAllSubscibers() {
        // Unsubscribe from data order book.
        orderBookSubscription.dispose();
//        orderSubscriptions.forEach((s, disposable) -> disposable.dispose());
        privateDataSubscription.dispose();
        accountInfoSubscription.dispose();
        futureIndexSubscription.dispose();
        final Completable com = exchange.disconnect(); // not invoked here
        return com;
    }

    private void createOrderBookObservable() {
        orderBookObservable = ((OkExStreamingMarketDataService) exchange.getStreamingMarketDataService())
                .getOrderBook(CurrencyPair.BTC_USD,
                        OkExStreamingMarketDataService.Tool.BTC,
                        FuturesContract.ThisWeek,
                        OkExStreamingMarketDataService.Depth.DEPTH_20)
                .doOnDispose(() -> logger.info("okcoin subscription doOnDispose"))
                .doOnTerminate(() -> logger.info("okcoin subscription doOnTerminate"))
                .doOnError(throwable -> logger.error("okcoin onError orderBook", throwable))
                .retryWhen(throwableObservable -> throwableObservable.delay(5, TimeUnit.SECONDS))
                .share();
    }

    private OkExStreamingExchange initExchange(String key, String secret) {
        ExchangeSpecification spec = new ExchangeSpecification(OkExStreamingExchange.class);
        spec.setApiKey(key);
        spec.setSecretKey(secret);

        spec.setExchangeSpecificParametersItem("Use_Intl", true);
        spec.setExchangeSpecificParametersItem("Use_Futures", true);
        spec.setExchangeSpecificParametersItem("Futures_Contract", FuturesContract.ThisWeek);

        return (OkExStreamingExchange) ExchangeFactory.INSTANCE.createExchange(spec);
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

    private void subscribeOnOrderBook() {
        //TODO subscribe on updates only to increase the speed
        orderBookSubscription = orderBookObservable
                .subscribeOn(Schedulers.io())
                .subscribe(orderBook -> {
                    final LimitOrder bestAsk = Utils.getBestAsk(orderBook);
                    final LimitOrder bestBid = Utils.getBestBid(orderBook);

                    this.orderBook = orderBook;

                    if (this.bestAsk != null && bestAsk != null && this.bestBid != null && bestBid != null
                            && this.bestAsk.compareTo(bestAsk.getLimitPrice()) != 0
                            && this.bestBid.compareTo(bestBid.getLimitPrice()) != 0) {
                        recalcAffordableContracts();
                    }
                    this.bestAsk = bestAsk != null ? bestAsk.getLimitPrice() : BigDecimal.ZERO;
                    this.bestBid = bestBid != null ? bestBid.getLimitPrice() : BigDecimal.ZERO;
                    logger.debug("ask: {}, bid: {}", this.bestAsk, this.bestBid);

//                    CompletableFuture.runAsync(this::checkOpenOrdersForMoving)
//                            .exceptionally(throwable -> {
//                                logger.error("OnCheckOpenOrders", throwable);
//                                return null;
//                            });


                    getArbitrageService().getSignalEventBus().send(SignalEvent.O_ORDERBOOK_CHANGED);

                }, throwable -> logger.error("ERROR in getting order book: ", throwable));
    }

    @Override
    public Logger getTradeLogger() {
        return tradeLogger;
    }

    @PreDestroy
    public void preDestroy() {
        // Disconnect from exchange (non-blocking)
        closeAllSubscibers().subscribe(() -> logger.info("Disconnected from the Exchange"));
    }

    @Override
    protected BigDecimal getMakerPriceStep() {
        return OKCOIN_STEP;
    }

    @Override
    protected BigDecimal getMakerDelta() {
        return arbitrageService.getParams().getMakerDelta();
    }

    @Scheduled(fixedRate = 1000)
    public void requestAccountInfo() {
        try {
            exchange.getStreamingAccountInfoService().requestAccountInfo();
        } catch (NotConnectedException e) {

            closeAllSubscibers()
                    .doOnComplete(this::initWebSocketAndAllSubscribers)
                    .subscribe(() -> logger.warn("Closing okcoin subscribers was done"),
                            throwable -> logger.error("ERROR on Closing okcoin subscribers"));

        } catch (IOException e) {
            logger.error("AccountInfo request error", e);
        }
    }

    @Scheduled(fixedRate = 2000)
    @Override
    public void fetchPosition() throws Exception {
        final OkCoinPositionResult positionResult = ((OkCoinTradeServiceRaw) exchange.getTradeService()).getFuturesPosition("btc_usd", FuturesContract.ThisWeek);
        mergePosition(positionResult, null);

        recalcAffordableContracts();
        recalcLiqInfo();

    }

    private synchronized void mergePosition(OkCoinPositionResult restUpdate, Position websocketUpdate) {
        if (restUpdate != null) {
            if (restUpdate.getPositions().length > 1) {
                logger.warn("{} More than one positions found", getCounterName());
                tradeLogger.warn("{} More than one positions found", getCounterName());
            }
            if (restUpdate.getPositions().length == 0) {
                position = new Position(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(20),
                        BigDecimal.ZERO,
                        ""
                );
            } else {
                final OkCoinPosition okCoinPosition = restUpdate.getPositions()[0];
                position = new Position(
                        okCoinPosition.getBuyAmount(),
                        okCoinPosition.getSellAmount(),
                        okCoinPosition.getRate(),
                        BigDecimal.ZERO,
                        okCoinPosition.getBuyPriceAvg(),
                        okCoinPosition.getSellPriceAvg(),
                        okCoinPosition.toString()
                );
            }
        } else if (websocketUpdate != null) { // TODO does it worth it
            position = new Position(websocketUpdate.getPositionLong(),
                    websocketUpdate.getPositionShort(),
                    this.position.getLeverage(),
                    BigDecimal.ZERO,
                    websocketUpdate.getRaw());
        }
    }

    @Scheduled(fixedRate = 1000 * 60 * 5)
    public void checker() {

    }

/*
    //TODO use subscribing on open orders
    @Scheduled(fixedRate = 1000 * 60 * 15)
    public void fetchOpenOrdersWithDelay() {
        Completable.timer(2000, TimeUnit.MILLISECONDS)
                .doOnError(throwable -> logger.error("onError fetchOOWithDelay", throwable))
                .doOnComplete(() -> {
                    fetchOpenOrders(); // Synchronous
//                    if (openOrders.size() == 0) {
//                        eventBus.send(BtsEvent.MARKET_FREE);
//                    }
                })
                .subscribe();
    }*/

    /*
        private Disposable startOrderListener(String orderId) {
            return exchange.getStreamingTradingService()
                    .getOpenOrderObservable("btc_usd", orderId)
                    .doOnError(throwable -> logger.error("onOrder onError", throwable))
                    .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                    .subscribeOn(Schedulers.io())
                    .subscribe(updatedOrder -> {
                        logger.info("Order update: " + updatedOrder.toString());
                        this.openOrders = this.openOrders.stream()
                                .map(existingInMemory -> {
                                    // merge if the update of an existingInMemory
                                    LimitOrder order = existingInMemory;
                                    final Optional<LimitOrder> optionalMatch = updatedOrder.getOpenOrders().stream()
                                            .filter(existing -> existingInMemory.getId().equals(existing.getId()))
                                            .findFirst();
                                    if (optionalMatch.isPresent()) {
                                        order.getCumulativeAmount();
                                        order = optionalMatch.get();
                                        logger.info("Order has been updated: " + order.toString());
                                    }
    //                                final List<LimitOrder> optionalOrder = new ArrayList<>();
    //                                if (order.getStatus() != Order.OrderStatus.CANCELED
    //                                        && order.getStatus() != Order.OrderStatus.EXPIRED
    //                                        && order.getStatus() != Order.OrderStatus.FILLED
    //                                        && order.getStatus() != Order.OrderStatus.REJECTED
    //                                        && order.getStatus() != Order.OrderStatus.REPLACED
    //                                        && order.getStatus() != Order.OrderStatus.STOPPED) {
    //                                    optionalOrder.add(order);
    //                                } else {
    //                                    orderSubscriptions.computeIfPresent(orderId, (s, disposable) -> {
    //                                        disposable.dispose();
    //                                        return disposable;
    //                                    });
    //                                    orderSubscriptions.remove(orderId);
    //                                }
                                    if (order.getStatus() == Order.OrderStatus.CANCELED
                                            || order.getStatus() == Order.OrderStatus.EXPIRED
                                            || order.getStatus() == Order.OrderStatus.FILLED
                                            || order.getStatus() == Order.OrderStatus.REJECTED
                                            || order.getStatus() == Order.OrderStatus.REPLACED
                                            || order.getStatus() == Order.OrderStatus.STOPPED) {
                                        orderSubscriptions.computeIfPresent(orderId, (s, disposable) -> {
                                            disposable.dispose();
                                            return disposable;
                                        });
                                        orderSubscriptions.remove(orderId);
                                        logger.info("");
                                    }

                                    return order;
                                }).collect(Collectors.toList());

                    }, throwable -> {
                        logger.error("OO.Exception: ", throwable);
                    });
        }
    */
/*    private Disposable startTradesListener() { //It doesn't work
        return exchange.getStreamingMarketDataService()
                .getTrades(CurrencyPair.BTC_USD, 20)
                .doOnError(throwable -> logger.error("onTrades", throwable))
                .retryWhen(throwables -> throwables.delay(1, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
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
*/
    private Disposable startAccountInfoSubscription() {
        return exchange.getStreamingAccountInfoService()
                .accountInfoObservable()
                .doOnError(throwable -> logger.error("Error on AccountInfo.Websocket observing", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(accountInfoContracts -> {
                    logger.debug("AccountInfo.Websocket: " + accountInfoContracts.toString());
                    setAccountInfoContracts(accountInfoContracts);

                }, throwable -> {
                    logger.error("AccountInfo.Websocket.Exception: ", throwable);
                });
    }

    private void setAccountInfoContracts(AccountInfoContracts accountInfoContracts) {
        BigDecimal eBest = null;
        BigDecimal eAvg = null;
        final Position pObj = getPosition();
        if (accountInfoContracts.getWallet() != null && pObj != null
                && pObj.getPositionLong() != null && pObj.getPositionShort() != null) {
            final BigDecimal pos = pObj.getPositionLong().subtract(pObj.getPositionShort());
            final BigDecimal wallet = accountInfoContracts.getWallet();
            final OrderBook orderBook = getOrderBook();

            if (pos.signum() > 0) {
                final BigDecimal entryPrice = pObj.getPriceAvgLong();
                final BigDecimal bid1 = Utils.getBestBid(orderBook).getLimitPrice();
                int amount = pObj.getPositionLong().intValue();
                final BigDecimal avgBid = Utils.getAvgBidPrice(orderBook, amount);

                // upl_long = pos/entry_price - pos/bid[1]
                final BigDecimal uplLong = pos.divide(entryPrice, 16, RoundingMode.HALF_UP)
                        .subtract(pos.divide(bid1, 16, RoundingMode.HALF_UP))
                        .setScale(8, RoundingMode.HALF_UP);
                // upl_long_avg = pos/entry_price - pos/bid[]
                final BigDecimal uplLongAvg = pos.divide(entryPrice, 16, RoundingMode.HALF_UP)
                        .subtract(pos.divide(bid1, 16, RoundingMode.HALF_UP))
                        .setScale(8, RoundingMode.HALF_UP);
                // e_best = ok_bal + upl_long
                eBest = wallet.add(uplLong);
                eAvg = wallet.add(uplLongAvg);
            } else if (pos.signum() < 0) {
                final BigDecimal entryPrice = pObj.getPriceAvgShort();
                final BigDecimal ask1 = Utils.getBestAsk(orderBook).getLimitPrice();
                // upl_short = pos / ask[1] - pos / entry_price
                final BigDecimal uplShort = pos.divide(ask1, 16, RoundingMode.HALF_UP)
                        .subtract(pos.divide(entryPrice, 16, RoundingMode.HALF_UP))
                        .setScale(8, RoundingMode.HALF_UP);
                // e_best = ok_bal + upl_long
                eBest = wallet.add(uplShort);
            } else { //pos==0
                // e_best == btm_bal
                eBest = wallet;
            }
        }

        this.accountInfoContracts = new AccountInfoContracts(
                accountInfoContracts.getWallet(),
                accountInfoContracts.getAvailable(),
                accountInfoContracts.geteMark(),
                accountInfoContracts.geteLast(),
                eBest,
                accountInfoContracts.geteAvg(),
                accountInfoContracts.getMargin(),
                accountInfoContracts.getUpl(),
                accountInfoContracts.getRpl(),
                accountInfoContracts.getRiskRate()
        );
    }

    private Disposable startPrivateDataListener() {
        return exchange.getStreamingPrivateDataService()
                .getAllPrivateDataObservable()
                .doOnError(throwable -> logger.error("Error on PrivateData observing", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(privateData -> {
                    logger.debug(privateData.toString());
                    if (privateData.getAccountInfoContracts() != null) {
                        requestAccountInfo();
                    }
                    final Position positionInfo = privateData.getPositionInfo();
                    if (positionInfo != null) {
                        mergePosition(null, positionInfo);
                        recalcAffordableContracts();
                        recalcLiqInfo();
                    }
                    if (privateData.getTrades() != null) {
                        updateOpenOrders(privateData.getTrades());
                    }
                }, throwable -> {
                    logger.error("PrivateData.Exception: ", throwable);
                });
    }

    private Disposable startFutureIndexListener() {
        return ((OkExStreamingMarketDataService) exchange.getStreamingMarketDataService())
                .getFutureIndex()
                .doOnError(throwable -> logger.error("Error on FutureIndex observing", throwable))
                .retryWhen(throwables -> throwables.delay(10, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(futureIndex -> {
                    logger.debug(futureIndex.toString());
                    this.contractIndex = new ContractIndex(futureIndex.getIndex(),
                            futureIndex.getTimestamp());
                }, throwable -> {
                    logger.error("FutureIndex.Exception: ", throwable);
                });
    }

    private void updateOpenOrders(List<LimitOrder> trades) {
        synchronized (openOrdersLock) {
            StringBuilder updateAction = new StringBuilder();

            // Replace all existing with new info
            this.openOrders = this.openOrders.stream()
                    .flatMap(existingInMemory -> {
                        // merge if the update of an existingInMemory
                        LimitOrder order = existingInMemory;
                        final Optional<LimitOrder> optionalMatch = trades.stream()
                                .filter(existing -> existingInMemory.getId().equals(existing.getId()))
                                .findFirst();
                        if (optionalMatch.isPresent()) {
                            order = optionalMatch.get();
                            tradeLogger.info("{} Order update:id={},status={},amount={},filled={}",
                                    getCounterName(),
                                    order.getId(), order.getStatus(), order.getTradableAmount(),
                                    order.getCumulativeAmount());
                            updateAction.append("update,");
                        }
                        // Remove old. 'CANCELED is skiped because it can be MovingInTheMiddle case'
                        Collection<LimitOrder> optionalOrder = new ArrayList<>();
                        if (order.getStatus() == Order.OrderStatus.CANCELED
                                || order.getStatus() == Order.OrderStatus.PENDING_CANCEL
                                || order.getStatus() == Order.OrderStatus.NEW
                                || order.getStatus() == Order.OrderStatus.PENDING_NEW
                                || order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED
                                ) {
                            optionalOrder.add(order);
                        } else {
                            tradeLogger.info("{} Order removing:id={},status={},amount={},filled={}",
                                    getCounterName(),
                                    order.getId(), order.getStatus(), order.getTradableAmount(),
                                    order.getCumulativeAmount());
                        }

                        return optionalOrder.stream();
                    }).collect(Collectors.toList());

            // Add new orders
            List<LimitOrder> newOrders = trades.stream()
                    .filter(order -> order.getStatus() != Order.OrderStatus.CANCELED
                            && order.getStatus() != Order.OrderStatus.EXPIRED
                            && order.getStatus() != Order.OrderStatus.FILLED
                            && order.getStatus() != Order.OrderStatus.REJECTED
                            && order.getStatus() != Order.OrderStatus.REPLACED
                            && order.getStatus() != Order.OrderStatus.PENDING_CANCEL
                            && order.getStatus() != Order.OrderStatus.PENDING_REPLACE
                            && order.getStatus() != Order.OrderStatus.STOPPED)
                    .filter((LimitOrder limitOrder) -> {
                        final String id = limitOrder.getId();
                        return this.openOrders.stream()
                                .anyMatch(existing -> id.equals(existing.getId()));
                    })
                    .collect(Collectors.toList());

//            debugLog.info("NewOrders: " + Arrays.toString(newOrders.toArray()));
            this.openOrders.addAll(newOrders);

            if (this.openOrders.size() == 0) {
//                tradeLogger.info("Okcoin-ready: " + trades.stream()
//                        .map(LimitOrder::toString)
//                        .collect(Collectors.joining("; ")));
                logger.info("Okcoin-ready: " + trades.stream()
                        .map(LimitOrder::toString)
                        .collect(Collectors.joining("; ")));
                eventBus.send(BtsEvent.MARKET_FREE);
            }
        }
    }

    /**
     * Add new openOrders.<br> It removes old. If no one left, it frees market. They will be checked in moveMakerOrder()
     *
     * @return list of open orders.
     */
    @Override
    protected List<LimitOrder> fetchOpenOrders() {

        if (getExchange() != null && getTradeService() != null) {
            try {
                final List<LimitOrder> fetchedList = getTradeService().getOpenOrders(null)
                        .getOpenOrders();
                if (fetchedList == null) {
                    logger.error("GetOpenOrdersError");
                    throw new IllegalStateException("GetOpenOrdersError");
                }

                updateOpenOrders(fetchedList);

//                    if (fetchedList.size() > 1) {
//                        getTradeLogger().warn("Warning: openOrders count " + fetchedList.size());
//                    }
//
//                    final List<LimitOrder> allNew = fetchedList.stream()
//                            .filter(fetched -> this.openOrders.stream()
//                                    .noneMatch(o -> o.getId().equals(fetched.getId())))
//                            .collect(Collectors.toList());
//
//                    this.openOrders.addAll(allNew);

            } catch (Exception e) {
                logger.error("GetOpenOrdersError", e);
                throw new IllegalStateException("GetOpenOrdersError", e);
            }

        }
        return openOrders;
    }

    public TradeResponse takerOrder(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, SignalType signalType)
            throws Exception {

        TradeResponse tradeResponse = new TradeResponse();

        final TradeService tradeService = exchange.getTradeService();

        orderType = adjustOrderType(orderType, amount);

        synchronized (openOrdersLock) {

            // Option 1: REAL TAKER
//            final MarketOrder marketOrder = new MarketOrder(orderType, amount, CURRENCY_PAIR_BTC_USD, new Date());
//            final String orderId = tradeService.placeMarketOrder(marketOrder);

            // Option 2: FAKE LIMIT ORDER
            BigDecimal thePrice = Utils.createPriceForTaker(getOrderBook(), orderType,
                    amount.intValue() + 500 //increased by 500 contracts to make sure it will be filled right away
            );
            getTradeLogger().info("The fake taker price is " + thePrice.toPlainString());
            final LimitOrder limitOrder = new LimitOrder(orderType, amount, CURRENCY_PAIR_BTC_USD, "123", new Date(), thePrice);
            String orderId = tradeService.placeLimitOrder(limitOrder);


            tradeResponse.setOrderId(orderId);
            final String counterName = getCounterName();

            final Optional<Order> orderInfoAttempts = getOrderInfoAttempts(orderId, counterName, "Taker:Status:");

            Order orderInfo = orderInfoAttempts.get();

            if (orderInfo.getStatus() != Order.OrderStatus.FILLED) { // 1. Try cancel then
                cancelOrderSync(orderId, "Taker:Cancel_maker:");
                orderInfo = getFinalOrderInfoSync(orderId, counterName, "Taker:Cancel_makerStatus:");
            }

            if (orderInfo.getStatus() != Order.OrderStatus.FILLED) { // 2. It is CANCELED
                tradeResponse.setErrorCode(TAKER_WAS_CANCELLED_MESSAGE);
                tradeResponse.setCancelledOrder(orderInfo);
            } else { //FILLED by any (orderInfo or cancelledOrder)
                if (signalType == SignalType.AUTOMATIC) {
                    arbitrageService.getOpenPrices().setSecondOpenPrice(orderInfo.getAveragePrice());
                }

                writeLogPlaceOrder(orderType, amount, bestQuotes, "taker", signalType,
                        orderInfo.getAveragePrice(), orderId, orderInfo.getStatus().toString());

            }
        }

        return tradeResponse;
    }


    private synchronized void recalcAffordableContracts() {
        final BigDecimal reserveBtc = arbitrageService.getParams().getReserveBtc2();
        final BigDecimal volPlan = arbitrageService.getParams().getBlock2();

        if (accountInfoContracts != null && position != null) {
            final BigDecimal availableBtc = accountInfoContracts.getAvailable();
            final BigDecimal equityBtc = accountInfoContracts.geteLast();

            final BigDecimal bestAsk = Utils.getBestAsks(orderBook, 1).get(0).getLimitPrice();
            final BigDecimal bestBid = Utils.getBestBids(orderBook, 1).get(0).getLimitPrice();
            final BigDecimal leverage = position.getLeverage();

            if (availableBtc != null && equityBtc != null && leverage != null && position.getPositionLong() != null && position.getPositionShort() != null) {

//                if (availableBtc.signum() > 0) {
//                if (orderType.equals(Order.OrderType.BID) || orderType.equals(Order.OrderType.EXIT_ASK)) {
                    if (position.getPositionShort().signum() != 0) { // there are sells
                        if (volPlan.compareTo(position.getPositionShort()) != 1) {
                            affordableContractsForLong = (position.getPositionShort().subtract(position.getPositionLong()).add(
                                    (equityBtc.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage).divide(BigDecimal.valueOf(100), 0, BigDecimal.ROUND_DOWN)
                            )).setScale(0, BigDecimal.ROUND_DOWN);
                        } else {
                            affordableContractsForLong = (availableBtc.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage).divide(BigDecimal.valueOf(100), 0, BigDecimal.ROUND_DOWN);
                        }
                        if (affordableContractsForLong.compareTo(position.getPositionShort()) == -1) {
                            affordableContractsForLong = position.getPositionShort();
                        }
                    } else { // no sells
                        affordableContractsForLong = (availableBtc.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage).divide(BigDecimal.valueOf(100), 0, BigDecimal.ROUND_DOWN);
                    }
//                }

//                if (orderType.equals(Order.OrderType.ASK) || orderType.equals(Order.OrderType.EXIT_BID)) {
                    if (position.getPositionLong().signum() != 0) { // we have BIDs
                        if (volPlan.compareTo(position.getPositionLong()) != 1) { // если мы хотим закрыть меньше чем есть
                            final BigDecimal divide = (equityBtc.subtract(reserveBtc)).multiply(bestBid.multiply(leverage)).divide(BigDecimal.valueOf(100), 0, BigDecimal.ROUND_DOWN);
                            affordableContractsForShort = (position.getPositionLong().subtract(position.getPositionShort()).add(
                                    divide
                            )).setScale(0, BigDecimal.ROUND_DOWN);
                        } else {
                            affordableContractsForShort = (availableBtc.subtract(reserveBtc)).multiply(bestBid).multiply(leverage).divide(BigDecimal.valueOf(100), 0, BigDecimal.ROUND_DOWN);
                        }
                        if (affordableContractsForShort.compareTo(position.getPositionLong()) == -1) {
                            affordableContractsForShort = position.getPositionLong();
                        }
                    } else { // no BIDs
                        affordableContractsForShort = ((availableBtc.subtract(reserveBtc)).multiply(bestBid).multiply(leverage)).divide(BigDecimal.valueOf(100), 0, BigDecimal.ROUND_DOWN);
                    }
//                }
//                }
            }
        }
    }

    @Override
    public boolean isAffordable(Order.OrderType orderType, BigDecimal tradableAmount) {
        boolean isAffordable;
        final BigDecimal affordableVol = (orderType == Order.OrderType.BID || orderType == Order.OrderType.EXIT_ASK)
                ? this.affordableContractsForLong : this.affordableContractsForShort;
        isAffordable = affordableVol.compareTo(tradableAmount) != -1;

        return isAffordable;
    }

    @Override
    public boolean isReadyForNewOrder() {
        return true;
    }

    @Override
    public TradeResponse placeOrderOnSignal(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes,
                                            SignalType signalType) {
        TradeResponse tradeResponse = null;
        BigDecimal amountToFill = amountInContracts;
        int attemptCount = 0;
        for (int i = 0; i < 2; i++) {
            try {
                attemptCount++;
                if (attemptCount > 1) {
                    Thread.sleep(200 * attemptCount);
                }

                if (arbitrageService.getParams().getOkCoinOrderType().equals("maker")) {
                    tradeResponse = placeSimpleMakerOrder(orderType, amountToFill, bestQuotes, false, signalType);
                } else {
                    tradeResponse = takerOrder(orderType, amountToFill, bestQuotes, signalType);
                    if (tradeResponse.getErrorCode() != null && tradeResponse.getErrorCode().equals(TAKER_WAS_CANCELLED_MESSAGE)) {
                        final BigDecimal filled = tradeResponse.getCancelledOrder().getCumulativeAmount();
                        amountToFill = amountToFill.subtract(filled);
                        continue;
                    }
                }
                break;
            } catch (Exception e) {
                String message = (e instanceof HttpStatusIOException)
                        ? e.getMessage() + ((HttpStatusIOException) e).getHttpBody()
                        : e.getMessage();

                String details = String.format("%s placeOrderOnSignal error. type=%s,a=%s,bestQuotes=%s,isMove=%s,signalT=%s. %s",
                        getCounterName(),
                        orderType, amountToFill, bestQuotes, false, signalType, message);
                logger.error(details.length() < 200 ? details : details.substring(0, 190), e);
                tradeLogger.error(details);
//                warningLogger.error("Warning placing: " + details);
                break;
            }
        }

        //
        setFree();

        return tradeResponse;
    }

    public TradeResponse placeSimpleMakerOrder(Order.OrderType orderType, BigDecimal tradeableAmount, BestQuotes bestQuotes,
                                               boolean isMoving, SignalType signalType) throws Exception {
        return placeSimpleMakerOrder(orderType, tradeableAmount, bestQuotes, isMoving, signalType, false);
    }

    public TradeResponse placeSimpleMakerOrder(Order.OrderType orderType, BigDecimal tradeableAmount, BestQuotes bestQuotes,
                                               boolean isMoving, SignalType signalType, boolean isFakeTaker) throws Exception {
        final TradeResponse tradeResponse = new TradeResponse();
        arbitrageService.setSignalType(signalType);
        eventBus.send(BtsEvent.MARKET_BUSY);

        BigDecimal thePrice;

        if (isFakeTaker) {
            thePrice = Utils.createPriceForTaker(getOrderBook(), orderType, tradeableAmount.intValue());
        } else {
            thePrice = createBestMakerPrice(orderType, false).setScale(2, BigDecimal.ROUND_HALF_UP);
        }

        if (thePrice.compareTo(BigDecimal.ZERO) == 0) {
            tradeResponse.setErrorCode("The new price is 0 ");
        } else if (tradeableAmount.compareTo(BigDecimal.ZERO) == 0) {

            tradeResponse.setErrorCode("Not enough amount left. amount=" + tradeableAmount.toPlainString());

        } else {
            // USING REST API
            orderType = adjustOrderType(orderType, tradeableAmount);

            final LimitOrder limitOrder = new LimitOrder(orderType, tradeableAmount, CURRENCY_PAIR_BTC_USD, "123", new Date(), thePrice);
            String orderId = exchange.getTradeService().placeLimitOrder(limitOrder);
            tradeResponse.setOrderId(orderId);

            final LimitOrder limitOrderWithId = new LimitOrder(orderType, tradeableAmount, CURRENCY_PAIR_BTC_USD, orderId, new Date(), thePrice);
            tradeResponse.setLimitOrder(limitOrderWithId);
            if (!isMoving) {
                openOrders.add(limitOrderWithId);
            }

            if (signalType == SignalType.AUTOMATIC) {
                arbitrageService.getOpenPrices().setSecondOpenPrice(thePrice);
            }
            orderIdToSignalInfo.put(orderId, bestQuotes);

            writeLogPlaceOrder(orderType, tradeableAmount, bestQuotes,
                    isMoving ? "Moving3:Moved" : "maker",
                    signalType, thePrice, orderId, null);
        }

//        fetchOpenOrdersWithDelay();
        return tradeResponse;
    }

    private void writeLogPlaceOrder(Order.OrderType orderType, BigDecimal tradeableAmount, BestQuotes bestQuotes,
                                    String placingType, SignalType signalType, BigDecimal thePrice, String orderId, String status) {
        String diffWithSignal = "";
        if (bestQuotes != null) {
            final BigDecimal diff1 = bestQuotes.getAsk1_o().subtract(thePrice);
            final BigDecimal diff2 = thePrice.subtract(bestQuotes.getBid1_o());
            diffWithSignal = (orderType.equals(Order.OrderType.BID) || orderType.equals(Order.OrderType.EXIT_ASK))
                    ? String.format("diff1_buy_o = ask_o[1](%s) - order_price_buy_o(%s) = %s", bestQuotes.getAsk1_o().toPlainString(), thePrice.toPlainString(), diff1.toPlainString()) //"BUY"/"EXIT_SELL"
                    : String.format("diff2_sell_o = order_price_sell_o(%s) - bid_o[1](%s) = %s", thePrice.toPlainString(), bestQuotes.getBid1_o().toPlainString(), diff2.toPlainString()); //"SELL"/"EXIT_BUY"

            arbitrageService.getOpenDiffs().setSecondOpenPrice(
                    (orderType.equals(Order.OrderType.BID) || orderType.equals(Order.OrderType.EXIT_ASK))
                    ? diff1 : diff2);
        }

        // sell, buy, close sell, close buy

        tradeLogger.info("{}/end: {} {} amount={}, quote={}, orderId={}, status={}, ({})",
                getCounterName(),
                placingType, //isMoving ? "Moving3:Moved" : "maker",
                Utils.convertOrderTypeName(orderType),
                tradeableAmount.toPlainString(),
                thePrice,
                orderId,
                status,
                diffWithSignal);
    }

    private Order.OrderType adjustOrderType(Order.OrderType orderType, BigDecimal tradeableAmount) {
        BigDecimal pLongBalance = (this.position != null && this.position.getPositionLong() != null)
                ? this.position.getPositionLong()
                : BigDecimal.ZERO;
        BigDecimal pShortBalance = (this.position != null && this.position.getPositionShort() != null)
                ? this.position.getPositionShort()
                : BigDecimal.ZERO;
        Order.OrderType newOrderType = orderType;
        if (orderType == Order.OrderType.BID) { // buy - long
            if (pShortBalance.compareTo(tradeableAmount) != -1) {
                newOrderType = Order.OrderType.EXIT_ASK;
            }
        } else if (orderType == Order.OrderType.ASK) { // sell - short
            if (pLongBalance.compareTo(tradeableAmount) != -1) {
                newOrderType = Order.OrderType.EXIT_BID;
            }
        }
        return newOrderType;
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
    protected void iterateOpenOrdersMove() {
        boolean haveToFetch = false;

        synchronized (openOrdersLock) {
            boolean freeTheMarket = false;
            List<String> toRemove = new ArrayList<>();
            List<LimitOrder> toAdd = new ArrayList<>();
            try {
                for (LimitOrder openOrder : openOrders) {
                    if (openOrder.getType() != null) {
                        final SignalType signalType = getArbitrageService().getSignalType();
                        final MoveResponse response = moveMakerOrderIfNotFirst(openOrder, signalType);

                        if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.ALREADY_CLOSED
                                || response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.ONLY_CANCEL) {
                            freeTheMarket = true;
                            toRemove.add(openOrder.getId());
                            haveToFetch = true;
                            logger.info(getName() + response.getDescription());
                        }

                        if (response.getMoveOrderStatus().equals(MoveResponse.MoveOrderStatus.MOVED_WITH_NEW_ID)) {
                            toRemove.add(openOrder.getId());
                            haveToFetch = true;
                            if (response.getNewOrder() != null) {
                                toAdd.add(response.getNewOrder());
                            }
                        }
                    }
                }

                openOrders.removeIf(o -> toRemove.contains(o.getId()));
                toRemove.forEach(s -> orderIdToSignalInfo.remove(s));
                openOrders.addAll(toAdd);

                if (freeTheMarket && openOrders.size() > 0) {
                    logger.warn(getName() + " Warning: get ALREADY_CLOSED, but there are still open orders");
                    warningLogger.warn(getName() + "Warning: get ALREADY_CLOSED, but there are still open orders");
                }

                if (freeTheMarket && openOrders.size() == 0) {
                    warningLogger.warn(getName() + " No openOrders left: send READY");
                    eventBus.send(BtsEvent.MARKET_FREE);
                }
            } catch (Exception e) {
                logger.error("On moving", e);
                haveToFetch = true;
//                final List<LimitOrder> orderList = fetchOpenOrders();
//                if (orderList.size() == 0) {
//                    eventBus.send(BtsEvent.MARKET_FREE);
//                }
            }
        }

        if (haveToFetch) {
            fetchOpenOrders();
        }
    }

    @Override
    public MoveResponse moveMakerOrder(LimitOrder limitOrder, SignalType signalType) {
        if (limitOrder.getStatus() == Order.OrderStatus.CANCELED) {
            tradeLogger.error("{} do not move ALREADY_CLOSED order", getCounterName());
            return new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, "");
        }

//        if (state != State.WAITING) {
//            tradeLogger.error("Moving declined. Try moving in wrong State={}", state);
//            return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "moving declined");
//        }

        arbitrageService.setSignalType(signalType);
        eventBus.send(BtsEvent.MARKET_BUSY);

        // IT doesn't support moving
        // Do cancel ant place
        MoveResponse response;
        BestQuotes bestQuotes = orderIdToSignalInfo.get(limitOrder.getId());

        // 1. cancell old order
        final String counterName = getCounterName();
        cancelOrderSync(limitOrder.getId(), "Moving1:cancelled:");

        // 2. We got result on cancel(true/false), but double-check status of an old order
        Order cancelledOrder = getFinalOrderInfoSync(limitOrder.getId(), counterName, "Moving2:cancelStatus:");

        // 3. Already closed?
        if (cancelledOrder.getStatus() != Order.OrderStatus.CANCELED) { // Already closed
            response = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, "");

            final String logString = String.format("%s %s %s status=%s,amount=%s,quote=%s,id=%s,lastException=%s",
                    counterName,
                    "Moving3:Already closed:",
                    limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                    cancelledOrder.getStatus(),
                    limitOrder.getTradableAmount(),
                    limitOrder.getLimitPrice().toPlainString(),
                    limitOrder.getId(),
                    null);
            tradeLogger.info(logString);

            // 3. Place order
        } else { //if (cancelledOrder.getStatus() == Order.OrderStatus.CANCELED) {
            TradeResponse tradeResponse = new TradeResponse();
            tradeResponse = finishMovingSync(limitOrder, signalType, bestQuotes, counterName, cancelledOrder, tradeResponse);
            response = new MoveResponse(MoveResponse.MoveOrderStatus.MOVED_WITH_NEW_ID, tradeResponse.getOrderId(), tradeResponse.getLimitOrder());
        }
        return response;
    }

    private TradeResponse finishMovingSync(LimitOrder limitOrder, SignalType signalType, BestQuotes bestQuotes, String counterName, Order cancelledOrder, TradeResponse tradeResponse) {
        int attemptCount = 0;
        while (true) {
            try {
                attemptCount++;
                if (attemptCount > 1) {
                    Thread.sleep(200 * attemptCount);
                }

                final BigDecimal newAmount = limitOrder.getTradableAmount().subtract(cancelledOrder.getCumulativeAmount());
                tradeResponse = placeSimpleMakerOrder(limitOrder.getType(), newAmount, bestQuotes, true, signalType);

                if (tradeResponse.getErrorCode() != null && tradeResponse.getErrorCode().startsWith("Insufficient")) {
                    tradeLogger.info("{}/{} Moving3:Failed {} amount={},quote={},id={},attempt={}. Error: {}",
                            counterName, attemptCount,
                            limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                            limitOrder.getTradableAmount(),
                            limitOrder.getLimitPrice().toPlainString(),
                            limitOrder.getId(),
                            attemptCount,
                            tradeResponse.getErrorCode());
                }
                if (tradeResponse.getErrorCode() == null) {
                    // tradeResponse.getErrorCode().startsWith("Insufficient")) { // when amount less then affordable.
                    break;
                }
            } catch (Exception e) {
                logger.error("{}/{} Moving3:placingError", counterName, attemptCount, e);
                tradeLogger.error("Warning: {}/{} Moving3:placingError {}", counterName, attemptCount, e.toString());
//                    warningLogger.error("Warning: {}/{} Moving3:placingError {}", counterName, attemptCount, e.toString());
                tradeResponse.setOrderId(null);
                tradeResponse.setErrorCode(e.getMessage());
            }
        }
        return tradeResponse;
    }

    /**
     * Loop until status CANCELED or FILLED.
     * @param orderId
     * @param counterName
     * @param logInfoId
     * @return
     */
    @NotNull
    private Order getFinalOrderInfoSync(String orderId, String counterName, String logInfoId) {
        final OkCoinFuturesTradeService tradeService = (OkCoinFuturesTradeService) exchange.getTradeService();
        Order result;
        int attemptCount = 0;
        while (true) {
            attemptCount++;
            try {
                if (attemptCount > 1) {
                    Thread.sleep(200 * attemptCount);
                }
                final Collection<Order> order = tradeService.getOrder(orderId);
                Order cancelledOrder = order.iterator().next();
                tradeLogger.info("{}/{} {} id={}, status={}, filled={}",
                        counterName,
                        attemptCount,
                        logInfoId,
                        cancelledOrder.getId(),
                        cancelledOrder.getStatus(),
                        cancelledOrder.getCumulativeAmount());

                if (cancelledOrder.getStatus() == Order.OrderStatus.CANCELED
                        || cancelledOrder.getStatus() == Order.OrderStatus.FILLED) {
                    result = cancelledOrder;
                    break;
                }
            } catch (Exception e) {
                logger.error("{}/{} error on get order status", counterName, attemptCount, e);
                tradeLogger.error("{}/{} error on get order status: {}", counterName, attemptCount, e.toString());
            }
        }
        return result;
    }

    @NotNull
    private OkCoinTradeResult cancelOrderSync(String orderId, String logInfoId) {
        final String counterName = getCounterName();
        OkCoinTradeResult result;

        int attemptCount = 0;
        while (true) {
            attemptCount++;
            try {
                if (attemptCount > 1) {
                    Thread.sleep(200 * attemptCount);
                }
                final OkCoinFuturesTradeService tradeService = (OkCoinFuturesTradeService) exchange.getTradeService();
                result = tradeService.cancelOrderWithResult(orderId, CurrencyPair.BTC_USD, FuturesContract.ThisWeek);

                if (result == null) {
                    tradeLogger.info("{}/{} {} id={}, no response", counterName, attemptCount, logInfoId, orderId);
                    continue;
                }

                tradeLogger.info("{}/{} {} id={},res={}({}),code={},details={}",
                        counterName, attemptCount,
                        logInfoId,
                        orderId,
                        result.isResult(),
                        result.isResult() ? "cancelled" : "probably already filled",
                        result.getErrorCode(),
                        result.getDetails());

                break;

            } catch (Exception e) {
                logger.error("{}/{} error cancel maker order", counterName, attemptCount, e);
                tradeLogger.error("{}/{} error cancel maker order: {}", counterName, attemptCount, e.toString());
            }
        }
        return result;
    }

    @Override
    public String getPositionAsString() {
        return null;
    }

    private synchronized void recalcLiqInfo() {
        final BigDecimal pos = position.getPositionLong().subtract(position.getPositionShort());
        final BigDecimal oMrLiq = arbitrageService.getParams().getoMrLiq();

        final AccountInfoContracts accountInfoContracts = getAccountInfoContracts();
        final BigDecimal equity = accountInfoContracts.geteLast();
        final BigDecimal margin = accountInfoContracts.getMargin();

        if (equity != null && margin != null && oMrLiq != null
                && position.getPriceAvgShort() != null
                && position.getPriceAvgLong() != null) {
            BigDecimal dql = null;
            String dqlString;
            if (pos.signum() > 0) {
                final BigDecimal m = Utils.getBestAsk(orderBook).getLimitPrice();
                final BigDecimal n = pos.multiply(BigDecimal.valueOf(100));
                final BigDecimal d = (n.divide(m, 16, BigDecimal.ROUND_HALF_UP)).subtract(
                        (oMrLiq.divide(BigDecimal.valueOf(100), 16, BigDecimal.ROUND_HALF_UP).multiply(margin)).subtract(equity)
                ).setScale(8, BigDecimal.ROUND_HALF_UP);

                if (margin.signum() > 0 && equity.signum() > 0 && d.signum() > 0 && n.signum() > 0) {
                    final BigDecimal L = n.divide(d, 2, BigDecimal.ROUND_HALF_UP);
                    final BigDecimal subtract = (BigDecimal.ONE.divide(m, 15, BigDecimal.ROUND_HALF_UP))
                            .subtract(BigDecimal.ONE.divide(L, 15, BigDecimal.ROUND_HALF_UP));
                    final BigDecimal eqLiq = equity.add(subtract.multiply(n));
                    final BigDecimal mrl = eqLiq.divide(margin, 16, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100))
                            .setScale(8, BigDecimal.ROUND_HALF_UP);
                    if (mrl.subtract(oMrLiq).subtract(BigDecimal.ONE).signum() < 0
                            && mrl.subtract(oMrLiq).add(BigDecimal.ONE).signum() > 0) {
                        dql = m.subtract(L);
                        dqlString = String.format("o_DQL = m%s - L%s = %s", m, L, dql);
                    } else {
                        dqlString = "o_DQL = na";
                        warningLogger.info(String.format("Warning. mrl is wrong: o_pos=%s, o_margin=%s, o_equity=%s, qu_ent=%s/%s, eqLiq=%s, mrl=%s, oMrLiq=%s",
                                pos.toPlainString(), margin.toPlainString(), equity.toPlainString(),
                                position.getPriceAvgLong(), position.getPriceAvgShort(),
                                eqLiq.toPlainString(), mrl.toPlainString(), oMrLiq.toPlainString()));
                    }
                } else {
                    dqlString = "o_DQL = na";
                    warningLogger.info(String.format("Warning.All should be > 0: o_pos=%s, o_margin=%s, o_equity=%s, qu_ent=%s/%s, n=%s, d=%s",
                            pos.toPlainString(), margin.toPlainString(), equity.toPlainString(),
                            position.getPriceAvgLong(), position.getPriceAvgShort(),
                            n.toPlainString(), d.toPlainString()));
                }

            } else if (pos.signum() < 0) {
                final BigDecimal m = Utils.getBestBid(orderBook).getLimitPrice();
                final BigDecimal n = pos.multiply(BigDecimal.valueOf(100)).negate();
                final BigDecimal d = (n.divide(m, 16, BigDecimal.ROUND_HALF_UP)).add(
                        (oMrLiq.divide(BigDecimal.valueOf(100), 16, BigDecimal.ROUND_HALF_UP).multiply(margin)).subtract(equity)
                ).setScale(8, BigDecimal.ROUND_HALF_UP);

                if (d.signum() > 0) {
                    if (margin.signum() > 0 && equity.signum() > 0 && n.signum() > 0) {
                        final BigDecimal L = n.divide(d, 2, BigDecimal.ROUND_HALF_UP);
                        final BigDecimal substract = (BigDecimal.ONE.divide(L, 15, BigDecimal.ROUND_HALF_UP))
                                .subtract(BigDecimal.ONE.divide(m, 15, BigDecimal.ROUND_HALF_UP));
                        final BigDecimal eqLiq = equity.add(substract.multiply(n));
                        final BigDecimal mrl = eqLiq.divide(margin, 16, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100))
                                .setScale(8, BigDecimal.ROUND_HALF_UP);
                        if (mrl.subtract(oMrLiq).subtract(BigDecimal.ONE).signum() < 0
                                && mrl.subtract(oMrLiq).add(BigDecimal.ONE).signum() > 0) {
                            dql = L.subtract(m);
                            dqlString = String.format("o_DQL = L%s - m%s = %s", L, m, dql);
                        } else {
                            dqlString = "o_DQL = na";
                            warningLogger.info(String.format("Warning. mrl is wrong: o_pos=%s, o_margin=%s, o_equity=%s, qu_ent=%s/%s, eqLiq=%s, mrl=%s, oMrLiq=%s",
                                    pos.toPlainString(), margin.toPlainString(), equity.toPlainString(),
                                    position.getPriceAvgLong(), position.getPriceAvgShort(),
                                    eqLiq.toPlainString(), mrl.toPlainString(), oMrLiq.toPlainString()));
                        }
                    } else {
                        dqlString = "o_DQL = na";
                        warningLogger.info(String.format("Warning.All should be > 0: o_pos=%s, o_margin=%s, o_equity=%s, qu_ent=%s/%s, n=%s",
                                pos.toPlainString(), margin.toPlainString(), equity.toPlainString(),
                                position.getPriceAvgLong(), position.getPriceAvgShort(),
                                n.toPlainString()));
                    }

                } else {
                    dqlString = "o_DQL = na";
                    // ordinary situation
                }

            } else {
                dqlString = "o_DQL = na";
            }

            BigDecimal dmrl = null;
            String dmrlString;
            if (pos.signum() != 0 && margin.signum() > 0) {
                final BigDecimal oMr = equity.divide(margin, 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP);
                dmrl = oMr.subtract(oMrLiq);
                dmrlString = String.format("o_DMRL = %s - %s = %s%%", oMr, oMrLiq, dmrl);
            } else {
                dmrlString = "o_DMRL = na";
            }

            if (dql != null) {
                if (liqInfo.getLiqParams().getDqlMax().compareTo(dql) == -1) {
                    liqInfo.getLiqParams().setDqlMax(dql);
                }
                if (liqInfo.getLiqParams().getDqlMin().compareTo(dql) == 1) {
                    liqInfo.getLiqParams().setDqlMin(dql);
                }
            }
            liqInfo.setDqlCurr(dql);

            if (dmrl != null) {
                if (liqInfo.getLiqParams().getDmrlMax().compareTo(dmrl) == -1) {
                    liqInfo.getLiqParams().setDmrlMax(dmrl);
                }
                if (liqInfo.getLiqParams().getDmrlMin().compareTo(dmrl) == 1) {
                    liqInfo.getLiqParams().setDmrlMin(dmrl);
                }
            }
            liqInfo.setDmrlCurr(dmrl);

            liqInfo.setDqlString(dqlString);
            liqInfo.setDmrlString(dmrlString);

            storeLiqParams();
        }
    }

    /**
     * @param orderType - only ASK, BID. There are no CLOSE_* types.
     */
    @Override
    public boolean checkLiquidationEdge(Order.OrderType orderType) {
        final BigDecimal oDQLOpenMin = arbitrageService.getParams().getoDQLOpenMin();

        boolean isOk;

        if (liqInfo.getDqlCurr() == null) {
            isOk = true;
        } else {
            if (orderType.equals(Order.OrderType.BID)) { // LONG
                if ((position.getPositionLong().subtract(position.getPositionShort())).signum() > 0) {
                    if (liqInfo.getDqlCurr().compareTo(oDQLOpenMin) != -1) {
                        isOk = true;
                    } else {
                        isOk = false;
                    }
                } else {
                    isOk = true;
                }
            } else if (orderType.equals(Order.OrderType.ASK)) {
                if ((position.getPositionLong().subtract(position.getPositionShort()).signum() < 0)) {
                    if (liqInfo.getDqlCurr().compareTo(oDQLOpenMin) != -1) {
                        isOk = true;
                    } else {
                        isOk = false;
                    }
                } else {
                    isOk = true;
                }
            } else {
                throw new IllegalArgumentException("Wrong orderType " + orderType);
            }
        }

        debugLog.info(String.format("CheckLiqEdge:%s(p%s/%s/%s)", isOk,
                position.getPositionLong().subtract(position.getPositionShort()),
                liqInfo.getDqlCurr(),
                oDQLOpenMin));

        return isOk;
    }

    @Scheduled(fixedDelay = 5 * 1000) // 30 sec
    public void checkForDecreasePosition() {
        final BigDecimal oDQLCloseMin = arbitrageService.getParams().getoDQLCloseMin();
        final BigDecimal pos = position.getPositionLong().subtract(position.getPositionShort());

        if (liqInfo.getDqlCurr() != null
                && liqInfo.getDqlCurr().compareTo(BigDecimal.valueOf(-30)) > 0 // workaround when DQL is less zero
                && liqInfo.getDqlCurr().compareTo(oDQLCloseMin) < 0
                && pos.signum() != 0) {
            final BestQuotes bestQuotes = Utils.createBestQuotes(
                    arbitrageService.getSecondMarketService().getOrderBook(),
                    arbitrageService.getFirstMarketService().getOrderBook());

            if (pos.signum() > 0) {
                tradeLogger.info(String.format("%s O_PRE_LIQ starting: p(%s-%s)/dql%s/dqlClose%s",
                        getCounterName(),
                        position.getPositionLong().toPlainString(), position.getPositionShort().toPlainString(),
                        liqInfo.getDqlCurr().toPlainString(), oDQLCloseMin.toPlainString()));

                arbitrageService.startTradingOnDelta2(SignalType.O_PRE_LIQ, bestQuotes);

            } else if (pos.signum() < 0) {
                tradeLogger.info(String.format("%s O_PRE_LIQ starting: p(%s-%s)/dql%s/dqlClose%s",
                        getCounterName(),
                        position.getPositionLong().toPlainString(), position.getPositionShort().toPlainString(),
                        liqInfo.getDqlCurr().toPlainString(), oDQLCloseMin.toPlainString()));

                arbitrageService.startTradingOnDelta1(SignalType.O_PRE_LIQ, bestQuotes);

            }
        }
    }
}
