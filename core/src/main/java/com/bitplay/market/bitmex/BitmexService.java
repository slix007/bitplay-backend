package com.bitplay.market.bitmex;

import com.bitplay.api.service.RestartService;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.BalanceService;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketState;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.SignalEvent;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.OrderRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.FplayOrderUtils;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.SysOverloadArgs;
import com.bitplay.utils.Utils;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.bitrich.xchangestream.bitmex.BitmexStreamingAccountService;
import info.bitrich.xchangestream.bitmex.BitmexStreamingExchange;
import info.bitrich.xchangestream.bitmex.BitmexStreamingMarketDataService;
import info.bitrich.xchangestream.bitmex.dto.BitmexContractIndex;
import info.bitrich.xchangestream.bitmex.dto.BitmexOrderBook;
import info.bitrich.xchangestream.bitmex.dto.BitmexStreamAdapters;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitmex.service.BitmexAccountService;
import org.knowm.xchange.bitmex.service.BitmexTradeService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.utils.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PreDestroy;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.swagger.client.model.Error;
import si.mazi.rescu.HttpStatusIOException;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Service("bitmex")
public class BitmexService extends MarketService {
    private final static Logger logger = LoggerFactory.getLogger(BitmexService.class);
    private static final Logger tradeLogger = LoggerFactory.getLogger("BITMEX_TRADE_LOG");
    private static final Logger ordersLogger = LoggerFactory.getLogger("BITMEX_ORDERS_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private static final String NAME = "bitmex";
    private static final CurrencyPair CURRENCY_PAIR_XBTUSD = new CurrencyPair("XBT", "USD");

    private BitmexStreamingExchange exchange;

    private static final int MIN_SEC_TO_RESTART = 60 * 5; // 5 min
    private long listenersStartTimeEpochSecond = Instant.now().getEpochSecond();
    private volatile boolean isDestroyed = false;

    // Moving timeout
    private volatile ScheduledFuture<?> scheduledMoveInProgressReset;
    private volatile ScheduledFuture<?> scheduledMovingErrorsReset;
    private volatile boolean movingInProgress = false;
    private static final int MAX_MOVING_TIMEOUT_SEC = 2;
    private static final int MAX_MOVING_OVERLOAD_ATTEMPTS_TIMEOUT_SEC = 60;
    private volatile AtomicInteger movingErrorsOverloaded = new AtomicInteger(0);

    private Disposable accountInfoSubscription;
    private Disposable positionSubscription;
    private Disposable futureIndexSubscription;

    private Disposable orderBookSubscription;
    private Disposable openOrdersSubscription;
    @SuppressWarnings({"UnusedDeclaration"})
    private BitmexSwapService bitmexSwapService;

    private ArbitrageService arbitrageService;

    @Autowired
    private BitmexBalanceService bitmexBalanceService;

    @Autowired
    private RestartService restartService;
    private volatile String orderBookLastTimestamp = "";

    @Autowired
    private PosDiffService posDiffService;
    @Autowired
    private PersistenceService persistenceService;
    @Autowired
    private SettingsRepositoryService settingsRepositoryService;
    @Autowired
    private OrderRepositoryService orderRepositoryService;
    private String key;
    private String secret;
    private Disposable restartTimer;

    @Override
    public ArbitrageService getArbitrageService() {
        return arbitrageService;
    }

    @Override
    public BalanceService getBalanceService() {
        return bitmexBalanceService;
    }

    @Autowired
    public void setArbitrageService(ArbitrageService arbitrageService) {
        this.arbitrageService = arbitrageService;
    }

    @Override
    public PosDiffService getPosDiffService() {
        return posDiffService;
    }

    @Override
    public PersistenceService getPersistenceService() {
        return persistenceService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Logger getTradeLogger() {
        return tradeLogger;
    }

    @Override
    protected Exchange getExchange() {
        return exchange;
    }

    @Scheduled(fixedDelay = 2000)
    public void openOrdersCleaner() {
        if (openOrders.size() > 0) {
            cleanOldOO();
        }
    }

    @Scheduled(fixedRate = 30000)
    public void dobleCheckAvailableBalance() {
        if (accountInfoContracts == null) {
            tradeLogger.warn("WARNING: Bitmex Balance is null");
            warningLogger.warn("WARNING: Bitmex Balance is null");
            accountInfoSubscription.dispose();
            startAccountInfoListener();
        }
    }

    @Override
    public void initializeMarket(String key, String secret) {
        this.usdInContract = 1;
        this.key = key;
        this.secret = secret;
        bitmexSwapService = new BitmexSwapService(this, arbitrageService);

        loadLiqParams();

        initWebSocketConnection();

        Completable.timer(1000, TimeUnit.MILLISECONDS)
                .doOnComplete(this::startAllListeners)
                .subscribe();

//        Observable.interval(60, TimeUnit.SECONDS)
//                .doOnEach(throwable -> logger.warn("RESTART BITMEX FOR TESTING"))
//                .doOnEach(throwable -> warningLogger.warn("RESTART BITMEX FOR TESTING"))
//                .subscribe(aLong -> checkForRestart());

    }

    private void startAllListeners() {

        startOrderBookListener();

        Completable.timer(1000, TimeUnit.MILLISECONDS)
                .doOnComplete(this::startAccountInfoListener)
                .subscribe();
        Completable.timer(2000, TimeUnit.MILLISECONDS)
                .doOnComplete(this::startOpenOrderListener)
                .subscribe();

        Completable.timer(3000, TimeUnit.MILLISECONDS)
                .doOnComplete(this::startPositionListener)
                .subscribe();

        Completable.timer(5000, TimeUnit.MILLISECONDS)
                .doOnComplete(this::startFutureIndexListener)
                .subscribe();

        listenersStartTimeEpochSecond = Instant.now().getEpochSecond();
    }

    private synchronized void checkForRestart() {
        logger.info("checkForRestart " + getSubscribersStatuses());
        if (!isDestroyed
                && orderBookSubscription != null
                && accountInfoSubscription != null
                && openOrdersSubscription != null
                && positionSubscription != null
                && futureIndexSubscription != null
                ) {
            if (orderBookSubscription.isDisposed()
                    || accountInfoSubscription.isDisposed()
                    || openOrdersSubscription.isDisposed()
                    || positionSubscription.isDisposed()
                    || futureIndexSubscription.isDisposed()) {
                final long nowEpochSecond = Instant.now().getEpochSecond();
                if (nowEpochSecond - listenersStartTimeEpochSecond > MIN_SEC_TO_RESTART) {
                    warningLogger.info("Warning: Bitmex hanged. " + getSubscribersStatuses());
                    logger.info("Warning: Bitmex hanged " + getSubscribersStatuses());
                    doRestart();
                } else {
                    final long secToRestart = MIN_SEC_TO_RESTART - (nowEpochSecond - listenersStartTimeEpochSecond);
                    warningLogger.info("Warning: Bitmex hanged. {}. No Restart. Restart will be in {} sec.",
                            getSubscribersStatuses(),
                            secToRestart);
                    if (restartTimer != null && !restartTimer.isDisposed()) {
                        restartTimer.dispose();
                    }
                    restartTimer = Completable.timer(secToRestart, TimeUnit.SECONDS)
                            .onErrorComplete()
                            .subscribe(this::doRestart);
                }
            } else {
                logger.info("no Restart: everything looks ok " + getSubscribersStatuses());
            }
        }
    }

    private String getSubscribersStatuses() {
        return String.format("isDisposed: orderBookSub=%s, accountInfoSub=%s," +
                        "openOrdersSub=%s," +
                        "posSub=%s," +
                        "futureIndexSub=%s." +
                        " isLocked: openOrdersLock=%s",
                orderBookSubscription == null ? null : orderBookSubscription.isDisposed(),
                accountInfoSubscription == null ? null : accountInfoSubscription.isDisposed(),
                openOrdersSubscription == null ? null : openOrdersSubscription.isDisposed(),
                positionSubscription == null ? null : positionSubscription.isDisposed(),
                futureIndexSubscription == null ? null : futureIndexSubscription.isDisposed(),
                Thread.holdsLock(openOrdersLock));
    }

    @Override
    public String fetchPosition() throws Exception {
        final BitmexAccountService accountService = (BitmexAccountService) exchange.getAccountService();
        final Position pUpdate = accountService.fetchPositionInfo();

        mergePosition(pUpdate);

        recalcAffordableContracts();
        recalcLiqInfo();
        return BitmexUtils.positionToString(pUpdate);
    }

    private synchronized void mergePosition(Position pUpdate) {
        BigDecimal leverage = pUpdate.getLeverage().signum() == 0 ? BigDecimal.valueOf(100) : pUpdate.getLeverage();
        BigDecimal liqPrice = pUpdate.getLiquidationPrice().signum() == 0 ? this.position.getLiquidationPrice() : pUpdate.getLiquidationPrice();
        BigDecimal markValue = pUpdate.getMarkValue() != null ? pUpdate.getMarkValue() : this.position.getMarkValue();
        BigDecimal avgPriceL = pUpdate.getPriceAvgLong().signum() == 0 ? this.position.getPriceAvgLong() : pUpdate.getPriceAvgLong();
        BigDecimal avgPriceS = pUpdate.getPriceAvgShort().signum() == 0 ? this.position.getPriceAvgShort() : pUpdate.getPriceAvgShort();
        this.position = new Position(
                pUpdate.getPositionLong(),
                pUpdate.getPositionShort(),
                leverage,
                liqPrice,
                markValue,
                avgPriceL,
                avgPriceS,
                pUpdate.getRaw()
        );

    }


    @Override
    protected void iterateOpenOrdersMove() { // if synchronized then the queue for moving could be long
        final MarketState marketState = getMarketState();
        if (marketState == MarketState.SYSTEM_OVERLOADED || marketState == MarketState.PLACING_ORDER) {
            return;
        }

        if (movingInProgress) {

            // Should not happen ever, because 'synch' on method
            final String logString = String.format("%s No moving. Too often requests.", getCounterName());
            logger.error(logString);
            return;

        } else {
            movingInProgress = true;
            scheduledMoveInProgressReset =
                    scheduler.schedule(() -> movingInProgress = false, MAX_MOVING_TIMEOUT_SEC, TimeUnit.SECONDS);
        }

        synchronized (openOrdersLock) {
            if (hasOpenOrders()) {

                final SysOverloadArgs sysOverloadArgs = settingsRepositoryService.getSettings().getBitmexSysOverloadArgs();
                final Integer maxAttempts = sysOverloadArgs.getMovingErrorsForOverload();

                openOrders = openOrders.stream()
                        .flatMap(openOrder -> {
                            Stream<FplayOrder> orderStream = Stream.of(openOrder); // default - the same

                            if (openOrder == null) {
                                warningLogger.warn("OO is null.");
                                orderStream = Stream.empty();
                            } else if (openOrder.getOrder().getType() == null) {
                                warningLogger.warn("OO type is null. " + openOrder.toString());
                            } else if (openOrder.getOrderDetail().getOrderStatus() != Order.OrderStatus.NEW
                                    && openOrder.getOrderDetail().getOrderStatus() != Order.OrderStatus.PENDING_NEW
                                    && openOrder.getOrderDetail().getOrderStatus() != Order.OrderStatus.PARTIALLY_FILLED) {
                                // keep the order

                            } else {

                                try {
                                    if (openOrder.getOrder().getId().equals("0")) {
                                        orderStream = Stream.empty();
                                        return orderStream;
                                    }

                                    final MoveResponse response = moveMakerOrderIfNotFirst(openOrder);
                                    //TODO keep an eye on 'hang open orders'
                                    if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.ALREADY_CLOSED) {
//                                        orderStream = Stream.empty(); // no such case anymore
                                        // keep the order

                                    } else if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.MOVED) {
                                        orderStream = Stream.of(response.getNewFplayOrder());
                                        movingErrorsOverloaded.set(0);
                                    } else if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.ONLY_CANCEL) {
                                        if (movingErrorsOverloaded.incrementAndGet() >= maxAttempts) {
                                            setOverloaded(null);
                                            movingErrorsOverloaded.set(0);
                                        } else {

                                            // place new order instead of 'cancelled-on-moving'
                                            final FplayOrder cancelledFplayOrder = response.getCancelledFplayOrder();
                                            if (cancelledFplayOrder != null) {
                                                final LimitOrder cancelledOrder = (LimitOrder) cancelledFplayOrder.getOrder();
                                                final TradeResponse tradeResponse = placeOrder(new PlaceOrderArgs(
                                                        cancelledOrder.getType(),
                                                        cancelledOrder.getTradableAmount().subtract(cancelledOrder.getCumulativeAmount()),
                                                        openOrder.getBestQuotes(),
                                                        openOrder.getPlacingType(),
                                                        openOrder.getSignalType(),
                                                        1));

                                                final Stream.Builder<FplayOrder> streamBuilder = Stream.builder();
                                                // 1. old order
                                                streamBuilder.add(cancelledFplayOrder);

                                                // 2. new order
                                                final LimitOrder placedOrder = tradeResponse.getLimitOrder();
                                                if (placedOrder != null) {
                                                    streamBuilder.add(new FplayOrder(placedOrder, openOrder.getBestQuotes(), openOrder.getPlacingType(), openOrder.getSignalType()));
                                                }

                                                // 3. failed on placing
                                                tradeResponse.getCancelledOrders()
                                                        .forEach(limitOrder -> streamBuilder.add(
                                                                new FplayOrder(limitOrder, openOrder.getBestQuotes(), openOrder.getPlacingType(), openOrder.getSignalType())));
                                                orderStream = streamBuilder.build();

                                            }

                                            scheduledMoveInProgressReset = scheduler.schedule(() -> movingErrorsOverloaded.set(0),
                                                    MAX_MOVING_OVERLOAD_ATTEMPTS_TIMEOUT_SEC, TimeUnit.SECONDS);
                                        }

                                    } else if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.EXCEPTION_SYSTEM_OVERLOADED) {

                                        if (movingErrorsOverloaded.incrementAndGet() >= maxAttempts) {
                                            setOverloaded(null);
                                            movingErrorsOverloaded.set(0);
                                        } else {
                                            scheduledMoveInProgressReset = scheduler.schedule(() -> movingErrorsOverloaded.set(0),
                                                    MAX_MOVING_OVERLOAD_ATTEMPTS_TIMEOUT_SEC, TimeUnit.SECONDS);
                                        }

                                    } else if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.EXCEPTION) {
                                        tradeLogger.warn("MovingException: " + response.getDescription());
                                        logger.warn("MovingException: " + response.getDescription());
                                    }

                                } catch (Exception e) {
                                    // use default OO
                                    warningLogger.warn("Error on moving: " + e.getMessage());
                                    logger.warn("Error on moving", e);
                                }
                            }

                            return orderStream; // default - the same
                        })
                        .collect(Collectors.toList());

