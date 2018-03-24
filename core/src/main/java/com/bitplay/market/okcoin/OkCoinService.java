package com.bitplay.market.okcoin;

import com.bitplay.api.service.RestartService;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.arbitrage.dto.AvgPrice;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.DealPrices;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.BalanceService;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketState;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.SignalEvent;
import com.bitplay.market.model.Affordable;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.OrderRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.FplayOrderUtils;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.SysOverloadArgs;
import com.bitplay.utils.Utils;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.net.SocketTimeoutException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PreDestroy;
import javax.validation.constraints.NotNull;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.swagger.client.model.Error;
import si.mazi.rescu.HttpStatusIOException;

/**
 * Created by Sergey Shurmin on 3/21/17.
 */
@Service("okcoin")
public class OkCoinService extends MarketService {

    public static final String TAKER_WAS_CANCELLED_MESSAGE = "Taker wasn't filled. Cancelled";
    private static final Logger logger = LoggerFactory.getLogger(OkCoinService.class);
    private static final Logger tradeLogger = LoggerFactory.getLogger("OKCOIN_TRADE_LOG");
    private static final Logger ordersLogger = LoggerFactory.getLogger("OKCOIN_ORDERS_LOG");

    private final static CurrencyPair CURRENCY_PAIR_BTC_USD = new CurrencyPair("BTC", "USD");
    private final static String NAME = "okcoin";
    ArbitrageService arbitrageService;

    private volatile AtomicReference<PlaceOrderArgs> placeOrderArgsRef = new AtomicReference<>();

    private static final int MAX_ATTEMPTS_STATUS = 50;
    private static final int MAX_ATTEMPTS_CANCEL = 10;
    private static final int MAX_ATTEMPTS_FOR_MOVING = 2;
    private static final int MAX_MOVING_TIMEOUT_SEC = 2;
    private static final int MAX_MOVING_OVERLOAD_ATTEMPTS_TIMEOUT_SEC = 60;
    // Moving timeout
    private volatile ScheduledFuture<?> scheduledMovingErrorsReset;
    private volatile boolean movingInProgress = false;
    private volatile AtomicInteger movingErrorsOverloaded = new AtomicInteger(0);

    private volatile String ifDisconnetedString = "";

    @Autowired
    private OkcoinBalanceService okcoinBalanceService;
    @Autowired
    private PosDiffService posDiffService;
    @Autowired
    private PersistenceService persistenceService;
    @Autowired
    private SettingsRepositoryService settingsRepositoryService;
    @Autowired
    private OrderRepositoryService orderRepositoryService;
    @Autowired
    private RestartService restartService;

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

    @Override
    public BalanceService getBalanceService() {
        return okcoinBalanceService;
    }

