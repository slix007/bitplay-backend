package com.bitplay.market.bitmex;

import com.bitplay.api.service.RestartService;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.AvgPriceItem;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.events.NtUsdCheckEvent;
import com.bitplay.arbitrage.events.ObChangeEvent;
import com.bitplay.arbitrage.events.SigEvent;
import com.bitplay.arbitrage.events.SigType;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.arbitrage.posdiff.PosDiffService;
import com.bitplay.core.StreamingExchange;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.BalanceService;
import com.bitplay.market.ExtrastopService;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.MarketStaticData;
import com.bitplay.market.bitmex.exceptions.ReconnectFailedException;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.BtsEventBox;
import com.bitplay.market.model.Affordable;
import com.bitplay.market.model.BtmFokAutoArgs;
import com.bitplay.market.model.DqlState;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.MoveMakerOrderArg;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.MoveResponse.MoveOrderStatus;
import com.bitplay.market.model.PlBefore;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.metrics.MetricsDictionary;
import com.bitplay.metrics.MetricsUtils;
import com.bitplay.model.AccountBalance;
import com.bitplay.model.Pos;
import com.bitplay.model.ex.OrderResultTiny;
import com.bitplay.persistance.LastPriceDeviationService;
import com.bitplay.persistance.MonitoringDataService;
import com.bitplay.persistance.OrderRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.LiqParams;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.FplayOrderUtils;
import com.bitplay.persistance.domain.fluent.dealprices.DealPrices;
import com.bitplay.persistance.domain.fluent.dealprices.FactPrice;
import com.bitplay.persistance.domain.mon.Mon;
import com.bitplay.persistance.domain.settings.AmountType;
import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.BitmexContractTypeEx;
import com.bitplay.persistance.domain.settings.BitmexObType;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.Dql;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.SysOverloadArgs;
import com.bitplay.service.exception.NotConnectedException;
import com.bitplay.service.ws.statistic.PingStatEvent;
import com.bitplay.settings.BitmexChangeOnSoService;
import com.bitplay.utils.Utils;
import com.bitplay.xchange.Exchange;
import com.bitplay.xchange.ExchangeSpecification;
import com.bitplay.xchange.bitmex.dto.BitmexXRateLimit;
import com.bitplay.xchange.bitmex.service.BitmexAccountService;
import com.bitplay.xchange.bitmex.service.BitmexTradeService;
import com.bitplay.xchange.currency.CurrencyPair;
import com.bitplay.xchange.dto.Order;
import com.bitplay.xchange.dto.Order.OrderStatus;
import com.bitplay.xchange.dto.Order.OrderType;
import com.bitplay.xchange.dto.account.AccountInfoContracts;
import com.bitplay.xchange.dto.marketdata.ContractIndex;
import com.bitplay.xchange.dto.marketdata.OrderBook;
import com.bitplay.xchange.dto.marketdata.Ticker.Builder;
import com.bitplay.xchange.dto.trade.LimitOrder;
import com.bitplay.xchange.dto.trade.OpenOrders;
import com.bitplay.xchange.exceptions.ExchangeException;
import com.bitplay.xchange.service.trade.TradeService;
import com.bitplay.xchange.utils.Assert;
import com.bitplay.xchangestream.bitmex.BitmexStreamingAccountService;
import com.bitplay.xchangestream.bitmex.BitmexStreamingExchange;
import com.bitplay.xchangestream.bitmex.BitmexStreamingMarketDataService;
import com.bitplay.xchangestream.bitmex.dto.BitmexContractIndex;
import com.bitplay.xchangestream.bitmex.dto.BitmexDepth;
import com.bitplay.xchangestream.bitmex.dto.BitmexOrderBook;
import com.bitplay.xchangestream.bitmex.dto.BitmexStreamAdapters;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import io.swagger.client.model.Error;
import io.swagger.client.model.Execution;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.websocket.DeploymentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import si.mazi.rescu.HttpStatusIOException;
import si.mazi.rescu.InvocationResult;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Service("bitmex")
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BitmexService extends MarketServicePreliq {

    private static final Logger logger = LoggerFactory.getLogger(BitmexService.class);
    private static final Logger ordersLogger = LoggerFactory.getLogger("BITMEX_ORDERS_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private static final MarketStaticData MARKET_STATIC_DATA = MarketStaticData.BITMEX;
    public static final String NAME = MARKET_STATIC_DATA.getName();

    private BitmexStreamingExchange exchange;

    protected static final int MAX_ATTEMPTS_CANCEL_BITMEX = 3;
    private static final int MAX_RECONNECTS_BEFORE_RESTART = 1;
    private static final int MAX_WAITING_OB_CHECKS = 5; // each sleep 1 sec. // Bitmex min delay is 2,5 sec: https://blog.bitmex.com/ru_ru-update-to-our-realtime-apis-image-delivery/
    private static final int MAX_RESUBSCRIBES = 5;
    private volatile boolean isDestroyed = false;

    // Moving timeout
    private volatile ScheduledFuture<?> movingDelayResetFuture;
    private volatile boolean movingDelay = false;
    private volatile ScheduledFuture<?> movingInProgressResetFuture;
    private volatile ScheduledFuture<?> movingErrorsResetFuture;
    private static final int MAX_MOVING_OVERLOAD_ATTEMPTS_TIMEOUT_SEC = 60;
    private volatile AtomicInteger movingErrorsOverloaded = new AtomicInteger(0);
    private volatile AtomicInteger soAttempts = new AtomicInteger(0);

    private volatile BigDecimal prevCumulativeAmount;

    private volatile AtomicInteger obWrongCount = new AtomicInteger(0);
    private volatile AtomicInteger obWrongCountXBTUSD = new AtomicInteger(0);
    private final AtomicInteger failedReconnects = new AtomicInteger(0);

    private volatile Disposable quoteSubscription;
    private volatile Disposable orderBookSubscription;
    private volatile Disposable orderBookSubscriptionExtra;
    private volatile Disposable openOrdersSubscription;
    private volatile Disposable accountInfoSubscription;
    private volatile Disposable positionSubscription;
    private volatile Disposable futureIndexSubscription;
    private volatile Disposable onDisconnectSubscription;
    private Disposable pingStatSub;
    @SuppressWarnings({"UnusedDeclaration"})
    private BitmexSwapService bitmexSwapService;

    private ArbitrageService arbitrageService;

    private BitmexObType bitmexObTypeCurrent;

    @Autowired
    private SlackNotifications slackNotifications;

    @Autowired
    private LastPriceDeviationService lastPriceDeviationService;

    private BitmexBalanceService bitmexBalanceService = new BitmexBalanceService();

    @Autowired
    private com.bitplay.persistance.TradeService fplayTradeService;

    @Autowired
    private RestartService restartService;
    private volatile Date orderBookLastTimestamp = new Date();
    //    private volatile Date orderBookLastTimestampXBTUSD = new Date();
    protected BigDecimal bestBidXBTUSD = BigDecimal.ZERO;
    protected BigDecimal bestAskXBTUSD = BigDecimal.ZERO;

    @Autowired
    private PersistenceService persistenceService;
    @Autowired
    private SettingsRepositoryService settingsRepositoryService;
    @Autowired
    private OrderRepositoryService orderRepositoryService;
    @Autowired
    private ExtrastopService extrastopService;
    @Autowired
    private MonitoringDataService monitoringDataService;
    @Autowired
    private BitmexChangeOnSoService bitmexChangeOnSoService;
    @Autowired
    private MetricsDictionary metricsDictionary;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    protected ApplicationEventPublisher getApplicationEventPublisher() {
        return applicationEventPublisher;
    }

    private String key;
    private String secret;
    private Disposable restartTimer;
    private volatile BitmexContractType bitmexContractType;
    private volatile BitmexContractTypeEx bitmexContractTypeEx;
    private Map<CurrencyPair, Integer> currencyToScale = new HashMap<>();

    private AtomicInteger cancelledInRow = new AtomicInteger();
    private volatile boolean reconnectInProgress = false;
    private volatile AtomicInteger reconnectCount = new AtomicInteger(0);
    private volatile AtomicInteger orderBookErrors = new AtomicInteger(0);
    private volatile BigDecimal cm = null; // correlation multiplier
    public static final BigDecimal DEFAULT_BTM_CM = BigDecimal.valueOf(100);
    public static final BigDecimal LOT_SIZE = BigDecimal.valueOf(100);

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
        return arbitrageService.getPosDiffService();
    }

    @Override
    public PersistenceService getPersistenceService() {
        return persistenceService;
    }

    @Override
    public boolean isMarketStopped() {
        return getArbitrageService().isArbStateStopped()
                || getArbitrageService().isArbForbidden()
                || (limitsService != null && limitsService.outsideLimits());
    }

    @Override
    public MarketStaticData getMarketStaticData() {
        return MARKET_STATIC_DATA;
    }

    @Override
    protected Exchange getExchange() {
        return exchange;
    }

    @Override
    public String getFuturesContractName() {
        return bitmexContractTypeEx != null ? bitmexContractTypeEx.getSymbol() : "";
    }

    public BitmexContractTypeEx getBitmexContractTypeEx() {
        return bitmexContractTypeEx;
    }

    @Override
    public boolean isSwap() {
        return bitmexContractType != null && bitmexContractType.isSwap();
    }

    @Override
    public SlackNotifications getSlackNotifications() {
        return slackNotifications;
    }

    @Override
    public MonitoringDataService getMonitoringDataService() {
        return monitoringDataService;
    }

    public BigDecimal getCm() {
        if (cm == null) {
            return DEFAULT_BTM_CM;
        }
        return cm;
    }

    @Override
    public boolean isStarted() {
        return bitmexContractType != null &&
                (!bitmexContractType.isQuanto()
                        ||
                        (bitmexContractType.isQuanto() && cm != null));
    }

    @Scheduled(fixedDelay = 2000)
    public void openOrdersCleaner() {
        Instant start = Instant.now();
        cleanOldOO();
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "openOrdersCleaner");
    }

    @Scheduled(fixedDelay = 5000)
    public void posXBTUSDUpdater() {
        if (!isStarted()) {
            return;
        }

        Instant start = Instant.now();
        if (bitmexContractType.isQuanto()) {
            try {
                final BitmexAccountService accountService = (BitmexAccountService) exchange.getAccountService();
                final Pos pUpdate = accountService.fetchPositionInfo(BitmexContractType.XBTUSD_Perpetual.getSymbol());
                mergeXBTUSDPos(pUpdate);

            } catch (HttpStatusIOException e) {

                overloadByXRateLimit();

                if (e.getMessage().contains("HTTP status code was not OK: 429")) {
                    logger.warn("WARNING:" + e.getMessage());
                    warningLogger.warn("WARNING:" + e.getMessage());
                    setOverloaded(null);
                } else if (e.getMessage().contains("HTTP status code was not OK: 403")) {// banned, no repeats
                    String msg = " Banned:" + e.getMessage();
                    logger.warn(msg);
                    warningLogger.warn(msg);
                    slackNotifications.sendNotify(NotifyType.BITMEX_BAN_403, NAME + msg);
                    setOverloaded(null);
                } else if (e.getHttpBody() != null) {
                    logger.warn("posXBTUSDUpdater: " + e.toString() + ". " + e.getHttpBody());
                } else {
                    logger.error("posXBTUSDUpdater:", e);
                }
            } catch (SocketTimeoutException e) {
                logger.error("posXBTUSDUpdater: " + e.toString());
            } catch (Exception e) {
                logger.error("posXBTUSDUpdater:", e);
            }

        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "openOrdersCleaner");
    }

    @Override
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
        if (!isStarted()) {
            return;
        }

        Instant start = Instant.now();
        if (account.get().getWallet().signum() == 0) {
            tradeLogger.warn("WARNING: Bitmex Balance is null. Restarting accountInfoListener.", getCurrencyPair().toString());
            warningLogger.warn("WARNING: Bitmex Balance is null. Restarting accountInfoListener.");
            if (accountInfoSubscription != null && !accountInfoSubscription.isDisposed()) {
                accountInfoSubscription.dispose();
            }
            accountInfoSubscription = startAccountInfoListener();
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "doubleCheckAvailableBalance");
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void afterReconnect() {
        Single.just(new Object())
                .observeOn(Schedulers.io())
                .subscribe(o -> {

                            logger.info("after reconnect check.");

                            dobleCheckAvailableBalance();

                            List<FplayOrder> openOrders = fetchOpenOrders();

                            if (!hasOpenOrders()) {
                                logger.info("market-ready after reconnect: ");
                                final Long tradeId = openOrders.stream()
                                        .map(FplayOrder::getTradeId)
                                        .reduce(Utils::lastTradeId)
                                        .orElseGet(() -> arbitrageService.getLastTradeId());
                                setFree(tradeId);
                                tradeLogger.info("reset WAITING_ARB after reconnect");
                                ((OkCoinService) arbitrageService.getRightMarketService()).resetWaitingArb("reset WAITING_ARB after reconnect");
                            } else {
                                String msg = String.format("Warning: Bitmex reconnect is finished, but there are %s openOrders.", getOnlyOpenOrders().size());
                                tradeLogger.info(msg, getCurrencyPair().toString());
                                warningLogger.info(msg);
                                logger.info(msg);
                            }

                        },
                        throwable -> logger.error("Error afterReconnect", throwable));
    }


    @Override
    public void initializeMarket(String key, String secret, ContractType contractType,
                                 String sslUri,
                                 String host,
                                 String port,
                                 String wssUrlPublic,
                                 String wssUrlPrivate,
                                 Object... exArgs) {
        monObTimestamp = monitoringDataService.fetchTimestampMonitoring(getNameWithType());

        bitmexContractType = (BitmexContractType) contractType;
        final CurrencyPair currencyPair = settingsRepositoryService.getCurrencyPair(bitmexContractType);
        bitmexContractTypeEx = new BitmexContractTypeEx(bitmexContractType, currencyPair);
        currencyToScale.put(getCurrencyPair(), bitmexContractType.getScale());
        if (!sameOrderBookXBTUSD()) {
            final CurrencyPair currencyPairXBTUSD = settingsRepositoryService.getCurrencyPair(BitmexContractType.XBTUSD_Perpetual);
            currencyToScale.put(currencyPairXBTUSD, BitmexContractType.XBTUSD_Perpetual.getScale());
        }

        this.usdInContract = 1; // not in use for Bitmex.
        this.key = key;
        this.secret = secret;
        bitmexSwapService = new BitmexSwapService(this, arbitrageService);

        initWebSocketConnection(sslUri, host, port, wssUrlPublic);

        startAllListeners();

        fetchOpenOrders();

        initPreliqScheduler();
    }

//    private void getInstrument() {
//        this.usdInContract = BigDecimal.valueOf(100);
//        final BitmexAccountService accountService = (BitmexAccountService) exchange.getAccountService();
//        try {
//            final Instrument instrument = accountService.getInstrument(bitmexContractTypeEx.getSymbol());
//            System.out.println(instrument);
//
//            this.usdInContract = instrument.getLotSize() != null
//                    ? instrument.getLotSize()
//                    : getUsdInContractByTime();
//
//        } catch (IOException e) {
//            logger.error("CAN NOT INIT usdInContract", e);
//            this.usdInContract = getUsdInContractByTime();
//        }

//        if (bitmexContractTypeEx.isBtc()) {
//            this.cm = this.usdInContract;
//        }
//    }

