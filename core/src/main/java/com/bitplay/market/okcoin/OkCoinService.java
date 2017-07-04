package com.bitplay.market.okcoin;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.MarketService;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private OkExStreamingExchange exchange;
    private Disposable orderBookSubscription;
    private Disposable privateDataSubscription;
    private Disposable accountInfoSubscription;
    private Disposable futureIndexSubscription;
    private Observable<OrderBook> orderBookObservable;

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
    public void fetchPosition() {
        try {
            final OkCoinPositionResult positionResult = ((OkCoinTradeServiceRaw) exchange.getTradeService()).getFuturesPosition("btc_usd", FuturesContract.ThisWeek);
            if (positionResult.getPositions().length > 1) {
                logger.warn("More than one positions found");
                tradeLogger.warn("More than one positions found");
            }
            final OkCoinPosition okCoinPosition = positionResult.getPositions()[0];
            position = new Position(
                    okCoinPosition.getBuyAmount(),
                    okCoinPosition.getSellAmount(),
                    okCoinPosition.getRate(),
                    okCoinPosition.toString()
            );

            CompletableFuture.runAsync(this::recalcAffordableContracts);

        } catch (Exception e) {
            logger.error("FetchPositionError", e);
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
                .retry(3)
                .subscribe();
//        this.fetchOpenOrders();
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
                        position = new Position(positionInfo.getPositionLong(),
                                positionInfo.getPositionShort(),
                                this.position.getLeverage(),
                                positionInfo.getRaw());
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
//        synchronized (openOrdersLock) {
            // Replace all existing with new info
            this.openOrders = this.openOrders.stream()
                    .map(existingInMemory -> {
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

                        return order;
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
//        }
    }

    @Override
    public OrderBook getOrderBook() {
        return orderBook;
    }

    public TradeResponse placeTakerOrder(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, SignalType signalType) {
        TradeResponse tradeResponse = new TradeResponse();
        try {
            final TradeService tradeService = exchange.getTradeService();

            orderType = adjustOrderType(orderType, amount);

            final MarketOrder marketOrder = new MarketOrder(orderType,
                    amount,
                    CURRENCY_PAIR_BTC_USD, new Date());
            String orderId = tradeService.placeMarketOrder(marketOrder);
            tradeResponse.setOrderId(orderId);

            String status = "none";
            BigDecimal averagePrice = BigDecimal.ZERO;
            for (int i = 0; i < 40; i++) {
                // 2. check status of the order
                long sleepTime = 200;
                if (i > 10) {
                    sleepTime = 2000;
                    tradeLogger.error("Warning: taker order is not ready");
                }
                Thread.sleep(sleepTime);
                final Collection<Order> order = tradeService.getOrder(orderId);
                Order orderInfo = order.iterator().next();
                status = orderInfo.getStatus().toString();
                averagePrice = orderInfo.getAveragePrice();

                if (orderInfo.getStatus().equals(Order.OrderStatus.FILLED)) {
                    break;
                } else {
                    tradeLogger.error("#{} taker {} status={}, avgPrice={}, orderId={}",
                            signalType == SignalType.AUTOMATIC ? arbitrageService.getCounter() : signalType.getCounterName(),
                            Utils.convertOrderTypeName(orderType),
                            orderInfo.getStatus().toString(),
                            orderInfo.getAveragePrice().toPlainString(),
                            orderInfo.getId());
                }
            }

            if (signalType == SignalType.AUTOMATIC) {
                arbitrageService.getOpenPrices().setSecondOpenPrice(averagePrice);
            }

            writeLogPlaceOrder(orderType, amount, bestQuotes, "taker", signalType,
                    averagePrice, orderId, status);

        } catch (Exception e) {
            tradeLogger.error("Place market order error " + e.toString());
            logger.error("Place market order error", e);
            tradeResponse.setErrorMessage(e.getMessage());
        } finally {
            if (isBusy) {
                getEventBus().send(BtsEvent.MARKET_FREE);
            }
        }
        return tradeResponse;
    }

    private void recalcAffordableContracts() {
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
    public TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes,
                                         SignalType signalType) {
        if (arbitrageService.getParams().getOkCoinOrderType().equals("maker")) {
            return placeMakerOrder(orderType, amountInContracts, bestQuotes, false, signalType);
        } else {
            return placeTakerOrder(orderType, amountInContracts, bestQuotes, signalType);
        }
    }

    private TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal tradeableAmount, BestQuotes bestQuotes,
                                          boolean isMoving, SignalType signalType) {
        final TradeResponse tradeResponse = new TradeResponse();
        try {
            arbitrageService.setSignalType(signalType);
            eventBus.send(BtsEvent.MARKET_BUSY);

            BigDecimal thePrice;

            thePrice = createBestMakerPrice(orderType, false)
                    .setScale(2, BigDecimal.ROUND_HALF_UP);

            if (thePrice.compareTo(BigDecimal.ZERO) == 0) {
                tradeResponse.setErrorMessage("The new price is 0 ");
            } else if (tradeableAmount.compareTo(BigDecimal.ZERO) == 0) {

                tradeResponse.setErrorMessage("Not enough amount left. amount=" + tradeableAmount.toPlainString());

            } else {
                // USING REST API
                orderType = adjustOrderType(orderType, tradeableAmount);

                final LimitOrder limitOrder = new LimitOrder(orderType,
                        tradeableAmount,
                        CURRENCY_PAIR_BTC_USD, "123", new Date(),
                        thePrice);

                String orderId = exchange.getTradeService().placeLimitOrder(limitOrder);
                tradeResponse.setOrderId(orderId);

                // USING WEBSOCKET API
//                ContractOrderType contractOrderType;
//                if (orderType == Order.OrderType.BID) {
//                    contractOrderType = ContractOrderType.OPEN_LONG_POSITION_BUY;
//                } else {
//                    contractOrderType = ContractOrderType.OPEN_SHORT_POSITION_SELL;
//                }
//
//                final OkExStreamingTradingService tradingService = (OkExStreamingTradingService) exchange.getStreamingTradingService();
//                String orderId = tradingService.placeContractOrder("btc_usd",
//                        FuturesContract.ThisWeek,
//                        thePrice, tradeableAmount,
//                        contractOrderType);
//                tradeResponse.setOrderId(orderId);

                writeLogPlaceOrder(orderType, tradeableAmount, bestQuotes,
                        isMoving ? "Moving3:Moved" : "maker",
                        signalType, thePrice, orderId, null);

//                final Disposable orderListener = startOrderListener(orderId);
//                orderSubscriptions.put(orderId, orderListener);
                final LimitOrder limitOrderWithId = new LimitOrder(orderType,
                        tradeableAmount, CURRENCY_PAIR_BTC_USD, orderId, new Date(),
                        thePrice);
                tradeResponse.setLimitOrder(limitOrderWithId);
                if (!isMoving) {
                    // Warning java.util.ConcurrentModificationException with checkOpenOrdersForMoving()
                    // (do not use from iterate over Open orders checkOpenOrdersForMoving())
                    openOrders.add(limitOrderWithId);
                }

                if (signalType == SignalType.AUTOMATIC) {
                    arbitrageService.getOpenPrices().setSecondOpenPrice(thePrice);
                }
                orderIdToSignalInfo.put(orderId, bestQuotes);
            }

        } catch (Exception e) {
            String details = String.format("type=%s,a=%s,bestQuotes=%s,isMove=%s,signalT=%s",
                    orderType, tradeableAmount, bestQuotes, isMoving, signalType);
            logger.error("Place market order error. Details: " + details, e);
            tradeLogger.info("maker error {}", e.toString() + ". Details: " + details);
            tradeResponse.setOrderId(null);
            tradeResponse.setErrorMessage(e.getMessage());
        }

        fetchOpenOrdersWithDelay();
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

        tradeLogger.info("#{} {} {} amount={}, quote={}, orderId={}, ({}), status={}",
                signalType == SignalType.AUTOMATIC ? arbitrageService.getCounter() : signalType.getCounterName(),
                placingType, //isMoving ? "Moving3:Moved" : "maker",
                Utils.convertOrderTypeName(orderType),
                tradeableAmount.toPlainString(),
                thePrice,
                orderId,
                diffWithSignal,
                status);
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
        if (arbitrageService.getParams().getOkCoinOrderType().equals("taker")) {
            return new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_FIRST, "");
        }

        arbitrageService.setSignalType(signalType);
        eventBus.send(BtsEvent.MARKET_BUSY);

        // IT doesn't support moving
        // Do cancel ant place
        final OkCoinFuturesTradeService tradeService = (OkCoinFuturesTradeService) exchange.getTradeService();
        MoveResponse response;
        BestQuotes bestQuotes = orderIdToSignalInfo.get(limitOrder.getId());

        // 1. cancell old order
        int attemptCount = 0;
        Exception lastException = null;
        OkCoinTradeResult okCoinTradeResult = null;
        final String counterName = signalType == SignalType.AUTOMATIC ? String.valueOf(arbitrageService.getCounter()) : signalType.getCounterName();
        while (attemptCount < 2) {
            attemptCount++;
            try {
                okCoinTradeResult = tradeService.cancelOrderWithResult(limitOrder.getId(), CurrencyPair.BTC_USD, FuturesContract.ThisWeek);
                if (okCoinTradeResult != null) {
                    tradeLogger.info("#{} Moving1:cancelled: {} amount={},quote={},id={},attempt={},res={},code={},details={}",
                            counterName,
                            limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                            limitOrder.getTradableAmount(),
                            limitOrder.getLimitPrice().toPlainString(),
                            limitOrder.getId(),
                            attemptCount,
                            okCoinTradeResult.isResult(),
                            okCoinTradeResult.getErrorCode(),
                            okCoinTradeResult.getDetails());
                    break;
                }
            } catch (Exception e) {
                lastException = e;
                logger.error("{} attempt on cancel maker order", attemptCount, e);
            }
        }

        // 2. check status of an old order
        Order cancelledOrder = null;
        try {
            final Collection<Order> order = tradeService.getOrder(limitOrder.getId());
            cancelledOrder = order.iterator().next();
            tradeLogger.info("#{} Moving2:cancelStatus: id={}, status={}",
                    counterName,
                    cancelledOrder.getId(),
                    cancelledOrder.getStatus());
        } catch (IOException e) {
            logger.error("On get order status", e);
        }

        // 3. Place new or keep closed
        if (okCoinTradeResult == null) {
            final String msg = "Moving3:WARNING: Cancel result is null";
            tradeLogger.info(msg);
            response = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, msg);
        }

        if (cancelledOrder == null) {
            final String msg = "Moving3:WARNING: cancelledOrder is null";
            tradeLogger.info(msg);
            response = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, msg);
        } else if (cancelledOrder.getStatus() == Order.OrderStatus.CANCELED) {
            // Place order
            TradeResponse tradeResponse = new TradeResponse();
            while (attemptCount < 4) {
                attemptCount++;
                final BigDecimal newAmount = limitOrder.getTradableAmount()
                        .subtract(limitOrder.getCumulativeAmount());
                tradeResponse = placeMakerOrder(limitOrder.getType(),
                        newAmount, bestQuotes, true, signalType);
                if (tradeResponse.getErrorCode() != null && tradeResponse.getErrorCode().startsWith("Insufficient")) {
                    tradeLogger.info("#{} Moving3:Failed {} amount={},quote={},id={},attempt={}. Error: {}",
                            counterName,
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
            }
            if (tradeResponse.getOrderId() != null) {
                response = new MoveResponse(MoveResponse.MoveOrderStatus.MOVED_WITH_NEW_ID, tradeResponse.getOrderId(), tradeResponse.getLimitOrder());
            } else {
                final String description = String.format("#%s Moving3:error: Cancelled amount %s, but %s",
                        counterName,
                        limitOrder.getTradableAmount().toPlainString(), tradeResponse.getErrorCode());
                response = new MoveResponse(MoveResponse.MoveOrderStatus.ONLY_CANCEL, description);
                tradeLogger.info(description);
            }
        } else {
            String logResponse = "";
            if (lastException == null) { // For now we assume that order is filled when no Exceptions
                response = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, "");
                logResponse = "Moving3:Already closed:";
            } else {
                response = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, lastException.getMessage());
                logResponse = "Moving3:Cancel failed:";
            }
            final String logString = String.format("#%s %s %s status=%s,amount=%s,quote=%s,id=%s,attempt=%s,lastException=%s",
                    counterName,
                    logResponse,
                    limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                    cancelledOrder.getStatus(),
                    limitOrder.getTradableAmount(),
                    limitOrder.getLimitPrice().toPlainString(),
                    limitOrder.getId(),
                    attemptCount,
                    lastException != null ? lastException.getMessage() : null);
            tradeLogger.info(logString);
        }
        return response;
    }

    @Override
    public String getPositionAsString() {
        return null;
    }

}