    @Autowired
    public void setArbitrageService(ArbitrageService arbitrageService) {
        this.arbitrageService = arbitrageService;
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
    protected Exchange getExchange() {
        return exchange;
    }

    @Override
    public void initializeMarket(String key, String secret) {
        this.usdInContract = 100;
        exchange = initExchange(key, secret);
        loadLiqParams();

        initWebSocketAndAllSubscribers();
        initDeferedPlacingOrder();
    }

    private void initWebSocketAndAllSubscribers() {
        initWebSocketConnection();

        try {
            // Workaround for deadlock. Init settings from DB.
            final Settings settings = settingsRepositoryService.getSettings();
            logger.trace(settings.getPlacingBlocks().toString());

            fetchPosition();
        } catch (Exception e) {
            logger.error("FetchPositionError", e);
        }

        createOrderBookObservable();
        subscribeOnOrderBook();

        privateDataSubscription = startPrivateDataListener();
        accountInfoSubscription = startAccountInfoSubscription();
        futureIndexSubscription = startFutureIndexListener();

        fetchOpenOrders();
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
        spec.setExchangeSpecificParametersItem("Futures_Leverage", "20");

        return (OkExStreamingExchange) ExchangeFactory.INSTANCE.createExchange(spec);
    }

    private void initWebSocketConnection() {
        // Connect to the Exchange WebSocket API. Blocking wait for the connection.
        exchange.connect().blockingAwait();

        // Retry on disconnect. (It's disconneced each 5 min)
        exchange.onDisconnect().doOnComplete(() -> {
            ifDisconnetedString += " okex disconnected at " + LocalTime.now();
            logger.warn("onClientDisconnect okCoinService");
            initWebSocketAndAllSubscribers();
        }).subscribe();
    }

    public String getIfDisconnetedString() {
        return ifDisconnetedString;
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

    @Scheduled(initialDelay = 5 * 1000, fixedRate = 1000)
    public void requestAccountInfo() {
        try {
            exchange.getStreamingAccountInfoService().requestAccountInfo();
        } catch (NotConnectedException e) {
            logger.error("AccountInfo request error: NotConnectedException", e);
            closeAllSubscibers()
                    .doOnComplete(this::initWebSocketAndAllSubscribers)
                    .subscribe(() -> logger.warn("Closing okcoin subscribers was done"),
                            throwable -> {
                                logger.error("ERROR on Closing okcoin subscribers", throwable);
                                final String TOO_MANY_OPEN_FILES = "Too many open files";
                                if (throwable.getCause().getMessage().equals(TOO_MANY_OPEN_FILES)
                                        || throwable.getCause().getCause().getMessage().equals(TOO_MANY_OPEN_FILES)) {
                                    restartService.doFullRestart(TOO_MANY_OPEN_FILES);
                                }
                            });

        } catch (IOException e) {
            logger.error("AccountInfo request error", e);
        }
    }

    @Scheduled(fixedRate = 1000)
    @Override
    public String fetchPosition() throws Exception {
        final OkCoinPositionResult positionResult = ((OkCoinTradeServiceRaw) exchange.getTradeService()).getFuturesPosition("btc_usd", FuturesContract.ThisWeek);
        mergePosition(positionResult, null);

        recalcAffordableContracts();
        recalcLiqInfo();
        return position != null ? position.toString() : "";
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

    private Disposable startAccountInfoSubscription() {
        return exchange.getStreamingAccountInfoService()
                .accountInfoObservable()
                .doOnError(throwable -> logger.error("Error on AccountInfo.Websocket observing", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(newInfo -> {
                    logger.debug("AccountInfo.Websocket: " + accountInfoContracts.toString());

                    synchronized (this) {
                        accountInfoContracts = new AccountInfoContracts(
                                newInfo.getWallet() != null ? newInfo.getWallet() : accountInfoContracts.getWallet(),
                                newInfo.getAvailable() != null ? newInfo.getAvailable() : accountInfoContracts.getAvailable(),
                                BigDecimal.ZERO,
                                newInfo.geteLast() != null ? newInfo.geteLast() : accountInfoContracts.geteLast(),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                newInfo.getMargin() != null ? newInfo.getMargin() : accountInfoContracts.getMargin(),
                                newInfo.getUpl() != null ? newInfo.getUpl() : accountInfoContracts.getUpl(),
                                newInfo.getRpl() != null ? newInfo.getRpl() : accountInfoContracts.getRpl(),
                                newInfo.getRiskRate() != null ? newInfo.getRiskRate() : accountInfoContracts.getRiskRate()
                        );
                    }

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
                        recalcLiqInfo();
                    }
                    if (privateData.getTrades() != null && privateData.getTrades().size() > 0) {

                        synchronized (openOrdersLock) {
                            // do not repeat for already 'FILLED' orders.
                            privateData.getTrades().stream()
                                    .filter(orderInfo -> openOrders.stream()
                                            .filter(o -> o.getOrderId().equals(orderInfo.getId())).noneMatch(o -> o.getOrder().getStatus() == Order.OrderStatus.FILLED))
                                    .forEach(orderInfo -> {
                                        arbitrageService.getDealPrices().getoPriceFact().addPriceItem(orderInfo.getId(), orderInfo.getCumulativeAmount(), orderInfo.getAveragePrice());
                                        writeAvgPriceLog();
                                    });
                        }

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

    @Scheduled(fixedDelay = 2000)
    public void openOrdersCleaner() {
        if (openOrders.size() > 0) {
            cleanOldOO();
        }
    }

    @Scheduled(fixedDelay = 20000)
    public void openOrdersHangedChecker() {
        try {
            updateOOStatuses();
        } catch (Exception e) {
            logger.error("Exception on openOrdersHangedChecker", e);
        }
    }

    public TradeResponse takerOrder(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, SignalType signalType)
            throws Exception {

        TradeResponse tradeResponse = new TradeResponse();

        final TradeService tradeService = exchange.getTradeService();

        orderType = adjustOrderType(orderType, amount);

        final MarketState savedState = getMarketState();
        setMarketState(MarketState.PLACING_ORDER);
        arbitrageService.setSignalType(signalType);

        try {
            synchronized (openOrdersLock) {

                // Option 1: REAL TAKER
//            final MarketOrder marketOrder = new MarketOrder(orderType, amount, CURRENCY_PAIR_BTC_USD, new Date());
//            final String orderId = tradeService.placeMarketOrder(marketOrder);

                // Option 2: FAKE LIMIT ORDER
                BigDecimal thePrice = Utils.createPriceForTaker(getOrderBook(), orderType);
                getTradeLogger().info("The fake taker price is " + thePrice.toPlainString());
                final LimitOrder limitOrder = new LimitOrder(orderType, amount, CURRENCY_PAIR_BTC_USD, "123", new Date(), thePrice);
                String orderId = tradeService.placeLimitOrder(limitOrder);

                final String counterName = getCounterName();

                final Optional<Order> orderInfoAttempts = getOrderInfoAttempts(orderId, counterName, "Taker:Status:");
                if (!orderInfoAttempts.isPresent()) {
                    throw new Exception("Failed to get info of just created order. id=" + orderId);
                }

                Order orderInfo = orderInfoAttempts.get();
                updateOpenOrders(Collections.singletonList((LimitOrder) orderInfo));

                arbitrageService.getDealPrices().setSecondOpenPrice(orderInfo.getAveragePrice());
                arbitrageService.getDealPrices().getoPriceFact().addPriceItem(orderInfo.getId(), orderInfo.getCumulativeAmount(), orderInfo.getAveragePrice());

                if (orderInfo.getStatus() != Order.OrderStatus.FILLED) { // 1. Try cancel then
                    final OkCoinTradeResult okCoinTradeResult = cancelOrderSync(orderId, "Taker:Cancel_maker:");

                    if (!okCoinTradeResult.isResult()) throw new Exception("Failed to cancel taker-maker id=" + orderId);

                    orderInfo = getFinalOrderInfoSync(orderId, counterName, "Taker:Cancel_makerStatus:");

                    if (orderInfo == null) throw new Exception("Failed to check status of cancelled taker-maker id=" + orderId);

                    updateOpenOrders(Collections.singletonList((LimitOrder) orderInfo));
                }

                if (orderInfo.getStatus() != Order.OrderStatus.FILLED) { // 2. It is CANCELED
                    tradeResponse.setErrorCode(TAKER_WAS_CANCELLED_MESSAGE);
                    tradeResponse.addCancelledOrder((LimitOrder) orderInfo);
                } else { //FILLED by any (orderInfo or cancelledOrder)

                    writeLogPlaceOrder(orderType, amount, "taker",
                            orderInfo.getAveragePrice(), orderId, orderInfo.getStatus().toString());

                    tradeResponse.setOrderId(orderId);
                }
            }
        } finally {
            setMarketState(savedState);
            if (tradeResponse.getOrderId() != null) {
                setFree();
            }
        }

        return tradeResponse;
    }


    private synchronized void recalcAffordableContracts() {
        final BigDecimal reserveBtc = arbitrageService.getParams().getReserveBtc2();
        final BigDecimal volPlan = settingsRepositoryService.getSettings().getPlacingBlocks().getFixedBlockOkex();
//        final BigDecimal volPlan = arbitrageService.getParams().getBlock2();

        if (accountInfoContracts != null && position != null && orderBook != null && orderBook.getBids().size() > 0) {
            final BigDecimal availableBtc = accountInfoContracts.getAvailable();
            final BigDecimal equityBtc = accountInfoContracts.geteLast();

            final BigDecimal bestAsk = Utils.getBestAsks(orderBook, 1).get(0).getLimitPrice();
            final BigDecimal bestBid = Utils.getBestBids(orderBook, 1).get(0).getLimitPrice();
            final BigDecimal leverage = position.getLeverage();

            if (availableBtc != null && equityBtc != null && leverage != null && position.getPositionLong() != null && position.getPositionShort() != null) {

//                if (availableBtc.signum() > 0) {
//                if (orderType.equals(Order.OrderType.BID) || orderType.equals(Order.OrderType.EXIT_ASK)) {
                    BigDecimal affordableContractsForLong;
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
                    affordable.setForLong(affordableContractsForLong);
//                }

//                if (orderType.equals(Order.OrderType.ASK) || orderType.equals(Order.OrderType.EXIT_BID)) {
                    BigDecimal affordableContractsForShort;
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
                    affordable.setForShort(affordableContractsForShort);
//                }
//                }
            }
        }
    }

    @Override
    public Affordable recalcAffordable() {
        recalcAffordableContracts();
        return affordable;
    }

    @Override
    public boolean isAffordable(Order.OrderType orderType, BigDecimal tradableAmount) {
        boolean isAffordable;
        final BigDecimal affordableVol = (orderType == Order.OrderType.BID || orderType == Order.OrderType.EXIT_ASK)
                ? this.affordable.getForLong() : this.affordable.getForShort();
        isAffordable = affordableVol.compareTo(tradableAmount) != -1;

        return isAffordable;
    }

    private void initDeferedPlacingOrder() {
        getArbitrageService().getSignalEventBus().toObserverable()
                .subscribe(signalEvent -> {
                    try {
                        if (signalEvent == SignalEvent.MT2_BITMEX_ORDER_FILLED) {
                            final Settings settings = settingsRepositoryService.getSettings();
                            if (settings.getArbScheme() == ArbScheme.CON_B_O
                                    && getMarketState() == MarketState.WAITING_ARB) {

                                final PlaceOrderArgs currArgs = placeOrderArgsRef.getAndSet(null);
                                if (currArgs != null) {
                                    setMarketState(MarketState.ARBITRAGE);
                                    tradeLogger.info(String.format("#%s MT2 start placing ", currArgs));

                                    placeOrder(currArgs);
                                }

                            }
                        }
                    } catch (Exception e) {
                        logger.error("{} deferedPlacingOrder error", getName(), e);
                    }
                }, throwable -> logger.error("{} deferedPlacingOrder error", getName(), throwable));
    }

    @Override
    public TradeResponse placeOrder(PlaceOrderArgs placeOrderArgs) {
        TradeResponse tradeResponse = new TradeResponse();

        final Integer maxAttempts = settingsRepositoryService.getSettings().getOkexSysOverloadArgs().getPlaceAttempts();

        final Order.OrderType orderType = placeOrderArgs.getOrderType();
        final BigDecimal amount = placeOrderArgs.getAmount();
        final BestQuotes bestQuotes = placeOrderArgs.getBestQuotes();
        final SignalType signalType = placeOrderArgs.getSignalType();

        BigDecimal amountLeft = amount;
        int attemptCount = 0;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                attemptCount++;
                if (attemptCount > 1) {
                    Thread.sleep(200 * attemptCount);
                }

                final PlacingType okexPlacingType = persistenceService.getSettingsRepositoryService().getSettings().getOkexPlacingType();

                if (okexPlacingType == PlacingType.MAKER || okexPlacingType == PlacingType.HYBRID) {
                    tradeResponse = placeMakerOrderWithStatus(orderType, amountLeft, bestQuotes, false, signalType, okexPlacingType);
                } else if (okexPlacingType == PlacingType.TAKER) {
                    tradeResponse = takerOrder(orderType, amountLeft, bestQuotes, signalType);
                    if (tradeResponse.getErrorCode() != null && tradeResponse.getErrorCode().equals(TAKER_WAS_CANCELLED_MESSAGE)) {
                        final BigDecimal filled = tradeResponse.getCancelledOrders().get(0).getCumulativeAmount();
                        amountLeft = amountLeft.subtract(filled);
                        continue;
                    }
                } else {
                    throw new IllegalStateException("unhandled placing type");
                }
                break;
            } catch (HttpStatusIOException e) {
                final String httpBody = e.getHttpBody();
                tradeResponse.setOrderId(httpBody);
                tradeResponse.setErrorCode(httpBody);

                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    final Error error = objectMapper.readValue(httpBody, Error.class);
                    final String marketResponseMessage = error.getError().getMessage();

                    if (marketResponseMessage.contains("UnknownHostException: www.okex.com")) {
                        if (attemptCount < maxAttempts) {
                            final String logString = String.format("%s/%s placeOrderOnSignal error: %s.",
                                    getCounterName(), attemptCount, httpBody);
                            tradeLogger.error(logString);
                            logger.error(logString);
                            continue; // retry!
                        } else {
                            setOverloaded(null);
                            final String logString = String.format("%s/%s placeOrderOnSignal error: %s. Set SYSTEM_OVERLOAD for %s sec",
                                    getCounterName(), attemptCount, httpBody,
                                    settingsRepositoryService.getSettings().getOkexSysOverloadArgs().getOverloadTimeSec());
                            tradeLogger.error(logString);
                            logger.error(logString);
                        }
                    }
                } catch (IOException e1) {
                    final String errMsg = String.format("On parse error:%s, %s", e.toString(), e.getHttpBody());
                    logger.error(errMsg, e1);
                    tradeLogger.error(errMsg);
                }

                break; // no retry by default
            } catch (SocketTimeoutException e) { // java.net.SocketTimeoutException: connect timed out
                final String message = e.getMessage();
                tradeResponse.setOrderId(message);
                tradeResponse.setErrorCode(message);

                if (attemptCount < maxAttempts) {
                    final String logString = String.format("%s/%s placeOrderOnSignal error: %s.",
                            getCounterName(), attemptCount, message);
                    tradeLogger.error(logString);
                    logger.error(logString);
                    continue;
                } else { // message.contains("connect timed out")
                    final String logString = String.format("%s/%s placeOrderOnSignal error: %s. Set SYSTEM_OVERLOAD for %s sec",
                            getCounterName(), attemptCount, message,
                            settingsRepositoryService.getSettings().getOkexSysOverloadArgs().getOverloadTimeSec());
                    tradeLogger.error(logString);
                    logger.error(logString);
                    setOverloaded(null);
                }

                break; // no retry by default
            } catch (Exception e) {
                String message = e.getMessage();

                String details = String.format("%s/%s placeOrderOnSignal error. type=%s,a=%s,bestQuotes=%s,isMove=%s,signalT=%s. %s",
                        getCounterName(), attemptCount,
                        orderType, amountLeft, bestQuotes, false, signalType, message);
                logger.error(details, e);
                details = details.length() < 300 ? details : details.substring(0, 300); // we can get html page as error message
                tradeLogger.error(details);

                tradeResponse.setOrderId(message);
                tradeResponse.setErrorCode(message);

                if (message.contains("Close amount bigger than your open positions")) {
                    try {
                        fetchPosition();
                    } catch (Exception e1) {
                        logger.info("FetchPositionError:", e1);
                    }
                    continue;
                }

                break; // no retry by default
            }
        }

        if (placeOrderArgs.getSignalType().isCorr()) { // It's only TAKER, so it should be DONE, if no errors
            if (tradeResponse.getErrorCode() == null && tradeResponse.getOrderId() != null) {
                posDiffService.finishCorr(true);
            } else {
                posDiffService.finishCorr(false);
            }
        }

        return tradeResponse;
    }

    public void deferredPlaceOrderOnSignal(PlaceOrderArgs currPlaceOrderArgs) {
        if (this.placeOrderArgsRef.compareAndSet(null, currPlaceOrderArgs)) {
            setMarketState(MarketState.WAITING_ARB);
            tradeLogger.info(String.format("#%s MT2 deferred placing %s", getCounterName(), currPlaceOrderArgs));
        } else {
            final String errorMessage = String.format("double placing-order for MT2. New:%s.", currPlaceOrderArgs);
            logger.error(errorMessage);
            tradeLogger.error(errorMessage);
            warningLogger.error(errorMessage);
        }
    }

    @Override
    public TradeResponse placeOrderOnSignal(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes,
                                            SignalType signalType) {
        throw new IllegalArgumentException("Use placeOrder instead");
    }

    public TradeResponse placeMakerOrderWithStatus(Order.OrderType orderType, BigDecimal tradeableAmount, BestQuotes bestQuotes,
                                                   boolean isMoving, SignalType signalType, PlacingType placingSubType) throws Exception {
        final TradeResponse tradeResponse;
        arbitrageService.setSignalType(signalType);

        setMarketState(MarketState.PLACING_ORDER);

        try {
            tradeResponse = placeMakerOrder(orderType, tradeableAmount, bestQuotes, isMoving, signalType, placingSubType);
        } finally {
            setMarketState(MarketState.ARBITRAGE);
        }

//        fetchOpenOrdersWithDelay();
        return tradeResponse;
    }

    private TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal tradeableAmount, BestQuotes bestQuotes,
                                          boolean isMoving, @NotNull SignalType signalType, PlacingType placingSubType) throws IOException {
        final TradeResponse tradeResponse = new TradeResponse();

        BigDecimal thePrice;

        final String message = Utils.getTenAskBid(getOrderBook(), signalType.getCounterName(), String.format("Before %s placing", placingSubType));
        logger.info(message);
        tradeLogger.info(message);

        if (tradeableAmount.compareTo(BigDecimal.ZERO) == 0) {

            tradeResponse.setErrorCode("Not enough amount left. amount=" + tradeableAmount.toPlainString());

        } else {
            // USING REST API
            orderType = adjustOrderType(orderType, tradeableAmount);

            if (placingSubType == PlacingType.MAKER) {
                thePrice = createBestMakerPrice(orderType).setScale(2, BigDecimal.ROUND_HALF_UP);
            } else {
                // the best Taker price(even for Hybrid)
                thePrice = createBestHybridPrice(orderType).setScale(2, BigDecimal.ROUND_HALF_UP);

                if (placingSubType == null || placingSubType == PlacingType.TAKER) {
                    tradeLogger.warn("placing maker, but subType is " + placingSubType);
                    warningLogger.warn("placing maker, but subType is " + placingSubType);
                }
            }

            if (thePrice.compareTo(BigDecimal.ZERO) == 0) {
                tradeResponse.setErrorCode("The new price is 0 ");
            } else {

                final LimitOrder limitOrder = new LimitOrder(orderType, tradeableAmount, CURRENCY_PAIR_BTC_USD, "123", new Date(), thePrice);
                String orderId = exchange.getTradeService().placeLimitOrder(limitOrder);
                tradeResponse.setOrderId(orderId);

                final LimitOrder limitOrderWithId = new LimitOrder(orderType, tradeableAmount, CURRENCY_PAIR_BTC_USD, orderId, new Date(), thePrice);
                tradeResponse.setLimitOrder(limitOrderWithId);
                final FplayOrder fplayOrder = new FplayOrder(limitOrderWithId, bestQuotes, placingSubType, signalType);
                orderRepositoryService.save(fplayOrder);

                String placingTypeString = (isMoving ? "Moving3:Moved:" : "") + placingSubType;

                if (!isMoving) {

                    final Order.OrderStatus status = limitOrderWithId.getStatus();
                    final String msg = String.format("%s: %s %s amount=%s, quote=%s, orderId=%s, status=%s",
                            getCounterName(),
                            placingTypeString,
                            Utils.convertOrderTypeName(orderType),
                            tradeableAmount.toPlainString(),
                            thePrice,
                            orderId,
                            status);

                    debugLog.info("placeOrder1 " + msg);

                    synchronized (openOrdersLock) {

                        debugLog.info("placeOrder2 " + msg);

                        tradeLogger.info(msg);
                        openOrders.replaceAll(exists -> {
                            if (fplayOrder.getOrderId().equals(exists.getOrderId())) {
                                return FplayOrderUtils.updateFplayOrder(exists, fplayOrder);
                            }
                            return exists;
                        });

                        if (openOrders.stream().noneMatch(o -> o.getOrderId().equals(fplayOrder.getOrderId()))) {
                            debugLog.warn("placeOrder2 Order was missed " + msg);
                            logger.warn("placeOrder2 Order was missed " + msg);
                            tradeLogger.warn("placeOrder2 Order was missed " + msg);

                            openOrders.add(fplayOrder);
                        }
                    }

                    debugLog.info("placeOrder3 " + msg);
                }

                arbitrageService.getDealPrices().setSecondOpenPrice(thePrice);
                arbitrageService.getDealPrices().getoPriceFact().addPriceItem(orderId, limitOrderWithId.getCumulativeAmount(), limitOrderWithId.getAveragePrice());

                orderIdToSignalInfo.put(orderId, bestQuotes);

                writeLogPlaceOrder(orderType, tradeableAmount,
                        placingTypeString,
                        thePrice, orderId,
                        (limitOrderWithId.getStatus() != null) ? limitOrderWithId.getStatus().toString() : null);
            }
        }

        return tradeResponse;
    }

    private void writeLogPlaceOrder(Order.OrderType orderType, BigDecimal tradeableAmount,
                                    String placingType, BigDecimal thePrice, String orderId, String status) {

        final String message = String.format("%s/end: %s %s amount=%s, quote=%s, orderId=%s, status=%s",
                getCounterName(),
                placingType, //isMoving ? "Moving3:Moved" : "maker",
                Utils.convertOrderTypeName(orderType),
                tradeableAmount.toPlainString(),
                thePrice,
                orderId,
                status);
        tradeLogger.info(message);
        ordersLogger.info(message);
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
                tradeLogger.info("{} OrderInfo id={}, status={}, filled={}",
                        getCounterName(),
                        orderId,
                        order.getStatus(),
                        order.getCumulativeAmount());
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

    private volatile CounterToDiff counterToDiff = new CounterToDiff(null, null);

    @Override
    public MoveResponse moveMakerOrder(FplayOrder fOrderToCancel, BigDecimal bestMarketPrice) {
        final LimitOrder limitOrder = LimitOrder.Builder.from(fOrderToCancel.getOrder()).build();
        final SignalType signalType = fOrderToCancel.getSignalType() != null ? fOrderToCancel.getSignalType() : getArbitrageService().getSignalType();

        if (limitOrder.getStatus() == Order.OrderStatus.CANCELED || limitOrder.getStatus() == Order.OrderStatus.FILLED) {
            tradeLogger.error("{} do not move ALREADY_CLOSED order", getCounterName());
            return new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, "", null,null, fOrderToCancel);
        }
        if (getMarketState() == MarketState.PLACING_ORDER) { // !arbitrageService.getParams().getOkCoinOrderType().equals("maker")
            return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "no moving for taker");
        }

        arbitrageService.setSignalType(signalType);

        final MarketState savedState = getMarketState();
        setMarketState(MarketState.MOVING);

        MoveResponse response;
        try {
            // IT doesn't support moving
            // Do cancel and place
            BestQuotes bestQuotes = orderIdToSignalInfo.get(limitOrder.getId());

            // 1. cancel old order
            final String counterName = getCounterName();
            final OkCoinTradeResult okCoinTradeResult = cancelOrderSync(limitOrder.getId(), "Moving1:cancelling:");

            // 2. We got result on cancel(true/false), but double-check status of an old order
            Order cancelledOrder = getFinalOrderInfoSync(limitOrder.getId(), counterName, "Moving2:cancelStatus:");
            if (cancelledOrder == null)
                return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "Failed to check status of cancelled order on moving id=" + limitOrder.getId());

            final FplayOrder cancelledFplayOrd = FplayOrderUtils.updateFplayOrder(fOrderToCancel, (LimitOrder) cancelledOrder);
            final LimitOrder cancelledLimitOrder = (LimitOrder) cancelledFplayOrd.getOrder();
            orderRepositoryService.update(cancelledLimitOrder);

            arbitrageService.getDealPrices().getoPriceFact().addPriceItem(cancelledLimitOrder.getId(), cancelledLimitOrder.getCumulativeAmount(),
                    cancelledLimitOrder.getAveragePrice());

            // 3. Already closed?
            if (cancelledLimitOrder.getStatus() == Order.OrderStatus.FILLED
                    || (!okCoinTradeResult.isResult() && cancelledLimitOrder.getStatus() == Order.OrderStatus.CANCELED)) { // Already closed (FILLED)
                response = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, "", null, null,
                        cancelledFplayOrd);

                final String logString = String.format("%s %s %s status=%s,amount=%s/%s,quote=%s/%s,id=%s,lastException=%s",
                        counterName,
                        "Moving3:Already closed:",
                        limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                        cancelledLimitOrder.getStatus(),
                        limitOrder.getTradableAmount(), cancelledLimitOrder.getCumulativeAmount(),
                        limitOrder.getLimitPrice().toPlainString(), cancelledLimitOrder.getAveragePrice(),
                        limitOrder.getId(),
                        null);
                tradeLogger.info(logString);

                // 3. Place order
            } else { //if (cancelledOrder.getStatus() == Order.OrderStatus.CANCELED) {
                TradeResponse tradeResponse = new TradeResponse();
                if (cancelledFplayOrd.getPlacingType() == null) {
                    getTradeLogger().warn("WARNING: PlaceType is null." + cancelledFplayOrd);
                }

                tradeResponse = finishMovingSync(limitOrder, signalType, bestQuotes, counterName, cancelledLimitOrder, tradeResponse,
                        cancelledFplayOrd.getPlacingType());

                if (tradeResponse.getLimitOrder() != null) {
                    final LimitOrder newOrder = tradeResponse.getLimitOrder();
                    final FplayOrder newFplayOrder = new FplayOrder(newOrder, cancelledFplayOrd.getBestQuotes(),
                            cancelledFplayOrd.getPlacingType(), cancelledFplayOrd.getSignalType());
                    response = new MoveResponse(MoveResponse.MoveOrderStatus.MOVED_WITH_NEW_ID, tradeResponse.getOrderId(),
                            newOrder, newFplayOrder, cancelledFplayOrd);
                } else {
                    warningLogger.info(String.format("%s Can not move orderId=%s, ONLY_CANCEL!!!",
                            getCounterName(), limitOrder.getId()));
                    response = new MoveResponse(MoveResponse.MoveOrderStatus.ONLY_CANCEL, tradeResponse.getOrderId(),
                            null, null, cancelledFplayOrd);
                }

            }
        } finally {
            setMarketState(savedState);
        }

        return response;
    }

    private TradeResponse finishMovingSync(LimitOrder limitOrder, SignalType signalType, BestQuotes bestQuotes, String counterName,
                                           Order cancelledOrder, TradeResponse tradeResponse, PlacingType placingType) {
        int attemptCount = 0;
        while (attemptCount < MAX_ATTEMPTS_FOR_MOVING) {
            try {
                attemptCount++;
                if (attemptCount > 1) {
                    Thread.sleep(500 * attemptCount);
                }

                final BigDecimal newAmount = limitOrder.getTradableAmount().subtract(cancelledOrder.getCumulativeAmount())
                        .setScale(0, RoundingMode.HALF_UP);

                tradeLogger.info("{}/{} Moving3:placingNew a={}, placingType={}", counterName, attemptCount, newAmount, placingType);

                PlacingType okexPlacingType = placingType;
                if (okexPlacingType == null) {
                    placingType = persistenceService.getSettingsRepositoryService().getSettings().getOkexPlacingType();
                }

                tradeResponse = placeMakerOrder(limitOrder.getType(), newAmount, bestQuotes, true, signalType, okexPlacingType);

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
                if (attemptCount < MAX_ATTEMPTS_FOR_MOVING &&
                        e.toString().startsWith("org.knowm.xchange.exceptions.ExchangeException: Code: 20012, translation: Risk rate higher than 90% after opening position")) {
                    continue;
                }
                setMarketState(MarketState.STOPPED); // it's duplicated in #iterateOpenOrdersMove()
                break;
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
        Order result = null;
        int attemptCount = 0;
        while (attemptCount < MAX_ATTEMPTS_STATUS) {
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
    public OkCoinTradeResult cancelOrderSync(String orderId, String logInfoId) {
        final String counterName = getCounterName();
        OkCoinTradeResult result = new OkCoinTradeResult(false, 0, 0);

        int attemptCount = 0;
        while (attemptCount < MAX_ATTEMPTS_CANCEL) {
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

                if (result.isResult()) {
                    break;
                }

            } catch (Exception e) {
                logger.error("{}/{} error cancel maker order", counterName, attemptCount, e);
                tradeLogger.error("{}/{} error cancel maker order: {}", counterName, attemptCount, e.toString());
                final Order order = fetchOrderInfo(orderId);
                if (order != null) {
                    if (order.getStatus() == Order.OrderStatus.CANCELED) {
                        result = new OkCoinTradeResult(true, 0, Long.valueOf(orderId));
                        break;
                    }
                    if (order.getStatus() == Order.OrderStatus.FILLED) {
                        result = new OkCoinTradeResult(false, 0, Long.valueOf(orderId));
                        break;
                    }
                }
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
        if (getMarketState() == MarketState.STOPPED) {
            return;
        }

        final CorrParams corrParams = getPersistenceService().fetchCorrParams();
        final GuiParams params = arbitrageService.getParams();

        if (corrParams.getPreliq().hasSpareAttempts()) {
            final BigDecimal oDQLCloseMin = params.getoDQLCloseMin();
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

                    arbitrageService.startPerliqOnDelta2(SignalType.O_PRE_LIQ, bestQuotes);

                } else if (pos.signum() < 0) {
                    tradeLogger.info(String.format("%s O_PRE_LIQ starting: p(%s-%s)/dql%s/dqlClose%s",
                            getCounterName(),
                            position.getPositionLong().toPlainString(), position.getPositionShort().toPlainString(),
                            liqInfo.getDqlCurr().toPlainString(), oDQLCloseMin.toPlainString()));

                    arbitrageService.startPreliqOnDelta1(SignalType.O_PRE_LIQ, bestQuotes);

                }
            }
        }
    }

    @Override
    protected void iterateOpenOrdersMove() { // if synchronized then the queue for moving could be long
        if (getMarketState() == MarketState.SYSTEM_OVERLOADED
                || getMarketState() == MarketState.PLACING_ORDER
                || getMarketState() == MarketState.STOPPED) {
            return;
        }

        if (movingInProgress) {

            // Should not happen ever, because 'synch' on method
            final String logString = String.format("%s No moving. Too often requests.", getCounterName());
            logger.error(logString);
            return;

        } else {
            movingInProgress = true;
        }

        try {
            synchronized (openOrdersLock) {
                if (hasOpenOrders()) {

                    final SysOverloadArgs sysOverloadArgs = settingsRepositoryService.getSettings().getBitmexSysOverloadArgs();
                    final Integer maxAttempts = sysOverloadArgs.getMovingErrorsForOverload();

                    openOrders = openOrders.stream()
                            .flatMap(openOrder -> {
                                Stream<FplayOrder> optionalOrder = Stream.of(openOrder); // default -> keep the order

                                if (openOrder == null) {
                                    warningLogger.warn("OO is null.");
                                    optionalOrder = Stream.empty();
                                } else if (openOrder.getOrder().getType() == null) {
                                    warningLogger.warn("OO type is null. " + openOrder.toString());
                                } else if (openOrder.getOrderDetail().getOrderStatus() != Order.OrderStatus.NEW
                                        && openOrder.getOrderDetail().getOrderStatus() != Order.OrderStatus.PENDING_NEW
                                        && openOrder.getOrderDetail().getOrderStatus() != Order.OrderStatus.PARTIALLY_FILLED) {
                                    // keep the order
                                } else {

                                    try {

                                        final MoveResponse response = moveMakerOrderIfNotFirst(openOrder);
                                        //TODO keep an eye on 'hang open orders'
                                        if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.ALREADY_CLOSED) {
                                            // update the status
                                            final FplayOrder cancelledFplayOrder = response.getCancelledFplayOrder();
                                            if (cancelledFplayOrder != null) optionalOrder = Stream.of(cancelledFplayOrder);
                                        } else if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.MOVED_WITH_NEW_ID) {
                                            final FplayOrder newOrder = response.getNewFplayOrder();
                                            final FplayOrder cancelledOrder = response.getCancelledFplayOrder();

                                            optionalOrder = Stream.of(newOrder, cancelledOrder);
                                            movingErrorsOverloaded.set(0);
                                        } else if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.EXCEPTION_SYSTEM_OVERLOADED) {

                                            if (movingErrorsOverloaded.incrementAndGet() >= maxAttempts) {
                                                setOverloaded(null);
                                                movingErrorsOverloaded.set(0);
                                            }

                                        } else if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.ONLY_CANCEL
                                                || response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.EXCEPTION) { // EXCEPTION also duplicated in #moveOO
                                            setMarketState(MarketState.STOPPED);
                                        }
                                        final FplayOrder newOrder = response.getNewFplayOrder();
                                        final FplayOrder cancelledOrder = response.getCancelledFplayOrder();

                                        if (newOrder != null) {
                                            final Order orderInfo = newOrder.getOrder();
                                            arbitrageService.getDealPrices().getoPriceFact().addPriceItem(orderInfo.getId(), orderInfo.getCumulativeAmount(), orderInfo.getAveragePrice());
                                        }
                                        if (cancelledOrder != null) {
                                            final Order orderInfo = cancelledOrder.getOrder();
                                            arbitrageService.getDealPrices().getoPriceFact().addPriceItem(orderInfo.getId(), orderInfo.getCumulativeAmount(), orderInfo.getAveragePrice());
                                            if (cancelledOrder.getOrder().getCumulativeAmount().signum() > 0) {
                                                writeAvgPriceLog();
                                            }
                                        }

                                    } catch (Exception e) {
                                        // use default OO
                                        warningLogger.warn("Error on moving: " + e.getMessage());
                                        logger.warn("Error on moving", e);
                                    }
                                }

                                return optionalOrder; // default -> keep the order
                            })
                            .collect(Collectors.toList());

                    if (!hasOpenOrders()) {
                        tradeLogger.warn("Free by iterateOpenOrdersMove");
                        logger.warn("Free by iterateOpenOrdersMove");
                        eventBus.send(BtsEvent.MARKET_FREE);
                    }

                }

            } // synchronized (openOrdersLock)

        } finally {
            movingInProgress = false;
        }
    }

