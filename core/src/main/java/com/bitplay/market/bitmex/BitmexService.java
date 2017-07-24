package com.bitplay.market.bitmex;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.MarketService;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.utils.Utils;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.bitrich.xchangestream.bitmex.BitmexStreamingAccountService;
import info.bitrich.xchangestream.bitmex.BitmexStreamingExchange;
import info.bitrich.xchangestream.bitmex.BitmexStreamingMarketDataService;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitmex.service.BitmexAccountService;
import org.knowm.xchange.bitmex.service.BitmexTradeService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.ContractIndex;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PreDestroy;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.swagger.client.model.Error;
import rx.Completable;
import si.mazi.rescu.HttpStatusIOException;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Service("bitmex")
public class BitmexService extends MarketService {
    private final static Logger logger = LoggerFactory.getLogger(BitmexService.class);
    private static final Logger tradeLogger = LoggerFactory.getLogger("BITMEX_TRADE_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private final static String NAME = "bitmex";
    private static final int MAX_ATTEMPTS = 10;

    private final static CurrencyPair CURRENCY_PAIR_XBTUSD = new CurrencyPair("XBT", "USD");

    private BitmexStreamingExchange exchange;

    private Disposable accountInfoSubscription;
    private Disposable positionSubscription;
    private Disposable futureIndexSubscription;


    private Observable<OrderBook> orderBookObservable;
    private Disposable orderBookSubscription;
    private Disposable openOrdersSubscription;

    private ArbitrageService arbitrageService;

    @Autowired
    private PosDiffService posDiffService;
    @Autowired
    private PersistenceService persistenceService;

    @Override
    public ArbitrageService getArbitrageService() {
        return arbitrageService;
    }

    @Override
    public PosDiffService getPosDiffService() {
        return posDiffService;
    }

    @Override
    public PersistenceService getPersistenceService() {
        return persistenceService;
    }

    @Autowired
    public void setArbitrageService(ArbitrageService arbitrageService) {
        this.arbitrageService = arbitrageService;
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
        this.exchange = initExchange(key, secret);
        loadLiqParams();

        initWebSocketConnection();

        startAllListeners();
    }

    private void startAllListeners() {

        startOrderBookListener();

        Completable.timer(1000, TimeUnit.MILLISECONDS)
                .doOnCompleted(this::startAccountInfoListener)
                .subscribe();
        Completable.timer(2000, TimeUnit.MILLISECONDS)
                .doOnCompleted(this::startOpenOrderListener)
                .subscribe();

        Completable.timer(3000, TimeUnit.MILLISECONDS)
                .doOnCompleted(this::startPositionListener)
                .subscribe();

        Completable.timer(4000, TimeUnit.MILLISECONDS)
                .doOnCompleted(this::startOpenOrderMovingListener)
                .subscribe();

        Completable.timer(5000, TimeUnit.MILLISECONDS)
                .doOnCompleted(this::startFutureIndexListener)
                .subscribe();

    }

    @Override
    public void fetchPosition() {
        try {
            final BitmexAccountService accountService = (BitmexAccountService) exchange.getAccountService();
            final Position pUpdate = accountService.fetchPositionInfo();
            mergePosition(pUpdate);

            recalcAffordableContracts();
            recalcLiqInfo();
        } catch (Exception e) {
            logger.error("On fetch position", e);
        }
    }

    private synchronized void mergePosition(Position pUpdate) {
        BigDecimal leverage = pUpdate.getLeverage().signum() == 0 ? BigDecimal.valueOf(100) : pUpdate.getLeverage();
        BigDecimal liqPrice = pUpdate.getLiquidationPrice().signum() == 0 ? this.position.getLiquidationPrice() : pUpdate.getLiquidationPrice();
        this.position = new Position(
                pUpdate.getPositionLong(),
                pUpdate.getPositionShort(),
                leverage,
                liqPrice,
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
        fetchOpenOrders(); // Synchronous
        if (openOrders.size() == 0) {
            setFree();
        }
    }

    private void startOpenOrderMovingListener() {
        orderBookObservable
                .subscribeOn(Schedulers.io())
                .subscribe(orderBook1 -> {
                    checkOpenOrdersForMoving();
                }, throwable -> {
                    logger.error("On Moving OpenOrders.", throwable); // restart
                });
    }

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
        exchange.connect()
                .retryWhen(throwableFlowable -> throwableFlowable.delay(10, TimeUnit.SECONDS))
                .doOnError(throwable -> logger.error("connection error", throwable))
                .blockingAwait();

        try {
            exchange.authenticate().blockingAwait();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Retry on disconnect.
        exchange.onDisconnect().subscribe(() -> {
                    logger.warn("onClientDisconnect BitmexService");
                    doDestoy();
                    initWebSocketConnection();
                    startAllListeners();
                },
                throwable -> logger.error("onClientDisconnect BitmexService error", throwable));
    }

    @PreDestroy
    private void doDestoy() {
        exchange.disconnect();
        orderBookSubscription.dispose();
        accountInfoSubscription.dispose();
        openOrdersSubscription.dispose();
        positionSubscription.dispose();
        futureIndexSubscription.dispose();
    }

    private void startOrderBookListener() {
        orderBookObservable = createOrderBookObservable();

        orderBookSubscription = orderBookObservable
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
                .subscribe(orderBook -> {
                    //workaround
                    if (openOrders == null) {
                        openOrders = new ArrayList<>();
                        if (isBusy) {
                            eventBus.send(BtsEvent.MARKET_FREE);
                        }
                    }

                    final List<LimitOrder> bestAsks = Utils.getBestAsks(orderBook.getAsks(), 1);
                    final LimitOrder bestAsk = bestAsks.size() > 0 ? bestAsks.get(0) : null;
                    final List<LimitOrder> bestBids = Utils.getBestBids(orderBook.getBids(), 1);
                    final LimitOrder bestBid = bestBids.size() > 0 ? bestBids.get(0) : null;

                    this.orderBook = orderBook;

                    if (this.bestAsk != null && bestAsk != null && this.bestBid != null && bestBid != null
                            && this.bestAsk.compareTo(bestAsk.getLimitPrice()) != 0
                            && this.bestBid.compareTo(bestBid.getLimitPrice()) != 0) {
                        recalcAffordableContracts();
                    }
                    this.bestAsk = bestAsk != null ? bestAsk.getLimitPrice() : BigDecimal.ZERO;
                    this.bestBid = bestBid != null ? bestBid.getLimitPrice() : BigDecimal.ZERO;
                    logger.debug("ask: {}, bid: {}", this.bestAsk, this.bestBid);

                }, throwable -> logger.error("ERROR in getting order book: ", throwable));
    }

    private Observable<OrderBook> createOrderBookObservable() {
        return exchange.getStreamingMarketDataService()
                .getOrderBook(CurrencyPair.BTC_USD, 20)
                .doOnDispose(() -> logger.info("bitmex subscription doOnDispose"))
                .doOnTerminate(() -> logger.info("bitmex subscription doOnTerminate"))
                .doOnError((throwable) -> logger.error("bitmex subscription doOnErro", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .share();
    }

    @Override
    public Observable<OrderBook> getOrderBookObservable() {
        return orderBookObservable;
    }


    private void startOpenOrderListener() {
        openOrdersSubscription = exchange.getStreamingTradingService()
                .getOpenOrdersObservable()
                .doOnError(throwable -> logger.error("onOpenOrdersListening", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.computation())
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
                                    if (mergedOrder.get().getStatus().equals(Order.OrderStatus.FILLED)) { // End orders right away step1
                                        // THere are no updates of FILLED orders
                                        tradeLogger.info("Order {} FILLED", mergedOrder.get().getId());
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
                    startOpenOrderListener();
                });
    }

    @Override
    public UserTrades fetchMyTradeHistory() {
        return null;
    }

    @Override
    public OrderBook getOrderBook() {
        return orderBook;
    }

    private synchronized void recalcAffordableContracts() {
        final BigDecimal reserveBtc = arbitrageService.getParams().getReserveBtc1();

        if (accountInfoContracts != null) {
            final BigDecimal availableBtc = accountInfoContracts.getAvailable();
            final BigDecimal equityBtc = accountInfoContracts.getEquity();
            final BigDecimal bestAsk = Utils.getBestAsks(orderBook, 1).get(0).getLimitPrice();
            final BigDecimal bestBid = Utils.getBestBids(orderBook, 1).get(0).getLimitPrice();
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
        return placeMakerOrder(orderType, amountInContracts, bestQuotes, false, signalType);
    }

    private TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes,
                                          boolean isMoving, SignalType signalType) {
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
                    thePrice = createBestMakerPrice(orderType, false)
                            .setScale(1, BigDecimal.ROUND_HALF_UP);

                    final LimitOrder limitOrder = new LimitOrder(orderType,
                            amount, CURRENCY_PAIR_XBTUSD, "0", new Date(),
                            thePrice);

                    orderId = tradeService.placeLimitOrder(limitOrder);
                    break;
                } catch (Exception e) {
                    logger.error("Error on placeLimitOrder", e);
                    final String logString = String.format("#%s maker error attempt=%s: %s",
                            signalType == SignalType.AUTOMATIC ? arbitrageService.getCounter() : signalType.getCounterName(),
                            attemptCount,
                            e.getMessage());
                    if (attemptCount == MAX_ATTEMPTS) {
                        logger.error(logString, e);
                        tradeLogger.error("Warning placing: " + logString);
                        warningLogger.error("bitmex placing. Warning: " + logString);
                    } else {
                        logger.error(logString, e);
                        tradeLogger.error(logString);
                    }

                    tradeResponse.setOrderId(e.getMessage());
                    tradeResponse.setErrorMessage(e.getMessage());
                }
            }
            if (orderId != null) {

                tradeResponse.setOrderId(orderId);

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

                tradeLogger.info("#{} {} {} amount={} with quote={} was placed.orderId={}. {}. position={}",
                        signalType == SignalType.AUTOMATIC ? arbitrageService.getCounter() : signalType.getCounterName(),
                        isMoving ? "Moved" : "maker",
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
            tradeResponse.setErrorMessage(e.getMessage());
        }
        return tradeResponse;
    }

    @Override
    public TradeService getTradeService() {
        return exchange.getTradeService();
    }

    @Override
    public MoveResponse moveMakerOrder(LimitOrder limitOrder, SignalType signalType) {
        MoveResponse moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "default");
        int attemptCount = 0;
        String lastExceptionMsg = "";
        BigDecimal bestMakerPrice = BigDecimal.ZERO;
        BestQuotes bestQuotes = orderIdToSignalInfo.get(limitOrder.getId());

        while (attemptCount < MAX_ATTEMPTS) {
            attemptCount++;
            try {
                bestMakerPrice = createBestMakerPrice(limitOrder.getType(), true)
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

                    logger.error("MoveError", e);
                    tradeLogger.error("MoveError: " + error.getError().getMessage());

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
                final String logString = String.format("#%s MovingError id=%s, attempt=%s: %s",
                        signalType == SignalType.AUTOMATIC ? arbitrageService.getCounter() : signalType.getCounterName(),
                        limitOrder.getId(),
                        attemptCount,
                        e.getMessage());
                if (attemptCount == MAX_ATTEMPTS) {
                    logger.error(logString, e);
                    tradeLogger.error("Warning: " + logString);
                    warningLogger.error("bitmex. Warning: " + logString);
                } else {
                    logger.error(logString, e);
                    tradeLogger.error(logString);
                }
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

            final String logString = String.format("#%s Moved %s amount=%s, filled=%s,quote=%s,id=%s,attempt=%s. %s. position=%s",
                    signalType == SignalType.AUTOMATIC ? arbitrageService.getCounter() : signalType.getCounterName(),
                    limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                    limitOrder.getTradableAmount(),
                    limitOrder.getCumulativeAmount(),
                    bestMakerPrice.toPlainString(),
                    limitOrder.getId(),
                    attemptCount,
                    diffWithSignal,
                    getPositionAsString());

            if (signalType == SignalType.AUTOMATIC) {
                arbitrageService.getOpenPrices().setFirstOpenPrice(bestMakerPrice);
            }

            tradeLogger.info(logString);
            moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.MOVED, logString);
        } else if (moveResponse == null) {
            final String logString = String.format("Moving error %s amount=%s,oldQuote=%s,id=%s,attempt=%s(%s)",
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
    protected BigDecimal getMakerPriceStep() {
        return new BigDecimal("0.1");
    }

    @Override
    protected BigDecimal getMakerDelta() {
        return new BigDecimal("0.00000001");
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

                    final BigDecimal equity = newInfo.getEquity() != null ? newInfo.getEquity() : accountInfoContracts.getEquity();
                    final BigDecimal available = newInfo.getAvailable() != null ? newInfo.getAvailable() : accountInfoContracts.getAvailable();
                    final BigDecimal margin = equity.subtract(available); //equity and available may be updated with separate responses

                    accountInfoContracts = new AccountInfoContracts(
                            newInfo.getWallet() != null ? newInfo.getWallet() : accountInfoContracts.getWallet(),
                            available,
                            equity,
                            margin,
                            newInfo.getUpl() != null ? newInfo.getUpl() : accountInfoContracts.getUpl(),
                            newInfo.getRpl() != null ? newInfo.getRpl() : accountInfoContracts.getRpl(),
                            newInfo.getRiskRate() != null ? newInfo.getRiskRate() : accountInfoContracts.getRiskRate()
                    );

                    logger.debug("Balance " + accountInfoContracts.toString());
                }, throwable -> {
                    logger.error("Can not fetchAccountInfo", throwable);
                    // schedule it again
                    sleep(5000);
                    startAccountInfoListener();
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
                    startPositionListener();
                });
    }

    private void startFutureIndexListener() {
        Observable<ContractIndex> indexObservable = ((BitmexStreamingMarketDataService) exchange.getStreamingMarketDataService())
                .getContractIndexObservable()
                .doOnError(throwable -> logger.error("Index fetch error", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS));

        futureIndexSubscription = indexObservable
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> logger.error("Index fetch error", throwable))
                .subscribe(contractIndex1 -> {
                    if (contractIndex1.getIndexPrice() != null) {
                        this.contractIndex = new ContractIndex(contractIndex1.getIndexPrice(), contractIndex1.getTimestamp());
                    }
                }, throwable -> {
                    logger.error("Can not fetch Position", throwable);
                    // schedule it again
                    sleep(5000);
                    startFutureIndexListener();
                });
    }

    @PreDestroy
    public void preDestroy() {
        // Unsubscribe from data order book.
        accountInfoSubscription.dispose();

        orderBookSubscription.dispose();
    }

    @Override
    public String getPositionAsString() {
        return position != null ? position.getPositionLong().toPlainString() : "0";
    }


    private synchronized void recalcLiqInfo() {
        final AccountInfoContracts accountInfoContracts = getAccountInfoContracts();

        final BigDecimal equity = accountInfoContracts.getEquity();
        final BigDecimal margin = accountInfoContracts.getMargin();

        final BigDecimal bMrliq = arbitrageService.getParams().getbMrLiq();

        if (equity != null && margin != null
                && contractIndex.getIndexPrice() != null
                && position.getLiquidationPrice() != null
                && position.getPositionLong() != null
                && position.getPositionShort() != null) {

            BigDecimal dql = null;

            String dqlString;
            if (position.getPositionLong().signum() > 0) {
                dql = contractIndex.getIndexPrice().subtract(position.getLiquidationPrice());
                dqlString = String.format("b_DQL = m%s - L%s = %s;",
                        contractIndex.getIndexPrice(),
                        position.getLiquidationPrice(),
                        dql
                );
            } else if (position.getPositionLong().signum() < 0) {
                dql = position.getLiquidationPrice().subtract(contractIndex.getIndexPrice());
                dqlString = String.format("b_DQL = L%s - m%s = %s;",
                        position.getLiquidationPrice(),
                        contractIndex.getIndexPrice(),
                        dql
                );
            } else {
                dqlString = "b_DQL = na";
            }

            BigDecimal dmrl = null;
            String dmrlString = null;
            if ((position.getPositionLong().subtract(position.getPositionShort())).signum() != 0) {
                final BigDecimal bMr = equity.divide(margin, 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP);
                dmrl = bMr.subtract(bMrliq);
                dmrlString = String.format("b_DMRL = %s - %s = %s%%", bMr, bMrliq, dmrl);
            } else {
                dmrlString = "o_DMRL = na";
            }

            if (dql != null) {
                liqInfo.setDqlCurr(dql);
                if (liqInfo.getLiqParams().getDqlMax().compareTo(dql) == -1) {
                    liqInfo.getLiqParams().setDqlMax(dql);
                }
                if (liqInfo.getLiqParams().getDqlMin().compareTo(dql) == 1) {
                    liqInfo.getLiqParams().setDqlMin(dql);
                }
            }
            if (dmrl != null) {
                liqInfo.setDmrlCurr(dmrl);
                if (liqInfo.getLiqParams().getDmrlMax().compareTo(dmrl) == -1) {
                    liqInfo.getLiqParams().setDmrlMax(dmrl);
                }
                if (liqInfo.getLiqParams().getDmrlMin().compareTo(dmrl) == 1) {
                    liqInfo.getLiqParams().setDmrlMin(dmrl);
                }
            }

            liqInfo.setDqlString(dqlString);
            liqInfo.setDmrlString(dmrlString);

            storeLiqParams();
        }
    }

    @Override
    public boolean checkLiquidationEdge(Order.OrderType orderType) {
        final BigDecimal bDQLOpenMin = arbitrageService.getParams().getbDQLOpenMin();

        boolean isOk;
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

        logger.debug(String.format("CheckLiqEdge:%s(p%s/%s/%s)", isOk,
                position.getPositionLong().subtract(position.getPositionShort()),
                liqInfo.getDqlCurr(),
                bDQLOpenMin));

        return isOk;
    }

    @Scheduled(fixedDelay = 5 * 1000) // 30 sec
    public void checkForDecreasePosition() {
        final BigDecimal bDQLCloseMin = arbitrageService.getParams().getbDQLCloseMin();

        if (liqInfo.getDqlCurr() != null
                && liqInfo.getDqlCurr().compareTo(BigDecimal.valueOf(-30)) == 1 // workaround when DQL is less zero
                && liqInfo.getDqlCurr().compareTo(bDQLCloseMin) != 1
                && position.getPositionLong().signum() != 0) {
            final BestQuotes bestQuotes = Utils.createBestQuotes(getOrderBook(), arbitrageService.getSecondMarketService().getOrderBook());
            final BigDecimal btcP = getAccountInfoContracts().getAvailable();

            if (position.getPositionLong().signum() > 0) {
                tradeLogger.info(String.format("B_PRE_LIQ starting: p%s/dql%s/dqlClose%s",
                        position.getPositionLong().toPlainString(),
                        liqInfo.getDqlCurr().toPlainString(), bDQLCloseMin.toPlainString()));

                arbitrageService.startTradingOnDelta1(SignalType.B_PRE_LIQ, bestQuotes.getAsk1_o(), bestQuotes.getBid1_p(), bestQuotes, btcP);

            } else if (position.getPositionLong().signum() < 0) {
                tradeLogger.info(String.format("B_PRE_LIQ starting: p%s/dql%s/dqlClose%s",
                        position.getPositionLong().toPlainString(),
                        liqInfo.getDqlCurr().toPlainString(), bDQLCloseMin.toPlainString()));

                arbitrageService.startTradingOnDelta2(SignalType.B_PRE_LIQ, bestQuotes.getAsk1_p(), bestQuotes.getBid1_o(), bestQuotes, btcP);

            }
        }
    }

}