                if (!hasOpenOrders()) {
                    tradeLogger.warn("Free by iterateOpenOrdersMove");
                    logger.warn("Free by iterateOpenOrdersMove");
                    eventBus.send(BtsEvent.MARKET_FREE);
                }

            }

        } // synchronized (openOrdersLock)

        movingInProgress = false;
    }

    private BitmexStreamingExchange initExchange(String key, String secret) {
        ExchangeSpecification spec = new ExchangeSpecification(BitmexStreamingExchange.class);
        spec.setApiKey(key);
        spec.setSecretKey(secret);

        //ExchangeFactory.INSTANCE.createExchange(spec); - class cast exception, because
        // bitmex-* implementations should be moved into libraries.
        return (BitmexStreamingExchange) createExchange(spec);
    }

    private Exchange createExchange(ExchangeSpecification exchangeSpecification) {

        Assert.notNull(exchangeSpecification, "exchangeSpecfication cannot be null");

        logger.debug("Creating exchange from specification");

        String exchangeClassName = exchangeSpecification.getExchangeClassName();

        // Attempt to create an instance of the exchange provider
        try {

            // Attempt to locate the exchange provider on the classpath
            Class exchangeProviderClass = Class.forName(exchangeClassName);

            // Test that the class implements Exchange
            if (Exchange.class.isAssignableFrom(exchangeProviderClass)) {
                // Instantiate through the default constructor
                Exchange exchange = (Exchange) exchangeProviderClass.newInstance();
                exchange.applySpecification(exchangeSpecification);
                return exchange;
            } else {
                throw new ExchangeException("Class '" + exchangeClassName + "' does not implement Exchange");
            }
        } catch (ClassNotFoundException e) {
            throw new ExchangeException("Problem starting exchange provider (class not found)", e);
        } catch (InstantiationException e) {
            throw new ExchangeException("Problem starting exchange provider (instantiation)", e);
        } catch (IllegalAccessException e) {
            throw new ExchangeException("Problem starting exchange provider (illegal access)", e);
        }

        // Cannot be here due to exceptions
    }

    private void initWebSocketConnection() {
        // Connect to the Exchange WebSocket API. Blocking wait for the connection.
        try {
            exchange = initExchange(this.key, this.secret);

            exchange.connect()
                    .doOnError(throwable -> logger.error("doOnError", throwable))
                    .doOnDispose(() -> logger.info("bitmex connect doOnDispose"))
                    .doOnTerminate(() -> logger.info("bitmex connect doOnTerminate"))
                    .retryWhen(e -> e.delay(10, TimeUnit.SECONDS))
                    .blockingAwait();

            exchange.authenticate().blockingAwait();

            // Retry on disconnect.
            exchange.onDisconnect().subscribe(() -> {
                        logger.warn("onClientDisconnect BitmexService");
                        doRestart();
                    },
                    throwable -> {
                        logger.error("onClientDisconnect BitmexService error", throwable);
                        doRestart();
                    });
        } catch (Exception e) {
            logger.error("Connection failed");
            checkForRestart();
        }
    }

    private void doRestart() {
        try {
            restartService.doFullRestart("BitmexService#doRestart(). orderBookLastTimestamp=" + orderBookLastTimestamp);
        } catch (IOException e) {
            logger.error("Error on full restart", e);
        }
//        warningLogger.info("Warning: Bitmex restart");
//        logger.info("Warning: Bitmex restart");
//        destroyAction();
//        initWebSocketConnection();
//        startAllListeners();
    }

    @PreDestroy
    private void preDestroy() {
        isDestroyed = true;
        destroyAction();
    }

    private void destroyAction() {
        logger.info("Bitmex destroyAction");
        orderBookSubscription.dispose();
        accountInfoSubscription.dispose();
        openOrdersSubscription.dispose();
        positionSubscription.dispose();
        futureIndexSubscription.dispose();
        exchange.disconnect().blockingAwait();

        Completable.timer(5000, TimeUnit.MILLISECONDS)
                .onErrorComplete()
                .blockingAwait();

        if (!orderBookSubscription.isDisposed()
                || !accountInfoSubscription.isDisposed()
                || !openOrdersSubscription.isDisposed()
                || !positionSubscription.isDisposed()
                || !futureIndexSubscription.isDisposed()
                ) {
            warningLogger.info("Warning: destroy loop " + getSubscribersStatuses());
            logger.warn("Warning: destroy loop " + getSubscribersStatuses());
            destroyAction();
        } else {
            logger.info("Destroy finished. " + getSubscribersStatuses());
        }
    }

    private OrderBook mergeOrderBook(BitmexOrderBook bitmexOrderBook) {

        OrderBook orderBook = getOrderBook();
            if (bitmexOrderBook.getAction().equals("partial")) {
                orderBook = BitmexStreamAdapters.adaptBitmexOrderBook(bitmexOrderBook, CURRENCY_PAIR_XBTUSD);
            } else if (bitmexOrderBook.getAction().equals("delete")) {
                orderBook = BitmexStreamAdapters.delete(orderBook, bitmexOrderBook);
            } else if (bitmexOrderBook.getAction().equals("update")) {
                orderBook = BitmexStreamAdapters.update(orderBook, bitmexOrderBook, new Date(), CURRENCY_PAIR_XBTUSD);
            } else if (bitmexOrderBook.getAction().equals("insert")) {
                orderBook = BitmexStreamAdapters.insert(orderBook, bitmexOrderBook, new Date(), CURRENCY_PAIR_XBTUSD);
            }
            this.orderBook = orderBook;

        return this.orderBook;
    }

    private void startOrderBookListener() {
        Observable<OrderBook> orderBookObservable = ((BitmexStreamingMarketDataService)exchange.getStreamingMarketDataService())
                .getOrderBookL2(CurrencyPair.BTC_USD, 20)
                .doOnError(throwable -> logger.error("can not get orderBook", throwable))
                .map(this::mergeOrderBook)
                .doOnError(throwable -> logger.error("can not merge orderBook", throwable))
                .doOnDispose(() -> {
                    logger.info("bitmex subscription doOnDispose");
                    checkForRestart();
                })
                .doOnTerminate(() -> logger.info("bitmex subscription doOnTerminate"))
                .doOnError((throwable) -> logger.error("bitmex subscription doOnError", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS));

        orderBookSubscription = orderBookObservable
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .subscribe(orderBook -> {
                    if (orderBook != null) {
                        final LimitOrder bestAsk = Utils.getBestAsk(orderBook);
                        final LimitOrder bestBid = Utils.getBestBid(orderBook);

                        if (bestAsk != null) {
                            orderBookLastTimestamp = bestAsk.getTimestamp().toString();
                        }

                        if (this.bestAsk != null && bestAsk != null && this.bestBid != null && bestBid != null
                                && this.bestAsk.compareTo(bestAsk.getLimitPrice()) != 0
                                && this.bestBid.compareTo(bestBid.getLimitPrice()) != 0) {
                            recalcAffordableContracts();
                        }
                        this.bestAsk = bestAsk != null ? bestAsk.getLimitPrice() : BigDecimal.ZERO;
                        this.bestBid = bestBid != null ? bestBid.getLimitPrice() : BigDecimal.ZERO;
                        logger.debug("ask: {}, bid: {}", this.bestAsk, this.bestBid);

                        getArbitrageService().getSignalEventBus().send(SignalEvent.B_ORDERBOOK_CHANGED);
                    }

                }, throwable -> {
                    logger.error("ERROR in getting order book: ", throwable);
                    checkForRestart();
                });
    }

    private void startOpenOrderListener() {
        openOrdersSubscription = exchange.getStreamingTradingService()
                .getOpenOrdersObservable()
                .doOnDispose(() -> {
                    logger.info("bitmex subscription doOnDispose");
                    checkForRestart();
                })
                .doOnTerminate(() -> logger.info("bitmex subscription doOnTerminate"))
                .doOnError(throwable -> logger.error("onOpenOrdersListening", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .observeOn(Schedulers.computation())
                .subscribeOn(Schedulers.io())
                .subscribe(updateOfOpenOrders -> {
                    try {
                        synchronized (openOrdersLock) {
                            logger.debug("OpenOrders: " + updateOfOpenOrders.toString());

                            updateOpenOrders(updateOfOpenOrders.getOpenOrders()); // all there: add/update/remove

                            // bitmex specific actions
                            updateOfOpenOrders.getOpenOrders()
                                    .forEach(update -> {
                                        if (update.getStatus() == Order.OrderStatus.FILLED) {
                                            logger.info("{} Order {} FILLED", getCounterName(), update.getId());
                                            getArbitrageService().getSignalEventBus().send(SignalEvent.MT2_BITMEX_ORDER_FILLED);
                                        }
                                    });

                        } // synchronized (openOrdersLock)
                    } catch (Exception e) {
                        logger.error("OpenOrderListener", e);
                    }

                }, throwable -> {
                    logger.error("OO.Exception: ", throwable);
//                    startOpenOrderListener();
                    sleep(5000);
                    checkForRestart();
                });
    }

    @Override
    public UserTrades fetchMyTradeHistory() {
        return null;
    }

    private synchronized void recalcAffordableContracts() {
        final BigDecimal reserveBtc = arbitrageService.getParams().getReserveBtc1();

        if (accountInfoContracts != null) {
            final BigDecimal availableBtc = accountInfoContracts.getAvailable();
            final BigDecimal equityBtc = accountInfoContracts.geteMark();
            final OrderBook orderBook = getOrderBook();
            final BigDecimal bestAsk = Utils.getBestAsk(orderBook).getLimitPrice();
            final BigDecimal bestBid = Utils.getBestBid(orderBook).getLimitPrice();
            final BigDecimal positionContracts = position.getPositionLong();
            final BigDecimal leverage = position.getLeverage();

            if (availableBtc != null && equityBtc != null && positionContracts != null && leverage != null) {

                if (positionContracts.signum() == 0) {
                    affordableContractsForLong = ((availableBtc.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage)).setScale(0, BigDecimal.ROUND_DOWN);
                    affordableContractsForShort = ((availableBtc.subtract(reserveBtc)).multiply(bestBid).multiply(leverage)).setScale(0, BigDecimal.ROUND_DOWN);
                } else if (positionContracts.signum() > 0) {
                    affordableContractsForLong = ((availableBtc.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage)).setScale(0, BigDecimal.ROUND_DOWN);
                    affordableContractsForShort = (positionContracts.add((equityBtc.subtract(reserveBtc)).multiply(bestBid).multiply(leverage))).setScale(0, BigDecimal.ROUND_DOWN);
                    if (affordableContractsForShort.compareTo(positionContracts) == -1) {
                        affordableContractsForShort = positionContracts;
                    }
                } else if (positionContracts.signum() < 0) {
                    affordableContractsForLong = (positionContracts.negate().add((equityBtc.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage))).setScale(0, BigDecimal.ROUND_DOWN);
                    if (affordableContractsForLong.compareTo(positionContracts.negate()) == -1) {
                        affordableContractsForLong = positionContracts.negate();
                    }
                    affordableContractsForShort = ((availableBtc.subtract(reserveBtc)).multiply(bestBid).multiply(leverage)).setScale(0, BigDecimal.ROUND_DOWN);
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
    public TradeResponse placeOrderOnSignal(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes,
                                            SignalType signalType) {
        final Settings settings = settingsRepositoryService.getSettings();
        PlacingType placingType = PlacingType.MAKER;
        switch (settings.getArbScheme()) {
            case MT:
            case MT2:
                placingType = PlacingType.MAKER;
                break;
            case TT:
                placingType = PlacingType.TAKER;
                break;
        }
        return placeOrderToOpenOrders(orderType, amountInContracts, bestQuotes, placingType, signalType);
    }

    public TradeResponse makerOrder(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes, SignalType signalType) {
        return placeOrderToOpenOrders(orderType, amountInContracts, bestQuotes, PlacingType.MAKER, signalType);
    }

    public TradeResponse takerOrder(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes, SignalType signalType) {
        return placeOrderToOpenOrders(orderType, amountInContracts, bestQuotes, PlacingType.TAKER, signalType);
    }

    private TradeResponse placeOrderToOpenOrders(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes,
                                                 PlacingType placingType, SignalType signalType) {
        final PlaceOrderArgs placeOrderArgs = new PlaceOrderArgs(orderType, amount, bestQuotes, placingType, signalType, 1);

        final TradeResponse tradeResponse = placeOrder(placeOrderArgs);

        final List<FplayOrder> failedOrders = tradeResponse.getCancelledOrders().stream()
                .map(limitOrder -> new FplayOrder(limitOrder, placeOrderArgs.getBestQuotes(), placeOrderArgs.getPlacingType(), placeOrderArgs.getSignalType()))
                .collect(Collectors.toList());
        FplayOrder newOrder = null;
        if (tradeResponse.getLimitOrder() != null) {
            newOrder = new FplayOrder(tradeResponse.getLimitOrder(), placeOrderArgs.getBestQuotes(), placeOrderArgs.getPlacingType(), placeOrderArgs.getSignalType());
        }
        if (!failedOrders.isEmpty() || newOrder != null) {
            synchronized (openOrdersLock) {
                this.openOrders.addAll(failedOrders);
                if (newOrder != null) this.openOrders.add(newOrder);
            }
        }

        return tradeResponse;
    }

    public TradeResponse placeOrder(final PlaceOrderArgs placeOrderArgs) {
        final TradeResponse tradeResponse = new TradeResponse();

        final Integer maxAttempts = settingsRepositoryService.getSettings().getBitmexSysOverloadArgs().getPlaceAttempts();
        if (placeOrderArgs.getAttempt() == maxAttempts) {
            final String logString = String.format("%s Bitmex Warning placing: too many attempt(%s) when SYSTEM_OVERLOADED. Do nothing.",
                    getCounterName(),
                    maxAttempts);

            logger.error(logString);
            tradeLogger.error(logString);
            warningLogger.error(logString);

            tradeResponse.setErrorCode(logString);
            return tradeResponse;
        }

        final Order.OrderType orderType = placeOrderArgs.getOrderType();
        final BigDecimal amount = placeOrderArgs.getAmount();
        final BestQuotes bestQuotes = placeOrderArgs.getBestQuotes();
        final PlacingType placingType = placeOrderArgs.getPlacingType();
        final SignalType signalType = placeOrderArgs.getSignalType();

        MarketState nextMarketState = getMarketState();

        try {
            arbitrageService.setSignalType(signalType);
            setMarketState(MarketState.PLACING_ORDER);

            final BitmexTradeService bitmexTradeService = (BitmexTradeService) exchange.getTradeService();

            int attemptCount = 0;
            while (attemptCount < maxAttempts) {
                attemptCount++;
                try {
                    String orderId;
                    BigDecimal thePrice;
                    if (placingType == PlacingType.MAKER) {

                        final BigDecimal bitmexPrice = settingsRepositoryService.getSettings().getBitmexPrice();
                        if (bitmexPrice != null && bitmexPrice.signum() != 0) {
                            thePrice = bitmexPrice;
                        } else {
                            thePrice = createBestMakerPrice(orderType).setScale(1, BigDecimal.ROUND_HALF_UP);
                        }
                        final LimitOrder requestOrder = new LimitOrder(orderType, amount, CURRENCY_PAIR_XBTUSD, "0", new Date(), thePrice);
                        final LimitOrder resultOrder = bitmexTradeService.placeLimitOrderBitmex(requestOrder);
                        orderId = resultOrder.getId();
                        final FplayOrder fplayOrder = new FplayOrder(resultOrder, bestQuotes, placingType, signalType);
                        orderRepositoryService.save(fplayOrder);
                        if (orderId != null && !orderId.equals("0")) {
                            tradeResponse.setLimitOrder(resultOrder);
                        }

                        if (resultOrder.getStatus() == Order.OrderStatus.CANCELED) {
                            tradeResponse.addCancelledOrder(requestOrder);
                            tradeResponse.setErrorCode("WAS CANCELED"); // for the last iteration
                            tradeResponse.setLimitOrder(null);
                            tradeLogger.info("{} {} {} CANCELED amount={}, filled={}, quote={}, orderId={}",
                                    getCounterName(),
                                    placingType,
                                    orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                                    amount.toPlainString(),
                                    resultOrder.getCumulativeAmount(),
                                    thePrice,
                                    orderId);
                            continue;
                        }
                        nextMarketState = MarketState.ARBITRAGE;

                    } else {
                        final MarketOrder marketOrder = new MarketOrder(orderType, amount, CURRENCY_PAIR_XBTUSD, new Date());
                        final MarketOrder resultOrder = bitmexTradeService.placeMarketOrderBitmex(marketOrder);
                        orderId = resultOrder.getId();
                        thePrice = resultOrder.getAveragePrice();
                    }

                    tradeResponse.setOrderId(orderId);
                    tradeResponse.setErrorCode(null);

                    String diffWithSignal = "";
                    if (bestQuotes != null) {
                        final BigDecimal ask1_p = bestQuotes.getAsk1_p().setScale(1, BigDecimal.ROUND_HALF_UP);
                        final BigDecimal bid1_p = bestQuotes.getBid1_p().setScale(1, BigDecimal.ROUND_HALF_UP);
                        final BigDecimal diff1 = ask1_p.subtract(thePrice);
                        final BigDecimal diff2 = thePrice.subtract(bid1_p);
                        diffWithSignal = orderType.equals(Order.OrderType.BID)
                                ? String.format("diff1_buy_p = ask_p[1] - order_price_buy_p = %s", diff1.toPlainString()) //"BUY"
                                : String.format("diff2_sell_p = order_price_sell_p - bid_p[1] = %s", diff2.toPlainString()); //"SELL"
                        arbitrageService.getOpenDiffs().setFirstOpenPrice(orderType.equals(Order.OrderType.BID)
                                ? diff1 : diff2);
                        orderIdToSignalInfo.put(orderId, bestQuotes);
                    }

                    if (signalType == SignalType.AUTOMATIC) {
                        arbitrageService.getOpenPrices().setFirstOpenPrice(thePrice);
                    }

                    final String message = String.format("%s %s %s amount=%s with quote=%s was placed.orderId=%s. %s. position=%s",
                            getCounterName(),
                            placingType,
                            orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                            amount.toPlainString(),
                            thePrice,
                            orderId,
                            diffWithSignal,
                            getPositionAsString());
                    tradeLogger.info(message);
                    ordersLogger.info(message);

                    break;
                } catch (HttpStatusIOException e) {
                    final String httpBody = e.getHttpBody();
                    tradeResponse.setErrorCode(httpBody);

                    HttpStatusIOExceptionHandler handler = new HttpStatusIOExceptionHandler(e, "PlaceOrderError", attemptCount).invoke();

                    if (MoveResponse.MoveOrderStatus.EXCEPTION_SYSTEM_OVERLOADED == handler.getMoveResponse().getMoveOrderStatus()) {
                        if (attemptCount < maxAttempts) {
                            // No sleep
//                            Thread.sleep(1000);
                        } else {
                            setOverloaded(null);
                            tradeResponse.setErrorCode(e.getMessage());
                            break;
                        }
                    } else {
                        break; // any unknown exception - no retry
                    }
                } catch (Exception e) {
                    final String message = e.getMessage();
                    tradeResponse.setErrorCode(message);

                    final String logString = String.format("%s/%s PlaceOrderError: %s", getCounterName(), attemptCount, message);
                    logger.error(logString, e);
                    tradeLogger.error(logString);
                    warningLogger.error(logString);

                    // message.startsWith("Connection refused") - when we got banned for a week. Just skip it.
                    // message.startsWith("Read timed out")
                    if (message != null &&
                            (message.startsWith("Network is unreachable") || message.startsWith("connect timed out"))) {
                        if (attemptCount < maxAttempts) {
                            Thread.sleep(1000);
                        } else {
                            setOverloaded(null);
                            break;
                        }
                    } else {
                        break; // any unknown exception - no retry
                    }

                }
            } // while

            String errorCode = "attemtp=" + attemptCount;
            if (tradeResponse.getErrorCode() != null) {
                errorCode += "," + tradeResponse.getErrorCode();
            }
            tradeResponse.setErrorCode(errorCode);

        } catch (Exception e) {
            logger.error("Place market order error", e);
            tradeLogger.info("maker error {}", e.toString());
            tradeResponse.setErrorCode(e.getMessage());
        } finally {
            setMarketState(nextMarketState);
        }
        return tradeResponse;
    }

    @Override
    public MoveResponse moveMakerOrder(FplayOrder fplayOrder, BigDecimal newPrice) {
        final LimitOrder limitOrder = (LimitOrder) fplayOrder.getOrder();
        final SignalType signalType = fplayOrder.getSignalType() != null ? fplayOrder.getSignalType() : getArbitrageService().getSignalType();
        MoveResponse moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "do nothing by default");

        if (fplayOrder.getPlacingType() != null && fplayOrder.getPlacingType() == PlacingType.TAKER) {
            return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "no moving. Order was placed as taker.");
        }

        try {
            BigDecimal bestMakerPrice = newPrice.setScale(1, BigDecimal.ROUND_HALF_UP);

            assert bestMakerPrice.signum() != 0;
            assert bestMakerPrice.compareTo(limitOrder.getLimitPrice()) != 0;

            final LimitOrder movedLimitOrder = ((BitmexTradeService) exchange.getTradeService())
                    .moveLimitOrder(limitOrder, bestMakerPrice);

            if (movedLimitOrder != null) {

                orderRepositoryService.updateOrder(fplayOrder, movedLimitOrder);
                FplayOrder updated = FplayOrderUtils.updateFplayOrder(fplayOrder, movedLimitOrder);

                String diffWithSignal = setQuotesForArbLogs(limitOrder, signalType, bestMakerPrice);

                final String logString = String.format("%s Moved %s from %s to %s(real %s) status=%s, amount=%s, filled=%s, avgPrice=%s, id=%s. %s. position=%s",
                        getCounterName(),
                        limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                        limitOrder.getLimitPrice(),
                        bestMakerPrice.toPlainString(),
                        movedLimitOrder.getLimitPrice(),
                        movedLimitOrder.getStatus(),
                        limitOrder.getTradableAmount(),
                        limitOrder.getCumulativeAmount(),
                        limitOrder.getAveragePrice(),
                        limitOrder.getId(),
                        diffWithSignal,
                        getPositionAsString());
                logger.info(logString);
                tradeLogger.info(logString);
                ordersLogger.info(logString);

                if (movedLimitOrder.getStatus() == Order.OrderStatus.CANCELED) {
                    moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.ONLY_CANCEL, logString, null, null, updated);
                } else {
                    moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.MOVED, logString, movedLimitOrder, updated);
                }

            } else {
                logger.info("Moving response is null");
                tradeLogger.info("Moving response is null");
            }

        } catch (HttpStatusIOException e) {

            HttpStatusIOExceptionHandler handler = new HttpStatusIOExceptionHandler(e, "MoveOrderError", movingErrorsOverloaded.get()).invoke();
            moveResponse = handler.getMoveResponse();
            // double check  "Invalid ordStatus"
            if (moveResponse.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.ALREADY_CLOSED) {
                final Optional<Order> orderInfo = getOrderInfo(limitOrder.getId(), getCounterName(), 1, "Moving:CheckInvOrdStatus:");
                if (orderInfo.isPresent()) {
                    final Order doubleChecked = orderInfo.get();
                    if (doubleChecked.getStatus() == Order.OrderStatus.FILLED) {
                        // we confirmed that order ALREADY_CLOSED (FILLED)
                    } else if (doubleChecked.getStatus() == Order.OrderStatus.CANCELED) {
                        final FplayOrder updated = FplayOrderUtils.updateFplayOrder(fplayOrder, (LimitOrder) doubleChecked);
                        moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.ONLY_CANCEL, moveResponse.getDescription(), null, null, updated);
                    }
                } else {
                    moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, moveResponse.getDescription());
                }
            }


        } catch (Exception e) {

            final String message = e.getMessage();
            final String logString = String.format("%s/%s MovingError id=%s: %s", getCounterName(), movingErrorsOverloaded.get(), limitOrder.getId(), message);
            logger.error(logString, e);
            tradeLogger.error(logString);
            warningLogger.error(logString);

            // message.startsWith("Connection refused") - when we got banned for a week. Just skip it.
            // message.startsWith("Read timed out")
            if (message.startsWith("Network is unreachable")
                    || message.startsWith("connect timed out")) {
                tradeLogger.error("{} MoveOrderError: {}", getCounterName(), message);
                moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION_SYSTEM_OVERLOADED, message);
            }

        }

        return moveResponse;
    }

    private String setQuotesForArbLogs(LimitOrder limitOrder, SignalType signalType, BigDecimal bestMakerPrice) {
        String diffWithSignal = "";
        BestQuotes bestQuotes = orderIdToSignalInfo.get(limitOrder.getId());
        if (bestQuotes != null) {
            final BigDecimal diff1 = bestQuotes.getAsk1_p().subtract(bestMakerPrice).setScale(1, BigDecimal.ROUND_HALF_UP);
            final BigDecimal diff2 = bestMakerPrice.subtract(bestQuotes.getBid1_p()).setScale(1, BigDecimal.ROUND_HALF_UP);
            diffWithSignal = limitOrder.getType().equals(Order.OrderType.BID)
                    ? String.format("diff1_buy_p = ask_p[1] - order_price_buy_p = %s", diff1.toPlainString()) //"BUY"
                    : String.format("diff2_sell_p = order_price_sell_p - bid_p[1] = %s", diff2.toPlainString()); //"SELL"

            arbitrageService.getOpenDiffs().setFirstOpenPrice(limitOrder.getType().equals(Order.OrderType.BID)
                    ? diff1 : diff2);
        }
        if (signalType == SignalType.AUTOMATIC) {
            arbitrageService.getOpenPrices().setFirstOpenPrice(bestMakerPrice);
        }
        return diffWithSignal;
    }

    @Override
    public TradeService getTradeService() {
        return exchange.getTradeService();
    }

    private void startAccountInfoListener() {
        Observable<AccountInfoContracts> accountInfoObservable = ((BitmexStreamingAccountService) exchange.getStreamingAccountService())
                .getAccountInfoContractsObservable()
                .doOnDispose(() -> logger.info("bitmex subscription doOnDispose"))
                .doOnTerminate(() -> logger.info("bitmex subscription doOnTerminate"))
                .share();

        accountInfoSubscription = accountInfoObservable
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> logger.error("Account fetch error", throwable))
                .subscribe(newInfo -> {

                    synchronized (this) {
                        accountInfoContracts = new AccountInfoContracts(
                                newInfo.getWallet() != null ? newInfo.getWallet() : accountInfoContracts.getWallet(),
                                newInfo.getAvailable() != null ? newInfo.getAvailable() : accountInfoContracts.getAvailable(),
                                newInfo.geteMark() != null ? newInfo.geteMark() : accountInfoContracts.geteMark(),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                newInfo.getMargin() != null ? newInfo.getMargin() : accountInfoContracts.getMargin(),
                                newInfo.getUpl() != null ? newInfo.getUpl() : accountInfoContracts.getUpl(),
                                newInfo.getRpl() != null ? newInfo.getRpl() : accountInfoContracts.getRpl(),
                                newInfo.getRiskRate() != null ? newInfo.getRiskRate() : accountInfoContracts.getRiskRate()
                        );
                    }

                }, throwable -> {
                    logger.error("Can not fetchAccountInfo", throwable);
                    // schedule it again
                    sleep(5000);
//                    startAccountInfoListener();
                    checkForRestart();
                });
    }

    private void startPositionListener() {
        Observable<Position> positionObservable = ((BitmexStreamingAccountService) exchange.getStreamingAccountService())
                .getPositionObservable()
                .doOnError(throwable -> logger.error("Position fetch error", throwable));

        positionSubscription = positionObservable
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> logger.error("Position fetch error", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribe(pUpdate -> {
                    mergePosition(pUpdate);

                    recalcAffordableContracts();
                    recalcLiqInfo();
                }, throwable -> {
                    logger.error("Can not fetch Position", throwable);
                    // schedule it again
                    sleep(5000);
//                    startPositionListener();
                    checkForRestart();
                });
    }

    private void startFutureIndexListener() {
        Observable<BitmexContractIndex> indexObservable = ((BitmexStreamingMarketDataService) exchange.getStreamingMarketDataService())
                .getContractIndexObservable()
                .doOnError(throwable -> logger.error("Index fetch error", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS));

        futureIndexSubscription = indexObservable
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> logger.error("Index fetch error", throwable))
                .subscribe(contractIndex1 -> {
                    // merge contractIndex
                    final BigDecimal indexPrice = contractIndex1.getIndexPrice() != null
                            ? contractIndex1.getIndexPrice()
                            : contractIndex.getIndexPrice();
                    final BigDecimal fundingRate;
                    final OffsetDateTime fundingTimestamp;
                    if (contractIndex instanceof BitmexContractIndex) {
                        fundingRate = contractIndex1.getFundingRate() != null
                                ? contractIndex1.getFundingRate()
                                : ((BitmexContractIndex) contractIndex).getFundingRate();
                        fundingTimestamp = contractIndex1.getSwapTime() != null
                                ? contractIndex1.getSwapTime()
                                : ((BitmexContractIndex) contractIndex).getSwapTime();
                    } else {
                        fundingRate = contractIndex1.getFundingRate();
                        fundingTimestamp = contractIndex1.getSwapTime();
                    }
                    final Date timestamp = contractIndex1.getTimestamp();

                    this.contractIndex = new BitmexContractIndex(indexPrice, timestamp, fundingRate, fundingTimestamp);

                }, throwable -> {
                    logger.error("Can not fetch Position", throwable);
                    // schedule it again
                    sleep(5000);
                    //startFutureIndexListener();
                    checkForRestart();
                });
    }

    @Override
    public String getPositionAsString() {
        return position != null ? position.getPositionLong().toPlainString() : "0";
    }


    private synchronized void recalcLiqInfo() {
        final AccountInfoContracts accountInfoContracts = getAccountInfoContracts();

        final BigDecimal equity = accountInfoContracts.geteMark();
        final BigDecimal margin = accountInfoContracts.getMargin();

        final BigDecimal bMrliq = arbitrageService.getParams().getbMrLiq();

        final BigDecimal m = contractIndex.getIndexPrice();
        final BigDecimal L = position.getLiquidationPrice();

        if (equity != null && margin != null
                && m != null
                && L != null
                && position.getPositionLong() != null
                && position.getPositionShort() != null) {

            BigDecimal dql = null;

            String dqlString;
            if (position.getPositionLong().signum() > 0) {
                if (m.signum() > 0 && L.signum() > 0) {
                    dql = m.subtract(L);
                    dqlString = String.format("b_DQL = m%s - L%s = %s", m, L, dql);
                } else {
                    dqlString = "b_DQL = na";
                    warningLogger.info(String.format("Warning.All should be > 0: m=%s, L=%s",
                            m.toPlainString(), L.toPlainString()));
                }
            } else if (position.getPositionLong().signum() < 0) {
                if (m.signum() > 0 && L.signum() > 0) {
                    if (L.subtract(BigDecimal.valueOf(100000)).signum() < 0) {
                        dql = L.subtract(m);
                        dqlString = String.format("b_DQL = L%s - m%s = %s", L, m, dql);
                    } else {
                        dqlString = "b_DQL = na";
                    }
                } else {
                    dqlString = "b_DQL = na";
                    warningLogger.info(String.format("Warning.All should be > 0: m=%s, L=%s",
                            m.toPlainString(), L.toPlainString()));
                }
            } else {
                dqlString = "b_DQL = na";
            }

            BigDecimal dmrl = null;
            String dmrlString = null;
            if (margin.signum() > 0) {
                final BigDecimal bMr = equity.divide(margin, 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP);
                dmrl = bMr.subtract(bMrliq);
                dmrlString = String.format("b_DMRL = %s - %s = %s%%", bMr, bMrliq, dmrl);
            } else {
                dmrlString = "b_DMRL = na";
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

    @Override
    public boolean checkLiquidationEdge(Order.OrderType orderType) {
        final BigDecimal bDQLOpenMin = arbitrageService.getParams().getbDQLOpenMin();

        boolean isOk;
        if (liqInfo.getDqlCurr() == null) {
            isOk = true;
        } else {
            if (orderType.equals(Order.OrderType.BID)) { //LONG
                if (position.getPositionLong().signum() > 0) {
                    if (liqInfo.getDqlCurr().compareTo(bDQLOpenMin) != -1) {
                        isOk = true;
                    } else {
                        isOk = false;
                    }
                } else {
                    isOk = true;
                }
            } else if ((orderType.equals(Order.OrderType.ASK))) {
                if (position.getPositionLong().signum() < 0) {
                    if (liqInfo.getDqlCurr().compareTo(bDQLOpenMin) != -1) {
                        isOk = true;
                    } else {
                        isOk = false;
                    }
                } else {
                    isOk = true;
                }
            } else {
                throw new IllegalArgumentException("Wrong order type");
            }
        }

        debugLog.info(String.format("CheckLiqEdge:%s(p%s/%s/%s)", isOk,
                position.getPositionLong().subtract(position.getPositionShort()),
                liqInfo.getDqlCurr(),
                bDQLOpenMin));

        return isOk;
    }

    @Scheduled(fixedDelay = 5 * 1000) // 30 sec
    public void checkForDecreasePosition() {
        if (getMarketState() == MarketState.STOPPED) {
            return;
        }

        final BigDecimal bDQLCloseMin = arbitrageService.getParams().getbDQLCloseMin();

        if (liqInfo.getDqlCurr() != null
                && liqInfo.getDqlCurr().compareTo(BigDecimal.valueOf(-30)) > 0 // workaround when DQL is less zero
                && liqInfo.getDqlCurr().compareTo(bDQLCloseMin) < 0
                && position.getPositionLong().signum() != 0) {
            final BestQuotes bestQuotes = Utils.createBestQuotes(
                    arbitrageService.getSecondMarketService().getOrderBook(),
                    arbitrageService.getFirstMarketService().getOrderBook());

            if (position.getPositionLong().signum() > 0) {
                tradeLogger.info(String.format("%s B_PRE_LIQ starting: p%s/dql%s/dqlClose%s",
                        getCounterName(),
                        position.getPositionLong().toPlainString(),
                        liqInfo.getDqlCurr().toPlainString(), bDQLCloseMin.toPlainString()));

                arbitrageService.startTradingOnDelta1(SignalType.B_PRE_LIQ, bestQuotes);

            } else if (position.getPositionLong().signum() < 0) {
                tradeLogger.info(String.format("%s B_PRE_LIQ starting: p%s/dql%s/dqlClose%s",
                        getCounterName(),
                        position.getPositionLong().toPlainString(),
                        liqInfo.getDqlCurr().toPlainString(), bDQLCloseMin.toPlainString()));

                arbitrageService.startTradingOnDelta2(SignalType.B_PRE_LIQ, bestQuotes);

            }
        }
    }

    public BitmexSwapService getBitmexSwapService() {
        return bitmexSwapService;
    }

    public BigDecimal getFundingCost() {
        BigDecimal fundingCost = BigDecimal.ZERO;
        if (this.getContractIndex() instanceof BitmexContractIndex) {
            fundingCost = bitmexSwapService.calcFundingCost(this.getPosition(),
                    ((BitmexContractIndex) this.getContractIndex()).getFundingRate());
        }
        return fundingCost;
    }

    private class HttpStatusIOExceptionHandler {
        private MoveResponse moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "default");

        private HttpStatusIOException e;
        private String operationName;
        private int attemptCount;

        public HttpStatusIOExceptionHandler(HttpStatusIOException e, String operationName, int attemptCount) {
            this.e = e;
            this.operationName = operationName;
            this.attemptCount = attemptCount;
        }

        /**
         * ALREADY_CLOSED or EXCEPTION or EXCEPTION_SYSTEM_OVERLOADED
         */
        public MoveResponse getMoveResponse() {
            return moveResponse;
        }

        public HttpStatusIOExceptionHandler invoke() {
            try {

                final Map<String, List<String>> responseHeaders = e.getResponseHeaders();
                final List<String> rateLimitValues = responseHeaders.get("X-RateLimit-Remaining");
                final String rateLimitStr = (rateLimitValues != null && rateLimitValues.size() > 0)
                        ? String.format(" X-RateLimit-Remaining=%s ", rateLimitValues.get(0)) : " ";


                final String httpBody = e.getHttpBody();
                final String marketResponseMessage = new ObjectMapper().readValue(httpBody, Error.class).getError().getMessage();

                String fullMessage = String.format("%s/%s %s: %s %s", getCounterName(), attemptCount, operationName, httpBody, rateLimitStr);
                String shortMessage = String.format("%s/%s %s: %s %s", getCounterName(), attemptCount, operationName, marketResponseMessage, rateLimitStr);

                tradeLogger.error(shortMessage);

                if (marketResponseMessage.startsWith("The system is currently overloaded. Please try again later")) {
                    moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION_SYSTEM_OVERLOADED, marketResponseMessage);
                    logger.error(fullMessage);
                } else if (marketResponseMessage.startsWith("Invalid ordStatus")) {
                    moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, marketResponseMessage);
                    logger.error(fullMessage);
                } else {
                    moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, httpBody);
                    logger.error(fullMessage, e);
                }

            } catch (IOException e1) {
                logger.error("Error on handling HttpStatusIOException", e1);
            }

            return this;
        }
    }

}