    public void writeAvgPriceLog() {
        final DealPrices dealPrices = arbitrageService.getDealPrices();
        final DealPrices.Details diffO = dealPrices.getDiffO();
        final BigDecimal avg = dealPrices.getoPriceFact().getAvg();
        final String counter = getCounterName();
        if ((counterToDiff == null || counterToDiff.counter == null || !counterToDiff.counter.equals(counter)
                || counterToDiff.diff.compareTo(diffO.val) != 0)
                && avg.signum() != 0) {
            tradeLogger.info("{} {}", counter, diffO.str);
            counterToDiff = new CounterToDiff(counter, diffO.val);
        }

    }

    static class CounterToDiff {
        String counter;
        BigDecimal diff;

        public CounterToDiff(String counter, BigDecimal diff) {
            this.counter = counter;
            this.diff = diff;
        }
    }

    @Override
    protected void postOverload() {
    }

    /**
     * Workaround! <br>
     * Request orders details. Use it before ending of a Round.
     *
     * @param avgPrice the object to be updated.
     */
    public void updateAvgPrice(AvgPrice avgPrice) {
        final Set<String> orderIds = avgPrice.getpItems().keySet();
        Collection<Order> orderInfos = new ArrayList<>();

        for (int attempt = 0; attempt < 3; attempt++) { // about 11 sec
            long sleepTime = 200;
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                tradeLogger.error("Error on sleeping");
            }

            orderInfos = getOrderInfos(orderIds.toArray(new String[0]), getCounterName(),
                    attempt, "updateAvgPrice:", getTradeLogger());

            if (orderInfos.size() == orderIds.size()
                    && orderInfos.stream()
                    .filter(order -> order.getAveragePrice() != null)
                    .anyMatch(order -> order.getAveragePrice().signum() > 0)) {
                break;
            }
        }

        orderInfos.forEach(order -> avgPrice.addPriceItem(order.getId(), order.getCumulativeAmount(), order.getAveragePrice()));
    }
}
