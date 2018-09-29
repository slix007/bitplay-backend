package com.bitplay.market.bitmex;

import com.bitplay.api.controller.DebugEndpoints;
import com.bitplay.api.service.RestartService;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.arbitrage.dto.AvgPrice;
import com.bitplay.arbitrage.dto.AvgPriceItem;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.events.SignalEvent;
import com.bitplay.market.BalanceService;
import com.bitplay.market.DefaultLogService;
import com.bitplay.market.ExtrastopService;
import com.bitplay.market.LogService;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketState;
import com.bitplay.market.bitmex.exceptions.ReconnectFailedException;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.model.Affordable;
import com.bitplay.market.model.BitmexXRateLimit;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.MoveResponse.MoveOrderStatus;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.OrderRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.FplayOrderUtils;
import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.ContractType;
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
import info.bitrich.xchangestream.service.exception.NotConnectedException;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.swagger.client.model.Error;
import io.swagger.client.model.Execution;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.PreDestroy;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitmex.service.BitmexAccountService;
import org.knowm.xchange.bitmex.service.BitmexTradeService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.ContractIndex;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.utils.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import si.mazi.rescu.HttpStatusIOException;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Service("bitmex")
public class BitmexService extends MarketService {
    private final static Logger logger = LoggerFactory.getLogger(BitmexService.class);
    private static final Logger ordersLogger = LoggerFactory.getLogger("BITMEX_ORDERS_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    public static final String NAME = "bitmex";

    private BitmexStreamingExchange exchange;

    private static final int MAX_RECONNECTS_BEFORE_RESTART = 1;
    private static final int MAX_WAITING_OB_CHECKS = 20; // each sleep 1 sec. // Bitmex min delay is 2,5 sec: https://blog.bitmex.com/ru_ru-update-to-our-realtime-apis-image-delivery/
    private static final int MAX_RESUBSCRIBES = 10;
    private volatile boolean isDestroyed = false;

    // Moving timeout
    private volatile ScheduledFuture<?> scheduledMoveInProgressReset;
    private volatile ScheduledFuture<?> scheduledMovingErrorsReset;
    private volatile boolean movingInProgress = false;
    private static final int MAX_MOVING_TIMEOUT_SEC = 2;
    private static final int MAX_MOVING_OVERLOAD_ATTEMPTS_TIMEOUT_SEC = 60;
    private volatile AtomicInteger movingErrorsOverloaded = new AtomicInteger(0);
    private volatile BitmexXRateLimit xRateLimit = BitmexXRateLimit.initValue();

    private volatile BigDecimal prevCumulativeAmount;

    private volatile AtomicInteger obWrongCount = new AtomicInteger(0);

    private volatile Disposable orderBookSubscription;
    private volatile Disposable openOrdersSubscription;
    private volatile Disposable accountInfoSubscription;
    private volatile Disposable positionSubscription;
    private volatile Disposable futureIndexSubscription;
    private volatile Disposable onDisconnectSubscription;
    @SuppressWarnings({"UnusedDeclaration"})
    private BitmexSwapService bitmexSwapService;

    private ArbitrageService arbitrageService;

    @Autowired
    private BitmexBalanceService bitmexBalanceService;

    @Autowired
    private BitmexTradeLogger tradeLogger;
    @Autowired
    private DefaultLogService defaultLogger;

    @Autowired
    private RestartService restartService;
    private volatile Date orderBookLastTimestamp = new Date();

    @Autowired
    private PosDiffService posDiffService;
    @Autowired
    private PersistenceService persistenceService;
    @Autowired
    private SettingsRepositoryService settingsRepositoryService;
    @Autowired
    private OrderRepositoryService orderRepositoryService;
    @Autowired
    private BitmexLimitsService bitmexLimitsService;
    @Autowired
    private ExtrastopService extrastopService;
    private String key;
    private String secret;
    private Disposable restartTimer;
    private BitmexContractType bitmexContractType;
    private BitmexContractType bitmexContractTypeXBTUSD = BitmexContractType.XBTUSD;
    private AtomicInteger cancelledInRow = new AtomicInteger();
    private volatile boolean reconnectInProgress = false;
    private volatile AtomicInteger reconnectCount = new AtomicInteger(0);
    private volatile AtomicInteger orderBookErrors = new AtomicInteger(0);
    private volatile BigDecimal cm = null; // correlation multiplier
    private final static BigDecimal DEFAULT_CM = BigDecimal.valueOf(100);


    public Date getOrderBookLastTimestamp() {
        return orderBookLastTimestamp;
    }

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
    public boolean isMarketStopped() {
        return getMarketState().isStopped() || bitmexLimitsService.outsideLimits();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public LogService getTradeLogger() {
        return tradeLogger;
    }

    @Override
    public LogService getLogger() {
        return defaultLogger;
    }

    @Override
    protected Exchange getExchange() {
        return exchange;
    }

    @Override
    public String getFuturesContractName() {
        return bitmexContractType.getSymbol();
    }

    public BigDecimal getCm() {
        if (cm == null) {
            return DEFAULT_CM;
        }
        return cm;
    }

    @Override
    public boolean isStarted() {
        return !bitmexContractType.isEth()
                ||
                (bitmexContractType.isEth() && cm != null);
    }

    @Scheduled(fixedDelay = 2000)
    public void openOrdersCleaner() {
        Instant start = Instant.now();
        if (openOrders.size() > 0) {
            cleanOldOO();
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "openOrdersCleaner");
    }

    @Scheduled(fixedDelay = 5000)
    public void posXBTUSDUpdater() {
        Instant start = Instant.now();
        if (bitmexContractType.isEth()) {
            try {
                final BitmexAccountService accountService = (BitmexAccountService) exchange.getAccountService();
                Position pUpdate = accountService.fetchPositionInfo(bitmexContractTypeXBTUSD.getSymbol());

                BigDecimal leverage = pUpdate.getLeverage().signum() == 0 ? BigDecimal.valueOf(100) : pUpdate.getLeverage();
                BigDecimal liqPrice = pUpdate.getLiquidationPrice().signum() == 0 ? this.position.getLiquidationPrice() : pUpdate.getLiquidationPrice();
                BigDecimal markValue = pUpdate.getMarkValue() != null ? pUpdate.getMarkValue() : this.position.getMarkValue();
                BigDecimal avgPriceL = pUpdate.getPriceAvgLong().signum() == 0 ? this.position.getPriceAvgLong() : pUpdate.getPriceAvgLong();
                BigDecimal avgPriceS = pUpdate.getPriceAvgShort().signum() == 0 ? this.position.getPriceAvgShort() : pUpdate.getPriceAvgShort();
                this.positionXBTUSD = new Position(
                        pUpdate.getPositionLong(),
                        pUpdate.getPositionShort(),
                        leverage,
                        liqPrice,
                        markValue,
                        avgPriceL,
                        avgPriceS,
                        pUpdate.getRaw()
                );

            } catch (HttpStatusIOException e) {
                updateXRateLimit(e);

                overloadByXRateLimit();

                if (e.getMessage().contains("HTTP status code was not OK: 429")) {
                    logger.warn("WARNING:" + e.getMessage());
                    warningLogger.warn("WARNING:" + e.getMessage());
                    setOverloaded(null);
                }
                if (e.getMessage().contains("HTTP status code was not OK: 403")) {// banned, no repeats
                    logger.warn("Banned:" + e.getMessage());
                    warningLogger.warn("Banned:" + e.getMessage());
                    setOverloaded(null);
                }
            } catch (Exception e) {
                logger.error("posXBTUSDUpdater:", e);
            }

        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "openOrdersCleaner");
    }


    public boolean isReconnectInProgress() {
        return reconnectInProgress;
    }

    public Integer getReconnectCount() {
        return reconnectCount.get();
    }

    public Integer getOrderBookErrors() {
        return orderBookErrors.get();
    }

    @Scheduled(fixedDelay = 30000)
    public void dobleCheckAvailableBalance() {
        Instant start = Instant.now();
        if (accountInfoContracts == null) {
            tradeLogger.warn("WARNING: Bitmex Balance is null. Restarting accountInfoListener");
            warningLogger.warn("WARNING: Bitmex Balance is null. Restarting accountInfoListener");
            accountInfoSubscription.dispose();
            accountInfoSubscription = startAccountInfoListener();
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "doubleCheckAvailableBalance");
    }

    public void afterReconnect() {
        Disposable subscribe = Single.fromCallable(() -> {

            logger.info("after reconnect check.");

            dobleCheckAvailableBalance();

            fetchOpenOrders();

            if (!hasOpenOrders()) {
                logger.info("market-ready after reconnect: ");
                setFree();
            } else {
                String msg = String.format("Warning: Bitmex reconnect is finished, but there are %s openOrders.", getOnlyOpenOrders().size());
                tradeLogger.info(msg);
                warningLogger.info(msg);
                logger.info(msg);
            }
            return new Object();
        })
                .subscribeOn(Schedulers.computation())
                .subscribe();

    }


    @Override
    public void initializeMarket(String key, String secret, ContractType contractType) {
        bitmexContractType = (BitmexContractType) contractType;

        scheduledMoveInProgressReset = scheduler.scheduleAtFixedRate(
                DebugEndpoints::detectDeadlock,
                5,
                60,
                TimeUnit.SECONDS);


        this.usdInContract = 1;
        this.key = key;
        this.secret = secret;
        bitmexSwapService = new BitmexSwapService(this, arbitrageService);

        loadLiqParams();

        initWebSocketConnection();

        startAllListeners();

    }

    private void startAllListeners() {

        logger.info("startAllListeners");
        orderBookSubscription = startOrderBookListener();
        accountInfoSubscription = startAccountInfoListener();
        openOrdersSubscription = startOpenOrderListener();
        positionSubscription = startPositionListener();
        futureIndexSubscription = startFutureIndexListener();

    }

    public void reSubscribeOrderBooks(boolean force) throws ReconnectFailedException, TimeoutException {

        if (force || !orderBookIsFilled() || !orderBookForPriceIsFilled()) {

            String msgOb = String.format("re-subscribe OrderBook: asks=%s, bids=%s, timestamp=%s. ",
                    orderBook.getAsks().size(),
                    orderBook.getBids().size(),
                    orderBook.getTimeStamp());
            tradeLogger.info(msgOb);
            warningLogger.info(msgOb);
            logger.info(msgOb);

            orderBook = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
            orderBookForPrice = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());

            orderBookSubscription.dispose();
            List<String> symbols = new ArrayList<>();
            symbols.add(bitmexContractType.getSymbol());
            if (!sameOrderBookForPrice()) {
                symbols.add(bitmexContractTypeXBTUSD.getSymbol());
            }

            Throwable throwable = ((BitmexStreamingMarketDataService) exchange.getStreamingMarketDataService())
                    .unsubscribeOrderBook(symbols)
                    .doOnComplete(() -> orderBookSubscription = startOrderBookListener())
                    .blockingGet(5, TimeUnit.SECONDS);
            if (throwable != null) {
                throw new ReconnectFailedException(throwable);
            }
        }
    }

    private void checkForRestart() {
        logger.info("checkForRestart reconnectInProgress={}. {}", reconnectInProgress, getSubscribersStatuses());
        requestReconnect(false);
    }

    public void requestReconnect(boolean isForceReconnect) {
        if (isDestroyed) {
            return;
        }

        logger.info("requestReconnect(Restart) reconnectInProgress={}. {}", reconnectInProgress, getSubscribersStatuses());

        if (!reconnectInProgress) {
            boolean needReconnect = isForceReconnect;

            if (!needReconnect) {
                if (orderBookSubscription != null
                        && accountInfoSubscription != null
                        && openOrdersSubscription != null
                        && positionSubscription != null
                        && futureIndexSubscription != null) {
                    if (orderBookSubscription.isDisposed()
                            || accountInfoSubscription.isDisposed()
                            || openOrdersSubscription.isDisposed()
                            || positionSubscription.isDisposed()
                            || futureIndexSubscription.isDisposed()) {

                        needReconnect = true;

                    } else {
                        logger.info("no Restart: everything looks ok " + getSubscribersStatuses());
                    }
                }
            }

            if (needReconnect) {
                reconnectInProgress = true;

                try {
                    reconnectOrRestart();
                } catch (Exception e) {
                    final String msg = String.format("Reconnect exception: %s", e.getMessage());
                    warningLogger.error(msg);
                    tradeLogger.error(msg);
                    logger.error(msg, e);
                } finally {
                    reconnectInProgress = false;
                }

            }

        }
    }

    private void reconnectOrRestart() {
        final Integer maxBitmexReconnects = settingsRepositoryService.getSettings().getRestartSettings().getMaxBitmexReconnects();
        int currReconnectCount = reconnectCount.incrementAndGet();
        if (currReconnectCount >= maxBitmexReconnects) {
            doRestart(String.format("Warning: Bitmex max reconnects(%s) is reached.", currReconnectCount));
            return;
        }
        int attempt = 0;
        while (true) {
            try {
                final String msg = String.format("Warning: Bitmex reconnect(%s) attempt=%s. %s", currReconnectCount, attempt, getSubscribersStatuses());
                warningLogger.info(msg);
                tradeLogger.info(msg);
                logger.info(msg);

                reconnect();
                break;

            } catch (ReconnectFailedException e) {
                if (++attempt >= MAX_RECONNECTS_BEFORE_RESTART) {
                    doRestart(String.format("Warning: Bitmex reconnect(%s) attempt=%s failed.", currReconnectCount, attempt));
                    break;
                }
            }
        }
    }

    private String getSubscribersStatuses() {
        return String.format(" Check for isDisposed: orderBookSub=%s, accountInfoSub=%s," +
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
        if (getMarketState() == MarketState.SYSTEM_OVERLOADED) {
            logger.warn("WARNING: no position fetch: SYSTEM_OVERLOADED");
            warningLogger.warn("WARNING: no position fetch: SYSTEM_OVERLOADED");
            return BitmexUtils.positionToString(position);
        }
        final Position pUpdate;
        try {
            final BitmexAccountService accountService = (BitmexAccountService) exchange.getAccountService();
            pUpdate = accountService.fetchPositionInfo(bitmexContractType.getSymbol());

            mergePosition(pUpdate);
            recalcAffordableContracts();
            recalcLiqInfo();

        } catch (HttpStatusIOException e) {
            updateXRateLimit(e);

            overloadByXRateLimit();

            if (e.getMessage().contains("HTTP status code was not OK: 429")) {
                logger.warn("WARNING:" + e.getMessage());
                warningLogger.warn("WARNING:" + e.getMessage());
                setOverloaded(null);
            }
            if (e.getMessage().contains("HTTP status code was not OK: 403")) {// banned, no repeats
                logger.warn("Banned:" + e.getMessage());
                warningLogger.warn("Banned:" + e.getMessage());
                setOverloaded(null);
            }
            throw e;
        }
        return BitmexUtils.positionToString(pUpdate);
    }

    private synchronized void mergePosition(Position pUpdate) {
        if (pUpdate.getPositionLong() == null) {
            if (this.position.getPositionLong() != null) {
                return; // no update when null
            }

            // use 0 when no pos yet
            pUpdate = new Position(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    "position is empty"
            );

        }
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
    protected void onReadyState() {
        iterateOpenOrdersMove();
    }

    @Override
    public ContractType getContractType() {
        return bitmexContractType;
    }

    @Override
    protected void iterateOpenOrdersMove() { // if synchronized then the queue for moving could be long
        final MarketState marketState = getMarketState();
        if (marketState == MarketState.SYSTEM_OVERLOADED
                || marketState == MarketState.PLACING_ORDER
                || isMarketStopped()) {
            return;
        }

        if (movingInProgress) {

            // Should not happen ever, because 'synch' on method
            final String logString = String.format("#%s No moving. Too often requests.", getCounterName());
            logger.error(logString);
            return;

        } else {
            movingInProgress = true;
            scheduledMoveInProgressReset = scheduler.schedule(() -> movingInProgress = false, MAX_MOVING_TIMEOUT_SEC, TimeUnit.SECONDS);
        }

        synchronized (openOrdersLock) {
            if (hasOpenOrders()) {

                final SysOverloadArgs sysOverloadArgs = settingsRepositoryService.getSettings().getBitmexSysOverloadArgs();
                final Integer maxAttempts = sysOverloadArgs.getMovingErrorsForOverload();

                distinctOpenOrders();

                openOrders = openOrders.stream()
                        .flatMap(openOrder -> {
                            Stream<FplayOrder> orderStream = Stream.of(openOrder); // default - the same

                            if (openOrder == null || openOrder.getOrderId() == null || openOrder.getOrderId().equals("0")) {
                                warningLogger.warn("OO is null. " + openOrder);
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
                                    if (overloadByXRateLimit()) {
                                        movingErrorsOverloaded.set(0);
                                    } else if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.ALREADY_CLOSED) {
                                        // update the status
                                        final FplayOrder cancelledFplayOrder = response.getCancelledFplayOrder();
                                        if (cancelledFplayOrder != null) {
                                            orderStream = Stream.of(cancelledFplayOrder);
                                            final LimitOrder cancelledOrder = (LimitOrder) cancelledFplayOrder.getOrder();
                                            arbitrageService.getDealPrices().getbPriceFact()
                                                    .addPriceItem(cancelledFplayOrder.getCounterName(), cancelledOrder.getId(),
                                                            cancelledOrder.getCumulativeAmount(),
                                                            cancelledOrder.getAveragePrice(), cancelledOrder.getStatus());
                                        }

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

                                                arbitrageService.getDealPrices().getbPriceFact()
                                                        .addPriceItem(cancelledFplayOrder.getCounterName(), cancelledOrder.getId(),
                                                                cancelledOrder.getCumulativeAmount(),
                                                                cancelledOrder.getAveragePrice(), cancelledOrder.getStatus());

                                                final TradeResponse tradeResponse = placeOrder(new PlaceOrderArgs(
                                                        cancelledOrder.getType(),
                                                        cancelledOrder.getTradableAmount().subtract(cancelledOrder.getCumulativeAmount()),
                                                        openOrder.getBestQuotes(),
                                                        openOrder.getPlacingType(),
                                                        openOrder.getSignalType(),
                                                        1,
                                                        cancelledFplayOrder.getCounterName()));

                                                final Stream.Builder<FplayOrder> streamBuilder = Stream.builder();
                                                // 1. old order
                                                streamBuilder.add(cancelledFplayOrder);

                                                // 2. new order
                                                final LimitOrder placedOrder = tradeResponse.getLimitOrder();
                                                if (placedOrder != null) {
                                                    streamBuilder.add(new FplayOrder(openOrder.getCounterName(), placedOrder, openOrder.getBestQuotes(),
                                                            openOrder.getPlacingType(), openOrder.getSignalType()));
                                                    arbitrageService.getDealPrices().getbPriceFact()
                                                            .addPriceItem(openOrder.getCounterName(), placedOrder.getId(),
                                                            placedOrder.getCumulativeAmount(), placedOrder.getAveragePrice(),
                                                            placedOrder.getStatus());
                                                }

                                                // 3. failed on placing
                                                tradeResponse.getCancelledOrders()
                                                        .forEach(limitOrder -> streamBuilder.add(
                                                                new FplayOrder(openOrder.getCounterName(), limitOrder, openOrder.getBestQuotes(),
                                                                        openOrder.getPlacingType(), openOrder.getSignalType())));
                                                orderStream = streamBuilder.build();

                                            }

                                            scheduledMoveInProgressReset = scheduler.schedule(() -> movingErrorsOverloaded.set(0),
                                                    MAX_MOVING_OVERLOAD_ATTEMPTS_TIMEOUT_SEC, TimeUnit.SECONDS);
                                        }

                                    } else if (response.getMoveOrderStatus() == MoveOrderStatus.EXCEPTION_SYSTEM_OVERLOADED) {

                                        if (movingErrorsOverloaded.incrementAndGet() >= maxAttempts) {
                                            setOverloaded(null);
                                            movingErrorsOverloaded.set(0);
                                        } else {
                                            scheduledMoveInProgressReset = scheduler.schedule(() -> movingErrorsOverloaded.set(0),
                                                    MAX_MOVING_OVERLOAD_ATTEMPTS_TIMEOUT_SEC, TimeUnit.SECONDS);
                                        }

                                    } else if (response.getMoveOrderStatus() == MoveOrderStatus.EXCEPTION
                                            || response.getMoveOrderStatus() == MoveOrderStatus.EXCEPTION_502_BAD_GATEWAY
                                            || response.getMoveOrderStatus() == MoveOrderStatus.EXCEPTION_NONCE
                                    ) {
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
        spec.setExchangeSpecificParametersItem("Symbol", bitmexContractType.getSymbol());
        spec.setExchangeSpecificParametersItem("Scale", bitmexContractType.getScale());

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

            exchangeConnect();

        } catch (Exception e) {
            logger.error("Connection failed", e);
            checkForRestart();
        }
    }

    private void exchangeConnect() {
        logger.info("bitmex connecting public");
        exchange.connect()
                .doOnError(throwable -> logger.error("doOnError", throwable))
//                .retryWhen(e -> e.delay(5, TimeUnit.SECONDS))
//                .retryWhen(e -> e.flatMap(throwable -> Flowable.timer(5, TimeUnit.SECONDS)))
                .retry()
                .doOnComplete(() -> logger.info("bitmex connecting public completed"))
                .blockingAwait();

        logger.info("bitmex authenticate");
        exchange.authenticate()
                .doOnError(throwable -> logger.error("doOnError authenticate", throwable))
                .retry()
//                .retryWhen(e -> e.delay(5, TimeUnit.SECONDS))
                .doOnComplete(() -> logger.info("bitmex authenticate completed"))
                .blockingAwait();

        // Retry on disconnect.
        onDisconnectSubscription = exchange.onDisconnect()
                .onErrorComplete()
                .subscribe(() -> {
                            logger.warn("onClientDisconnect BitmexService");
                            requestReconnect(true);
                },
                throwable -> {
                    String msg = "BitmexService onDisconnect exception. ";
                    logger.error(msg + throwable);
                    requestReconnect(true);
                });
    }

    public void reconnect() throws ReconnectFailedException {

        String startMsg = "Warning: Bitmex reconnect is starting. " + getSubscribersStatuses();
        tradeLogger.info(startMsg);
        warningLogger.info(startMsg);
        logger.info(startMsg);

        try {
            destroyAction(1);

            orderBook = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
            orderBookForPrice = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
            orderBookErrors.set(0);

            exchangeConnect();

            startAllListeners();

            int reSubAttempts = 0;
            while (reSubAttempts++ < MAX_RESUBSCRIBES) {

                int checkAttempts = 0;
                while (checkAttempts++ < MAX_WAITING_OB_CHECKS) {
                    if (orderBookIsFilled() && orderBookForPriceIsFilled()) {
                        break;
                    }
                    Thread.sleep(1000);
                }

                if (orderBookIsFilled() && orderBookForPriceIsFilled()) {
                    break;
                }

                reSubscribeOrderBooks(false);
            }

            String msgOb = String.format("OrderBook: asks=%s, bids=%s, timestamp=%s. ",
                    orderBook.getAsks().size(),
                    orderBook.getBids().size(),
                    orderBook.getTimeStamp());
            String msgObForPrice = sameOrderBookForPrice() ? ""
                    : String.format("OrderBookForPrice: asks=%s, bids=%s, timestamp=%s. ",
                            orderBookForPrice.getAsks().size(),
                            orderBookForPrice.getBids().size(),
                            orderBookForPrice.getTimeStamp());
            if (!orderBookIsFilled() || !orderBookForPriceIsFilled()) {
                String msg = String.format("OrderBook(ForPrice) is not full. %s; %s. %s",
                        msgOb,
                        msgObForPrice,
                        getSubscribersStatuses());
                throw new ReconnectFailedException(msg);
            } else {
                String finishMsg = String.format("Warning: Bitmex reconnect is finished. %s; %s. %s. OpenOrdersCount(но это не точно)=%s",
                        msgOb,
                        msgObForPrice,
                        getSubscribersStatuses(),
                        openOrders.size());

                tradeLogger.info(finishMsg);
                warningLogger.info(finishMsg);
                logger.info(finishMsg);

                afterReconnect();
            }

        } catch (Exception e) {
            String msg = "Warning: Bitmex reconnect error: " + e.getMessage() + getSubscribersStatuses();
            tradeLogger.info(msg);
            warningLogger.info(msg);
            logger.info(msg);

            throw new ReconnectFailedException(e);
        }
    }

    private boolean sameOrderBookForPrice() {
        return bitmexContractType == bitmexContractTypeXBTUSD;
    }

    private boolean orderBookIsFilled() {
        return orderBook.getAsks().size() > 10 && orderBook.getBids().size() > 10;
    }

    private boolean orderBookForPriceIsFilled() {
        return sameOrderBookForPrice()
                || (orderBookForPrice.getAsks().size() > 10 && orderBookForPrice.getBids().size() > 10);
    }

    private void doRestart(String errMsg) {
        errMsg += " Do restart. " + getSubscribersStatuses();
        warningLogger.info(errMsg);
        tradeLogger.info(errMsg);
        logger.info(errMsg);

        try {

            restartService.doFullRestart("BitmexService#doRestart(). orderBookLastTimestamp=" + orderBookLastTimestamp);

        } catch (IOException e) {
            logger.error("Error on full restart", e);
        }
    }

    @PreDestroy
    private void preDestroy() {
        isDestroyed = true;
        destroyAction(1);
    }

    private void destroyAction(int attempt) {

        try {
            logger.info("Bitmex destroyAction " + attempt);

            if (orderBookSubscription != null) {
                orderBookSubscription.dispose();
            }
            if (accountInfoSubscription != null) {
                accountInfoSubscription.dispose();
            }
            if (openOrdersSubscription != null) {
                openOrdersSubscription.dispose();
            }
            if (positionSubscription != null) {
                positionSubscription.dispose();
            }
            if (futureIndexSubscription != null) {
                futureIndexSubscription.dispose();
            }
            exchange.disconnect().blockingAwait();
            if (onDisconnectSubscription != null && !onDisconnectSubscription.isDisposed()) {
                onDisconnectSubscription.dispose();
            }

//            Completable.timer(5000, TimeUnit.MILLISECONDS)
//                    .onErrorComplete()
//                    .blockingAwait();

            if (attempt < 5 &&
                    (orderBookSubscription == null || !orderBookSubscription.isDisposed()
                            || accountInfoSubscription == null || !accountInfoSubscription.isDisposed()
                            || openOrdersSubscription == null || !openOrdersSubscription.isDisposed()
                            || positionSubscription == null || !positionSubscription.isDisposed()
                            || futureIndexSubscription == null || !futureIndexSubscription.isDisposed()
                    )) {
                logger.warn("Warning: destroy loop " + getSubscribersStatuses());
                attempt++;
                destroyAction(attempt);
            } else {
                logger.info("Destroy finished. " + getSubscribersStatuses());
            }
        } catch (Exception e) {
            logger.error("Destroy error. ", e);
        }
    }

    private OrderBook convertOrderBook(BitmexOrderBook obUpdate) {
        if (obUpdate.getBitmexOrderList().size() == 0 || obUpdate.getBitmexOrderList().get(0).getSymbol() == null) {
            // skip the update
            throw new IllegalArgumentException("OB update has no symbol. " + obUpdate);
        }
        CurrencyPair currencyPair;
        OrderBook fullOB;
        String symbol = obUpdate.getBitmexOrderList().get(0).getSymbol();
        if (symbol.equals(bitmexContractType.getSymbol())) {
            currencyPair = bitmexContractType.getCurrencyPair();
            fullOB = getFullOrderBook();
        } else if (symbol.equals(bitmexContractTypeXBTUSD.getSymbol())) {
            currencyPair = bitmexContractTypeXBTUSD.getCurrencyPair();
            fullOB = getFullOrderBookForPrice();
        } else {
            // skip the update
            throw new IllegalArgumentException("OB update has no symbol. " + obUpdate);
        }

        OrderBook finalOB;
        if (obUpdate.getAction().equals("partial")) {
            finalOB = BitmexStreamAdapters.adaptBitmexOrderBook(obUpdate, currencyPair);
        } else if (obUpdate.getAction().equals("delete")) {
            finalOB = BitmexStreamAdapters.delete(fullOB, obUpdate);
        } else if (obUpdate.getAction().equals("update")) {
            finalOB = BitmexStreamAdapters.update(fullOB, obUpdate, new Date(), currencyPair);
        } else if (obUpdate.getAction().equals("insert")) {
            finalOB = BitmexStreamAdapters.insert(fullOB, obUpdate, new Date(), currencyPair);
        } else {
            // skip the update
            throw new IllegalArgumentException("Unknown OrderBook action=" + obUpdate.getAction() + ". " + obUpdate);
        }
        if (finalOB.getBids().size() == 0 || finalOB.getAsks().size() == 0) {
            logger.warn("update OB is empty: " + obUpdate);
            logger.warn("full OB is empty: " + finalOB);
        } else if (!startFlag) {
            startFlag = true;
            logger.warn("update OB : " + obUpdate);
            logger.warn("full OB : " + finalOB);
            logger.info("full OB bids=" + finalOB.getBids().size());
        }

        return finalOB;
    }

    boolean startFlag = false;

    private Disposable startOrderBookListener() {
        startFlag = false;
        List<String> symbols = new ArrayList<>();
        symbols.add(bitmexContractType.getSymbol());
        if (!sameOrderBookForPrice()) {
            symbols.add(bitmexContractTypeXBTUSD.getSymbol());
        }

        return ((BitmexStreamingMarketDataService) exchange.getStreamingMarketDataService())
                .getOrderBookL2(symbols)
                .doOnError(throwable -> handleSubscriptionError(throwable, "can not get orderBook"))
                .subscribeOn(Schedulers.from(scheduler))
                .observeOn(Schedulers.from(scheduler))
                .map(this::convertOrderBook)
                .doOnDispose(() -> logger.info("bitmex subscription doOnDispose"))
                .doOnTerminate(() -> logger.info("bitmex subscription doOnTerminate"))
//                .subscribeOn(Schedulers.io())
                .filter(ob -> ob.getBids().size() != 0 && ob.getAsks().size() != 0)
                .doOnError(throwable -> {
                    logger.error("can not convert orderBook", throwable);
                    warningLogger.error("can not convert orderBook", throwable);
                    orderBookErrors.incrementAndGet();
                })
                .retry()
                .subscribe(orderBook -> {
                    try {
                        if (isDefaultOB(orderBook)) {
                            this.orderBook = orderBook;
                            afterOrderBookChanged(orderBook);
                        } else {
                            this.orderBookForPrice = orderBook;
                        }
                    } catch (Exception e) {
                        logger.error("Can not merge OrderBook", e);
                    }

                }, throwable -> {
                    logger.error("can not merge orderBook exception", throwable);
                    warningLogger.error("can not merge orderBook", throwable);
                    orderBookErrors.incrementAndGet();
                    checkForRestart();
                });
    }

    private boolean isDefaultOB(OrderBook orderBook) {
        CurrencyPair currencyPair = null;
        for (LimitOrder ask : orderBook.getAsks()) {
            currencyPair = ask.getCurrencyPair();
            break;
        }
        if (currencyPair == null) {
            for (LimitOrder bid : orderBook.getBids()) {
                currencyPair = bid.getCurrencyPair();
                break;
            }
        }
        if (currencyPair == null) {
            throw new IllegalArgumentException("OB update has no symbol. " + orderBook.toString());
        }
        return bitmexContractType.getCurrencyPair().equals(currencyPair);
    }

    private void afterOrderBookChanged(OrderBook orderBook) {
        if (orderBook != null && orderBook.getBids().size() > 0 && orderBook.getAsks().size() > 0) {
            final LimitOrder bestAsk = Utils.getBestAsk(orderBook);
            final LimitOrder bestBid = Utils.getBestBid(orderBook);

            if (bestAsk != null && bestBid != null) {
                orderBookLastTimestamp = new Date();
            }

            if (this.bestAsk != null && bestAsk != null && this.bestBid != null && bestBid != null
                    && this.bestAsk.compareTo(bestAsk.getLimitPrice()) != 0
                    && this.bestBid.compareTo(bestBid.getLimitPrice()) != 0) {
                recalcAffordableContracts();
                recalcLiqInfo();
            }
            this.bestAsk = bestAsk != null ? bestAsk.getLimitPrice() : BigDecimal.ZERO;
            this.bestBid = bestBid != null ? bestBid.getLimitPrice() : BigDecimal.ZERO;
            logger.debug("ask: {}, bid: {}", this.bestAsk, this.bestBid);
            if (this.bestBid.compareTo(this.bestAsk) >= 0) {
                String warn = String.format("#%s bid(%s) >= ask(%s). LastRun of 'checkOrderBooks' is %s. ",
                        getCounterName(), this.bestBid, this.bestAsk, extrastopService.getLastRun());
                if (obWrongCount.incrementAndGet() < 100) {
                    logger.warn(warn);
                    warningLogger.warn(warn);
                } else {
                    warn += "Do reconnect.";
                    logger.warn(warn);
                    warningLogger.warn(warn);
                    requestReconnect(true);
                    obWrongCount.set(0);
                }
            }

            getArbitrageService().getSignalEventBus().send(SignalEvent.B_ORDERBOOK_CHANGED);
        }
    }

    @Override
    public OrderBook getOrderBookForPrice() {
        OrderBook orderBook;
        if (sameOrderBookForPrice()) {
            synchronized (orderBookLock) {
                orderBook = getShortOrderBook(this.orderBook);
            }
        } else {
            synchronized (orderBookForPriceLock) {
                orderBook = getShortOrderBook(this.orderBookForPrice);
            }
        }

        return orderBook;
    }

    private Disposable startOpenOrderListener() {
        return exchange.getStreamingTradingService()
                .getOpenOrderObservable(bitmexContractType.getSymbol(), bitmexContractType.getScale())
                .doOnError(throwable -> handleSubscriptionError(throwable, "onOpenOrdersListening"))
                .doOnDispose(() -> logger.info("bitmex subscription doOnDispose"))
                .doOnTerminate(() -> logger.info("bitmex subscription doOnTerminate"))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribe(updateOfOpenOrders -> {
                    try {

                        mergeOpenOrders(updateOfOpenOrders);

                    } catch (Exception e) {
                        logger.error("Can not merge OpenOrders", e);
                    }

                }, throwable -> {
                    logger.error("Can not merge OpenOrders exception", throwable);
                    checkForRestart();
                });
    }

    private void mergeOpenOrders(OpenOrders updateOfOpenOrders) {
        synchronized (openOrdersLock) {
            logger.debug("OpenOrders: " + updateOfOpenOrders.toString());

            // update DealPrice object firstly
            updateOfOpenOrders.getOpenOrders()
                    .forEach(update -> {
                        LimitOrder limitOrder = update;
                        String counterName = getCounterName();

                        for (FplayOrder ord : openOrders) {
                            if (update.getId().equals(ord.getOrderId())) {
                                final FplayOrder fplayOrder = FplayOrderUtils.updateFplayOrder(ord, update);
                                limitOrder = (LimitOrder) fplayOrder.getOrder();
                                counterName = fplayOrder.getCounterName();
                                break;
                            }
                        }

                        setQuotesForArbLogs(counterName, limitOrder, limitOrder.getAveragePrice(), false);
                    });

            updateOpenOrders(updateOfOpenOrders.getOpenOrders()); // all there: add/update/remove -> free Market -> write logs

            // bitmex specific actions
            updateOfOpenOrders.getOpenOrders()
                    .forEach(update -> {
                        if (update.getStatus() == OrderStatus.FILLED) {
                            logger.info("#{} Order {} FILLED", getCounterName(), update.getId());
                            getArbitrageService().getSignalEventBus().send(SignalEvent.MT2_BITMEX_ORDER_FILLED);
                        }
                    });

        } // synchronized (openOrdersLock)
    }

    @Override
    public UserTrades fetchMyTradeHistory() {
        return null;
    }

    private synchronized void recalcAffordableContracts() {
        final BigDecimal reserveBtc = arbitrageService.getParams().getReserveBtc1();

        if (accountInfoContracts != null && Utils.orderBookIsFull(orderBook)) {
            final BigDecimal availableBtc = accountInfoContracts.getAvailable();
            final BigDecimal equityBtc = accountInfoContracts.geteMark();
            final OrderBook orderBook = getOrderBook();
            final BigDecimal bestAsk = Utils.getBestAsk(orderBook).getLimitPrice();
            final BigDecimal bestBid = Utils.getBestBid(orderBook).getLimitPrice();
            final BigDecimal positionContracts = position.getPositionLong();
            final BigDecimal leverage = position.getLeverage();

            if (availableBtc != null && equityBtc != null && positionContracts != null && leverage != null) {

                if (positionContracts.signum() == 0) {
                    affordable.setForLong(((availableBtc.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage)).setScale(0, BigDecimal.ROUND_DOWN));
                    affordable.setForShort(((availableBtc.subtract(reserveBtc)).multiply(bestBid).multiply(leverage)).setScale(0, BigDecimal.ROUND_DOWN));
                } else if (positionContracts.signum() > 0) {
                    affordable.setForLong(((availableBtc.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage)).setScale(0, BigDecimal.ROUND_DOWN));
                    BigDecimal forShort = (positionContracts.add((equityBtc.subtract(reserveBtc)).multiply(bestBid).multiply(leverage))).setScale(0, BigDecimal.ROUND_DOWN);
                    if (forShort.compareTo(positionContracts) < 0) {
                        forShort = positionContracts;
                    }
                    affordable.setForShort(forShort);
                } else if (positionContracts.signum() < 0) {
                    BigDecimal forLong = (positionContracts.negate().add((equityBtc.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage))).setScale(0, BigDecimal.ROUND_DOWN);
                    if (forLong.compareTo(positionContracts) < 0) {
                        forLong = positionContracts;
                    }
                    affordable.setForLong(forLong);
                    affordable.setForShort(((availableBtc.subtract(reserveBtc)).multiply(bestBid).multiply(leverage)).setScale(0, BigDecimal.ROUND_DOWN));
                }

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

    @Override
    public TradeResponse placeOrderOnSignal(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes,
                                            SignalType signalType) {
        throw new IllegalArgumentException("Use placeOrderToOpenOrders instead");
    }

    public TradeResponse nonTakerOrder(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes, SignalType signalType,
            PlacingType placingType) {
        return placeOrderToOpenOrders(getCounterName(signalType), orderType, amountInContracts, bestQuotes, placingType, signalType);
    }

    public TradeResponse takerOrder(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes, SignalType signalType) {
        return placeOrderToOpenOrders(getCounterName(signalType), orderType, amountInContracts, bestQuotes, PlacingType.TAKER, signalType);
    }

    public TradeResponse placeOrderToOpenOrders(String counterName, Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes,
                                                 PlacingType placingType, SignalType signalType) {
        final PlaceOrderArgs placeOrderArgs = new PlaceOrderArgs(orderType, amount, bestQuotes, placingType, signalType, 1, counterName);

        final TradeResponse tradeResponse = placeOrder(placeOrderArgs);

        // update this.openOrders
        final List<LimitOrder> updates = new ArrayList<>();
        if (tradeResponse.getCancelledOrders() != null) updates.addAll(tradeResponse.getCancelledOrders());
        if (tradeResponse.getLimitOrder() != null) updates.add(tradeResponse.getLimitOrder());

        LimitOrder limitOrder = tradeResponse.getLimitOrder();
        if (limitOrder == null) {
            String orderId = tradeResponse.getOrderId() != null ? tradeResponse.getOrderId() : "0";
            limitOrder = new LimitOrder(orderType, amount, CurrencyPair.BTC_USD, orderId, new Date(), BigDecimal.ZERO);
        }
        final FplayOrder stub = new FplayOrder(counterName, limitOrder, placeOrderArgs.getBestQuotes(), placeOrderArgs.getPlacingType(),
                placeOrderArgs.getSignalType());

        updateOpenOrders(updates, stub);

        return tradeResponse;
    }

    public TradeResponse placeOrder(final PlaceOrderArgs placeOrderArgs) {
        prevCumulativeAmount = BigDecimal.ZERO;

        final TradeResponse tradeResponse = new TradeResponse();

        final Settings settings = settingsRepositoryService.getSettings();
        final Integer maxAttempts = settings.getBitmexSysOverloadArgs().getPlaceAttempts();
        if (placeOrderArgs.getAttempt() == maxAttempts) {
            final String logString = String.format("#%s Bitmex Warning placing: too many attempt(%s) when SYSTEM_OVERLOADED. Do nothing.",
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
        PlacingType placingType = placeOrderArgs.getPlacingType();
        final SignalType signalType = placeOrderArgs.getSignalType();
        final String counterName = placeOrderArgs.getCounterName();

        if (placingType == null) {
            tradeLogger.warn("WARNING: placingType is null. " + placeOrderArgs);
            placingType = settings.getBitmexPlacingType();
        }

        MarketState nextMarketState = getMarketState();
        arbitrageService.setSignalType(signalType);

        try {
            setMarketState(MarketState.PLACING_ORDER);

            final BitmexTradeService bitmexTradeService = (BitmexTradeService) exchange.getTradeService();

            int attemptCount = 0;
            int badGatewayCount = 0;
            shouldStopPlacing = false;
            while (attemptCount < maxAttempts && !getMarketState().isStopped() && !shouldStopPlacing) {
                attemptCount++;
                try {
                    String orderId;
                    BigDecimal thePrice;
                    if (reconnectInProgress) {
                        tradeLogger.warn("placeOrder waiting for reconnect.");
                        while (reconnectInProgress) {
                            Thread.sleep(200);
                        }
                        tradeLogger.warn("placeOrder end waiting for reconnect.");
                    }

                    if (placingType != PlacingType.TAKER) {

                        final BigDecimal bitmexPrice = settings.getBitmexPrice();
                        if (bitmexPrice != null && bitmexPrice.signum() != 0) {
                            thePrice = bitmexPrice;
                        } else {
                            thePrice = createNonTakerPrice(orderType, placingType);
                        }
                        arbitrageService.getDealPrices().getbPriceFact().setOpenPrice(thePrice);

                        final LimitOrder requestOrder = new LimitOrder(orderType, amount, bitmexContractType.getCurrencyPair(), "0", new Date(), thePrice);
                        boolean participateOnly = placingType == PlacingType.MAKER || placingType == PlacingType.MAKER_TICK;
                        final LimitOrder resultOrder = bitmexTradeService.placeLimitOrderBitmex(requestOrder, participateOnly);
                        orderId = resultOrder.getId();
                        final FplayOrder fplayOrder = new FplayOrder(counterName, resultOrder, bestQuotes, placingType, signalType);
                        orderRepositoryService.save(fplayOrder);
                        if (orderId != null && !orderId.equals("0")) {
                            tradeResponse.setLimitOrder(resultOrder);
                            arbitrageService.getDealPrices().getbPriceFact()
                                    .addPriceItem(counterName, orderId, resultOrder.getCumulativeAmount(), resultOrder.getAveragePrice(),
                                            resultOrder.getStatus());
                        }

                        if (resultOrder.getStatus() == Order.OrderStatus.CANCELED) {
                            int cancelledCount = cancelledInRow.incrementAndGet();
                            if (cancelledCount == 5) {
                                tradeLogger.info("CANCELED more 4 in a row");
                            }
                            if (cancelledCount % 20 == 0) {
                                tradeLogger.info("CANCELED more 20 in a row. Do reconnect.");
                                requestReconnect(true);
                            }

                            tradeResponse.addCancelledOrder(requestOrder);
                            tradeResponse.setErrorCode("WAS CANCELED"); // for the last iteration
                            tradeResponse.setLimitOrder(null);
                            tradeLogger.info(String.format("#%s %s %s CANCELED amount=%s, filled=%s, quote=%s, orderId=%s",
                                    counterName,
                                    placingType,
                                    orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                                    amount.toPlainString(),
                                    resultOrder.getCumulativeAmount(),
                                    thePrice,
                                    orderId));
                            continue;
                        }
                        cancelledInRow.set(0);
                        nextMarketState = MarketState.ARBITRAGE;

                    } else { // TAKER
                        final MarketOrder marketOrder = new MarketOrder(orderType, amount, bitmexContractType.getCurrencyPair(), new Date());
                        final MarketOrder resultOrder = bitmexTradeService.placeMarketOrderBitmex(marketOrder);
                        orderId = resultOrder.getId();
                        thePrice = resultOrder.getAveragePrice();
                        final FplayOrder fplayOrder = new FplayOrder(counterName, resultOrder, bestQuotes, placingType, signalType);
                        orderRepositoryService.save(fplayOrder);
                        arbitrageService.getDealPrices().getbPriceFact().setOpenPrice(thePrice);
                        arbitrageService.getDealPrices().getbPriceFact()
                                .addPriceItem(counterName, orderId, resultOrder.getCumulativeAmount(), resultOrder.getAveragePrice(), resultOrder.getStatus());

                        // workaround for OO list: set as limit order
                        tradeResponse.setLimitOrder(new LimitOrder(orderType, amount, bitmexContractType.getCurrencyPair(), orderId, new Date(),
                                thePrice, thePrice, resultOrder.getCumulativeAmount(), resultOrder.getStatus()));
                    }

                    tradeResponse.setOrderId(orderId);
                    tradeResponse.setErrorCode(null);

                    if (bestQuotes != null) {
                        orderIdToSignalInfo.put(orderId, bestQuotes);
                    }

                    final String message = String.format("#%s %s %s amount=%s with quote=%s was placed.orderId=%s. pos=%s",
                            counterName,
                            placingType,
                            orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                            amount.toPlainString(),
                            thePrice,
                            orderId,
                            getPositionAsString());
                    tradeLogger.info(message);
                    ordersLogger.info(message);

                    break;
                } catch (HttpStatusIOException e) {
                    final String httpBody = e.getHttpBody();
                    tradeResponse.setErrorCode(httpBody);

                    HttpStatusIOExceptionHandler handler = new HttpStatusIOExceptionHandler(e, "PlaceOrderError", attemptCount).invoke();

                    if (overloadByXRateLimit()) {
                        nextMarketState = MarketState.SYSTEM_OVERLOADED;
                        tradeResponse.setErrorCode(e.getMessage());
                        break;
                    }

                    final MoveResponse.MoveOrderStatus placeOrderStatus = handler.getMoveResponse().getMoveOrderStatus();
                    if (MoveResponse.MoveOrderStatus.EXCEPTION_SYSTEM_OVERLOADED == placeOrderStatus) {
                        if (attemptCount < maxAttempts) {
//                            Thread.sleep(200);
                        } else {
                            setOverloaded(null);
                            nextMarketState = MarketState.SYSTEM_OVERLOADED;
                            tradeResponse.setErrorCode(e.getMessage());
                            break;
                        }
                    } else if (MoveResponse.MoveOrderStatus.EXCEPTION_502_BAD_GATEWAY == placeOrderStatus
                            || MoveOrderStatus.EXCEPTION_NONCE == placeOrderStatus) {
                        badGatewayCount++;
                        if (badGatewayCount < 3) {
                            Thread.sleep(200);
                        } else {
                            tradeResponse.setErrorCode(e.getMessage());
                            nextMarketState = MarketState.READY;
                            break;
                        }
                    } else {
                        break; // any unknown exception - no retry
                    }
                } catch (Exception e) {
                    final String message = e.getMessage();
                    tradeResponse.setErrorCode(message);

                    final String logString = String.format("#%s/%s PlaceOrderError: %s", counterName, attemptCount, message);
                    logger.error(logString, e);
                    tradeLogger.error(logString);
                    warningLogger.error(logString);

                    // message.startsWith("Connection refused") - when we got banned for a week. Just skip it.
                    // message.startsWith("Read timed out")
//                    if (message != null &&
//                            (message.startsWith("Network is unreachable") || message.startsWith("connect timed out"))) {
//                        if (attemptCount < maxAttempts) {
//                            Thread.sleep(1000);
//                        } else {
//                            setOverloaded(null);
//                            break;
//                        }
//                    } else {
                        break; // any unknown exception - no retry
//                    }

                }
            } // while

            String errorCode = "attemtp=" + attemptCount;
            if (tradeResponse.getErrorCode() != null) {
                errorCode += "," + tradeResponse.getErrorCode();
            }
            tradeResponse.setErrorCode(errorCode);

        } catch (Exception e) {
            logger.error("Place market order error", e);
            tradeLogger.info(String.format("maker error %s", e.toString()));
            tradeResponse.setErrorCode(e.getMessage());
        }

        try {
            if (placeOrderArgs.getSignalType().isCorr()) { // It's only TAKER, so it should be DONE, if no errors
                if (tradeResponse.getOrderId() != null) {
                    posDiffService.finishCorr(true); // - Only when FILLED by subscription
                } else {
                    posDiffService.finishCorr(false);
                }
                nextMarketState = MarketState.READY;
                setMarketState(nextMarketState, counterName);
                eventBus.send(BtsEvent.MARKET_FREE);
            }
        } finally {
            setMarketState(nextMarketState, counterName);
        }

        return tradeResponse;
    }

    @Override
    public MoveResponse moveMakerOrder(FplayOrder fplayOrder, BigDecimal newPrice) {
        final LimitOrder limitOrder = (LimitOrder) fplayOrder.getOrder();
        MoveResponse moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "do nothing by default");

        if (fplayOrder.getPlacingType() != null && fplayOrder.getPlacingType() == PlacingType.TAKER) {
            return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "no moving. Order was placed as taker.");
        }

        final String counterName = fplayOrder.getCounterName();
        try {
            BigDecimal bestMakerPrice = newPrice.setScale(bitmexContractType.getScale(), BigDecimal.ROUND_HALF_UP);

            assert bestMakerPrice.signum() != 0;
            assert bestMakerPrice.compareTo(limitOrder.getLimitPrice()) != 0;

            final LimitOrder movedLimitOrder = ((BitmexTradeService) exchange.getTradeService())
                    .moveLimitOrder(limitOrder, bestMakerPrice);

            if (movedLimitOrder != null) {

                orderRepositoryService.updateOrder(fplayOrder, movedLimitOrder);
                FplayOrder updated = FplayOrderUtils.updateFplayOrder(fplayOrder, movedLimitOrder);

                boolean showDiff = false;
                if (movedLimitOrder.getCumulativeAmount().compareTo(prevCumulativeAmount) > 0) {
                    showDiff = true;
                }
                prevCumulativeAmount = movedLimitOrder.getCumulativeAmount();

                final LimitOrder updatedOrder = (LimitOrder)updated.getOrder();

                String diffWithSignal = setQuotesForArbLogs(updated.getCounterName(), limitOrder, bestMakerPrice, showDiff);

                final String logString = String.format("#%s Moved %s from %s to %s(real %s) status=%s, amount=%s, filled=%s, avgPrice=%s, id=%s, pos=%s. %s.",
                        counterName,
                        limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                        limitOrder.getLimitPrice(),
                        bestMakerPrice.toPlainString(),
                        updatedOrder.getLimitPrice(),
                        updatedOrder.getStatus(),
                        limitOrder.getTradableAmount(),
                        limitOrder.getCumulativeAmount(),
                        limitOrder.getAveragePrice(),
                        limitOrder.getId(),
                        getPositionAsString(),
                        diffWithSignal);
                logger.info(logString);
                tradeLogger.info(logString);
                ordersLogger.info(logString);

                arbitrageService.getDealPrices().getbPriceFact()
                        .addPriceItem(counterName, updatedOrder.getId(), updatedOrder.getCumulativeAmount(), updatedOrder.getAveragePrice(),
                                updatedOrder.getStatus());

                if (updatedOrder.getStatus() == Order.OrderStatus.CANCELED) {
                    int cancelledCount = cancelledInRow.incrementAndGet();
                    if (cancelledCount == 5) {
                        tradeLogger.info("CANCELED more 4 in a row");
                    }
                    if (cancelledCount % 20 == 0) {
                        tradeLogger.info("CANCELED more 20 in a row. Do reconnect.");
                        requestReconnect(true);
                    }
                    moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.ONLY_CANCEL, logString, null, null, updated);
                } else {
                    cancelledInRow.set(0);
                    moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.MOVED, logString, updatedOrder, updated);
                }

            } else {
                logger.info("Moving response is null");
                tradeLogger.info("Moving response is null");
            }

        } catch (HttpStatusIOException e) {

            HttpStatusIOExceptionHandler handler = new HttpStatusIOExceptionHandler(
                    e,
                    String.format("MoveOrderError:ordId=%s", limitOrder.getId()),
                    movingErrorsOverloaded.get()
            ).invoke();
            moveResponse = handler.getMoveResponse();
            // double check  "Invalid ordStatus"
            if (moveResponse.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.ALREADY_CLOSED) {
                final Optional<Order> orderInfo = getOrderInfo(limitOrder.getId(), counterName, 1, "Moving:CheckInvOrdStatus:");
                if (orderInfo.isPresent()) {
                    final Order doubleChecked = orderInfo.get();
                    final FplayOrder updated = FplayOrderUtils.updateFplayOrder(fplayOrder, (LimitOrder) doubleChecked);
                    if (doubleChecked.getStatus() == Order.OrderStatus.FILLED || doubleChecked.getStatus() == Order.OrderStatus.CANCELED) { // just update the status
                        moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, moveResponse.getDescription(), null, null, updated);
                    }
                } else {
                    moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, moveResponse.getDescription());
                }
            }


        } catch (Exception e) {

            final String message = e.getMessage();
            final String logString = String.format("#%s/%s MovingError id=%s: %s", counterName, movingErrorsOverloaded.get(), limitOrder.getId(), message);
            logger.error(logString, e);
            tradeLogger.error(logString);
            warningLogger.error(logString);

            // message.startsWith("Connection refused") - when we got banned for a week. Just skip it.
            // message.startsWith("Read timed out")
//            if (message.startsWith("Network is unreachable")
//                    || message.startsWith("connect timed out")) {
//                tradeLogger.error("{} MoveOrderError: {}", getCounterName(), message);
//                moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION_SYSTEM_OVERLOADED, message);
//            }

        }

        return moveResponse;
    }

    private String setQuotesForArbLogs(String counterName, LimitOrder limitOrder, BigDecimal openPrice, boolean showDiff) {
        String diffWithSignal = "";
        if (openPrice != null) {
            arbitrageService.getDealPrices().getbPriceFact().setOpenPrice(openPrice);

            if (showDiff) {
                diffWithSignal = arbitrageService.getDealPrices().getDiffB().str;
            }
        }

        arbitrageService.getDealPrices().getbPriceFact()
                .addPriceItem(counterName, limitOrder.getId(), limitOrder.getCumulativeAmount(), limitOrder.getAveragePrice(), limitOrder.getStatus());
        return diffWithSignal;
    }

    @Override
    public TradeService getTradeService() {
        return exchange.getTradeService();
    }

    private Disposable startAccountInfoListener() {
        Observable<AccountInfoContracts> accountInfoObservable = ((BitmexStreamingAccountService) exchange.getStreamingAccountService())
                .getAccountInfoContractsObservable()
                .doOnError(throwable -> handleSubscriptionError(throwable, "Account fetch error"))
                .doOnDispose(() -> logger.info("bitmex subscription doOnDispose"))
                .doOnTerminate(() -> logger.info("bitmex subscription doOnTerminate"))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS));

        return accountInfoObservable
                .subscribe(newInfo -> {
                    try {

                        mergeAccountInfo(newInfo);

                    } catch (Exception e) {
                        logger.error("Can not merge AccountInfo", e);
                    }

                }, throwable -> {
                    logger.error("Can not merge AccountInfo exception", throwable);
                    checkForRestart();
                });
    }

    private void handleSubscriptionError(Throwable throwable, String errorMessage) {
        if (throwable instanceof NotConnectedException) {
            logger.error(errorMessage + ". " + throwable.getMessage());
        } else {
            logger.error(errorMessage, throwable);
            requestReconnect(true);
        }
    }

    private synchronized void mergeAccountInfo(AccountInfoContracts newInfo) {
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

    private Disposable startPositionListener() {
        Observable<Position> positionObservable = ((BitmexStreamingAccountService) exchange.getStreamingAccountService())
                .getPositionObservable(bitmexContractType.getSymbol())
                .doOnError(throwable -> handleSubscriptionError(throwable, "Position fetch error"))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS));

        return positionObservable
                .subscribe(pUpdate -> {
                    try {

                        mergePosition(pUpdate);
                        recalcAffordableContracts();
                        recalcLiqInfo();

                    } catch (Exception e) {
                        logger.error("Can not merge Position", e);
                    }
                }, throwable -> {
                    logger.error("Can not merge Position exception", throwable);
                    checkForRestart();
                });
    }

    private Disposable startFutureIndexListener() {
        List<String> symbols = new ArrayList<>();
        symbols.add(bitmexContractType.getSymbol());
        if (bitmexContractType.isEth()) {
            symbols.add(BitmexContractType.XBTUSD.getSymbol());
        }

        Observable<BitmexContractIndex> indexObservable = ((BitmexStreamingMarketDataService) exchange.getStreamingMarketDataService())
                .getContractIndexObservable(symbols)
                .doOnError(throwable -> handleSubscriptionError(throwable, "Index fetch error"))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS));