//    private BigDecimal getUsdInContractByTime() {
//        // https://blog.bitmex.com/site_announcement/important-lot-size-change-on-xbtusd-swap-and-futures-moving-to-100-lot-size-effective-8-june/
//        // We are changing the Lot Size, or minimum trading unit, on the XBTUSD Swap and XBT futures (XBTM21 ,XBTU21 and future listings)
//        // from 1 contract to 100 contracts, effective 04:30 UTC on 8 June 2021
//        final Instant now = Instant.now();
//        final Instant timeX = Instant.parse("2021-06-08T04:30:00Z");
//
//        return (bitmexContractTypeEx.isBtc() && now.isAfter(timeX))
//                ? BigDecimal.valueOf(100)
//                : BigDecimal.ZERO;
//    }

    private void startAllListeners() {

        logger.info("startAllListeners");
        quoteSubscription = startQuoteListener();
        startOrderBookListener();
        accountInfoSubscription = startAccountInfoListener();
        openOrdersSubscription = startOpenOrderListener();
        positionSubscription = startPositionListener();
        futureIndexSubscription = startFutureIndexListener();
        startPingStatSub();

    }

    private void startPingStatSub() {
        if (pingStatSub != null) {
            pingStatSub.dispose();
        }
        pingStatSub = exchange.subscribePingStats()
                .map(PingStatEvent::getPingPongMs)
                .subscribe(ms -> metricsDictionary.putBitmexPing(ms),
                        e -> logger.error("ping stats error", e));
    }


    public void reSubscribeOrderBooks(boolean force) throws ReconnectFailedException, TimeoutException {

        if (force || !orderBookIsFilled() || !orderBookForPriceIsFilled()) {
            slackNotifications.sendNotify(NotifyType.BITMEX_RECONNECT, "bitmex resubscribe");

            final OrderBook ob = this.orderBook;
            String msgOb = String.format("re-subscribe OrderBook: asks=%s, bids=%s, timestamp=%s. ",
                    ob.getAsks().size(), // should be lock on collections
                    ob.getBids().size(), // should be lock on collections
                    ob.getTimeStamp());
            tradeLogger.info(msgOb, getCurrencyPair().toString());
            warningLogger.info(msgOb);
            logger.info(msgOb);
            if (!sameOrderBookXBTUSD()) {
                final OrderBook obXBTUSD = this.orderBookXBTUSD;
                String msgObXBTUSD = String.format("re-subscribe OrderBookXBTUSD: asks=%s, bids=%s, timestamp=%s. ",
                        obXBTUSD.getAsks().size(),
                        obXBTUSD.getBids().size(),
                        obXBTUSD.getTimeStamp());
                final CurrencyPair currencyPair = settingsRepositoryService.getCurrencyPair(BitmexContractType.XBTUSD_Perpetual);
                tradeLogger.info(msgObXBTUSD, currencyPair.toString());
                warningLogger.info(msgObXBTUSD);
                logger.info(msgObXBTUSD);
            }

            this.orderBook = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
            this.setOrderBookShort(new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>()));
            this.orderBookXBTUSD = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
            this.orderBookXBTUSDShort = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());

            orderBookSubscription.dispose();
            if (orderBookSubscriptionExtra != null) {
                orderBookSubscriptionExtra.dispose();
            }
            List<String> symbols = new ArrayList<>();
            symbols.add(bitmexContractTypeEx.getSymbol());
            if (!sameOrderBookXBTUSD()) {
                symbols.add(BitmexContractType.XBTUSD_Perpetual.getSymbol());
            }

            Throwable throwable = ((BitmexStreamingMarketDataService) exchange.getStreamingMarketDataService())
                    .unsubscribeOrderBook(symbols)
                    .doOnComplete(this::startOrderBookListener)
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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void requestReconnectAsync() {
        Single.just(new Object())
                .observeOn(Schedulers.io())
                .subscribe(o -> {
                    try {
                        requestReconnect(true);
                    } catch (Exception e) {
                        logger.error("Error requestReconnectAsync", e);
                    }
                });
    }

    public void requestReconnect(boolean isForceReconnect, boolean... skipResetInTheEnd) {
        final boolean skipAfterReconnectReset = skipResetInTheEnd != null && skipResetInTheEnd.length > 0 && skipResetInTheEnd[0];
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
                slackNotifications.sendNotify(NotifyType.BITMEX_RECONNECT, "bitmex reconnect");

                try {
                    reconnectOrRestart(skipAfterReconnectReset);
                } catch (Exception e) {
                    final String msg = String.format("Reconnect exception: %s", e.getMessage());
                    warningLogger.error(msg);
                    tradeLogger.error(msg, getCurrencyPair().toString());
                    logger.error(msg, e);
                } finally {
                    reconnectInProgress = false;
                }

            }

        }
    }

    private void reconnectOrRestart(boolean skipAfterReconnectReset) {
        final Integer maxBitmexReconnects = settingsRepositoryService.getSettings().getRestartSettings().getMaxBitmexReconnects();
        int currReconnectCount = reconnectCount.incrementAndGet();
        metricsDictionary.incBitmexReconnects();

        if (currReconnectCount >= maxBitmexReconnects) {
            doRestart(String.format("Warning: Bitmex max reconnects(%s) is reached.", currReconnectCount));
            return;
        }
        int attempt = 0;
        while (true) {
            try {
                final String msg = String.format("Warning: Bitmex reconnect(%s) attempt=%s. %s", currReconnectCount, attempt, getSubscribersStatuses());
                warningLogger.info(msg);
                tradeLogger.info(msg, getCurrencyPair().toString());
                logger.info(msg);

                reconnect(skipAfterReconnectReset);
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
                        " isLocked: openOrdersLock=false",
                orderBookSubscription == null ? null : orderBookSubscription.isDisposed(),
                accountInfoSubscription == null ? null : accountInfoSubscription.isDisposed(),
                openOrdersSubscription == null ? null : openOrdersSubscription.isDisposed(),
                positionSubscription == null ? null : positionSubscription.isDisposed(),
                futureIndexSubscription == null ? null : futureIndexSubscription.isDisposed());
//                Thread.holdsLock(openOrdersLock));
    }

    @Override
    public OrderBook fetchOrderBookMain() {
        try {
            final BitmexObType obType = getPersistenceService().getSettingsRepositoryService().getSettings().getBitmexObType();
            if (obType != BitmexObType.TRADITIONAL_10) {
                final OrderBook orderBook = getExchange().getMarketDataService().getOrderBook(getCurrencyPair());
                final OrderBook ob = new OrderBook(new Date(), orderBook.getAsks(), orderBook.getBids()); // timestamp may be null
                return ob; // for bitmex incremental
            }
            final OrderBook orderBook = getExchange().getMarketDataService().getOrderBook(getCurrencyPair());
            final OrderBook ob = new OrderBook(new Date(), orderBook.getAsks(), orderBook.getBids());
            this.orderBook = ob;
            this.setOrderBookShort(ob);
        } catch (IOException e) {
            log.error("can not fetch orderBook");
        }
        return this.getOrderBookShort().getOb();
    }

    @Override
    public String fetchPosition() throws Exception {
        if (getMarketState() == MarketState.SYSTEM_OVERLOADED) {
            logger.warn("WARNING: no position fetch: SYSTEM_OVERLOADED");
            warningLogger.warn("WARNING: no position fetch: SYSTEM_OVERLOADED");
            return BitmexUtils.positionToString(getPos());
        }
        try {
            final BitmexAccountService accountService = (BitmexAccountService) exchange.getAccountService();
            final Pos pUpdate = accountService.fetchPositionInfo(bitmexContractTypeEx.getSymbol());
            mergePosition(pUpdate);

            getApplicationEventPublisher().publishEvent(new NtUsdCheckEvent());
            stateRecalcInStateUpdaterThread();

        } catch (HttpStatusIOException e) {
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
        return BitmexUtils.positionToString(getPos());
    }

    private void mergePosition(Pos pUpdate) {
        int iter = 0;
        boolean success = false;
        while (!success) {
            final Pos current = this.pos.get();
            if (current.getTimestamp() != null && pUpdate.getTimestamp() != null
                    && pUpdate.getTimestamp().isBefore(current.getTimestamp())) {
                logger.warn("skip older pos update. current=" + current.getTimestamp() + ", pUpdate=" + pUpdate.getTimestamp());
                return;
            }

            if (pUpdate.getPositionLong() == null) { // TODO when ETH then XBTUSD as null(only fetch XBTUSD once in 5 sec)
                if (current.getPositionLong() != null) {
                    // logger.warn("mergePos no update when null"); // the case XBTUSD when main is ETH
                    return; // no update when null
                }
                // use 0 when no pos yet
                pUpdate = Pos.emptyPos();
            }

            BigDecimal defaultLeverage = bitmexContractType.defaultLeverage();
            final Pos updated = new Pos(
                    pUpdate.getPositionLong(),
                    pUpdate.getPositionShort(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    pUpdate.getLeverage() == null || pUpdate.getLeverage().signum() == 0 ? defaultLeverage
                            : pUpdate.getLeverage().setScale(2, RoundingMode.HALF_UP),
                    pUpdate.getLiquidationPrice() == null ? current.getLiquidationPrice() : pUpdate.getLiquidationPrice(),
                    pUpdate.getMarkValue() != null ? pUpdate.getMarkValue() : current.getMarkValue(),
                    pUpdate.getPriceAvgLong() == null || pUpdate.getPriceAvgLong().signum() == 0 ? current.getPriceAvgLong() : pUpdate.getPriceAvgLong(),
                    pUpdate.getPriceAvgShort() == null || pUpdate.getPriceAvgShort().signum() == 0 ? current.getPriceAvgShort() : pUpdate.getPriceAvgShort(),
                    pUpdate.getTimestamp(), pUpdate.getRaw(),
                    null, null);
            success = this.pos.compareAndSet(current, updated);

            if (++iter > 1) {
                logger.warn("merge pos iter=" + iter);
            }
        }
    }

    private void mergeXBTUSDPos(Pos pUpdate) {
        int iter = 0;
        boolean success = false;
        while (!success) {
            final Pos pos = this.posXBTUSD.get();
            if (pos.getTimestamp() != null && pUpdate.getTimestamp() != null
                    && pUpdate.getTimestamp().isBefore(pos.getTimestamp())) {
                logger.warn("skip older pos update. current=" + pos.getTimestamp() + ", pUpdate=" + pUpdate.getTimestamp());
                return;
            }

            BigDecimal defaultLeverage = BigDecimal.valueOf(100);
            final Pos updated = new Pos(
                    pUpdate.getPositionLong(),
                    pUpdate.getPositionShort(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    pUpdate.getLeverage() == null || pUpdate.getLeverage().signum() == 0 ? defaultLeverage : pUpdate.getLeverage(),
                    pUpdate.getLiquidationPrice() == null ? pos.getLiquidationPrice() : pUpdate.getLiquidationPrice(),
                    pUpdate.getMarkValue() != null ? pUpdate.getMarkValue() : pos.getMarkValue(),
                    pUpdate.getPriceAvgLong() == null || pUpdate.getPriceAvgLong().signum() == 0 ? pos.getPriceAvgLong() : pUpdate.getPriceAvgLong(),
                    pUpdate.getPriceAvgShort() == null || pUpdate.getPriceAvgShort().signum() == 0 ? pos.getPriceAvgShort() : pUpdate.getPriceAvgShort(),
                    pUpdate.getTimestamp(), pUpdate.getRaw(),
                    null, null);
            success = this.posXBTUSD.compareAndSet(pos, updated);
            if (++iter > 1) {
                logger.warn("merge posXBTUSD iter=" + iter);
            }
        }
    }

    @Override
    protected boolean onReadyState() {
        iterateOpenOrdersMoveAsync();
        return true;
    }

    @Override
    public ContractType getContractType() {
        return bitmexContractType;
    }

    @Override
    public BigDecimal getSCV() {
        BigDecimal okexSCV = arbitrageService.getRightMarketService().getSCV();
        if (cm == null || okexSCV == null) {
            return null;
        }
        // Bitmex_SCV = Okex_SCV / CM
        return okexSCV.divide(cm, 8, RoundingMode.HALF_UP);
    }

    public BitmexObType getBitmexObTypeCurrent() {
        return bitmexObTypeCurrent;
    }

    @Override
    protected void iterateOpenOrdersMoveAsync(Object... iterateArgs) { // if synchronized then the queue for moving could be long
        ooSingleExecutor.execute(() ->
                getMetricsDictionary().getBitmexMovingIter().record(() ->
                        iterateOpenOrdersMoveSync(iterateArgs)));
    }

    private void iterateOpenOrdersMoveSync(Object[] iterateArgs) {
        try {
            final MarketState marketState = getMarketState();
            if (marketState == MarketState.SYSTEM_OVERLOADED
                    || marketState == MarketState.PLACING_ORDER
                    || getArbitrageService().isArbStateStopped()
                    || (limitsService != null && limitsService.outsideLimits())
                    || getArbitrageService().getDqlStateService().isPreliq()) {
                return;
            }
        } catch (NotYetInitializedException e) {
            return;
        }

        if (movingDelay) {
            final String logString = String.format("Too often moving requests. movingDelay=%s", movingDelay);
            logger.error(logString);
            return;
        }

        Instant startMoving = (iterateArgs != null && iterateArgs.length > 0 && iterateArgs[0] != null)
                ? (Instant) iterateArgs[0]
                : null;
        Instant beforeLock = Instant.now();

        if (hasOpenOrders()) {

            Long tradeId = null;

            Long waitingPrevMs = Instant.now().toEpochMilli() - beforeLock.toEpochMilli();

            final SysOverloadArgs sysOverloadArgs = settingsRepositoryService.getSettings().getBitmexSysOverloadArgs();
            final Integer maxAttempts = sysOverloadArgs.getMovingErrorsForOverload();

            List<FplayOrder> resultOOList = new ArrayList<>();

            for (FplayOrder openOrder : getOpenOrders()) {
                if (openOrder.isOpen() && !arbitrageService.isArbForbidden(openOrder.getSignalType())) {

                    final PlacingType btmPlacingTypeToChange = bitmexChangeOnSoService.getPlacingTypeToChange(openOrder.getSignalType());
                    if (btmPlacingTypeToChange != null) {
                        cancelAndPlaceOnSo(openOrder, btmPlacingTypeToChange); // set status 'CANCELLED' if it is successful
                    }

                    if (openOrder.getOrderId() == null
                            || openOrder.getOrderId().equals("0")
                            || openOrder.getOrder().getId().equals("0")) {
                        warningLogger.warn("OO is null. " + openOrder);
                        // empty, do not add

                    } else if (openOrder.getOrder().getType() == null) {
                        warningLogger.warn("OO type is null. " + openOrder.toString());
                        // keep the order
                        resultOOList.add(openOrder);

                    } else if (openOrder.getOrderDetail().getOrderStatus() != OrderStatus.NEW
                            && openOrder.getOrderDetail().getOrderStatus() != OrderStatus.PENDING_NEW
                            && openOrder.getOrderDetail().getOrderStatus() != OrderStatus.PARTIALLY_FILLED) {
                        // keep the order
                        resultOOList.add(openOrder);

                    } else {
                        final MoveResponse response = moveMakerOrderIfNotFirst(openOrder, startMoving, waitingPrevMs);

                        List<FplayOrder> orderList = handleMovingResponse(response, maxAttempts, openOrder);

                        // update all fplayOrders
                        resultOOList.addAll(orderList);

                    }
                }
            }

            for (FplayOrder resultOO : resultOOList) {
                tradeId = Utils.lastTradeId(tradeId, resultOO.getTradeId());
            }
            updateFplayOrders(resultOOList);

            if (getMarketState() != MarketState.READY && !hasOpenOrders()) {
                tradeLogger.warn("Free by iterateOpenOrdersMove");
                logger.warn("Free by iterateOpenOrdersMove");
                eventBus.send(new BtsEventBox(BtsEvent.MARKET_FREE, tradeId));
            }

        }
    }

    private void startMovingDelay() {
        final int betweenAttemptsMsSafe = settingsRepositoryService.getSettings().getBitmexSysOverloadArgs().getBetweenAttemptsMsSafe();
        if (movingDelayResetFuture != null) {
            movingDelayResetFuture.cancel(false);
        }
        movingDelay = true;
        movingDelayResetFuture = scheduler.schedule(() -> movingDelay = false, betweenAttemptsMsSafe, TimeUnit.MILLISECONDS);
    }

    private void scheduleMovingErrorsReset() {
        if (movingErrorsResetFuture != null && !movingErrorsResetFuture.isDone()) {
            return;
        }
        movingErrorsResetFuture = scheduler.schedule(() -> {
                    movingErrorsOverloaded.set(0);
                    soAttempts.set(0);
                },
                MAX_MOVING_OVERLOAD_ATTEMPTS_TIMEOUT_SEC, TimeUnit.SECONDS);
    }


    private List<FplayOrder> handleMovingResponse(final MoveResponse response, Integer maxAttempts, FplayOrder initialOpenOrder) {
        List<FplayOrder> resultOOList = new ArrayList<>(); // default - the same
        String contractType = initialOpenOrder.getOrderDetail().getContractType();

        try {
            //TODO keep an eye on 'hang open orders'
            if (overloadByXRateLimit()) {
                movingErrorsOverloaded.set(0);
                soAttempts.set(0);
                resultOOList.add(initialOpenOrder); // keep the same

            } else if (response.getMoveOrderStatus() == MoveOrderStatus.ALREADY_CLOSED) {
                // update the status
                final FplayOrder cancelledFplayOrder = response.getCancelledFplayOrder();
                if (cancelledFplayOrder != null) {
                    resultOOList.add(cancelledFplayOrder); // update the info
                }

            } else if (response.getMoveOrderStatus() == MoveOrderStatus.MOVED) {
                movingErrorsOverloaded.set(0);
                soAttempts.set(0);
                resultOOList.add(response.getNewFplayOrder());

            } else if (response.getMoveOrderStatus() == MoveOrderStatus.ONLY_CANCEL) { // update cancelled and place new

                if (movingErrorsOverloaded.incrementAndGet() >= maxAttempts) {
                    {
                        final FplayOrder ord = response.getCancelledFplayOrder() != null
                                ? response.getCancelledFplayOrder() : initialOpenOrder;
                        final LimitOrder lo = ord.getLimitOrder();
                        final BigDecimal amountLeft = lo.getTradableAmount().subtract(lo.getCumulativeAmount());

                        final BigDecimal okexAm = PlacingBlocks.getOkexBlockByBitmexBlock(amountLeft, ord.isEth(), getCm());
                        if (okexAm.signum() > 0) {
                            ((OkCoinService) getArbitrageService().getRightMarketService()).updateDeferredAmount(okexAm);
                            setOverloaded(null);
                        } else {
                            setOverloaded(null, true);
                        }
                    }
                    movingErrorsOverloaded.set(0);
                    soAttempts.set(0);
                    resultOOList.add(initialOpenOrder); // keep the same

                } else {

                    // place new order instead of 'cancelled-on-moving'
                    final FplayOrder cancelledFplayOrder = response.getCancelledFplayOrder();
                    if (cancelledFplayOrder == null) {
                        resultOOList.add(initialOpenOrder); // keep the same
                    } else {
                        // 1. old order
                        resultOOList.add(cancelledFplayOrder);

                        final LimitOrder cancelledOrder = cancelledFplayOrder.getLimitOrder();
                        final BigDecimal amountLeft = cancelledOrder.getTradableAmount().subtract(cancelledOrder.getCumulativeAmount());

                        final PlacingType placingType = initialOpenOrder.getPlacingType();
                        final List<FplayOrder> orderList = placeOrderInsteadOfCancelled(initialOpenOrder, amountLeft, placingType);
                        resultOOList.addAll(orderList);
                        if (orderList.stream().noneMatch(FplayOrder::isOpen)) {
                            logger.warn("placeOrderInsteadOfCancelled returned no new order.");
                        }
                    }
                }

            } else if (response.getMoveOrderStatus() == MoveOrderStatus.EXCEPTION_SYSTEM_OVERLOADED) {

                bitmexChangeOnSoService.tryActivate(soAttempts.incrementAndGet());
                final PlacingType btmPlacingTypeToChange = bitmexChangeOnSoService.getPlacingTypeToChange(initialOpenOrder.getSignalType());
                if (btmPlacingTypeToChange != null) {
                    cancelAndPlaceOnSo(initialOpenOrder, btmPlacingTypeToChange); // set status 'CANCELLED' if it is successful
                }

                if (movingErrorsOverloaded.incrementAndGet() >= maxAttempts) {
                    setOverloaded(null);
                    movingErrorsOverloaded.set(0);
                    soAttempts.set(0);
                } else {
                    startMovingDelay();
                    scheduleMovingErrorsReset();
                }
                resultOOList.add(initialOpenOrder); // keep the same


            } else if (response.getMoveOrderStatus() == MoveOrderStatus.EXCEPTION
                    || response.getMoveOrderStatus() == MoveOrderStatus.EXCEPTION_502_BAD_GATEWAY
                    || response.getMoveOrderStatus() == MoveOrderStatus.EXCEPTION_NONCE
            ) {
                tradeLogger.warn("MovingException: " + response.getDescription(), contractType);
                logger.warn("MovingException: " + response.getDescription());
                resultOOList.add(initialOpenOrder); // keep the same

            } else {
                resultOOList.add(initialOpenOrder); // keep the same
            }

        } catch (Exception e) {
            // use default OO
            warningLogger.warn("Error on moving: " + e.getMessage());
            logger.warn("Error on moving", e);

            resultOOList = new ArrayList<>();
            resultOOList.add(initialOpenOrder); // keep the same
        }
        return resultOOList;
    }

    /**
     * @return the list may have<br>- the placed order<br>- several cancelled orders(because of participateDoNotInitiate).
     */
    private List<FplayOrder> placeOrderInsteadOfCancelled(FplayOrder openOrder, BigDecimal amountLeft, PlacingType placingType) {
//        FplayOrder res = null;
        List<FplayOrder> res = new ArrayList<>();

        // PLACE ORDER instead of cancelled on moving.
        final BitmexContractType cntType = BitmexContractType.parse(openOrder.getOrder().getCurrencyPair(),
                settingsRepositoryService.getSettings().getBitmexContractTypes());
        if (cntType == null) {
            String msg = String.format("no moving. Can not determinate contractType! %s", openOrder.toString());
            warningLogger.warn("Error on moving: " + msg);
            logger.warn("Error on moving: " + msg);
        } else {

            final TradeResponse tradeResponse = placeOrder(
                    PlaceOrderArgs.builder()
                            .orderType(openOrder.getLimitOrder().getType())
                            .amount(amountLeft)
                            .bestQuotes(openOrder.getBestQuotes())
                            .placingType(placingType)
                            .signalType(openOrder.getSignalType())
                            .attempt(1)
                            .tradeId(openOrder.getTradeId())
                            .counterName(openOrder.getCounterName())
                            .contractType(cntType)
                            .build()
            );

            // 2. new order
            final LimitOrder placedOrder = tradeResponse.getLimitOrder();
            if (placedOrder != null) {
                final FplayOrder fplayOrder = openOrder.cloneWithUpdate(placedOrder);
                fplayOrder.setPlacingType(placingType);
                res.add(fplayOrder);
            }

            // 3. failed on placing
            for (LimitOrder limitOrder : tradeResponse.getCancelledOrders()) {
                final FplayOrder fplayOrder = openOrder.cloneWithUpdate(limitOrder);
                fplayOrder.setPlacingType(placingType);
                res.add(fplayOrder);
            }

            scheduleMovingErrorsReset();
        } // end PLACE ORDER instead of cancelled on moving.

        return res;
    }

    private void cancelAndPlaceOnSo(FplayOrder openOrder, PlacingType btmPlacingTypeToChange) {
        FplayOrder placedFplayOrder = null;
        // 1. cancel current
        final String counterForLogs = getCounterName(openOrder.getTradeId());
        LimitOrder cancelledOrder = null;
        if (getMarketState() != MarketState.SYSTEM_OVERLOADED) {
            final String orderId = openOrder.getOrderId();
            final int attemptCount = soAttempts.get();
            try {
                BitmexTradeService tradeService = (BitmexTradeService) getExchange().getTradeService();
                cancelledOrder = tradeService.cancelLimitOrder(orderId);

                getTradeLogger().info(String.format("#%s/%s bitmexChangeOnSo cancelled id=%s", counterForLogs, attemptCount, orderId));

            } catch (HttpStatusIOException e) {
                overloadByXRateLimit();

                logger.error("#{}/{} error cancel order id={}", counterForLogs, attemptCount, orderId, e);
                getTradeLogger().error(String.format("#%s/%s error cancel order id=%s: %s", counterForLogs, attemptCount, orderId, e.toString()));
            } catch (Exception e) {
                logger.error("#{}/{} error cancel order id={}", counterForLogs, attemptCount, orderId, e);
                getTradeLogger().error(String.format("#%s/%s error cancel order id=%s: %s", counterForLogs, attemptCount, orderId, e.toString()));
            }

            // 2. place taker
            if (cancelledOrder != null) {
                openOrder.getOrderDetail().setOrderStatus(OrderStatus.CANCELED);
                final BigDecimal amountLeft = cancelledOrder.getTradableAmount().subtract(cancelledOrder.getCumulativeAmount());
                final List<FplayOrder> orderList = placeOrderInsteadOfCancelled(openOrder, amountLeft, btmPlacingTypeToChange);
            }
        }

//        return placedFplayOrder;
    }

    private BitmexStreamingExchange initExchange(String key, String secret,
                                                 String sslUri,
                                                 String host,
                                                 String port,
                                                 String wssUrl) {
        ExchangeSpecification spec = new ExchangeSpecification(BitmexStreamingExchange.class);
        spec.setApiKey(key);
        spec.setSecretKey(secret);
        spec.setExchangeSpecificParametersItem("Symbol", bitmexContractTypeEx.getSymbol());
        spec.setExchangeSpecificParametersItem("Scale", bitmexContractType.getScale());
        spec.setExchangeSpecificParametersItem("currencyToScale", currencyToScale);
        spec.setSslUri(sslUri);
        spec.setHost(host);
        spec.setPort(Integer.parseInt(port));
        spec.setExchangeSpecificParametersItem(StreamingExchange.API_URL, wssUrl);

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

    private void initWebSocketConnection(
            String sslUri,
            String host,
            String port,
            String wssUrl
    ) {
        // Connect to the Exchange WebSocket API. Blocking wait for the connection.
        try {
            exchange = initExchange(this.key, this.secret, sslUri, host, port, wssUrl);

            exchangeConnect();

        } catch (Exception e) {
            logger.error("Connection failed", e);
            checkForRestart();
        }
    }

    private void exchangeConnect() {
        logger.info("bitmex connecting public");
        exchange.connect()
                .doOnError(e -> {
                    logger.error("doOnError", e);
                    if (e instanceof NullPointerException
                            || (e instanceof DeploymentException && e.getMessage().contains("429"))) {
                        logger.warn("try fix WsWebSocketContainer.connectToServer NullPointerException."
                                + " Thread.currentThread().getContextClassLoader()=" + Thread.currentThread().getContextClassLoader());
                        ClassLoader cl = ClassLoader.getSystemClassLoader();
                        Thread.currentThread().setContextClassLoader(cl);
                    }
                })
//                .retryWhen(flowable -> flowable.delay(5, TimeUnit.SECONDS))
                .retryWhen(f -> f.flatMap(ex -> {
                    final int failedRec = failedReconnects.incrementAndGet();
                    final String m = ex.getMessage();
                    if (ex instanceof NullPointerException
                            || m.contains("429")
                            || m.contains("403")
                    ) {
                        if (failedRec % 2 == 0)
                        return Flowable.timer(10, TimeUnit.SECONDS);
                    }
                    return Flowable.timer(1, TimeUnit.MILLISECONDS);
                }))
//                .retry()
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
                            requestReconnectAsync();
                        },
                        throwable -> {
                            String msg = "BitmexService onDisconnect exception. ";
                            logger.error(msg + throwable);
                            requestReconnectAsync();
                        });
    }

    public void reconnect(boolean skipAfterReconnectReset) throws ReconnectFailedException {
        if (Thread.currentThread().getName().startsWith("bitmex-ob-executor")) {
            String startMsg = "WARNING: Bitmex reconnect in the thread " + Thread.currentThread().getName();
            tradeLogger.info(startMsg, getCurrencyPair().toString());
            warningLogger.info(startMsg);
            logger.info(startMsg);
        }

        String startMsg = "Warning: Bitmex reconnect is starting. " + getSubscribersStatuses();
        tradeLogger.info(startMsg, getCurrencyPair().toString());
        warningLogger.info(startMsg);
        logger.info(startMsg);

        try {
            destroyAction(1);

            this.orderBook = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
            this.setOrderBookShort(new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>()));
            this.orderBookXBTUSD = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
            this.orderBookXBTUSDShort = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
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

            final OrderBook ob = this.orderBook;
            String msgOb = String.format("OrderBook: asks=%s, bids=%s, timestamp=%s. ",
                    ob.getAsks().size(), // should be lock on collections
                    ob.getBids().size(), // should be lock on collections
                    ob.getTimeStamp());
            final OrderBook obXBTUSD = this.orderBookXBTUSD;
            String msgObForPrice = sameOrderBookXBTUSD() ? ""
                    : String.format("OrderBookForPrice: asks=%s, bids=%s, timestamp=%s. ",
                            obXBTUSD.getAsks().size(),
                            obXBTUSD.getBids().size(),
                            obXBTUSD.getTimeStamp());
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
                        getOpenOrdersSize());

                tradeLogger.info(finishMsg, getCurrencyPair().toString());
                warningLogger.info(finishMsg);
                logger.info(finishMsg);

                if (!skipAfterReconnectReset) {
                    afterReconnect();
                }
            }

        } catch (Exception e) {
            String msg = "Warning: Bitmex reconnect error: " + e.getMessage() + getSubscribersStatuses();
            tradeLogger.info(msg, getCurrencyPair().toString());
            warningLogger.info(msg);
            logger.info(msg);

            throw new ReconnectFailedException(e);
        }
    }

    private boolean sameOrderBookXBTUSD() {
        return bitmexContractType == BitmexContractType.XBTUSD_Perpetual;
    }

    private boolean orderBookIsFilled() {
        final OrderBook ob = this.getOrderBook(); // should be lock on collections
        return ob.getAsks().size() >= ORDERBOOK_MAX_SIZE && ob.getBids().size() >= ORDERBOOK_MAX_SIZE;
    }

    private boolean orderBookForPriceIsFilled() {
        final OrderBook ob = this.orderBookXBTUSDShort;
        return sameOrderBookXBTUSD()
                || (ob.getAsks().size() >= ORDERBOOK_MAX_SIZE && ob.getBids().size() >= ORDERBOOK_MAX_SIZE);
    }

    private void doRestart(String errMsg) {
        errMsg += " Do restart. " + getSubscribersStatuses();
        warningLogger.info(errMsg);
        tradeLogger.info(errMsg, getCurrencyPair().toString());
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

            if (quoteSubscription != null) {
                quoteSubscription.dispose();
            }
            if (orderBookSubscription != null) {
                orderBookSubscription.dispose();
            }
            if (orderBookSubscriptionExtra != null) {
                orderBookSubscriptionExtra.dispose();
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
            if (pingStatSub != null) {
                pingStatSub.dispose();
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

    private OrderBook mergeOrderBook10(BitmexDepth obUpdate) {
        final long ms = Instant.now().toEpochMilli() - obUpdate.getTimestamp().toInstant().toEpochMilli();
        OrderBook finalOB;
        String symbol = obUpdate.getSymbol();
        if (symbol.equals(bitmexContractTypeEx.getSymbol())) {
//            final long adaptStart = Instant.now().toEpochMilli();
            finalOB = BitmexStreamAdapters.adaptBitmexOrderBook(obUpdate, getCurrencyPair());
            this.orderBook = finalOB;
            this.getOrderBookShort().setOb(finalOB, obUpdate.getTimestamp(), finalOB.getTimeStamp().toInstant());
//            final long adaptObMs = Instant.now().toEpochMilli() - adaptStart; // tested by logs. Result 0ms
//            System.out.println("trad10ToUsMs=" + ms + ". adaptOrderBookMs=" + adaptObMs);
            metricsDictionary.putBitmex_plBefore_ob_saveTime_traditional10_market(ms);

            final long leftMs = obUpdate.getReceiveTimestamp().toInstant().toEpochMilli() - obUpdate.getTimestamp().toInstant().toEpochMilli();
            addGetObDelay(leftMs);

        } else if (symbol.equals(BitmexContractType.XBTUSD_Perpetual.getSymbol())) {
            final CurrencyPair currencyPair = settingsRepositoryService.getCurrencyPair(BitmexContractType.XBTUSD_Perpetual);
            finalOB = BitmexStreamAdapters.adaptBitmexOrderBook(obUpdate, currencyPair);
            this.orderBookXBTUSD = finalOB;
            this.orderBookXBTUSDShort = this.orderBookXBTUSD;
        } else {
            // skip the update
            throw new IllegalArgumentException("OB update has no symbol. " + obUpdate);
        }

        final long endMergeMs = Instant.now().toEpochMilli();
        metricsDictionary.putBitmex_plBefore_ob_saveTime_traditional10(endMergeMs - obUpdate.getReceiveTimestamp().toInstant().toEpochMilli());
        return finalOB;
    }

    private OrderBook mergeOrderBookIncremental(BitmexOrderBook obUpdate, BitmexObType obType) {
        if (obUpdate.getBitmexOrderList().size() == 0 || obUpdate.getBitmexOrderList().get(0).getSymbol() == null) {
            // skip the update
            throw new IllegalArgumentException("OB update has no symbol. " + obUpdate);
        }
        CurrencyPair currencyPair;
        OrderBook fullOB;
        String symbol = obUpdate.getBitmexOrderList().get(0).getSymbol();
        boolean isDefault = false;
        if (symbol.equals(bitmexContractTypeEx.getSymbol())) {
            currencyPair = getCurrencyPair();
            fullOB = this.orderBook;
            isDefault = true;
        } else if (symbol.equals(BitmexContractType.XBTUSD_Perpetual.getSymbol())) {
            currencyPair = settingsRepositoryService.getCurrencyPair(BitmexContractType.XBTUSD_Perpetual);
            fullOB = this.orderBookXBTUSD;
        } else {
            // skip the update
            throw new IllegalArgumentException("OB update has no symbol. " + obUpdate);
        }

        OrderBook finalOB;
        final Date gettingTimestamp = new Date(obUpdate.getGettingTimeEpochMs());
        if (obUpdate.getAction().equals("partial")) {
            logger.info("update OB. partial: " + obUpdate);
            finalOB = BitmexStreamAdapters.adaptBitmexOrderBook(obUpdate, currencyPair);
        } else if (obUpdate.getAction().equals("delete")) {
            fullOB = BitmexStreamAdapters.cloneOrderBook(fullOB, gettingTimestamp);
            finalOB = BitmexStreamAdapters.delete(fullOB, obUpdate);
        } else if (obUpdate.getAction().equals("update")) {
            fullOB = BitmexStreamAdapters.cloneOrderBook(fullOB, gettingTimestamp);
            finalOB = BitmexStreamAdapters.update(fullOB, obUpdate, new Date(), currencyPair);
        } else if (obUpdate.getAction().equals("insert")) {
            fullOB = BitmexStreamAdapters.cloneOrderBook(fullOB, gettingTimestamp);
            finalOB = BitmexStreamAdapters.insert(fullOB, obUpdate, new Date(), currencyPair);
        } else {
            // skip the update
            throw new IllegalArgumentException("Unknown OrderBook action=" + obUpdate.getAction() + ". " + obUpdate);
        }
        if (finalOB.getBids().size() == 0 || finalOB.getAsks().size() == 0) {
            logger.warn("finalOB is empty. obUpdate: " + obUpdate.getAction() + " count=" + obUpdate.getBitmexOrderList().size());
            logger.warn("finalOB is empty. finalOB: asks=" + finalOB.getAsks().size()
                    + " bids=" + finalOB.getBids().size() + " timestamp=" + finalOB.getTimeStamp() + finalOB);
//            try {
//                reSubscribeOrderBooks(true);
//            } catch (Exception e) {
//                String msg = "Warning: Bitmex resubscribe error: " + e.getMessage() + getSubscribersStatuses();
//                tradeLogger.info(msg, getCurrencyPair().toString());
//                warningLogger.info(msg);
//                logger.info(msg);
//            }
        } else if (!startFlag) {
            startFlag = true;
            logger.warn("update OB : " + obUpdate);
            logger.warn("finalOB : " + finalOB);
            logger.info("finalOB bids=" + finalOB.getBids().size());
        }

        OrderBook shortOb;
        if (isDefault) {
            this.orderBook = finalOB;
            final OrderBook orderBookShort = obType == BitmexObType.INCREMENTAL_FULL
                    ? new OrderBook(finalOB.getTimeStamp(),
                    finalOB.getAsks().stream().limit(ORDERBOOK_MAX_SIZE).map(Utils::cloneLimitOrder).collect(Collectors.toList()),
                    finalOB.getBids().stream().limit(ORDERBOOK_MAX_SIZE).map(Utils::cloneLimitOrder).collect(Collectors.toList()))
                    : this.orderBook;
            this.setOrderBookShort(orderBookShort);
            this.getOrderBookShort().setOb(orderBookShort, null, Instant.now());
            shortOb = orderBookShort;
        } else {
            this.orderBookXBTUSD = finalOB;
            this.orderBookXBTUSDShort = obType == BitmexObType.INCREMENTAL_FULL
                    ? new OrderBook(finalOB.getTimeStamp(),
                    finalOB.getAsks().stream().limit(ORDERBOOK_MAX_SIZE).map(Utils::cloneLimitOrder).collect(Collectors.toList()),
                    finalOB.getBids().stream().limit(ORDERBOOK_MAX_SIZE).map(Utils::cloneLimitOrder).collect(Collectors.toList()))
                    : this.orderBookXBTUSD;
            shortOb = this.orderBookXBTUSDShort;
        }

        final long ms = Instant.now().toEpochMilli() - obUpdate.getGettingTimeEpochMs();
        if (obType == BitmexObType.INCREMENTAL_FULL) {
            metricsDictionary.putBitmex_plBefore_ob_saveTime_incrementalFull(ms);
        } else {
            metricsDictionary.putBitmex_plBefore_ob_saveTime_incremental50(ms);
        }

        return shortOb;
    }

    boolean startFlag = false;

    private Disposable startQuoteListener() {
        final BitmexStreamingMarketDataService streamingMarketDataService = (BitmexStreamingMarketDataService) exchange.getStreamingMarketDataService();
        List<String> symbols = new ArrayList<>();
        symbols.add(bitmexContractTypeEx.getSymbol());
//        if (!sameOrderBookXBTUSD()) {
//            symbols.add(bitmexContractTypeXBTUSD.getSymbol());
//        }
        final BitmexObType obType = settingsRepositoryService.getSettings().getBitmexObType();
        return streamingMarketDataService.getQuote(symbols)
                .observeOn(Schedulers.newThread()) // the sync queue is here.
                .filter(q -> q.getAction().equals("insert"))
                .doOnNext(bitmexQuoteLine -> {
                    if (obType != BitmexObType.TRADITIONAL_10) {
                        getOrderBookShort().updateMarketTimestamp(bitmexQuoteLine);
                    }
                })
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .doOnError(throwable -> logger.error("quote error " + throwable.getMessage()))
                .subscribe();
    }

    private void startOrderBookListener() {
        final BitmexObType obType = settingsRepositoryService.getSettings().getBitmexObType();
        startFlag = false;
        final Flowable<OrderBook> orderBookObservable;
        final Flowable<OrderBook> orderBookObservableExtra;
        if (obType != BitmexObType.TRADITIONAL_10) {
            orderBookObservable = getBitmexOrderBookObservable(obType)
                    .doOnError(throwable -> handleSubscriptionError(throwable, "can not get orderBook"))
                    .toFlowable(BackpressureStrategy.BUFFER)
                    .observeOn(stateUpdater) // the sync queue is here.
                    .map(bitmexOrderBook -> mergeOrderBookIncremental(bitmexOrderBook, obType));
            orderBookObservableExtra = null;
        } else {
            List<String> symbols = new ArrayList<>();
            symbols.add(bitmexContractTypeEx.getSymbol());
            if (!sameOrderBookXBTUSD()) {
                symbols.add(BitmexContractType.XBTUSD_Perpetual.getSymbol());
            }
            final BitmexStreamingMarketDataService streamingMarketDataService = (BitmexStreamingMarketDataService) exchange.getStreamingMarketDataService();
            final Observable<BitmexDepth> allOrderBooks = streamingMarketDataService.getOrderBookTop10(symbols)
                    .doOnError(throwable -> handleSubscriptionError(throwable, "can not get orderBook"))
                    .doOnNext(obUpdate -> {
                        if (obUpdate.getSymbol() == null) {
                            // skip the update
                            throw new IllegalArgumentException("OB update has no symbol. " + obUpdate);
                        }
                    }).share();
            orderBookObservable = allOrderBooks
                    .filter(u -> u.getSymbol().equals(bitmexContractTypeEx.getSymbol()))
                    .toFlowable(BackpressureStrategy.LATEST)
                    .observeOn(stateUpdater, false, 1)
                    .filter(ob -> {
                        final boolean hasData = ob.getBids().length != 0 && ob.getAsks().length != 0;
                        if (!hasData) {
                            log.warn("Empty OB: " + ob);
                        }
                        return hasData;
                    })
                    .map(this::mergeOrderBook10);

            orderBookObservableExtra = allOrderBooks
                    .filter(u -> u.getSymbol().equals(BitmexContractType.XBTUSD_Perpetual.getSymbol()))
                    .toFlowable(BackpressureStrategy.LATEST)
                    .observeOn(stateUpdaterObExtra, false, 1)
                    .map(this::mergeOrderBook10);
        }
        bitmexObTypeCurrent = obType;

        final Consumer<Throwable> obErrorConsumer = throwable -> {
            if (throwable instanceof NotConnectedException) {
                logger.error("can not convert orderBook " + throwable.getMessage()); //
                warningLogger.error("can not convert orderBook " + throwable.getMessage());
            } else {
                logger.error("can not convert orderBook", throwable); //
                warningLogger.error("can not convert orderBook", throwable);
            }
            orderBookErrors.incrementAndGet();
        };
        orderBookSubscription = orderBookObservable
                .doOnTerminate(() -> logger.info("bitmex subscription doOnTerminate"))
                .filter(ob -> ob.getBids().size() != 0 && ob.getAsks().size() != 0)
                .doOnError(obErrorConsumer)
                .retry()
                .subscribe(orderBook -> {
                    try {
                        if (isDefaultOB(orderBook)) {
                            metricsDictionary.incBitmexObCounter();
                            afterOrderBookChanged(orderBook);
                        } else {
                            afterOrderBookXBTUSDChanged(orderBook);
                        }
                    } catch (Exception e) {
                        logger.error("Can not merge OrderBook", e);
                    }

                }, throwable -> {
                    logger.error("can not merge orderBook exception", throwable);
                    warningLogger.error("can not merge orderBook", throwable);
                    orderBookErrors.incrementAndGet();
                    requestReconnect(true);
                });
        if (orderBookObservableExtra != null) {
            orderBookSubscriptionExtra = orderBookObservableExtra
                    .doOnTerminate(() -> logger.info("bitmex subscription doOnTerminate"))
                    .filter(ob -> ob.getBids().size() != 0 && ob.getAsks().size() != 0)
                    .doOnError(obErrorConsumer)
                    .retry()
                    .subscribe(orderBook -> {
                        try {
                            afterOrderBookXBTUSDChanged(orderBook);
                        } catch (Exception e) {
                            logger.error("Can not merge OrderBook", e);
                        }
                    }, throwable -> {
                        logger.error("can not merge orderBook exception", throwable);
                        warningLogger.error("can not merge orderBook", throwable);
                        orderBookErrors.incrementAndGet();
                        requestReconnect(true);
                    });

        }

    }

    private Observable<BitmexOrderBook> getBitmexOrderBookObservable(BitmexObType obType) {
        List<String> symbols = new ArrayList<>();
        symbols.add(bitmexContractTypeEx.getSymbol());
        if (!sameOrderBookXBTUSD()) {
            symbols.add(BitmexContractType.XBTUSD_Perpetual.getSymbol());
        }
        final BitmexStreamingMarketDataService streamingMarketDataService = (BitmexStreamingMarketDataService) exchange.getStreamingMarketDataService();
        if (obType == null || obType == BitmexObType.INCREMENTAL_25) {
            return streamingMarketDataService.getOrderBookL2_25(symbols);
        } else if (obType == BitmexObType.INCREMENTAL_FULL) {
            return streamingMarketDataService.getOrderBookL2(symbols);
        }
        // else obType == BitmexObType.TRADITIONAL_10
        throw new IllegalArgumentException("use another orderBook subscriber for TRADITIONAL_10 ");
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
        return getCurrencyPair().equals(currencyPair);
    }

    @SuppressWarnings("Duplicates")
    private void afterOrderBookChanged(OrderBook orderBook) {
        if (orderBook != null && orderBook.getBids().size() > 0 && orderBook.getAsks().size() > 0) {
            Instant obChangeTime = Instant.now();

            final LimitOrder bestAsk = Utils.getBestAsk(orderBook);
            final LimitOrder bestBid = Utils.getBestBid(orderBook);

            if (bestAsk != null && bestBid != null) {
                orderBookLastTimestamp = new Date();
            }

            stateRecalcInStateUpdaterThread();

            this.bestAsk = bestAsk != null ? bestAsk.getLimitPrice() : BigDecimal.ZERO;
            this.bestBid = bestBid != null ? bestBid.getLimitPrice() : BigDecimal.ZERO;
            logger.debug("ask: {}, bid: {}", this.bestAsk, this.bestBid);
            if (this.bestBid.compareTo(this.bestAsk) >= 0) {
                String warn = String.format("bid(%s) >= ask(%s). LastRun of 'checkOrderBooks' is %s. ",
                        this.bestBid, this.bestAsk, extrastopService.getLastRun());
                if (obWrongCount.incrementAndGet() < 100) {
                    logger.warn(warn);
                    warningLogger.warn(warn);
                } else {
                    warn += "Do reconnect.";
                    logger.warn(warn);
                    warningLogger.warn(warn);
                    requestReconnectAsync();
                    obWrongCount.set(0);
                }
            }

            getApplicationEventPublisher().publishEvent(new ObChangeEvent(new SigEvent(SigType.BTM, getArbType(), obChangeTime)));
        }
    }

    @SuppressWarnings("Duplicates")
    private void afterOrderBookXBTUSDChanged(OrderBook orderBook) {
        if (orderBook != null && orderBook.getBids().size() > 0 && orderBook.getAsks().size() > 0) {
            final LimitOrder bestAsk = Utils.getBestAsk(orderBook);
            final LimitOrder bestBid = Utils.getBestBid(orderBook);

//            if (bestAsk != null && bestBid != null) {
//                orderBookLastTimestampXBTUSD = new Date();
//            }

            this.bestAskXBTUSD = bestAsk != null ? bestAsk.getLimitPrice() : BigDecimal.ZERO;
            this.bestBidXBTUSD = bestBid != null ? bestBid.getLimitPrice() : BigDecimal.ZERO;
            logger.debug("XBTUSD ask: {}, bid: {}", this.bestAskXBTUSD, this.bestBidXBTUSD);
            if (this.bestBidXBTUSD.compareTo(this.bestAskXBTUSD) >= 0) {
                String warn = String.format("bid(%s) >= ask(%s). LastRun of 'checkOrderBooks' is %s. ",
                        this.bestBidXBTUSD, this.bestAskXBTUSD, extrastopService.getLastRun());
                if (obWrongCountXBTUSD.incrementAndGet() < 100) {
                    logger.warn(warn);
                    warningLogger.warn(warn);
                } else {
                    warn += "Do reconnect.";
                    logger.warn(warn);
                    warningLogger.warn(warn);
                    requestReconnectAsync();
                    obWrongCountXBTUSD.set(0);
                }
            }
        }
    }

    @Override
    public OrderBook getOrderBookXBTUSD() {
        OrderBook orderBook;
        if (sameOrderBookXBTUSD()) {
            orderBook = this.getOrderBook(); // getShortOrderBook(this.orderBook);
        } else {
            orderBook = this.orderBookXBTUSDShort;// getShortOrderBook(this.orderBookXBTUSD);
        }

        return orderBook;
    }

    private final ExecutorService ooMergerExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory(getName() + "-oo-merger"));
    private final Scheduler ooMergerScheduler = Schedulers.from(ooMergerExecutor);

    private Disposable startOpenOrderListener() {
        return exchange.getStreamingTradingService()
                .getOpenOrderObservable(currencyToScale)
                .observeOn(ooMergerScheduler) // blocking queue is here
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
        logger.debug("OpenOrders: " + updateOfOpenOrders.toString());

        final FplayOrder stub = new FplayOrder(getMarketId());
        final List<LimitOrder> limitOrderList = updateOfOpenOrders.getOpenOrders();

        updateFplayOrdersToCurrStab(limitOrderList, stub);

        getOpenOrders()
                .forEach(o -> setQuotesForArbLogs(o.getTradeId(), o.getLimitOrder().getAveragePrice(), false));

        addCheckOoToFree();
    }

    @Override
    protected Completable recalcAffordableContracts() {
        return Completable.fromAction(() -> {
            final BigDecimal reserveBtc = arbitrageService.getParams().getReserveBtc1();

            final Pos position = this.pos.get();
            final AccountBalance account = this.account.get();
            final OrderBook orderBook = getOrderBook();

            if (account != null && position != null && Utils.orderBookIsFull(orderBook)) {
                final BigDecimal availableBtc = account.getAvailable();
                final BigDecimal equityBtc = account.getEMark();
                final BigDecimal bestAsk = Utils.getBestAsk(orderBook).getLimitPrice();
                final BigDecimal bestBid = Utils.getBestBid(orderBook).getLimitPrice();
                final BigDecimal positionContracts = position.getPositionLong();
                final BigDecimal leverage = position.getLeverage();

                if (availableBtc != null && equityBtc != null && positionContracts != null && leverage != null) {

                    final BigDecimal forLong1 = ((availableBtc.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage))
                            .setScale(0, BigDecimal.ROUND_DOWN);
                    final BigDecimal forShort1 = ((availableBtc.subtract(reserveBtc)).multiply(bestBid).multiply(leverage))
                            .setScale(0, BigDecimal.ROUND_DOWN);
                    if (positionContracts.signum() == 0) {
                        affordable.setForLong(forLong1);
                        affordable.setForShort(forShort1);
                    } else if (positionContracts.signum() > 0) {
                        affordable.setForLong(forLong1);
                        BigDecimal forShort = (positionContracts.add((equityBtc.subtract(reserveBtc)).multiply(bestBid).multiply(leverage)))
                                .setScale(0, BigDecimal.ROUND_DOWN);
                        if (forShort.compareTo(positionContracts) < 0) {
                            forShort = positionContracts;
                        }
                        affordable.setForShort(forShort);
                    } else { //if (positionContracts.signum() < 0) {
                        BigDecimal forLong = (positionContracts.negate().add((equityBtc.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage)))
                                .setScale(0, BigDecimal.ROUND_DOWN);
                        if (forLong.compareTo(positionContracts) < 0) {
                            forLong = positionContracts;
                        }
                        affordable.setForLong(forLong);
                        affordable.setForShort(forShort1);
                    }

                }
            }
        });
    }

    @Override
    public Affordable recalcAffordable() {
        recalcAffordableContracts().subscribe();
        return affordable;
    }

    @Override
    public boolean isAffordable(OrderType orderType, BigDecimal tradableAmount) {
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

    public TradeResponse singleOrder(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, SignalType signalType,
            PlacingType placingType, String toolName, AmountType amountType) {
        final Long tradeId = arbitrageService.getLastInProgressTradeId();
        final String counterName = getCounterName(signalType, tradeId);
        final BitmexContractType contractType = (bitmexContractType.isQuanto() && toolName != null && toolName.equals("XBTUSD"))
                ? BitmexContractType.XBTUSD_Perpetual
                : bitmexContractType;

        final PlaceOrderArgs placeOrderArgs = PlaceOrderArgs.builder()
                .orderType(orderType)
                .amount(amount)
                .bestQuotes(bestQuotes)
                .placingType(placingType)
                .signalType(signalType)
                .attempt(1)
                .tradeId(tradeId)
                .counterName(counterName)
                .contractType(contractType)
                .amountType(amountType)
                .build();

        return placeOrder(placeOrderArgs);
    }

    public TradeResponse singleTakerOrder(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes, SignalType signalType) {
        return singleOrder(orderType, amountInContracts, bestQuotes, signalType, PlacingType.TAKER, null, AmountType.CONT);
    }

    @Override
    public TradeResponse placeOrder(final PlaceOrderArgs placeOrderArgs) {
        final Instant startPlacing = Instant.now();
        prevCumulativeAmount = BigDecimal.ZERO;

        final TradeResponse tradeResponse = new TradeResponse();

        final BitmexContractType btmContType = (placeOrderArgs.getContractType() != null && placeOrderArgs.getContractType() instanceof BitmexContractType)
                ? ((BitmexContractType) placeOrderArgs.getContractType())
                : bitmexContractType;
        final CurrencyPair currencyPair = settingsRepositoryService.getCurrencyPair(btmContType);
        final String contractTypeStr = currencyPair.toString();

        final Settings settings = settingsRepositoryService.getSettings();
        final Integer maxAttempts = settings.getBitmexSysOverloadArgs().getPlaceAttempts();
        final String counterName = placeOrderArgs.getCounterName();
        if (placeOrderArgs.getAttempt() > maxAttempts) {
            final String logString = String.format("#%s Bitmex Warning placing: too many attempt(%s) when SYSTEM_OVERLOADED. Do nothing.",
                    counterName,
                    maxAttempts);

            logger.error(logString);
            tradeLogger.error(logString, contractTypeStr);
            warningLogger.error(logString);

            tradeResponse.setErrorCode(logString);
            return tradeResponse;
        }

        final Order.OrderType orderType = placeOrderArgs.getOrderType();
//         MANUAL TEST
//        final BigDecimal amount = BigDecimal.valueOf(10);
        final BigDecimal amount = BitmexUtils.amountInContracts(placeOrderArgs, getCm());
        final BestQuotes bestQuotes = placeOrderArgs.getBestQuotes();
        PlacingType placingTypeInitial = placeOrderArgs.getPlacingType();
        final SignalType signalType = placeOrderArgs.getSignalType();
        final Long tradeId = placeOrderArgs.getTradeId();
        final PlBefore plBeforeBtm = placeOrderArgs.getPlBefore();
        final String symbol = currencyPair.base.getCurrencyCode() + currencyPair.counter.getCurrencyCode();
        final Integer scale = btmContType.getScale();
        BtmFokAutoArgs btmFokArgs = placeOrderArgs.getBtmFokArgs(); // not null only when by signal

        plBeforeBtm.setRequestPlacing(startPlacing);
        final Mon monPlacing = monitoringDataService.fetchMon(getName(), "placeOrder");

        if (placingTypeInitial == null) {
            tradeLogger.warn("WARNING: placingType is null. " + placeOrderArgs, contractTypeStr);
            placingTypeInitial = bitmexChangeOnSoService.getLeftPlacingType(signalType);
        }

        PlacingType placingType = placingTypeInitial;

        MarketState nextMarketState = getMarketState();
        arbitrageService.setSignalType(signalType);

        try {
            setMarketState(MarketState.PLACING_ORDER);

            final BitmexTradeService bitmexTradeService = (BitmexTradeService) exchange.getTradeService();

            int attemptCount = 0;
            int badGatewayCount = 0;
            shouldStopPlacing = false;
            while (attemptCount < maxAttempts
                    && !getArbitrageService().isArbStateStopped()
                    && !getArbitrageService().isArbForbidden(signalType)
                    && !shouldStopPlacing
                    && !placeOrderArgs.isShouldStopNtUsdRecovery()) {
                attemptCount++;
                if (placeOrderArgs.getAttempt() == PlaceOrderArgs.NO_REPEATS_ATTEMPT && attemptCount > 1) {
                    // means: R_wait_L on signal. No repeats. Cancel okex deferred.
                    nextMarketState = MarketState.READY;
                    final String warnMsg = "NO_REPEATS_ATTEMPT on placing. restore marketState to READY and reset WAITING_ARB";
                    logger.info(warnMsg);
                    tradeLogger.info(warnMsg);
                    ((OkCoinService) arbitrageService.getRightMarketService()).resetWaitingArb(warnMsg);
                    break;
                }
                if (settingsRepositoryService.getSettings().getManageType().isManual() && !signalType.isRecoveryNtUsd()) {
                    if (!signalType.isManual() || attemptCount > 1) {
                        final String warnMsg = "MangeType is MANUAL. Stop placing.";
                        logger.info(warnMsg);
                        tradeLogger.info(warnMsg);
                        warningLogger.info(warnMsg);
                        ((OkCoinService) arbitrageService.getRightMarketService()).resetWaitingArb(warnMsg);
                        break; // when MangeType is MANUAL, only the first manualSignal is accepted
                    }
                }

                try {
                    String execDuration = "n/a";
                    String orderId;
                    BigDecimal thePrice;
                    if (reconnectInProgress) {
                        tradeLogger.warn("placeOrder waiting for reconnect.", contractTypeStr);
                        while (reconnectInProgress) {
                            Thread.sleep(200);
                            if (placeOrderArgs.isShouldStopNtUsdRecovery()) {
                                nextMarketState = MarketState.READY;
                                final String warnMsg = "ShouldStopNtUsdRecovery";
                                logger.info(warnMsg);
                                tradeLogger.info(warnMsg);
                                ((OkCoinService) arbitrageService.getRightMarketService()).resetWaitingArb(warnMsg);
                                break;
                            }
                        }
                        tradeLogger.warn("placeOrder end waiting for reconnect.", contractTypeStr);
                    }

                    final OrderBook orderBook;
                    if (getContractType().isQuanto() && !btmContType.isQuanto()) {
                        orderBook = getOrderBookXBTUSD();
                    } else {
                        if (bestQuotes != null && bestQuotes.getBtmOrderBook() != null) {
                            orderBook = bestQuotes.getBtmOrderBook();
                            bestQuotes.setBtmOrderBook(null);
                            final String message = String.format("#%s placing %s using L_OB_timestamp=%s",
                                    counterName, orderType,
                                    Utils.getTenAskBid(orderBook, counterName, "Bitmex orderBook", getArbType().s()));
                            tradeLogger.info(message, contractTypeStr);

                        } else {
                            orderBook = getOrderBook();
                        }
                    }

                    bitmexChangeOnSoService.tryActivate(attemptCount);

                    final PlacingType changedPt = bitmexChangeOnSoService.getPlacingTypeToChange(signalType);
                    placingType = changedPt != null ? changedPt : placingTypeInitial;
                    String extraLog = "";

                    if (placingType.isNonTaker()) {

                        thePrice = (settings.getBitmexPrice() != null && settings.getBitmexPrice().signum() != 0)
                                ? settings.getBitmexPrice()
                                : createBestPrice(orderType, placingType, orderBook, btmContType);

                        persistenceService.getDealPricesRepositoryService().setBtmOpenPrice(tradeId, thePrice);

                        boolean participateDoNotInitiate = placingType == PlacingType.MAKER || placingType == PlacingType.MAKER_TICK;

                        // metrics
                        final Instant startReq = Instant.now();
                        throwTestingException();
                        final LimitOrder resultOrder = bitmexTradeService.placeLimitOrderBitmex(
                                new LimitOrder(orderType, amount, currencyPair, "0", new Date(), thePrice),
                                participateDoNotInitiate, symbol, scale, placeOrderArgs.isPreliqOrder());
                        final Instant endReq = Instant.now();
                        tradeLogger.info("placingNonTaker " + getxRateLimit().getString());

                        plBeforeBtm.setMarketTransactTime(resultOrder.getTimestamp().toInstant());
                        plBeforeBtm.setGetAnswerFromPlacing(endReq);
                        final long waitingMarketMs = endReq.toEpochMilli() - startReq.toEpochMilli();
                        monPlacing.getWaitingMarket().add(BigDecimal.valueOf(waitingMarketMs));
                        if (waitingMarketMs > 5000) {
                            logger.warn(placingType + " bitmexPlaceOrder waitingMarketMs=" + waitingMarketMs);
                        }
                        monitoringDataService.saveMon(monPlacing);
                        metricsDictionary.putBitmexPlacing(waitingMarketMs);
                        fplayTradeService.addBitmexPlacingMs(tradeId, waitingMarketMs);

                        if (attemptCount == 1) {
                            MetricsUtils.sendPlBefore(metricsDictionary, plBeforeBtm, logger);
                        }

                        orderId = resultOrder.getId();
                        final FplayOrder fplayOrder = new FplayOrder(this.getMarketId(), tradeId, counterName, resultOrder, bestQuotes, placingType, signalType,
                                placeOrderArgs.getPortionsQty(), placeOrderArgs.getPortionsQtyMax());
                        orderRepositoryService.updateSync(fplayOrder); // updateAsync?
                        addOpenOrder(fplayOrder);

                        if (resultOrder.getStatus() == Order.OrderStatus.CANCELED) {
                            incCancelledInRow(contractTypeStr);

                            tradeResponse.addCancelledOrder(resultOrder);
                            tradeResponse.setErrorCode("WAS CANCELED"); // for the last iteration
                            tradeResponse.setLimitOrder(null);
                            tradeLogger.info(String.format("#%s %s %s CANCELED order had execInst ParticipateDoNotInitiate "
                                            + "amount=%s, filled=%s, quote=%s, orderId=%s",
                                    counterName,
                                    placingType,
                                    orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                                    amount.toPlainString(),
                                    resultOrder.getCumulativeAmount(),
                                    thePrice,
                                    orderId), contractTypeStr);
                            Thread.sleep(settings.getBitmexSysOverloadArgs().getBetweenAttemptsMsSafe());
                            continue;
                        }
                        cancelledInRow.set(0);
                        nextMarketState = MarketState.ARBITRAGE;

                    } else { // TAKER
                        final LimitOrder marketOrder = new LimitOrder(orderType, amount, null, null, null, null);

                        // metrics
                        final Instant startReq = Instant.now();
                        throwTestingException();
                        final LimitOrder resultOrder;
                        if (placingType == PlacingType.TAKER_FOK || placingType == PlacingType.TAKER_IOC) {
                            final StringBuilder fokExtraLogs = new StringBuilder();
                            thePrice = (settings.getBitmexPrice() != null && settings.getBitmexPrice().signum() != 0)
                                    ? settings.getBitmexPrice()
                                    : createFillOrKillPrice(fokExtraLogs, orderType, orderBook, btmContType, btmFokArgs, placingType);
                            final String message = String.format("#%s placing %s %s, q=%s, a=%s. pos=%s, L_OB_timestamp=%s. %s",
                                    counterName,
                                    placingType,
                                    orderType,
                                    thePrice,
                                    amount.toPlainString(),
                                    getPositionAsString(),
                                    Utils.dateToString(orderBook.getTimeStamp()),
                                    fokExtraLogs.toString());
                            tradeLogger.info(message, contractTypeStr);
                            ordersLogger.info(message);

                            if (placingType == PlacingType.TAKER_FOK) {
                                resultOrder = bitmexTradeService.placeMarketOrderFillOrKill(marketOrder, symbol, thePrice, scale,
                                        placeOrderArgs.isPreliqOrder());
                            } else { //TAKER_IOC
                                resultOrder = bitmexTradeService.placeMarketOrderImmediateOrCancel(marketOrder, symbol, thePrice, scale,
                                        placeOrderArgs.isPreliqOrder());
                            }
                            if (resultOrder.getStatus() == OrderStatus.CANCELED) {
                                String type = placingType == PlacingType.TAKER_FOK ? "FillOrKill" : "ImmediateOrCancel";
                                extraLog = "order had timeInForce of " + type + ". "
                                        + "filledAmount=" + resultOrder.getCumulativeAmount() + ". "
                                        + Utils.getTenAskBid(orderBook, counterName, "Bitmex OrderBook",
                                        getArbType().s());
                                nextMarketState = MarketState.READY;
                            }
                        } else {
                            resultOrder = bitmexTradeService.placeMarketOrderBitmex(marketOrder, symbol, placeOrderArgs.isPreliqOrder());
                        }
                        tradeLogger.info("placingTaker " + getxRateLimit().getString());

                        if (attemptCount == 1
                                && resultOrder.getTimestamp() != null
                                && plBeforeBtm.getSignalTime() != null
                                && signalType == SignalType.AUTOMATIC
                                && (
                                resultOrder.getStatus() == OrderStatus.FILLED
                                        || resultOrder.getStatus() == OrderStatus.PARTIALLY_FILLED
                                        || resultOrder.getCumulativeAmount().signum() > 0 // IOC may be CANCELLED with filledAmount>0
                        )) {
                            final long d = resultOrder.getTimestamp().toInstant().toEpochMilli() - startPlacing.toEpochMilli();
                            addExecDuration(d);
                            execDuration = String.valueOf(d);
                        }
                        final Instant endReq = Instant.now();
                        plBeforeBtm.setMarketTransactTime(resultOrder.getTimestamp().toInstant());
                        plBeforeBtm.setGetAnswerFromPlacing(endReq);
                        final long waitingMarketMs = endReq.toEpochMilli() - startReq.toEpochMilli();
                        monPlacing.getWaitingMarket().add(BigDecimal.valueOf(waitingMarketMs));
                        if (waitingMarketMs > 5000) {
                            logger.warn(placingType + " bitmexPlaceOrder waitingMarketMs=" + waitingMarketMs);
                        }
                        monitoringDataService.saveMon(monPlacing);
                        metricsDictionary.putBitmexPlacing(waitingMarketMs);
                        fplayTradeService.addBitmexPlacingMs(tradeId, waitingMarketMs);

                        if (attemptCount == 1) {
                            MetricsUtils.sendPlBefore(metricsDictionary, plBeforeBtm, logger);
                        }

                        orderId = resultOrder.getId();
                        thePrice = resultOrder.getAveragePrice();
                        final FplayOrder fplayOrder = new FplayOrder(this.getMarketId(), tradeId, counterName, resultOrder, bestQuotes, placingType,
                                signalType, placeOrderArgs.getPortionsQty(), placeOrderArgs.getPortionsQtyMax());
                        orderRepositoryService.updateSync(fplayOrder);// sync because avgPrice related to it.(fix: btm not fully filled)
                        persistenceService.getDealPricesRepositoryService().setBtmOpenPrice(tradeId, thePrice);
                        addOpenOrder(fplayOrder);

                        tradeResponse.setLimitOrder(resultOrder);

                        final boolean partiallyFilled = placingType == PlacingType.TAKER_IOC
                                && resultOrder.getCumulativeAmount() != null
                                && resultOrder.getCumulativeAmount().signum() > 0;
                        if (resultOrder.getStatus() == OrderStatus.CANCELED && !partiallyFilled) {
                            ((OkCoinService) arbitrageService.getRightMarketService()).resetWaitingArb("bitmex placeOrder was cancelled");
                        } else {
                            nextMarketState = MarketState.READY; // immediate ready after all Taker
                        }
                    }

                    tradeResponse.setOrderId(orderId);
                    tradeResponse.setErrorCode(null);

                    final String message = String.format("#%s %s %s amount=%s with quote=%s was placed.orderId=%s. pos=%s. "
                                    + "Exec_duration = %s (ms). %s. %s",
                            counterName,
                            placingType,
                            orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                            amount.toPlainString(),
                            thePrice,
                            orderId,
                            getPositionAsString(),
                            execDuration,
                            extraLog,
                            getxRateLimit().getString()
                    );
                    tradeLogger.info(message, contractTypeStr);
                    ordersLogger.info(message);

                    break;
                } catch (HttpStatusIOException e) {
                    final String httpBody = e.getHttpBody();
                    tradeResponse.setErrorCode(httpBody);

                    HttpStatusIOExceptionHandler handler = new HttpStatusIOExceptionHandler(e, "PlaceOrderError", attemptCount, counterName).invoke();

                    if (overloadByXRateLimit(true)) {
                        nextMarketState = MarketState.SYSTEM_OVERLOADED;
                        tradeResponse.setErrorCode(e.getMessage());
                        break;
                    }

                    final MoveResponse.MoveOrderStatus placeOrderStatus = handler.getMoveResponse().getMoveOrderStatus();
                    if (MoveResponse.MoveOrderStatus.EXCEPTION_SYSTEM_OVERLOADED == placeOrderStatus) {
                        if (attemptCount < maxAttempts) {
                            Thread.sleep(settings.getBitmexSysOverloadArgs().getBetweenAttemptsMsSafe());
                            if (placeOrderArgs.isShouldStopNtUsdRecovery()) {
                                nextMarketState = MarketState.READY;
                                final String warnMsg = "ShouldStopNtUsdRecovery";
                                logger.info(warnMsg);
                                tradeLogger.info(warnMsg);
                                ((OkCoinService) arbitrageService.getRightMarketService()).resetWaitingArb(warnMsg);
                                break;
                            }
                        } else {
                            setOverloaded(null, true);
                            nextMarketState = MarketState.SYSTEM_OVERLOADED;
                            tradeResponse.setErrorCode(e.getMessage());
                            break;
                        }
                    } else if (MoveResponse.MoveOrderStatus.EXCEPTION_502_BAD_GATEWAY == placeOrderStatus
                            || MoveOrderStatus.EXCEPTION_NONCE == placeOrderStatus) {
                        badGatewayCount++;
                        if (badGatewayCount < 3) {
                            Thread.sleep(200);
                            if (placeOrderArgs.isShouldStopNtUsdRecovery()) {
                                nextMarketState = MarketState.READY;
                                final String warnMsg = "ShouldStopNtUsdRecovery";
                                logger.info(warnMsg);
                                tradeLogger.info(warnMsg);
                                ((OkCoinService) arbitrageService.getRightMarketService()).resetWaitingArb(warnMsg);
                                break;
                            }
                        } else {
                            tradeResponse.setErrorCode(e.getMessage());
                            nextMarketState = MarketState.READY;
                            ((OkCoinService) arbitrageService.getRightMarketService()).resetWaitingArb("placeOrderStatus " + placeOrderStatus);
                            break;
                        }
                    } else {
                        ((OkCoinService) arbitrageService.getRightMarketService()).resetWaitingArb("placeOrderStatus " + placeOrderStatus);
                        break; // any unknown exception - no retry
                    }
                } catch (Exception e) {
                    final String message = e.getMessage() + " " + getxRateLimit().getString();
                    tradeResponse.setErrorCode(message);

                    final String logString = String.format("#%s/%s PlaceOrderError: %s", counterName, attemptCount, message);
                    logger.error(logString, e);
                    tradeLogger.error(logString, contractTypeStr);
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
                    nextMarketState = MarketState.READY;
                    ((OkCoinService) arbitrageService.getRightMarketService()).resetWaitingArb("placeOrder exception");
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
            final String warnMsg = String.format("maker error %s", e.toString());
            tradeLogger.info(warnMsg, contractTypeStr);
            tradeResponse.setErrorCode(e.getMessage());
            nextMarketState = MarketState.READY;
            ((OkCoinService) arbitrageService.getRightMarketService()).resetWaitingArb(warnMsg);
        }

        if (placeOrderArgs.isPreliqOrder()) {
            logger.info("restore marketState to READY after PRELIQ");
            setMarketState(MarketState.READY, counterName);
        } else {
            if (nextMarketState == MarketState.PLACING_ORDER || nextMarketState == MarketState.STARTING_VERT) {
                nextMarketState = MarketState.ARBITRAGE;
            }

            logger.info("restore marketState to " + nextMarketState);
            setMarketState(nextMarketState, counterName);
            if (nextMarketState == MarketState.SYSTEM_OVERLOADED) {
                final String warnMsg = "restore marketState to SYSTEM_OVERLOADED and reset WAITING_ARB";
                tradeLogger.info(warnMsg);
                ((OkCoinService) arbitrageService.getRightMarketService()).resetWaitingArb(warnMsg);
            } else if (nextMarketState == MarketState.READY) {
                setFree(tradeId);
            }
        }

        final Instant endPlacing = Instant.now();
        long wholeMs = endPlacing.toEpochMilli() - startPlacing.toEpochMilli();
        monPlacing.getWholePlacing().add(BigDecimal.valueOf(wholeMs));
        if (wholeMs > 5000) {
            logger.warn(placingType + " bitmex wholePlacingMs=" + wholeMs);
        }
        monPlacing.incCount();
        monitoringDataService.saveMon(monPlacing);
        metricsDictionary.putBitmexPlacingWhole(wholeMs);

        return tradeResponse;
    }

    private BigDecimal createFillOrKillPrice(StringBuilder fokExtraLogs, Order.OrderType orderType, OrderBook orderBook, ContractType contractType,
            BtmFokAutoArgs btmFokArgs, PlacingType placingType) {
        final String type = placingType == PlacingType.TAKER_FOK ? "FOK" : "IOC";

        final Settings s = settingsRepositoryService.getSettings();
        final BigDecimal bitmexFokTotalDiff = s.getBitmexFokTotalDiff() != null ? s.getBitmexFokTotalDiff() : BigDecimal.ZERO;
        final BigDecimal bitmexFokMaxDiff = s.getBitmexFokMaxDiff() != null ? s.getBitmexFokMaxDiff() : BigDecimal.ZERO;
        final boolean bitmexFokMaxDiffAuto = s.getBitmexFokMaxDiffAuto() != null && s.getBitmexFokMaxDiffAuto();
        final BigDecimal FOK_total_diff = setScaleDown(bitmexFokTotalDiff, contractType);
        final BigDecimal FOK_Max_diff = setScaleDown(bitmexFokMaxDiff, contractType);

        // if signal and
        if (bitmexFokMaxDiffAuto && btmFokArgs != null) {
            final BigDecimal delta = btmFokArgs.getDelta();
            final BigDecimal maxBorder = btmFokArgs.getMaxBorder();
            final BigDecimal price;
            final BigDecimal deltaMinusMaxBorder = setScaleDown(delta.subtract(maxBorder), contractType);
            if (orderType == Order.OrderType.BID || orderType == Order.OrderType.EXIT_ASK) { // bitmex buy
                final BigDecimal ask1 = Utils.getBestAsk(orderBook).getLimitPrice();
                //Для Buy:
                //if (FOK_Max_diff - (delta - max_border)) < FOK_total_diff
                //Price buy FOK == ask[1] - FOK_total_diff;
                if (FOK_Max_diff.subtract(deltaMinusMaxBorder).compareTo(FOK_total_diff) < 0) {
                    price = ask1.subtract(FOK_total_diff);
                    fokExtraLogs.append(String.format("use %s_total_diff: ", type));
                    fokExtraLogs.append(String.format("Price_buy_%s(%s) = ask[1](%s) - %s_total_diff(%s);"
                                    + " delta=%s, maxBorder=%s, %s_Max_diff=%s, allBorders=%s;",
                            type, price, ask1, type, FOK_total_diff,
                            delta, maxBorder, type, FOK_Max_diff, btmFokArgs.getAllBorders()));
                } else {
                    //Price buy FOK = ask[1] - FOK_Max_diff + (delta - max_border)
                    price = ask1.subtract(FOK_Max_diff).add(deltaMinusMaxBorder);
                    fokExtraLogs.append(String.format("Price_buy_%s(%s) = ask[1](%s) - %s_Max_diff(%s) + (delta(%s) - maxBorder(%s))(%s);"
                                    + " %s_total_diff=%s, allBorders=%s;",
                            type, price, ask1, type, FOK_Max_diff, delta, maxBorder, deltaMinusMaxBorder,
                            type, FOK_total_diff, btmFokArgs.getAllBorders()));
                }
            } else { // B_DELTA - bitmex sell
                final BigDecimal bid1 = Utils.getBestBid(orderBook).getLimitPrice();
                // Для Sell:
                //if ((delta - max_border) - FOK_Max_diff) > - FOK_total_diff)
                //Price sell FOK == bid[1] + FOK_total_diff.
                if (deltaMinusMaxBorder.subtract(FOK_Max_diff).compareTo(FOK_total_diff.negate()) > 0) {
                    price = bid1.add(FOK_total_diff);
                    fokExtraLogs.append(String.format("use %s_total_diff: ", type));
                    fokExtraLogs.append(String.format("Price_sell_%s(%s) = bid[1](%s) + %s_total_diff(%s);"
                                    + " delta=%s, maxBorder=%s, %s_Max_diff=%s, allBorders=%s;",
                            type, price, bid1, type, FOK_total_diff,
                            delta, maxBorder, type, FOK_Max_diff, btmFokArgs.getAllBorders()));
                } else {
                    //Price sell FOK = bid[1] + FOK_Max_diff - (delta - max_border)
                    price = bid1.add(FOK_Max_diff).subtract(deltaMinusMaxBorder);
                    fokExtraLogs.append(String.format("Price_sell_%s(%s) = bid[1](%s) + %s_Max_diff(%s) - (delta(%s) - maxBorder(%s))(%s);"
                                    + " %s_total_diff=%s, allBorders=%s;",
                            type, price, bid1, type, FOK_Max_diff, delta, maxBorder, deltaMinusMaxBorder,
                            type, FOK_total_diff, btmFokArgs.getAllBorders()));
                }
            }
            fokExtraLogs.append(Utils.getTenAskBid(orderBook, "", "Bitmex OrderBook", getArbType().s()));
            tryPrintZeroPriceWarning(price);
            return price;
        }
        // else

        final BigDecimal FOK_Max_diff_UP = setScaleUp(bitmexFokMaxDiff, contractType);
        BigDecimal price = BigDecimal.ZERO;
        if (orderType == Order.OrderType.BID || orderType == Order.OrderType.EXIT_ASK) {
            final BigDecimal ask1 = Utils.getBestAsk(orderBook).getLimitPrice();
            price = ask1.subtract(FOK_Max_diff_UP);
            fokExtraLogs.append(String.format("Price_buy_%s(%s) = ask1(%s) - %s_Max_diff(%s)", type, price, ask1, type, FOK_Max_diff_UP));
        } else if (orderType == Order.OrderType.ASK || orderType == Order.OrderType.EXIT_BID) {
            final BigDecimal bid1 = Utils.getBestBid(orderBook).getLimitPrice();
            price = bid1.add(FOK_Max_diff_UP);
            fokExtraLogs.append(String.format("Price_sell_%s(%s) = bid1(%s) - %s_Max_diff(%s)", type, price, bid1, type, FOK_Max_diff_UP));
        }
        tryPrintZeroPriceWarning(price);

        return price;
    }

    @Override
    public MoveResponse moveMakerOrder(MoveMakerOrderArg moveMakerOrderArg) {
        FplayOrder fplayOrder = moveMakerOrderArg.getFplayOrder();
        BigDecimal newPrice = moveMakerOrderArg.getNewPrice();
        Object[] reqMovingArgs = moveMakerOrderArg.getReqMovingArgs();

        final Long tradeId = fplayOrder.getTradeId();
        final LimitOrder limitOrder = (LimitOrder) fplayOrder.getOrder();
        MoveResponse moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "do nothing by default");

        if (fplayOrder.getPlacingType() != null && fplayOrder.getPlacingType() == PlacingType.TAKER) {
            return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "no moving. Order was placed as taker.");
        }

        if (limitOrder.getCurrencyPair() == null) {
            String msg = String.format("no moving. currencyPair is null. Can not determinate contractType! %s", limitOrder.toString());
            return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, msg);
        }

        final BitmexContractType cntType = BitmexContractType.parse(limitOrder.getCurrencyPair(),
                settingsRepositoryService.getSettings().getBitmexContractTypes());
        if (cntType == null) {
            String msg = String.format("no moving. Can not determinate contractType! %s", limitOrder.toString());
            return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, msg);
        }
        final CurrencyPair currencyPair = settingsRepositoryService.getCurrencyPair(cntType);
        final String contractTypeStr = currencyPair.toString();

        final String counterWithPortion = fplayOrder.getCounterWithPortion();
        Instant startReq = null;
        Instant endReq = null;
        BigDecimal bestMakerPrice = null;
        try {
            bestMakerPrice = newPrice.setScale(cntType.getScale(), BigDecimal.ROUND_HALF_UP);

            if (bestMakerPrice.signum() == 0 || bestMakerPrice.compareTo(limitOrder.getLimitPrice()) == 0) {
                return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "no moving. newPrice==oldPrice");
            } else {

                startReq = Instant.now();
                throwTestingException();
                final LimitOrder movedLimitOrder = ((BitmexTradeService) exchange.getTradeService()).moveLimitOrder(limitOrder, bestMakerPrice);
                endReq = Instant.now();
                metricsDictionary.putBitmexUpdateOrder(Duration.between(startReq, endReq));
                tradeLogger.info("moveOrder " + getxRateLimit().getString());

                if (movedLimitOrder == null) {
                    logger.info("Moving response is null");
                    tradeLogger.info("Moving response is null", contractTypeStr);
                } else {

                    FplayOrder updated = FplayOrderUtils.updateFplayOrder(fplayOrder, movedLimitOrder);
                    orderRepositoryService.updateSync(updated);

                    boolean showDiff = false;
                    if (prevCumulativeAmount != null && movedLimitOrder.getCumulativeAmount() != null
                            && movedLimitOrder.getCumulativeAmount().compareTo(prevCumulativeAmount) > 0) {
                        showDiff = true;
                    }
                    prevCumulativeAmount = movedLimitOrder.getCumulativeAmount();

                    final LimitOrder updatedOrder = (LimitOrder) updated.getOrder();

                    String diffWithSignal = setQuotesForArbLogs(updated.getTradeId(), bestMakerPrice, showDiff);

                    final boolean isParticipateDoNotInitiate = updatedOrder.getStatus() == OrderStatus.CANCELED;
                    final String logString = String
                            .format("#%s Moved %s from %s to %s(real %s) status=%s, amount=%s, filled=%s, avgPrice=%s, id=%s, pos=%s.%s.%s.; OB: %s",
                                    counterWithPortion,
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
                                    diffWithSignal,
                                    isParticipateDoNotInitiate ? "CANCELED order had execInst ParticipateDoNotInitiate" : "",
                                    moveMakerOrderArg.getObBestFive());
                    logger.info(logString);
                    tradeLogger.info(logString, contractTypeStr);
                    ordersLogger.info(logString);
                    if (isParticipateDoNotInitiate) {
                        final Settings settings = settingsRepositoryService.getSettings();
                        final int betweenAttemptsMsSafe = settings.getBitmexSysOverloadArgs().getBetweenAttemptsMsSafe();
                        Thread.sleep(betweenAttemptsMsSafe);
                    }

                    if (updatedOrder.getStatus() == Order.OrderStatus.CANCELED) {
                        incCancelledInRow(contractTypeStr);
                        moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.ONLY_CANCEL, logString, null, null, updated);
                    } else {
                        cancelledInRow.set(0);
                        moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.MOVED, logString, updatedOrder, updated);
                    }

                } // end movedLimitOrder != null
            } // end bestMakerPrice is changed
        } catch (HttpStatusIOException e) {

            HttpStatusIOExceptionHandler handler = new HttpStatusIOExceptionHandler(
                    e,
                    String.format("MoveOrderError:ordId=%s(from %s to %s amount=%s)",
                            limitOrder.getId(),
                            limitOrder.getLimitPrice(),
                            bestMakerPrice.toPlainString(),
                            limitOrder.getTradableAmount()
                    ),
                    movingErrorsOverloaded.get(),
                    counterWithPortion
            ).invoke();
            moveResponse = handler.getMoveResponse();
            // double check  "Invalid ordStatus"
            if (moveResponse.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.ALREADY_CLOSED
                    || moveResponse.getMoveOrderStatus() == MoveOrderStatus.INVALID_AMEND) {
                final Optional<Order> orderInfo = getOrderInfo(limitOrder.getId(), tradeId + ":" + counterWithPortion, 1, "Moving:CheckInvOrdStatus:");
                if (orderInfo.isPresent()) {
                    final Order doubleChecked = orderInfo.get();
                    final FplayOrder updated = FplayOrderUtils.updateFplayOrder(fplayOrder, (LimitOrder) doubleChecked);
                    if (doubleChecked.getStatus() == Order.OrderStatus.CANCELED) {
                        incCancelledInRow(contractTypeStr);
                        final String logString = String.format(
                                "#%s Moved %s from %s to ... status=CANCELLED, amount=%s, filled=%s, avgPrice=%s, id=%s, pos=%s.",
                                counterWithPortion,
                                limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                                limitOrder.getLimitPrice(),
                                limitOrder.getTradableAmount(),
                                limitOrder.getCumulativeAmount(),
                                limitOrder.getAveragePrice(),
                                limitOrder.getId(),
                                getPositionAsString());
                        moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.ONLY_CANCEL, logString, null, null, updated);
                    } else if (doubleChecked.getStatus() == Order.OrderStatus.FILLED) { // just update the status
                        moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, moveResponse.getDescription(), null, null, updated);
                    } else if (moveResponse.getMoveOrderStatus() == MoveOrderStatus.INVALID_AMEND) {
                        // if invalid amend and no previous cases
                        // then we just updated the info
                        cancelledInRow.set(0);
                        moveResponse = new MoveResponse(MoveOrderStatus.MOVED, moveResponse.getDescription(), updated.getLimitOrder(), updated);
                        validateForDuplicatesOO();
                        orderRepositoryService.updateSync(updated);
                    } else {
                        moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, moveResponse.getDescription());
                    }
                } else {
                    moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, moveResponse.getDescription());
                }
            }


        } catch (Exception e) {

            final String message = e.getMessage() + " " + getxRateLimit().getString();
            final String logString = String
                    .format("#%s/%s MovingError id=%s: %s", counterWithPortion, movingErrorsOverloaded.get(), limitOrder.getId(), message);
            logger.error(logString, e);
            tradeLogger.error(logString, contractTypeStr);
            warningLogger.error(logString);

            // message.startsWith("Connection refused") - when we got banned for a week. Just skip it.
            // message.startsWith("Read timed out")
//            if (message.startsWith("Network is unreachable")
//                    || message.startsWith("connect timed out")) {
//                tradeLogger.error("{} MoveOrderError: {}", getCounterName(), message);
//                moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION_SYSTEM_OVERLOADED, message);
//            }

        } finally {
            if (reqMovingArgs != null && reqMovingArgs.length == 2 && reqMovingArgs[0] != null) {
                Instant lastEnd = Instant.now();
                Mon mon = monitoringDataService.fetchMon(getName(), "moveMakerOrder");
                if (reqMovingArgs[0] != null && startReq != null) {
                    Instant startMoving = (Instant) reqMovingArgs[0];
                    long beforeMs = startReq.toEpochMilli() - startMoving.toEpochMilli();
                    mon.getBefore().add(BigDecimal.valueOf(beforeMs));
//                    CounterAndTimer metrics = MetricFactory.getCounterAndTimer(getName(), "beforeMoveOrder");
//                    metrics.durationMs(beforeMs);
                    if (beforeMs > 5000) {
                        logger.warn("beforeMs=" + beforeMs);
                    }
                }
                if (reqMovingArgs[1] != null) {
                    Long waitingPrevMs = (Long) reqMovingArgs[1];
                    mon.getWaitingPrev().add(BigDecimal.valueOf(waitingPrevMs));
                }

                if (startReq != null && endReq != null) {
                    long waitingMarketMs = endReq.toEpochMilli() - startReq.toEpochMilli();
                    mon.getWaitingMarket().add(BigDecimal.valueOf(waitingMarketMs));
                    if (waitingMarketMs > 5000) {
                        logger.warn("waitingMarketMs=" + waitingMarketMs);
                    }
                }

                if (endReq != null) {
                    mon.getAfter().add(new BigDecimal(lastEnd.toEpochMilli() - endReq.toEpochMilli()));
                }
                mon.incCount();
                monitoringDataService.saveMon(mon);

//                CounterAndTimer moveOrderMetrics = MetricFactory.getCounterAndTimer(getName(), "moveOrder");
//                moveOrderMetrics.durationMs(waitingMarketMs);
            }

        }

        return moveResponse;
    }

    private void incCancelledInRow(String contractTypeStr) {
        int cancelledCount = cancelledInRow.incrementAndGet();
        if (cancelledCount == 5) {
            tradeLogger.info("CANCELED more 4 in a row", contractTypeStr);
        }
        if (cancelledCount % 20 == 0) {
            tradeLogger.info("CANCELED more 20 in a row. Do reconnect.", contractTypeStr);
            requestReconnect(true, true);
        }
        if (cancelledCount > 20) {
            final BitmexXRateLimit xRateLimit = exchange.getBitmexStateService().getxRateLimit();
            String msg = "CANCELED more 20 in a row. " + xRateLimit.getString();
            logger.info(msg);
            tradeLogger.info(msg);
        }
    }

    private void throwTestingException() throws HttpStatusIOException, InterruptedException {
        if (bitmexChangeOnSoService.isTestingSo()) {
            final String httpBody = "{\"error\": {"
                    + "  \"message\": \"The system is currently overloaded. Please try again later\","
                    + "  \"name\": \"Error\""
                    + "}}";
            final InvocationResult invocationResult = new InvocationResult(httpBody, 500);
            final HttpStatusIOException exception = new HttpStatusIOException("system overloaded", invocationResult);
            exception.setResponseHeaders(new HashMap<>());

            throw exception;
        }
    }

    private String setQuotesForArbLogs(Long tradeId, BigDecimal openPrice, boolean showDiff) {
        String diffWithSignal = "";
        if (openPrice != null) {
            persistenceService.getDealPricesRepositoryService().setBtmOpenPrice(tradeId, openPrice);

            if (showDiff) {
                diffWithSignal = arbitrageService.getDealPrices().getDiffB().str;
            }
        }

        return diffWithSignal;
    }

    @Override
    public TradeService getTradeService() {
        return exchange.getTradeService();
    }

    private Disposable startAccountInfoListener() {

        return ((BitmexStreamingAccountService) exchange.getStreamingAccountService())
                .getAccountInfoContractsObservable()
                .doOnError(throwable1 -> handleSubscriptionError(throwable1, "Account fetch error"))
                .doOnDispose(() -> logger.info("bitmex subscription doOnDispose"))
                .doOnTerminate(() -> logger.info("bitmex subscription doOnTerminate"))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribe(newInfo -> {
                    try {

                        mergeAccountSafe(newInfo);

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
            requestReconnect(false);
        } else {
            logger.error(errorMessage, throwable);
            requestReconnect(true);
        }
    }


    @Override
    protected AccountBalance mergeAccount(AccountInfoContracts newInfo, AccountBalance current) {
        return new AccountBalance(
                newInfo.getWallet() != null ? newInfo.getWallet() : current.getWallet(),
                newInfo.getAvailable() != null ? newInfo.getAvailable() : current.getAvailable(),
                newInfo.geteMark() != null ? newInfo.geteMark() : current.getEMark(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                newInfo.getMargin() != null ? newInfo.getMargin() : current.getMargin(),
                newInfo.getUpl() != null ? newInfo.getUpl() : current.getUpl(),
                newInfo.getRpl() != null ? newInfo.getRpl() : current.getRpl(),
                newInfo.getRiskRate() != null ? newInfo.getRiskRate() : current.getRiskRate()
        );
    }

    private Disposable startPositionListener() {

        return ((BitmexStreamingAccountService) exchange.getStreamingAccountService())
                .getPositionObservable(bitmexContractTypeEx.getSymbol())
                .observeOn(stateUpdater)
                .doOnError(throwable1 -> handleSubscriptionError(throwable1, "Position fetch error"))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribe(pUpdate -> {
                    try {

                        mergePosition(pUpdate);

                        getApplicationEventPublisher().publishEvent(new NtUsdCheckEvent());
                        stateRecalcInStateUpdaterThread();

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
        symbols.add(bitmexContractTypeEx.getSymbol());
        if (bitmexContractType.isQuanto()) {
            symbols.add(BitmexContractType.XBTUSD_Perpetual.getSymbol());
        }

        return ((BitmexStreamingMarketDataService) exchange.getStreamingMarketDataService())
                .getContractIndexObservable(symbols)
                .observeOn(indexSingleExecutor)
                .doOnError(throwable1 -> handleSubscriptionError(throwable1, "Index fetch error"))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribe(contIndUpdate -> {
                    try {
                        if (bitmexContractType.isQuanto()
                                && contIndUpdate.getSymbol().equals(BitmexContractType.XBTUSD_Perpetual.getSymbol())) {

                            boolean success = false;
                            while (!success) {
                                final ContractIndex current = this.btcContractIndex.get();
                                final BitmexContractIndex updated = mergeContractIndex(current, contIndUpdate);
                                success = this.btcContractIndex.compareAndSet(current, updated);
                            }
                            calcCM();

                        } else {

                            boolean success = false;
                            while (!success) {
                                final ContractIndex current = this.contractIndex.get();
                                final BitmexContractIndex updated = mergeContractIndex(current, contIndUpdate);
                                success = this.contractIndex.compareAndSet(current, updated);
                            }
                            this.ticker = new Builder().last(
                                    ((BitmexContractIndex) this.contractIndex.get()).getLastPrice()).timestamp(new Date()).build();
                            lastPriceDeviationService.updateAndCheckDeviationAsync();

                            if (bitmexContractType.isQuanto()) {
                                calcCM();
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
        if (this.contractIndex.get() instanceof BitmexContractIndex && this.btcContractIndex.get() instanceof BitmexContractIndex) {
            final BigDecimal bxbtIndex = btcContractIndex.get().getIndexPrice();
            BitmexContractIndex bci = (BitmexContractIndex) this.contractIndex.get();
            final BigDecimal ethUsdMark = bci.getMarkPrice();

            // OLD: ETH: CM = round(10000000 / (ETHUSD_mark BXBT);2);
            BigDecimal bm = bitmexContractType.getBm();
            if (bm == null) { // all XBT
                this.cm = null;
            }

            // NEW:
            // ETH / LTC / LINK / XRP / BCH
            // CM = round((Okex_SCV / BM) / (ETHUSD_mark * BXBT);2),
            // CM = round((Okex_SCV / BM) / (LTCUSD_mark * BXBT);2),
            // CM = round((Okex_SCV / BM) / (LINKUSDT_mark * BXBT);2),
            // CM = round((Okex_SCV / BM) / (XRPUSD_mark * BXBT);2),
            // CM = round((Okex_SCV / BM) / (BCHUSD_mark * BXBT);2),
            //BigDecimal firstArg = (Okex_SCV / BM)

//            Bitmex_SCV = Okex_SCV / CM,
//                    где Okex_SCV - Okex single contract value - номинальная стоимость одного контракта Okex в usd.

            MarketServicePreliq right = arbitrageService.getRightMarketService();
            if (right == null || right.getSCV() == null) {
                return;
            }
            BigDecimal okexSCV = right.getSCV();
            this.cm = okexSCV.divide(bm, 8, RoundingMode.HALF_UP)
                    .divide(bxbtIndex.multiply(ethUsdMark), 8, RoundingMode.HALF_UP);
        }
    }

    private BitmexContractIndex mergeContractIndex(ContractIndex current, BitmexContractIndex update) {
        // merge contractIndex
        final BigDecimal indexPrice = update.getIndexPrice() != null
                ? update.getIndexPrice()
                : current.getIndexPrice();
        final BigDecimal markPrice;
        BigDecimal lastPrice;
        final BigDecimal fundingRate;
        final BigDecimal indicativeFundingRate;
        final OffsetDateTime fundingTimestamp;
        if (current instanceof BitmexContractIndex) {
            BitmexContractIndex cur = (BitmexContractIndex) current;
            markPrice = update.getMarkPrice() != null ? update.getMarkPrice() : cur.getMarkPrice();
            lastPrice = update.getLastPrice() != null ? update.getLastPrice() : cur.getLastPrice();
            fundingRate = update.getFundingRate() != null ? update.getFundingRate() : cur.getFundingRate();
            fundingTimestamp = update.getSwapTime() != null ? update.getSwapTime() : cur.getSwapTime();
            indicativeFundingRate = update.getIndicativeFundingRate() != null ? update.getIndicativeFundingRate() : cur.getIndicativeFundingRate();
        } else {
            markPrice = update.getMarkPrice();
            lastPrice = update.getLastPrice();
            fundingRate = update.getFundingRate();
            fundingTimestamp = update.getSwapTime();
            indicativeFundingRate = update.getIndicativeFundingRate();
        }
        if (lastPrice == null && markPrice != null) {
            lastPrice = markPrice;
        }

        final Date timestamp = update.getTimestamp();

        final ContractType ct = getContractType();
        // BTC - 2 знак после запятой
        // ETH - 2 знака после запятой
        //LINK - 5 знаков после запятой
        //LTC - 3 знака после запятой
        //XRP - 5 знаков после запятой
        //BCH -3 знака после запятой
        int s = (ct.getName().startsWith("ETH"))
                ? ct.getScale()
                : ct.getScale() + 1;

        return new BitmexContractIndex(update.getSymbol(),
                indexPrice != null ? indexPrice.setScale(s, RoundingMode.HALF_UP) : null,
                markPrice != null ? markPrice.setScale(s, RoundingMode.HALF_UP) : null,
                lastPrice != null ? lastPrice.setScale(s, RoundingMode.HALF_UP) : null,
                timestamp,
                fundingRate != null ? fundingRate.setScale(ct.getScale() + 2, RoundingMode.HALF_UP) : null,
                fundingTimestamp,
                indicativeFundingRate);
    }

    @Override
    public String getPositionAsString() {
        final Pos position = getPos();
        return position != null ? position.getPositionLong().toPlainString() : "0";
    }

    @Override
    protected Completable recalcLiqInfo() {
        return Completable.fromAction(() -> {
            final Pos position = getPos();
            final AccountBalance account = getAccount();

            final BigDecimal equity = account.getEMark();
            final BigDecimal margin = account.getMargin();

            final BigDecimal bMrliq = persistenceService.getSettingsRepositoryService().getSettings().getDql().getLeftMrLiq();

            if (!(contractIndex.get() instanceof BitmexContractIndex)) {
                // bitmex contract index is not updated yet. Skip the re-calc.
                return;
            }

            final BigDecimal m = ((BitmexContractIndex) contractIndex.get()).getMarkPrice();
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
                        dqlString = String.format("L_DQL = m%s - L%s = %s", m, L, dql);
                    } else {
                        dqlString = "L_DQL = na";
                        dql = null;
//                        warningLogger.info(String.format("Warning.All should be > 0: m=%s, L=%s",
//                                m.toPlainString(), L.toPlainString()));
                    }
                } else if (position.getPositionLong().signum() < 0) {
                    if (m.signum() > 0 && L.signum() > 0) {
                        if (L.subtract(BigDecimal.valueOf(100000)).signum() < 0) {
                            dql = L.subtract(m);
                            dqlString = String.format("L_DQL = L%s - m%s = %s", L, m, dql);
                        } else {
                            dqlString = "L_DQL = na";
                        }
                    } else {
                        dqlString = "L_DQL = na";
                        dql = null;
//                        warningLogger.info(String.format("Warning.All should be > 0: m=%s, L=%s",
//                                m.toPlainString(), L.toPlainString()));
                    }
                } else {
                    dqlString = "L_DQL = na";
                }

                BigDecimal dmrl = null;
                String dmrlString;
                if (margin.signum() > 0) {
                    final BigDecimal bMr = equity.divide(margin, 4, BigDecimal.ROUND_HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, BigDecimal.ROUND_HALF_UP);
                    dmrl = bMr.subtract(bMrliq);
                    dmrlString = String.format("L_DMRL = %s - %s = %s%%", bMr, bMrliq, dmrl);
                } else {
                    dmrlString = "L_DMRL = na";
                }

                liqInfo.setDqlCurr(dql);
                liqInfo.setDmrlCurr(dmrl);
                liqInfo.setDqlString(dqlString);
                liqInfo.setDmrlString(dmrlString);

                final LiqParams liqParams = getPersistenceService().fetchLiqParams(getNameWithType());
                liqParams.updateDql(dql);
                liqParams.updateDmrl(dmrl);

                recalcLiqInfoExtra(liqParams);

                storeLiqParams(liqParams); // race condition with resetLiqInfo() => user just have to reset one more time.
                updateDqlState();
            }
        });
    }

    private void recalcLiqInfoExtra(LiqParams liqParams) {
        if (!bitmexContractType.isQuanto()) {
            return;
        }
        final AtomicReference<ContractIndex> contractIndex = this.btcContractIndex;
        if (!(contractIndex.get() instanceof BitmexContractIndex)) {
            // bitmex contract index is not updated yet. Skip the re-calc.
            return;
        }
        final BitmexContractIndex bitmexContractIndex = (BitmexContractIndex) contractIndex.get();

        final Pos position = this.posXBTUSD.get();
        final BigDecimal m = bitmexContractIndex.getMarkPrice();
        final BigDecimal L = position.getLiquidationPrice();

        if (m != null
                && L != null
                && position.getPositionLong() != null
                && position.getPositionShort() != null) {

            BigDecimal dql = null;

            String dqlString;
            if (position.getPositionLong().signum() > 0) {
                if (m.signum() > 0 && L.signum() > 0) {
                    dql = m.subtract(L);
                    dqlString = String.format("L_DQL_extra = m%s - L%s = %s", m, L, dql);
                } else {
                    dqlString = "L_DQL_extra = na";
                    dql = null;
                }
            } else if (position.getPositionLong().signum() < 0) {
                if (m.signum() > 0 && L.signum() > 0) {
                    if (L.subtract(BigDecimal.valueOf(100000)).signum() < 0) {
                        dql = L.subtract(m);
                        dqlString = String.format("L_DQL_extra = L%s - m%s = %s", L, m, dql);
                    } else {
                        dqlString = "L_DQL_extra = na";
                    }
                } else {
                    dqlString = "L_DQL_extra = na";
                    dql = null;
                }
            } else {
                dqlString = "L_DQL_extra = na";
            }

            liqParams.updateDqlExtra(dql);
            liqInfo.setDqlStringExtra(dqlString);
        }
    }

    @Override
    public DqlState updateDqlState() {
        final Dql dql = persistenceService.getSettingsRepositoryService().getSettings().getDql();
        final BigDecimal bDQLOpenMin = dql.getLeftDqlOpenMin();
        final BigDecimal bDQLCloseMin = dql.getLeftDqlCloseMin();
        final BigDecimal leftDqlKillPos = dql.getLeftDqlKillPos();
        final BigDecimal dqlCurr = getLiqInfo().getDqlCurr();
        final BigDecimal dqlLevel = dql.getDqlLevel();
        return arbitrageService.getDqlStateService().updateLeftDqlState("DQL",
                leftDqlKillPos, bDQLOpenMin, bDQLCloseMin, dqlCurr, dqlLevel);
    }

    @Override
    public boolean checkLiquidationEdge(Order.OrderType orderType) {
        final Dql dql = persistenceService.getSettingsRepositoryService().getSettings().getDql();
        final BigDecimal bDQLOpenMin = dql.getLeftDqlOpenMin();
        final BigDecimal bDQLCloseMin = dql.getLeftDqlCloseMin();
        final BigDecimal dqlCurr = getLiqInfo().getDqlCurr();
        final Pos position = getPos();

        boolean isOk;
        if (dqlCurr == null) {
            isOk = true;
        } else {
            if (orderType.equals(Order.OrderType.BID)) { //LONG
                if (position.getPositionLong().signum() > 0) {
                    isOk = dqlCurr.compareTo(bDQLOpenMin) >= 0;
                } else {
                    isOk = true;
                }
            } else if ((orderType.equals(Order.OrderType.ASK))) {
                if (position.getPositionLong().signum() < 0) {
                    isOk = dqlCurr.compareTo(bDQLOpenMin) >= 0;
                } else {
                    isOk = true;
                }
            } else {
                throw new IllegalArgumentException("Wrong order type");
            }
        }

        final BigDecimal leftDqlKillPos = dql.getLeftDqlKillPos();
        final BigDecimal dqlLevel = dql.getDqlLevel();
        arbitrageService.getDqlStateService().updateLeftDqlState("DQL", leftDqlKillPos, bDQLOpenMin, bDQLCloseMin, dqlCurr, dqlLevel);

        return isOk;
    }

    private final ScheduledExecutorService preliqScheduler = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("bitmex-preliq-thread-%d").build());

    public void initPreliqScheduler() {
        preliqScheduler.scheduleWithFixedDelay(() -> {
            try {
                preliqService.checkForPreliq(false);
            } catch (Exception e) {
                logger.error("Error on checkForDecreasePosition", e);
            }
        }, 30, 1, TimeUnit.SECONDS);
    }

    public BitmexSwapService getBitmexSwapService() {
        return bitmexSwapService;
    }

    public BitmexXRateLimit getxRateLimit() {
        return exchange.getBitmexStateService().getxRateLimit();
    }

    @Override
    protected void postOverload() {
//        exchange.getBitmexStateService().resetXrateLimit();
    }

    /**
     * Workaround! <br> Bitmex sends wrong avgPrice. Fetch detailed history for each order and calc avgPrice.
     *
     * @param dealPrices the object to be updated.
     */
    @Override
    public void updateAvgPrice(DealPrices dealPrices, boolean onlyOneAttempt) {
        final String counterName = dealPrices.getCounterName();
        final String contractTypeStr = getCurrencyPair().toString();
        final FactPrice avgPrice = dealPrices.getBPriceFact();
        if (avgPrice.isZeroOrder()) {
            String msg = String.format("#%s WARNING: no updateAvgPrice for btm orders tradeId=%s. Zero order", counterName, dealPrices.getTradeId());
            tradeLogger.info(msg, contractTypeStr);
            logger.warn(msg);
            return;
        }

        final Map<String, AvgPriceItem> itemMap = getPersistenceService().getDealPricesRepositoryService().getPItems(dealPrices.getTradeId(), getMarketId());

        if (getArbitrageService().isArbStateStopped() || getArbitrageService().isArbForbidden()) {
            tradeLogger.info(String.format("#%s WARNING: no updateAvgPrice. ArbState.STOPPED", counterName),
                    contractTypeStr);
            return;
        }
        avgPrice.getPItems().clear(); // TODO replace one by one.
        for (String orderId : itemMap.keySet()) {
            AvgPriceItem theItem = itemMap.get(orderId);
            if (theItem == null || theItem.getAmount() == null || theItem.getOrdStatus() == null
                    || (theItem.getAmount().signum() == 0 && theItem.getOrdStatus().equals("CANCELED"))) {
                String msg = String.format("#%s WARNING: no updateAvgPrice for orderId=%s. theItem=%s", counterName, orderId, theItem);
                tradeLogger.info(msg, contractTypeStr);
                logger.warn(msg);
                continue;
            }
            final String logMsg = String.format("#%s AvgPrice update of orderId=%s.", counterName, orderId);
            int MAX_ATTEMPTS = 1;
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                try {
                    if (getArbitrageService().isArbStateStopped() || getArbitrageService().isArbForbidden()) {
                        tradeLogger.info(String.format("#%s WARNING: no updateAvgPrice. ArbState.STOPPED", counterName), contractTypeStr);
                        return;
                    }
                    final Collection<Execution> orderParts = ((BitmexTradeService) getTradeService()).getOrderParts(orderId);

                    if (orderParts.size() == 0) {
                        // Try to Update a whole order info.
                        Collection<Order> orders = getTradeService().getOrder(orderId);
                        if (orders.size() == 0) {
                            tradeLogger.info(String.format("%s WARNING: no order parts. Can not update order.", logMsg),
                                    contractTypeStr);
                        } else {
                            Order order = orders.iterator().next();
                            if (order.getStatus() != null &&
                                    (order.getStatus() == OrderStatus.CANCELED || order.getStatus() == OrderStatus.REJECTED)) {
                                tradeLogger.info(String.format("%s WARNING: no order parts. Order is %s: %s", logMsg,
                                                order.getStatus(), Arrays.toString(orders.toArray())),
                                        contractTypeStr);
                                break;
                            } else {
                                tradeLogger.info(String.format("%s WARNING: no order parts. UpdatedOrderInfo:%s", logMsg, Arrays.toString(orders.toArray())),
                                        contractTypeStr);
                                avgPrice.addPriceItem(orderId, order.getCumulativeAmount(), order.getAveragePrice(), order.getStatus());
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
                            avgPrice.addPriceItem(orderId, amountSum, price, ordStatus);
                            tradeLogger.info(String.format("%s p=%s, a=%s. ordStatus=%s", logMsg, price, amountSum, ordStatus),
                                    contractTypeStr);
                            break;
                        } else {
                            tradeLogger.info(String.format("%s price=0. Use 'order history' price p=%s, a=%s, ordStatus=%s. %s",
                                            logMsg,
                                            theItem.getPrice(),
                                            theItem.getAmount(),
                                            ordStatus,
                                            Arrays.toString(orderParts.toArray())
                                    ),
                                    contractTypeStr);
                        }
                    }

                } catch (HttpStatusIOException e) {

                    final BitmexXRateLimit xRateLimit = exchange.getBitmexStateService().getxRateLimit();
                    final String rateLimitStr = " " + xRateLimit.getString();

                    logger.info(String.format("%s %s updateAvgPriceError.", logMsg, rateLimitStr), e);
                    tradeLogger.info(String.format("%s %s updateAvgPriceError %s", logMsg, rateLimitStr, e.getMessage()),
                            contractTypeStr);
                    warningLogger.info(String.format("%s %s updateAvgPriceError %s", logMsg, rateLimitStr, e.getMessage()));

                    overloadByXRateLimit();

                    if (e.getMessage().contains("HTTP status code was not OK: 403")) {// banned, no repeats
                        break;
                    }

                } catch (Exception e) {
                    logger.info(String.format("%s updateAvgPriceError.", logMsg), e);
                    tradeLogger.info(String.format("%s updateAvgPriceError %s", logMsg, e.getMessage()),
                            contractTypeStr);
                    warningLogger.info(String.format("%s updateAvgPriceError %s", logMsg, e.getMessage()));
                }

                if (onlyOneAttempt) {
                    break;
                }

                sleepByXrateLimit(logMsg);
            }
        }
        tradeLogger.info(String.format("#%s AvgPrice by %s orders(%s) is %s", counterName,
                        itemMap.size(),
                        Arrays.toString(itemMap.keySet().toArray()),
                        avgPrice.getAvg()),
                contractTypeStr);

        tradeLogger.info(String.format("#%s %s", counterName, arbitrageService.getDealPrices().getDiffB().str),
                contractTypeStr);

        getPersistenceService().getDealPricesRepositoryService().updateBtmFactPrice(dealPrices.getTradeId(), avgPrice);
    }

    @Override
    public void sleepByXrateLimit(String logStr) {
        final int sleepSec = settingsRepositoryService.getSettings().getBtmAvgPriceUpdateSettings().getUpdateDelayMs();
        final BitmexXRateLimit xRateLimit = exchange.getBitmexStateService().getxRateLimit();
//        int sleepSec = (xRateLimit.getxRateLimit() > 20) ? 1 : 5; // xRateLimit: 60 attempts in 1 min
        final String rateLimitStr = String.format("%s. sleep=%s sec. %s ", logStr, sleepSec, xRateLimit.getString());
        logger.info(rateLimitStr);
        tradeLogger.info(rateLimitStr);
        warningLogger.info(rateLimitStr);
        try {
            Thread.sleep(sleepSec);
        } catch (InterruptedException e) {
            logger.info(String.format("%s Sleep Error.", rateLimitStr), e);
        }
    }

    private boolean overloadByXRateLimit() {
        return overloadByXRateLimit(false);
    }

    private boolean overloadByXRateLimit(boolean withResetWaitingArb) {
        final BitmexXRateLimit xRateLimit = exchange.getBitmexStateService().getxRateLimit();
        boolean isExceeded = xRateLimit.getxRateLimit() <= 0;
        boolean isExceeded1s = xRateLimit.getxRateLimit1s() <= 0;
        if (isExceeded || isExceeded1s) {
            final String msg = "overloadByXRateLimit " + xRateLimit.getString() + ". Stop!";
            logger.info(msg);
            tradeLogger.info(msg);
            warningLogger.info(msg);
            slackNotifications.sendNotify(NotifyType.BITMEX_X_RATE_LIMIT, NAME + msg);
            setOverloaded(null, withResetWaitingArb);
        }
        return isExceeded;
    }

    private class HttpStatusIOExceptionHandler {

        private MoveResponse moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "default");

        private HttpStatusIOException e;
        private String operationName;
        private int attemptCount;
        private String counterName;

        public HttpStatusIOExceptionHandler(HttpStatusIOException e, String operationName, int attemptCount, String counterName) {
            this.e = e;
            this.operationName = operationName;
            this.attemptCount = attemptCount;
            this.counterName = counterName;
        }

        /**
         * ALREADY_CLOSED or EXCEPTION or EXCEPTION_SYSTEM_OVERLOADED
         */
        public MoveResponse getMoveResponse() {
            return moveResponse;
        }

        public HttpStatusIOExceptionHandler invoke() {
            try {

                final BitmexXRateLimit xRateLimit = exchange.getBitmexStateService().getxRateLimit();
                final String rateLimitStr = String.format(" %s ", xRateLimit.getString());

                final String marketResponseMessage;
                final String httpBody = e.getHttpBody();
                final String BAD_GATEWAY = "502 Bad Gateway";
                if (httpBody.contains(BAD_GATEWAY)) {
                    marketResponseMessage = BAD_GATEWAY;
                } else {
                    marketResponseMessage = new ObjectMapper().readValue(httpBody, Error.class).getError().getMessage();
                }

                String fullMessage = String.format("#%s/%s %s: %s %s", counterName, attemptCount, operationName, httpBody, rateLimitStr);
                String shortMessage = String.format("#%s/%s %s: %s %s", counterName, attemptCount, operationName, marketResponseMessage, rateLimitStr);

                tradeLogger.error(shortMessage);

                if (marketResponseMessage.startsWith("The system is currently overloaded. Please try again later")) {
                    moveResponse = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION_SYSTEM_OVERLOADED, marketResponseMessage);
                    logger.error(fullMessage);
                } else if (marketResponseMessage.startsWith("Invalid amend: orderQty, leavesQty, price, stopPx unchanged")) {
                    moveResponse = new MoveResponse(MoveOrderStatus.INVALID_AMEND, marketResponseMessage);
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
                String cause = "";
                if (e1.getCause() != null) {
                    cause = " cause: " + e1.getCause().getMessage();
                }
                tradeLogger.error("Error on handling HttpStatusIOException " + e1.getMessage() + cause);
                logger.error("Error on handling HttpStatusIOException", e1);
            }

            return this;
        }
    }

    public OrderResultTiny cancelOrderSync(String orderId, String logInfoId) {
        final String counterForLogs = getCounterName();

        int attemptCount = 0;
        while (attemptCount < MAX_ATTEMPTS_CANCEL_BITMEX && getMarketState() != MarketState.SYSTEM_OVERLOADED) {
            attemptCount++;
            try {
                if (attemptCount > 1) {
                    Thread.sleep(1000);
                }
                BitmexTradeService tradeService = (BitmexTradeService) getExchange().getTradeService();
                boolean res = tradeService.cancelOrder(orderId);

                final String warnMsg = String.format("#%s/%s %s cancelled id=%s",
                        counterForLogs, attemptCount,
                        logInfoId,
                        orderId);
                getTradeLogger().info(warnMsg);
                ((OkCoinService) arbitrageService.getRightMarketService()).resetWaitingArb(warnMsg);

                return new OrderResultTiny(res, orderId);

            } catch (HttpStatusIOException e) {
                overloadByXRateLimit();

                logger.error("#{}/{} error cancel order id={}", counterForLogs, attemptCount, orderId, e);
                getTradeLogger().error(String.format("#%s/%s error cancel order id=%s: %s", counterForLogs, attemptCount, orderId, e.toString()));
            } catch (Exception e) {
                logger.error("#{}/{} error cancel order id={}", counterForLogs, attemptCount, orderId, e);
                getTradeLogger().error(String.format("#%s/%s error cancel order id=%s: %s", counterForLogs, attemptCount, orderId, e.toString()));
            }
        }
        return new OrderResultTiny(false, "");
    }

    @Override
    public List<LimitOrder> cancelAllOrders(FplayOrder stub, String logInfoId, boolean beforePlacing, boolean withResetWaitingArb) {
        final FplayOrder currStub = stub != null ? stub : getCurrStub();
        final String counterForLogs = currStub.getCounterWithPortion();
        String contractTypeStr = "";

        int attemptCount = 0;
        while (attemptCount < MAX_ATTEMPTS_CANCEL_BITMEX && getMarketState() != MarketState.SYSTEM_OVERLOADED) {
            attemptCount++;
            try {
                if (attemptCount > 1) {
                    Thread.sleep(1000);
                }
                BitmexTradeService tradeService = (BitmexTradeService) getExchange().getTradeService();
                List<LimitOrder> limitOrders = tradeService.cancelAllOrders();
                contractTypeStr = limitOrders.stream()
                        .map(Order::getCurrencyPair)
                        .filter(Objects::nonNull)
                        .map(CurrencyPair::toString)
                        .findFirst()
                        .orElse("");

                final Optional<String> cancelledOrdersStr = limitOrders.stream().map(LimitOrder::toString).reduce((acc, item) -> acc + "; " + item);
                if (cancelledOrdersStr.isPresent()) {
                    getTradeLogger().info(String.format("#%s/%s %s cancelled. %s", counterForLogs, attemptCount, logInfoId, cancelledOrdersStr),
                            contractTypeStr);
                }

                updateFplayOrdersToCurrStab(limitOrders, currStub);
                if (beforePlacing) {
                    setMarketState(MarketState.PLACING_ORDER);
                } else {
                    if (withResetWaitingArb) {
                        ((OkCoinService) arbitrageService.getRightMarketService()).resetWaitingArb("cancellAllOrders withResetWaitingArb");
                    }
                    addCheckOoToFree();
                }

                return limitOrders;

            } catch (HttpStatusIOException e) {
                overloadByXRateLimit();

                logger.error("#{}/{} error cancel orders", counterForLogs, attemptCount, e);
                getTradeLogger().error(String.format("#%s/%s error cancel orders: %s", counterForLogs, attemptCount, e.toString()), contractTypeStr);
            } catch (Exception e) {
                logger.error("#{}/{} error cancel orders", counterForLogs, attemptCount, e);
                getTradeLogger().error(String.format("#%s/%s error cancel orders: %s", counterForLogs, attemptCount, e.toString()), contractTypeStr);
            }
        }
        return new ArrayList<>();
    }


    @Override
    public TradeResponse closeAllPos() {
        return closeAllPos(bitmexContractTypeEx);
    }

    private TradeResponse closeAllPos(BitmexContractTypeEx btmContType) {
        final String symbol = btmContType.getSymbol();
        final TradeResponse tradeResponse = new TradeResponse();
        tradeResponse.setErrorCode("");

        final Instant start = Instant.now();
        try {
            final BitmexTradeService tradeService = (BitmexTradeService) getExchange().getTradeService();

            LimitOrder order = tradeService.closeAllPos(symbol);

            final Instant end = Instant.now();
            if (order.getTradableAmount() == null) { // if cancelled order
                order = new LimitOrder(null,
                        BigDecimal.ZERO,
                        order.getCurrencyPair(),
                        order.getId(),
                        order.getTimestamp(),
                        null,
                        order.getAveragePrice(),
                        order.getCumulativeAmount(),
                        order.getStatus());
            }

            final FplayOrder stub = new FplayOrder(this.getMarketId(), null, "closeAllPos");
            final FplayOrder closeOrder = new FplayOrder(stub.getMarketId(), stub.getTradeId(), stub.getCounterName(),
                    order, null, PlacingType.TAKER, null);
            addOpenOrder(closeOrder);
            tradeLogger.info(String.format("#closeAllPos id=%s. %s", order.getId(), order));

            tradeResponse.setOrderId(order.getId());
            final String timeStr = String.format("(%d ms)", Duration.between(start, end).toMillis());
            tradeResponse.setErrorCode(timeStr);

            // one attempt to close all limit orders
            List<LimitOrder> limitOrders = tradeService.cancelAllOrders();
            updateFplayOrdersToCurrStab(limitOrders, stub);
            if (limitOrders.size() > 0) {
                final String cancelledOrdersStr = limitOrders.stream()
                        .map(LimitOrder::toString)
                        .reduce((acc, item) -> acc + "; " + item)
                        .orElse("");
                tradeLogger.info(String.format("#closeAllPos:cancelled %s order(s): %s", limitOrders.size(), cancelledOrdersStr));
            }

            addCheckOoToFree();

        } catch (Exception e) {
            // NOTE: there should not be overloaded(403 response)
            // instead of that there may be 'long handing'.

            final Instant end = Instant.now();
            final String timeStr = String.format("; (%d ms)", Duration.between(start, end).toMillis());
            final String message = e.getMessage() + timeStr;
            tradeResponse.setErrorCode(tradeResponse.getErrorCode() + message);

            final String logString = String.format("#closeAllPos %s: %s", getName(), message);
            logger.error(logString, e);
            final CurrencyPair currencyPair = settingsRepositoryService.getCurrencyPair(btmContType);
            tradeLogger.error(logString, currencyPair.toString());
            warningLogger.error(logString);
        }
        return tradeResponse;
    }

    @Override
    protected MetricsDictionary getMetricsDictionary() {
        return metricsDictionary;
    }

}
