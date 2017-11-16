package com.bitplay.market.bitmex;

import com.bitplay.api.service.RestartService;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.BalanceService;
import com.bitplay.market.MarketService;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.SignalEvent;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.PersistenceService;
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
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private static final String NAME = "bitmex";
    private static final int MAX_ATTEMPTS = 1;
    private static final CurrencyPair CURRENCY_PAIR_XBTUSD = new CurrencyPair("XBT", "USD");

    private BitmexStreamingExchange exchange;

    private static final int MIN_SEC_TO_RESTART = 60 * 5; // 5 min
    private long listenersStartTimeEpochSecond = Instant.now().getEpochSecond();
    private volatile boolean isDestroyed = false;

    private static final int MOVING_TIMEOUT_SEC = 2;
    // Moving timeout
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile boolean movingInProgress = false;

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

//        Completable.timer(4000, TimeUnit.MILLISECONDS)
//                .doOnComplete(this::startOpenOrderMovingListener)
//                .subscribe();

        Completable.timer(5000, TimeUnit.MILLISECONDS)
                .doOnComplete(this::startFutureIndexListener)
                .subscribe();

        listenersStartTimeEpochSecond = Instant.now().getEpochSecond();
    }

    private synchronized void checkForRestart() {
        logger.info("checkForRestart " + getSubscribersStatuses());
        if (!isDestroyed) {
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
    public void fetchPosition() throws Exception {
        final BitmexAccountService accountService = (BitmexAccountService) exchange.getAccountService();
        final Position pUpdate = accountService.fetchPositionInfo();
        mergePosition(pUpdate);

        recalcAffordableContracts();
        recalcLiqInfo();
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


    @Scheduled(fixedRate = 30000)
    public void dobleCheckAvailableBalance() {
        if (accountInfoContracts == null) {
            tradeLogger.warn("WARNING: Bitmex Balance is null");
            warningLogger.warn("WARNING: Bitmex Balance is null");
            accountInfoSubscription.dispose();
            startAccountInfoListener();
        }
    }

/*
    @Scheduled(fixedRate = 60 * 1000)
    public void checkForWrongFlag() {
        if (!isBusy && openOrders.size() > 0) {
            openOrders.clear();
            tradeLogger.info("{}: clear openOrders, lock={}", getName());
            //, Thread.holdsLock(openOrdersLock));
//            iterateOpenOrdersMove();
        }
    }*/

    @Scheduled(fixedRate = 1000 * 60)
    public void checkForHangOrders() {
        try {
            fetchOpenOrders(); // Synchronous
        } catch (IllegalStateException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                checkForRestart();
            }
        }
        if (openOrders.size() == 0) {
            setFree();
        }
    }

    // Use Websocket API instead
    /*public void getFunding() {
        try {
            final BitmexTradeService tradeService = (BitmexTradeService) exchange.getTradeService();
            final List<Instrument> instrumentList = tradeService.getFunding();
            instrument = instrumentList.get(0);
        } catch (IOException e) {
            logger.error("Can not get funding", e);
        }
    }*/

    @Override
    protected void iterateOpenOrdersMove() {
        boolean haveToClear = false;
//        synchronized (openOrdersLock) {
        for (LimitOrder openOrder : openOrders) {
            if (openOrder.getType() != null) {
                final SignalType signalType = arbitrageService.getSignalType();
                final MoveResponse response = moveMakerOrderIfNotFirst(openOrder, signalType);
                if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.ALREADY_CLOSED) {
                    haveToClear = true;
                }
            }
//            }
        }

        if (haveToClear) {
            openOrders.clear();
            eventBus.send(BtsEvent.MARKET_FREE);
        }
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
                        //workaround
                        if (openOrders == null) {
                            openOrders = new ArrayList<>();
                            if (isBusy()) {
                                eventBus.send(BtsEvent.MARKET_FREE);
                            }
                        }

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
                    synchronized (openOrdersLock) {
                        logger.debug("OpenOrders: " + updateOfOpenOrders.toString());
                        this.openOrders = updateOfOpenOrders.getOpenOrders().stream()
                                .flatMap(update -> {

                                    // merge the orders
                                    Optional<LimitOrder> mergedOrder = Optional.of(update);
                                    final Optional<LimitOrder> optionalExisting = this.openOrders.stream()
                                            .filter(existing -> update.getId().equals(existing.getId()))
                                            .findFirst();
                                    if (optionalExisting.isPresent()) {
                                        final LimitOrder existing = optionalExisting.get();
                                        mergedOrder = Optional.of(new LimitOrder(
                                                existing.getType(),
                                                update.getTradableAmount() != null ? update.getTradableAmount() : existing.getTradableAmount(),
                                                existing.getCurrencyPair(),
                                                existing.getId(),
                                                update.getTimestamp(),
                                                update.getLimitPrice() != null ? update.getLimitPrice() : existing.getLimitPrice(),
                                                update.getAveragePrice() != null ? update.getAveragePrice() : existing.getAveragePrice(),
                                                update.getCumulativeAmount() != null ? update.getCumulativeAmount() : existing.getCumulativeAmount(),
                                                update.getStatus() != null ? update.getStatus() : existing.getStatus()
                                        ));
                                    }
//                                    tradeLogger.info("Order id={} status={}", mergedOrder.get().getId(), mergedOrder.get().getStatus());
                                    final LimitOrder mergedOrderObj = mergedOrder.get();
                                    if (mergedOrderObj.getStatus() != null
                                            && mergedOrderObj.getStatus().equals(Order.OrderStatus.FILLED)) { // End orders right away step1
                                        // THere are no updates of FILLED orders
                                        tradeLogger.info("{} Order {} FILLED", getCounterName(), mergedOrderObj.getId());
                                        mergedOrder = Optional.empty();
                                    }

                                    return mergedOrder
                                            .map(Stream::of)
                                            .orElseGet(Stream::empty);

                                }).collect(Collectors.toList());
                        if (this.openOrders == null) {
                            this.openOrders = new ArrayList<>();
                        }
                        if (openOrders.size() == 0) { // End orders right away step2
                            setFree();
                        }
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
        return placeOrder(orderType, amountInContracts, bestQuotes, BitmexOrderType.MAKER, signalType);
    }

    public TradeResponse takerOrder(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes, SignalType signalType) {
        return placeOrder(orderType, amountInContracts, bestQuotes, BitmexOrderType.TAKER, signalType);
    }

    private TradeResponse placeOrder(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes,
                                     BitmexOrderType bitmexOrderType, SignalType signalType) {
        final TradeResponse tradeResponse = new TradeResponse();
        try {
            arbitrageService.setSignalType(signalType);
            eventBus.send(BtsEvent.MARKET_BUSY);

            final TradeService tradeService = exchange.getTradeService();
            BigDecimal thePrice = BigDecimal.ZERO;

            String orderId = null;
            int attemptCount = 0;
            while (attemptCount < MAX_ATTEMPTS) {
                attemptCount++;
                try {
                    if (bitmexOrderType == BitmexOrderType.MAKER) {

                        thePrice = createBestMakerPrice(orderType)
                                .setScale(1, BigDecimal.ROUND_HALF_UP);

                        final LimitOrder limitOrder = new LimitOrder(orderType,
                                amount, CURRENCY_PAIR_XBTUSD, "0", new Date(),
                                thePrice);
                        orderId = tradeService.placeLimitOrder(limitOrder);
                    } else {
                        final MarketOrder marketOrder = new MarketOrder(orderType, amount, CURRENCY_PAIR_XBTUSD, new Date());
                        orderId = getTradeService().placeMarketOrder(marketOrder);
                    }

                    break;
                } catch (Exception e) {
                    logger.error("Error on placeLimitOrder", e);
                    final String message = (e instanceof HttpStatusIOException)
                            ? e.getMessage() + ((HttpStatusIOException) e).getHttpBody()
                            : e.getMessage();

                    final String logString = String.format("%s maker error attempt=%s: %s",
                            getCounterName(),
                            attemptCount,
                            message);
                    if (e.getMessage().startsWith("Read timed out") || e.getMessage().startsWith("Network is unreachable")) {
                        attemptCount = MAX_ATTEMPTS;
                    }

//                    if (attemptCount == MAX_ATTEMPTS) {
                        logger.error(logString, e);
                        tradeLogger.error("Warning placing: " + logString);
                        warningLogger.error("bitmex placing. Warning: " + logString);
//                    } else {
//                        logger.error(logString, e);
//                        tradeLogger.error(logString);
//                    }

                    tradeResponse.setOrderId(message);
                    tradeResponse.setErrorCode(message);
                }
            }
            if (orderId != null) {

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

                tradeLogger.info("{} {} {} amount={} with quote={} was placed.orderId={}. {}. position={}",
                        getCounterName(),
                        bitmexOrderType.toString(),
                        orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                        amount.toPlainString(),
                        thePrice,
                        orderId,
                        diffWithSignal,
                        getPositionAsString());

                if (signalType == SignalType.AUTOMATIC) {
                    arbitrageService.getOpenPrices().setFirstOpenPrice(thePrice);
                }

            }

        } catch (Exception e) {
            logger.error("Place market order error", e);
            tradeLogger.info("maker error {}", e.toString());
            tradeResponse.setOrderId(e.getMessage());
            tradeResponse.setErrorCode(e.getMessage());
        }
        return tradeResponse;
    }

    @Override
    public MoveResponse moveMakerOrder(LimitOrder limitOrder, SignalType signalType) {
        if (movingInProgress) {
            final String logString = String.format("%s No moving. Too often requests. id=%s", getCounterName(), limitOrder.getId());
            logger.error(logString);
            tradeLogger.error(logString);
            return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "No moving to often requests");
        } else {
            movingInProgress = true;
            scheduler.schedule(() -> movingInProgress = false, MOVING_TIMEOUT_SEC, TimeUnit.SECONDS);
        }

        MoveResponse moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "default");
        int attemptCount = 0;
        String lastExceptionMsg = "";
        BigDecimal bestMakerPrice = BigDecimal.ZERO;
        BestQuotes bestQuotes = orderIdToSignalInfo.get(limitOrder.getId());

        while (attemptCount < MAX_ATTEMPTS) {
            attemptCount++;
            try {
                bestMakerPrice = createBestMakerPrice(limitOrder.getType())
                        .setScale(1, BigDecimal.ROUND_HALF_UP);
                final BitmexTradeService tradeService = (BitmexTradeService) exchange.getTradeService();
                final String order = tradeService.moveLimitOrder(limitOrder, bestMakerPrice);

                if (order != null) {
                    moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.MOVED, "");
                    break;
                }
            } catch (HttpStatusIOException e) {

                final String httpBody = e.getHttpBody();
                lastExceptionMsg = httpBody;
                ObjectMapper objectMapper = new ObjectMapper();

                try {
                    final Error error = objectMapper.readValue(httpBody, Error.class);
                    if (error.getError().getMessage().startsWith("Invalid ordStatus")) {
                        logger.error("MoveException " + httpBody);
                    } else {
                        logger.error("MoveException " + httpBody, e);
                    }
                    tradeLogger.error("{} MoveException: {}", getCounterName(), error.getError().getMessage());

                    if (error.getError().getMessage().startsWith("Invalid ordStatus")) {
                        moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, "");
                        // add flag
                        arbitrageService.getFlagOpenOrder().setFirstReady(true);
                        break;
                    }
                } catch (IOException e1) {
                    logger.error("On parse error", e1);
                }

            } catch (Exception e) {
                lastExceptionMsg = e.getMessage();
                final String logString = String.format("%s MovingError id=%s, attempt=%s: %s",
                        getCounterName(),
                        limitOrder.getId(),
                        attemptCount,
                        e.getMessage());
                if (e.getMessage().startsWith("Read timed out") || e.getMessage().startsWith("Network is unreachable")) {
                    attemptCount = MAX_ATTEMPTS;
                }

                logger.error(logString, e);
//                if (attemptCount == MAX_ATTEMPTS) {
                    tradeLogger.error("Warning: " + logString);
                    warningLogger.error("bitmex. Warning: " + logString);
//                } else {
//                    tradeLogger.error(logString);
//                }
            }
        }

        if (moveResponse != null && moveResponse.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.MOVED) {
            String diffWithSignal = "";
            if (bestQuotes != null) {
                final BigDecimal diff1 = bestQuotes.getAsk1_p().subtract(bestMakerPrice).setScale(1, BigDecimal.ROUND_HALF_UP);
                final BigDecimal diff2 = bestMakerPrice.subtract(bestQuotes.getBid1_p()).setScale(1, BigDecimal.ROUND_HALF_UP);
                diffWithSignal = limitOrder.getType().equals(Order.OrderType.BID)
                        ? String.format("diff1_buy_p = ask_p[1] - order_price_buy_p = %s", diff1.toPlainString()) //"BUY"
                        : String.format("diff2_sell_p = order_price_sell_p - bid_p[1] = %s", diff2.toPlainString()); //"SELL"
                arbitrageService.getOpenDiffs().setFirstOpenPrice(limitOrder.getType().equals(Order.OrderType.BID)
                        ? diff1 : diff2);
            }

            final String logString = String.format("%s Moved %s amount=%s, filled=%s,newQuote=%s,avgPrice=%s,id=%s,attempt=%s. %s. position=%s",
                    getCounterName(),
                    limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                    limitOrder.getTradableAmount(),
                    limitOrder.getCumulativeAmount(),
                    bestMakerPrice.toPlainString(),
                    limitOrder.getAveragePrice(),
                    limitOrder.getId(),
                    attemptCount,
                    diffWithSignal,
                    getPositionAsString());

            if (signalType == SignalType.AUTOMATIC) {
                arbitrageService.getOpenPrices().setFirstOpenPrice(bestMakerPrice);
            }

            tradeLogger.info(logString);
            moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.MOVED, logString);
            movingInProgress = false;
        } else if (moveResponse == null || moveResponse.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.EXCEPTION) {
            final String logString = String.format("%s Moving error %s amount=%s,oldQuote=%s,id=%s,attempt=%s(%s)",
                    getCounterName(),
                    limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                    limitOrder.getTradableAmount().setScale(1, BigDecimal.ROUND_HALF_UP),
                    limitOrder.getLimitPrice().toPlainString(),
                    limitOrder.getId(),
                    attemptCount,
                    lastExceptionMsg);
            tradeLogger.info(logString);
            sleep(200);
            moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, logString);
        }
        return moveResponse;
    }

    @Override
    public TradeService getTradeService() {
        return exchange.getTradeService();
    }

    enum BitmexOrderType {
        MAKER,
        TAKER
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
                                BigDecimal.ZERO,
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

}
