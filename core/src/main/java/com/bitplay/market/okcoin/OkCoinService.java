package com.bitplay.market.okcoin;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.MarketService;
import com.bitplay.market.State;
import com.bitplay.market.dto.LiqInfo;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.utils.Utils;

import info.bitrich.xchangestream.okex.OkExStreamingExchange;
import info.bitrich.xchangestream.okex.OkExStreamingMarketDataService;

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
import org.knowm.xchange.dto.trade.MarketOrder;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;
import javax.validation.constraints.NotNull;

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
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private final static CurrencyPair CURRENCY_PAIR_BTC_USD = new CurrencyPair("BTC", "USD");

    private static final BigDecimal OKCOIN_STEP = new BigDecimal("0.01");
    private final static String NAME = "okcoin";

    @Autowired
    private PosDiffService posDiffService;
    ArbitrageService arbitrageService;
    private OkExStreamingExchange exchange;
    private Disposable orderBookSubscription;
    private Disposable privateDataSubscription;
    private Disposable accountInfoSubscription;
    private Disposable futureIndexSubscription;
    private Observable<OrderBook> orderBookObservable;
    private static final int MAX_ATTEMPTS = 10;
    protected State state = State.READY;

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

        fetchOpenOrdersWithDelay();

//        fetchAccountInfo();
        fetchPosition();
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
    public Logger getTradeLogger() {
        return tradeLogger;
    }

    @Override
    public Observable<OrderBook> getOrderBookObservable() {
        return orderBookObservable;
    }

    @PreDestroy
    public void preDestroy() {
        // Unsubscribe from data order book.
        orderBookSubscription.dispose();
//        orderSubscriptions.forEach((s, disposable) -> disposable.dispose());
        privateDataSubscription.dispose();
        accountInfoSubscription.dispose();
        futureIndexSubscription.dispose();

        // Disconnect from exchange (non-blocking)
        exchange.disconnect().subscribe(() -> logger.info("Disconnected from the Exchange"));
    }

    @Override
    protected BigDecimal getMakerPriceStep() {
        return OKCOIN_STEP;
    }

    @Override
    protected BigDecimal getMakerDelta() {
        return arbitrageService.getParams().getMakerDelta();
    }

    public String fetchCurrencies() {
        final List<CurrencyPair> exchangeSymbols = exchange.getExchangeSymbols();
        final String toString = Arrays.toString(exchangeSymbols.toArray());
        logger.info(toString);
        return toString;
    }

    @Scheduled(fixedRate = 1000)
    public void requestAccountInfo() {
        try {
            exchange.getStreamingAccountInfoService().requestAccountInfo();
        } catch (IOException e) {
            logger.error("AccountInfo request error", e);
        }
    }

    @Scheduled(fixedRate = 1000)
    @Override
    public void fetchPosition() {
        try {
            final OkCoinPositionResult positionResult = ((OkCoinTradeServiceRaw) exchange.getTradeService()).getFuturesPosition("btc_usd", FuturesContract.ThisWeek);
            mergePosition(positionResult, null);

            recalcAffordableContracts();

        } catch (Exception e) {
            logger.error("FetchPositionError", e);
        }
    }

    private synchronized void mergePosition(OkCoinPositionResult restUpdate, Position websocketUpdate) {
        if (restUpdate != null) {
            if (restUpdate.getPositions().length > 1) {
                logger.warn("More than one positions found");
                tradeLogger.warn("More than one positions found");
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

    //TODO use subscribing on open orders
    @Scheduled(fixedRate = 1000 * 60 * 15)
    public void fetchOpenOrdersWithDelay() {
        Completable.timer(2000, TimeUnit.MILLISECONDS)
                .doOnError(throwable -> logger.error("onError fetchOOWithDelay", throwable))
                .doOnCompleted(() -> {
                    fetchOpenOrders(); // Synchronous
//                    if (openOrders.size() == 0) {
//                        eventBus.send(BtsEvent.MARKET_FREE);
//                    }
                })
                .subscribe();
    }

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
                    this.accountInfoContracts = accountInfoContracts;

                }, throwable -> {
                    logger.error("AccountInfo.Websocket.Exception: ", throwable);
                });
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
                            tradeLogger.info("Order update:id={},status={},amount={},filled={}",
                                    order.getId(), order.getStatus(), order.getTradableAmount(),
                                    order.getCumulativeAmount());
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
                            tradeLogger.info("Order removing:id={},status={},amount={},filled={}",
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

            debugLog.info("NewOrders: " + Arrays.toString(newOrders.toArray()));
            this.openOrders.addAll(newOrders);

            if (this.openOrders.size() == 0) {
                eventBus.send(BtsEvent.MARKET_FREE);
            }
        }
    }

    @Override
    public OrderBook getOrderBook() {
        return orderBook;
    }

    public TradeResponse takerOrder(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, SignalType signalType)
            throws Exception {
        state = State.IN_PROGRESS;

        TradeResponse tradeResponse = new TradeResponse();

        final TradeService tradeService = exchange.getTradeService();

        orderType = adjustOrderType(orderType, amount);

        synchronized (openOrdersLock) {

            final MarketOrder marketOrder = new MarketOrder(orderType, amount, CURRENCY_PAIR_BTC_USD, new Date());
            final String orderId = tradeService.placeMarketOrder(marketOrder);
            tradeResponse.setOrderId(orderId);
            final String counterName = signalType == SignalType.AUTOMATIC ? String.valueOf(arbitrageService.getCounter()) : signalType.getCounterName();

            final Optional<Order> orderInfoAttempts = getOrderInfoAttempts(orderId, counterName, orderType, "Taker:Status:");

            Order orderInfo = orderInfoAttempts.get();

            if (orderInfo.getStatus() != Order.OrderStatus.FILLED) { // 1. Try cancel then
                cancelOrderSync(signalType, orderId, "Taker:Cancel_maker:");
                orderInfo = getFinalOrderInfoSync(orderId, counterName, "Taker:Cancel_makerStatus:");
            }

            if (orderInfo.getStatus() != Order.OrderStatus.FILLED) { // 2. It is CANCELED
                throw new Exception("Taker wasn't filled. Cancelled");
            } else { //FILLED by any (orderInfo or cancelledOrder)
                if (signalType == SignalType.AUTOMATIC) {
                    arbitrageService.getOpenPrices().setSecondOpenPrice(orderInfo.getAveragePrice());
                }

                writeLogPlaceOrder(orderType, amount, bestQuotes, "taker", signalType,
                        orderInfo.getAveragePrice(), orderId, orderInfo.getStatus().toString());

                setFree();

                state = State.READY;
            }
        }

        return tradeResponse;
    }

    private Optional<Order> getOrderInfoAttempts(String orderId, String counterName, Order.OrderType orderType,
                                             String logInfoId) throws InterruptedException, IOException {
        final OkCoinFuturesTradeService tradeService = (OkCoinFuturesTradeService) exchange.getTradeService();
        Order orderInfo = null;
        for (int i = 0; i < MAX_ATTEMPTS; i++) { // about 11 sec
            // 2. check status of the order
            long sleepTime = 200;
            if (i > 5) {
                sleepTime = 2000;
            }
            Thread.sleep(sleepTime);
            final Collection<Order> order = tradeService.getOrder(orderId);
            orderInfo = order.iterator().next();

            if (orderInfo.getStatus().equals(Order.OrderStatus.FILLED)) {
                break;
            }

            tradeLogger.error("#{}/{} taker {} status={}, avgPrice={}, orderId={}, type={}, cumAmount={}",
                    counterName, i,
                    Utils.convertOrderTypeName(orderType),
                    orderInfo.getStatus().toString(),
                    orderInfo.getAveragePrice().toPlainString(),
                    orderInfo.getId(),
                    orderInfo.getType(),
                    orderInfo.getCumulativeAmount().toPlainString());

        }
        return Optional.ofNullable(orderInfo);
    }

    private synchronized void recalcAffordableContracts() {
        final BigDecimal reserveBtc = arbitrageService.getParams().getReserveBtc2();
        final BigDecimal volPlan = arbitrageService.getParams().getBlock2();

        if (accountInfoContracts != null && position != null) {
            final BigDecimal availableBtc = accountInfoContracts.getAvailable();
            final BigDecimal equityBtc = accountInfoContracts.getEquity();

            final BigDecimal bestAsk = Utils.getBestAsks(orderBook, 1).get(0).getLimitPrice();
            final BigDecimal bestBid = Utils.getBestBids(orderBook, 1).get(0).getLimitPrice();
            final BigDecimal leverage = position.getLeverage();

            if (availableBtc != null && equityBtc != null && leverage != null && position.getPositionLong() != null && position.getPositionShort() != null) {

                if (availableBtc.signum() > 0) {
//                if (orderType.equals(Order.OrderType.BID) || orderType.equals(Order.OrderType.EXIT_ASK)) {
                    if (position.getPositionShort().signum() != 0) { // there are sells
                        if (volPlan.compareTo(position.getPositionShort()) != 1) {
                            affordableContractsForLong = (position.getPositionShort().subtract(position.getPositionLong()).add(
                                    (equityBtc.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage).divide(BigDecimal.valueOf(100), 0, BigDecimal.ROUND_DOWN)
                            )).setScale(0, BigDecimal.ROUND_DOWN);
                            ;
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
                }
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
        return state == State.READY;
    }

    @Override
    public TradeResponse placeOrderOnSignal(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes,
                                            SignalType signalType) {
        TradeResponse tradeResponse;
        state = State.IN_PROGRESS;

        int attemptCount = 0;
        while (true) {
            try {
                attemptCount++;
                if (attemptCount > 1) {
                    Thread.sleep(200 * attemptCount);
                }

                if (arbitrageService.getParams().getOkCoinOrderType().equals("maker")) {
                    tradeResponse = placeSimpleMakerOrder(orderType, amountInContracts, bestQuotes, false, signalType);
                } else {
                    tradeResponse = takerOrder(orderType, amountInContracts, bestQuotes, signalType);
                }
                break;
            } catch (Exception e) {
                String details = String.format("#%s placeOrderOnSignal error. type=%s,a=%s,bestQuotes=%s,isMove=%s,signalT=%s",
                        signalType == SignalType.AUTOMATIC ? arbitrageService.getCounter() : signalType.getCounterName(),
                        orderType, amountInContracts, bestQuotes, false, signalType);
                logger.error(details, e);
                tradeLogger.error(details + e.toString());
//                warningLogger.error("Warning placing: " + details);
            }
        }

        return tradeResponse;
    }

    public TradeResponse placeSimpleMakerOrder(Order.OrderType orderType, BigDecimal tradeableAmount, BestQuotes bestQuotes,
                                               boolean isMoving, SignalType signalType) throws Exception {
        final TradeResponse tradeResponse = new TradeResponse();
        arbitrageService.setSignalType(signalType);
        eventBus.send(BtsEvent.MARKET_BUSY);
        state = State.IN_PROGRESS;

        BigDecimal thePrice;

        thePrice = createBestMakerPrice(orderType, false).setScale(2, BigDecimal.ROUND_HALF_UP);

        if (thePrice.compareTo(BigDecimal.ZERO) == 0) {
            tradeResponse.setErrorMessage("The new price is 0 ");
        } else if (tradeableAmount.compareTo(BigDecimal.ZERO) == 0) {

            tradeResponse.setErrorMessage("Not enough amount left. amount=" + tradeableAmount.toPlainString());

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
        state = State.WAITING;
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

        tradeLogger.info("#{}/end: {} {} amount={}, quote={}, orderId={}, status={}, ({})",
                signalType == SignalType.AUTOMATIC ? arbitrageService.getCounter() : signalType.getCounterName(),
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
    public MoveResponse moveMakerOrder(LimitOrder limitOrder, SignalType signalType) {
        if (limitOrder.getStatus() == Order.OrderStatus.CANCELED) {
            tradeLogger.error("do not move ALREADY_CLOSED order");
            return new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, "");
        }

        if (arbitrageService.getParams().getOkCoinOrderType().equals("taker")
                && state != State.IN_PROGRESS) {
            logger.error("taker not in progress. State={}", state);
            tradeLogger.error("taker not in progress. State={}", state);
            return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "moving taker order");
        }
//        if (state != State.WAITING) {
//            tradeLogger.error("Moving declined. Try moving in wrong State={}", state);
//            return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "moving declined");
//        }
        state = State.IN_PROGRESS;

        arbitrageService.setSignalType(signalType);
        eventBus.send(BtsEvent.MARKET_BUSY);

        // IT doesn't support moving
        // Do cancel ant place
        MoveResponse response;
        BestQuotes bestQuotes = orderIdToSignalInfo.get(limitOrder.getId());

        // 1. cancell old order
        final String counterName = signalType == SignalType.AUTOMATIC ? String.valueOf(arbitrageService.getCounter()) : signalType.getCounterName();
        cancelOrderSync(signalType, limitOrder.getId(), "Moving1:cancelled:");

        // 2. We got result on cancel(true/false), but double-check status of an old order
        Order cancelledOrder = getFinalOrderInfoSync(limitOrder.getId(), counterName, "Moving2:cancelStatus:");

        // 3. Already closed?
        if (cancelledOrder.getStatus() != Order.OrderStatus.CANCELED) { // Already closed
            response = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, "");

            final String logString = String.format("#%s %s %s status=%s,amount=%s,quote=%s,id=%s,lastException=%s",
                    counterName,
                    "Moving3:Already closed:",
                    limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                    cancelledOrder.getStatus(),
                    limitOrder.getTradableAmount(),
                    limitOrder.getLimitPrice().toPlainString(),
                    limitOrder.getId(),
                    null);
            tradeLogger.info(logString);
            state = State.READY;

            // 3. Place order
        } else { //if (cancelledOrder.getStatus() == Order.OrderStatus.CANCELED) {
            TradeResponse tradeResponse = new TradeResponse();
            tradeResponse = finishMovingSync(limitOrder, signalType, bestQuotes, counterName, cancelledOrder, tradeResponse);
            response = new MoveResponse(MoveResponse.MoveOrderStatus.MOVED_WITH_NEW_ID, tradeResponse.getOrderId(), tradeResponse.getLimitOrder());
            state = State.WAITING;
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
                    tradeLogger.info("#{}/{} Moving3:Failed {} amount={},quote={},id={},attempt={}. Error: {}",
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
                logger.error("#{}/{} Moving3:placingError", counterName, attemptCount, e);
                tradeLogger.error("Warning: #{}/{} Moving3:placingError {}", counterName, attemptCount, e.toString());
//                    warningLogger.error("Warning: #{}/{} Moving3:placingError {}", counterName, attemptCount, e.toString());
                tradeResponse.setOrderId(null);
                tradeResponse.setErrorMessage(e.getMessage());
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
                tradeLogger.info("#{}/{} {} id={}, status={}, filled={}",
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
                logger.error("#{}/{} error on get order status", counterName, attemptCount, e);
                tradeLogger.error("#{}/{} error on get order status: {}", counterName, attemptCount, e.toString());
            }
        }
        return result;
    }

    @NotNull
    private OkCoinTradeResult cancelOrderSync(SignalType signalType, String orderId, String logInfoId) {
        final String counterName = signalType == SignalType.AUTOMATIC ? String.valueOf(arbitrageService.getCounter()) : signalType.getCounterName();
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
                    tradeLogger.info("#{}/{} {} id={}, no response", counterName, attemptCount, logInfoId, orderId);
                    continue;
                }

                tradeLogger.info("#{}/{} {} id={},res={}({}),code={},details={}",
                        counterName, attemptCount,
                        logInfoId,
                        orderId,
                        result.isResult(),
                        result.isResult() ? "cancelled" : "probably already filled",
                        result.getErrorCode(),
                        result.getDetails());

                break;

            } catch (Exception e) {
                logger.error("#{}/{} error cancel maker order", counterName, attemptCount, e);
                tradeLogger.error("#{}/{} error cancel maker order: {}", counterName, attemptCount, e.toString());
            }
        }
        return result;
    }

    @Override
    public String getPositionAsString() {
        return null;
    }

    @Override
    public LiqInfo getLiqInfo() {
        final BigDecimal pos = position.getPositionLong().subtract(position.getPositionShort());
        final BigDecimal oMrLiq = arbitrageService.getParams().getoMrLiq();

        final AccountInfoContracts accountInfoContracts = getAccountInfoContracts();
        final BigDecimal equity = accountInfoContracts.getEquity();
        final BigDecimal margin = accountInfoContracts.getMargin();

        String dql;
        if (pos.signum() > 0) {
            final BigDecimal n = pos.multiply(BigDecimal.valueOf(100));
            final BigDecimal quEnt = position.getPriceAvgLong();
            final BigDecimal d = (n.divide(quEnt, 8, BigDecimal.ROUND_HALF_UP)).subtract(
                    (oMrLiq.divide(BigDecimal.valueOf(100), 8, BigDecimal.ROUND_HALF_UP).multiply(margin)).subtract(equity)
            );
            final BigDecimal L = n.divide(d, 2, BigDecimal.ROUND_HALF_UP);
            final BigDecimal m = Utils.getBestBid(orderBook).getLimitPrice();

            dql = String.format("o_DQL = m%s - L%s = %s;", m, L, m.subtract(L));
        } else if (pos.signum() < 0) {
            final BigDecimal n = pos.multiply(BigDecimal.valueOf(100)).negate();
            final BigDecimal quEnt = position.getPriceAvgShort();
            final BigDecimal d = (n.divide(quEnt, 8, BigDecimal.ROUND_HALF_UP)).add(
                    (oMrLiq.divide(BigDecimal.valueOf(100), 8, BigDecimal.ROUND_HALF_UP).multiply(margin)).subtract(equity)
            );
            final BigDecimal L = n.divide(d, 2, BigDecimal.ROUND_HALF_UP);
            final BigDecimal m = Utils.getBestAsk(orderBook).getLimitPrice();

            dql = String.format("o_DQL = L%s - m%s = %s;", L, m, L.subtract(m));
        } else {
            dql = "b_DQL = na";
        }

        final BigDecimal oMr = equity.divide(margin, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP);
        String dmrl = String.format("o_DMRL = %s - %s = %s%%", oMr, oMrLiq, oMr.subtract(oMrLiq));

        return new LiqInfo(dql, dmrl);
    }
}