        return indexObservable
                .observeOn(Schedulers.computation())
                .subscribe(contIndUpdate -> {
                    try {

                        synchronized (contractIndexLock) {
                            if (bitmexContractType.isEth() && contIndUpdate.getSymbol().equals(BitmexContractType.XBTUSD.getSymbol())) {

                                this.btcContractIndex = mergeContractIndex(this.btcContractIndex, contIndUpdate);

                                if (this.contractIndex instanceof BitmexContractIndex) {
                                    calcCM();
                                }

                            } else {

                                BitmexContractIndex bitmexContractIndex = mergeContractIndex(this.contractIndex, contIndUpdate);
                                this.contractIndex = bitmexContractIndex;
                                this.ticker = new Ticker.Builder().last(bitmexContractIndex.getLastPrice()).timestamp(new Date()).build();

                                if (cm != null) {
                                    calcCM();
                                }
                            }
                        }

                    } catch (Exception e) {
                        logger.error("Can not merge contractIndex", e);
                    }

                }, throwable -> {
                    logger.error("Can not merge contractIndex exception", throwable);
                    checkForRestart();
                });
    }

    private void calcCM() {
        if (this.contractIndex instanceof BitmexContractIndex && this.btcContractIndex instanceof BitmexContractIndex) {
            final BigDecimal bxbtIndex = btcContractIndex.getIndexPrice();
            final BigDecimal ethUsdMark = ((BitmexContractIndex) this.contractIndex).getMarkPrice();
            // CM = round(10000000 / (ETHUSD_mark BXBT);2);
            this.cm = BigDecimal.valueOf(10 * 1000 * 1000).divide(bxbtIndex.multiply(ethUsdMark), 2, RoundingMode.HALF_UP);
        }
    }

    private BitmexContractIndex mergeContractIndex(ContractIndex current, BitmexContractIndex update) {
        // merge contractIndex
        final BigDecimal indexPrice = update.getIndexPrice() != null
                ? update.getIndexPrice()
                : current.getIndexPrice();
        final BigDecimal markPrice;
        final BigDecimal lastPrice;
        final BigDecimal fundingRate;
        final OffsetDateTime fundingTimestamp;
        if (current instanceof BitmexContractIndex) {
            BitmexContractIndex cur = (BitmexContractIndex) current;
            markPrice = update.getMarkPrice() != null ? update.getMarkPrice() : cur.getMarkPrice();
            lastPrice = update.getLastPrice() != null ? update.getLastPrice() : cur.getLastPrice();
            fundingRate = update.getFundingRate() != null ? update.getFundingRate() : cur.getFundingRate();
            fundingTimestamp = update.getSwapTime() != null ? update.getSwapTime() : cur.getSwapTime();
        } else {
            markPrice = update.getMarkPrice();
            lastPrice = update.getLastPrice();
            fundingRate = update.getFundingRate();
            fundingTimestamp = update.getSwapTime();
        }
        final Date timestamp = update.getTimestamp();

        return new BitmexContractIndex(update.getSymbol(), indexPrice, markPrice, lastPrice, timestamp, fundingRate, fundingTimestamp);
    }

    @Override
    public String getPositionAsString() {
        return position != null ? position.getPositionLong().toPlainString() : "0";
    }


    private synchronized void recalcLiqInfo() {
        final AccountInfoContracts accountInfoContracts = getAccountInfoContracts();

        final BigDecimal equity = accountInfoContracts.geteMark();
        final BigDecimal margin = accountInfoContracts.getMargin();

        final BigDecimal bMrliq = persistenceService.fetchGuiLiqParams().getBMrLiq();

        if (!(contractIndex instanceof BitmexContractIndex)) {
            // bitmex contract index is not updated yet. Skip the re-calc.
            return;
        }

        final BigDecimal m = ((BitmexContractIndex) contractIndex).getMarkPrice();
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
                    dql = DQL_WRONG;
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
                    dql = DQL_WRONG;
                    warningLogger.info(String.format("Warning.All should be > 0: m=%s, L=%s",
                            m.toPlainString(), L.toPlainString()));
                }
            } else {
                dqlString = "b_DQL = na";
            }

            BigDecimal dmrl = null;
            String dmrlString;
            if (margin.signum() > 0) {
                final BigDecimal bMr = equity.divide(margin, 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP);
                dmrl = bMr.subtract(bMrliq);
                dmrlString = String.format("b_DMRL = %s - %s = %s%%", bMr, bMrliq, dmrl);
            } else {
                dmrlString = "b_DMRL = na";
            }

            if (dql != null && dql.compareTo(DQL_WRONG) != 0) {
                if (liqInfo.getLiqParams().getDqlMax().compareTo(dql) < 0) {
                    liqInfo.getLiqParams().setDqlMax(dql);
                }
                if (liqInfo.getLiqParams().getDqlMin().compareTo(dql) > 0) {
                    liqInfo.getLiqParams().setDqlMin(dql);
                }
            }
            liqInfo.setDqlCurr(dql);

            if (dmrl != null) {
                if (liqInfo.getLiqParams().getDmrlMax().compareTo(dmrl) < 0) {
                    liqInfo.getLiqParams().setDmrlMax(dmrl);
                }
                if (liqInfo.getLiqParams().getDmrlMin().compareTo(dmrl) > 0) {
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
        final BigDecimal bDQLOpenMin = persistenceService.fetchGuiLiqParams().getBDQLOpenMin();

        boolean isOk;
        if (liqInfo.getDqlCurr() == null) {
            isOk = true;
        } else {
            if (orderType.equals(Order.OrderType.BID)) { //LONG
                if (position.getPositionLong().signum() > 0) {
                    isOk = liqInfo.getDqlCurr().compareTo(bDQLOpenMin) >= 0;
                } else {
                    isOk = true;
                }
            } else if ((orderType.equals(Order.OrderType.ASK))) {
                if (position.getPositionLong().signum() < 0) {
                    isOk = liqInfo.getDqlCurr().compareTo(bDQLOpenMin) >= 0;
                } else {
                    isOk = true;
                }
            } else {
                throw new IllegalArgumentException("Wrong order type");
            }
        }

        return isOk;
    }

    @Scheduled(initialDelay = 30 * 1000, fixedDelay = 5 * 1000) // 30 sec
    public void checkForDecreasePosition() {
        Instant start = Instant.now();
        if (isMarketStopped()) {
            return;
        }

        final CorrParams corrParams = getPersistenceService().fetchCorrParams();

        if (corrParams.getPreliq().hasSpareAttempts()) {
            final BigDecimal bDQLCloseMin = getPersistenceService().fetchGuiLiqParams().getBDQLCloseMin();

            if (liqInfo.getDqlCurr() != null
                    && liqInfo.getDqlCurr().compareTo(DQL_WRONG) != 0
                    && liqInfo.getDqlCurr().compareTo(BigDecimal.valueOf(-30)) > 0 // workaround when DQL is less zero
                    && liqInfo.getDqlCurr().compareTo(bDQLCloseMin) < 0
                    && position.getPositionLong().signum() != 0) {
                final BestQuotes bestQuotes = Utils.createBestQuotes(
                        arbitrageService.getSecondMarketService().getOrderBook(),
                        arbitrageService.getFirstMarketService().getOrderBook());

                if (position.getPositionLong().signum() > 0) {
                    tradeLogger.info(String.format("#%s B_PRE_LIQ starting: p%s/dql%s/dqlClose%s",
                            getCounterName(),
                            position.getPositionLong().toPlainString(),
                            liqInfo.getDqlCurr().toPlainString(), bDQLCloseMin.toPlainString()));

                    arbitrageService.startPreliqOnDelta1(SignalType.B_PRE_LIQ, bestQuotes);

                } else if (position.getPositionLong().signum() < 0) {
                    tradeLogger.info(String.format("#%s B_PRE_LIQ starting: p%s/dql%s/dqlClose%s",
                            getCounterName(),
                            position.getPositionLong().toPlainString(),
                            liqInfo.getDqlCurr().toPlainString(), bDQLCloseMin.toPlainString()));

                    arbitrageService.startPerliqOnDelta2(SignalType.B_PRE_LIQ, bestQuotes);

                }
            }
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "checkForDecreasePosition");
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

    public BitmexXRateLimit getxRateLimit() {
        return xRateLimit;
    }

    @Override
    protected void postOverload() {
        xRateLimit = BitmexXRateLimit.initValue();
    }

    /**
     * Workaround! <br>
     * Bitmex sends wrong avgPrice. Fetch detailed history for each order and calc avgPrice.
     *
     * @param avgPrice the object to be updated.
     */
    public void updateAvgPrice(String counterName, AvgPrice avgPrice) {
        final MarketState marketState = getMarketState();
        if (marketState.isStopped()) {
            tradeLogger.info(String.format("#%s WARNING: no updateAvgPrice. MarketState=%s.", counterName, marketState));
            return;
        }
        final int LONG_SLEEP = 10000;
        final int SHORT_SLEEP = 1000;
        final Map<String, AvgPriceItem> itemMap = avgPrice.getpItems();
        for (String orderId : itemMap.keySet()) {
            AvgPriceItem theItem = itemMap.get(orderId);
            if (theItem == null || theItem.getAmount() == null || theItem.getOrdStatus() == null
                    || (theItem.getAmount().signum() == 0 && theItem.getOrdStatus().equals("CANCELED"))) {
                String msg = String.format("#%s WARNING: no updateAvgPrice for orderId=%s. theItem=%s", counterName, orderId, theItem);
                tradeLogger.info(msg);
                logger.warn(msg);
                continue;
            }
            final String logMsg = String.format("#%s AvgPrice update of orderId=%s.", counterName, orderId);
            int MAX_ATTEMPTS = 5;
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                int sleepIfFails = SHORT_SLEEP;
                try {
                    if (marketState.isStopped()) {
                        tradeLogger.info(String.format("#%s WARNING: no updateAvgPrice. MarketState=%s.", counterName, marketState));
                        return;
                    }
                    final Collection<Execution> orderParts = ((BitmexTradeService) getTradeService()).getOrderParts(orderId);

                    if (orderParts.size() == 0) {
                        // Try to Update a whole order info.
                        Collection<Order> orders = getTradeService().getOrder(orderId);
                        if (orders.size() == 0) {
                            tradeLogger.info(String.format("%s WARNING: no order parts. Can not update order.", logMsg));
                        } else {
                            Order order = orders.iterator().next();
                            if (order.getStatus() != null &&
                                    (order.getStatus() == OrderStatus.CANCELED || order.getStatus() == OrderStatus.REJECTED)) {
                                tradeLogger.info(String.format("%s WARNING: no order parts. Order is %s: %s", logMsg,
                                        order.getStatus(), Arrays.toString(orders.toArray())));
                                break;
                            } else {
                                tradeLogger.info(String.format("%s WARNING: no order parts. UpdatedOrderInfo:%s", logMsg, Arrays.toString(orders.toArray())));
                                avgPrice.addPriceItem(counterName, orderId, order.getCumulativeAmount(), order.getAveragePrice(), order.getStatus());
                            }
                        }
                    } else {
                        BigDecimal multiplySum = BigDecimal.ZERO;
                        BigDecimal amountSum = BigDecimal.ZERO;
                        String ordStatus = "";

                        for (Execution orderPart : orderParts) {
                            final BigDecimal lastPx = BigDecimal.valueOf(orderPart.getLastPx());
                            final BigDecimal lastQty = orderPart.getLastQty();

                            multiplySum = multiplySum.add(lastPx.multiply(lastQty));
                            amountSum = amountSum.add(lastQty);
                            ordStatus = orderPart.getOrdStatus();
                        }

                        if (amountSum.signum() > 0) {
                            final BigDecimal price = multiplySum.divide(amountSum, 2, RoundingMode.HALF_UP);
                            avgPrice.addPriceItem(counterName, orderId, amountSum, price, ordStatus);
                            tradeLogger.info(String.format("%s p=%s, a=%s. ordStatus=%s", logMsg, price, amountSum, ordStatus));
                            break;
                        } else {
                            tradeLogger.info(String.format("%s price=0. Use 'order history' price p=%s, a=%s, ordStatus=%s. %s",
                                    logMsg,
                                    theItem.getPrice(),
                                    theItem.getAmount(),
                                    ordStatus,
                                    Arrays.toString(orderParts.toArray())
                            ));
                        }
                    }

                } catch (HttpStatusIOException e) {
                    updateXRateLimit(e);

                    final String rateLimitStr = String.format(" X-RateLimit-Remaining=%s ", xRateLimit.getxRateLimit());

                    logger.info(String.format("%s %s updateAvgPriceError.", logMsg, rateLimitStr), e);
                    tradeLogger.info(String.format("%s %s updateAvgPriceError %s", logMsg, rateLimitStr, e.getMessage()));
                    warningLogger.info(String.format("%s %s updateAvgPriceError %s", logMsg, rateLimitStr, e.getMessage()));

                    overloadByXRateLimit();

                    if (e.getMessage().contains("HTTP status code was not OK: 429")
                            || marketState == MarketState.SYSTEM_OVERLOADED) {
                        sleepIfFails = LONG_SLEEP;
                    }
                    if (e.getMessage().contains("HTTP status code was not OK: 403")) {// banned, no repeats
                        break;
                    }

                } catch (Exception e) {
                    logger.info(String.format("%s updateAvgPriceError.", logMsg), e);
                    tradeLogger.info(String.format("%s updateAvgPriceError %s", logMsg, e.getMessage()));
                    warningLogger.info(String.format("%s updateAvgPriceError %s", logMsg, e.getMessage()));
                }

                try {
                    if (sleepIfFails != LONG_SLEEP && marketState == MarketState.SYSTEM_OVERLOADED) {
                        sleepIfFails = LONG_SLEEP;
                    }

                    Thread.sleep(sleepIfFails);
                } catch (InterruptedException e) {
                    logger.info(String.format("%s Sleep Error.", logMsg), e);
                }
            }
        }
        tradeLogger.info(String.format("#%s AvgPrice by %s orders(%s) is %s", counterName,
                itemMap.size(),
                Arrays.toString(itemMap.keySet().toArray()),
                avgPrice.getAvg()));

        tradeLogger.info(String.format("#%s %s", counterName, arbitrageService.getDealPrices().getDiffB().str));
    }

    private void updateXRateLimit(HttpStatusIOException e) {
        final Map<String, List<String>> responseHeaders = e.getResponseHeaders();
        final List<String> rateLimitValues = responseHeaders.get("X-RateLimit-Remaining");
        if (rateLimitValues != null && rateLimitValues.size() > 0) {
            xRateLimit = new BitmexXRateLimit(
                    Integer.valueOf(rateLimitValues.get(0)),
                    new Date()
            );
        }
    }

    private boolean overloadByXRateLimit() {
        boolean isExceeded = xRateLimit.getxRateLimit() <= 0;
        if (isExceeded) {
            String msg = String.format("xRateLimit=%s(updated=%s). Stop!", xRateLimit.getxRateLimit(), xRateLimit.getLastUpdate());
            logger.info(msg);
            tradeLogger.info(msg);
            warningLogger.info(msg);
            setOverloaded(null);
        }
        return isExceeded;
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

                updateXRateLimit(e);

                final String rateLimitStr = String.format(" X-RateLimit-Remaining=%s ", xRateLimit.getxRateLimit());

                final String marketResponseMessage;
                final String httpBody = e.getHttpBody();
                final String BAD_GATEWAY = "502 Bad Gateway";
                if (httpBody.contains(BAD_GATEWAY)) {
                    marketResponseMessage = BAD_GATEWAY;
                } else {
                    marketResponseMessage = new ObjectMapper().readValue(httpBody, Error.class).getError().getMessage();
                }

                String fullMessage = String.format("#%s/%s %s: %s %s", getCounterName(), attemptCount, operationName, httpBody, rateLimitStr);
                String shortMessage = String.format("#%s/%s %s: %s %s", getCounterName(), attemptCount, operationName, marketResponseMessage, rateLimitStr);

                tradeLogger.error(shortMessage);

                if (marketResponseMessage.startsWith("The system is currently overloaded. Please try again later")) {
                    moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION_SYSTEM_OVERLOADED, marketResponseMessage);
                    logger.error(fullMessage);
                } else if (marketResponseMessage.startsWith("Invalid ordStatus") || marketResponseMessage.startsWith("Invalid orderID")) {
                    moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, marketResponseMessage);
                    logger.error(fullMessage);
                } else if (marketResponseMessage.startsWith(BAD_GATEWAY)) {
                    moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION_502_BAD_GATEWAY, marketResponseMessage);
                    logger.error(fullMessage);
                } else if (marketResponseMessage.contains("Nonce is not increasing")) {
                    moveResponse = new MoveResponse(MoveOrderStatus.EXCEPTION_NONCE, marketResponseMessage);
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

    public boolean cancelOrderSync(String orderId, String logInfoId) {
        final String counterName = getCounterName();

        int attemptCount = 0;
        while (attemptCount < MAX_ATTEMPTS_CANCEL && getMarketState() != MarketState.SYSTEM_OVERLOADED) {
            attemptCount++;
            try {
                if (attemptCount > 1) {
                    Thread.sleep(1000);
                }
                BitmexTradeService tradeService = (BitmexTradeService) getExchange().getTradeService();
                boolean res = tradeService.cancelOrder(orderId);

                getTradeLogger().info(String.format("#%s/%s %s cancelled id=%s",
                        counterName, attemptCount,
                        logInfoId,
                        orderId));

                return res;

            } catch (HttpStatusIOException e) {
                updateXRateLimit(e);
                overloadByXRateLimit();

                logger.error("#{}/{} error cancel order id={}", counterName, attemptCount, orderId, e);
                getTradeLogger().error(String.format("#%s/%s error cancel order id=%s: %s", counterName, attemptCount, orderId, e.toString()));
            } catch (Exception e) {
                logger.error("#{}/{} error cancel order id={}", counterName, attemptCount, orderId, e);
                getTradeLogger().error(String.format("#%s/%s error cancel order id=%s: %s", counterName, attemptCount, orderId, e.toString()));
            }
        }
        return false;
    }

    @Override
    public boolean cancelAllOrders(String logInfoId) {
        final String counterName = getCounterName();

        int attemptCount = 0;
        while (attemptCount < MAX_ATTEMPTS_CANCEL && getMarketState() != MarketState.SYSTEM_OVERLOADED) {
            attemptCount++;
            try {
                if (attemptCount > 1) {
                    Thread.sleep(1000);
                }
                BitmexTradeService tradeService = (BitmexTradeService) getExchange().getTradeService();
                List<LimitOrder> limitOrders = tradeService.cancelAllOrders();

                getTradeLogger().info(String.format("#%s/%s %s cancelled id=%s",
                        counterName, attemptCount,
                        logInfoId,
                        limitOrders.stream().map(Order::getId).reduce((acc, item) -> acc + "," + item)));

                updateOpenOrders(limitOrders);

                return true;

            } catch (HttpStatusIOException e) {
                updateXRateLimit(e);
                overloadByXRateLimit();

                logger.error("#{}/{} error cancel orders", counterName, attemptCount, e);
                getTradeLogger().error(String.format("#%s/%s error cancel orders: %s", counterName, attemptCount, e.toString()));
            } catch (Exception e) {
                logger.error("#{}/{} error cancel orders", counterName, attemptCount, e);
                getTradeLogger().error(String.format("#%s/%s error cancel orders: %s", counterName, attemptCount, e.toString()));
            }
        }
        return false;
    }
}
