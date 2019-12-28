package com.bitplay.market.okcoin;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.AvgPriceItem;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.events.NtUsdCheckEvent;
import com.bitplay.arbitrage.events.ObChangeEvent;
import com.bitplay.arbitrage.events.SigEvent;
import com.bitplay.arbitrage.events.SigType;
import com.bitplay.arbitrage.posdiff.PosDiffService;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.BalanceService;
import com.bitplay.market.DefaultLogService;
import com.bitplay.market.LimitsService;
import com.bitplay.market.LogService;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.MarketStaticData;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.Affordable;
import com.bitplay.market.model.BeforeSignalMetrics;
import com.bitplay.market.model.LiqInfo;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.metrics.MetricsDictionary;
import com.bitplay.model.AccountBalance;
import com.bitplay.model.EstimatedPrice;
import com.bitplay.model.Leverage;
import com.bitplay.model.Pos;
import com.bitplay.model.SwapSettlement;
import com.bitplay.model.ex.OrderResultTiny;
import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.FplayExchangeOkex;
import com.bitplay.okex.v3.client.ApiCredentials;
import com.bitplay.okex.v3.enums.FuturesOrderTypeEnum;
import com.bitplay.okex.v3.exception.ApiException;
import com.bitplay.okex.v3.service.futures.adapter.OkexOrderConverter;
import com.bitplay.persistance.CumPersistenceService;
import com.bitplay.persistance.DealPricesRepositoryService;
import com.bitplay.persistance.LastPriceDeviationService;
import com.bitplay.persistance.MonitoringDataService;
import com.bitplay.persistance.OrderRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.GuiLiqParams;
import com.bitplay.persistance.domain.LiqParams;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.FplayOrderUtils;
import com.bitplay.persistance.domain.fluent.TradeMStatus;
import com.bitplay.persistance.domain.fluent.dealprices.DealPrices;
import com.bitplay.persistance.domain.fluent.dealprices.FactPrice;
import com.bitplay.persistance.domain.mon.Mon;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.OkexContractType;
import com.bitplay.persistance.domain.settings.OkexPostOnlyArgs;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.TradingMode;
import com.bitplay.utils.Utils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import info.bitrich.xchangestream.core.dto.PositionStream;
import info.bitrich.xchangestream.okexv3.OkExAdapters;
import info.bitrich.xchangestream.okexv3.OkExStreamingExchange;
import info.bitrich.xchangestream.okexv3.OkExStreamingMarketDataService;
import info.bitrich.xchangestream.okexv3.OkExStreamingPrivateDataService;
import info.bitrich.xchangestream.okexv3.dto.InstrumentDto;
import info.bitrich.xchangestream.okexv3.dto.marketdata.OkCoinDepth;
import info.bitrich.xchangestream.okexv3.dto.marketdata.OkcoinPriceRange;
import info.bitrich.xchangestream.service.ws.statistic.PingStatEvent;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.marketdata.ContractIndex;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.okcoin.FuturesContract;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import si.mazi.rescu.HttpStatusIOException;

import javax.annotation.PreDestroy;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.bitplay.market.model.LiqInfo.DQL_WRONG;

//import info.bitrich.xchangestream.okex.OkExStreamingExchange;
//import info.bitrich.xchangestream.okex.OkExStreamingMarketDataService;

/**
 * Created by Sergey Shurmin on 3/21/17.
 */
@Service("okcoin")
@RequiredArgsConstructor
public class OkCoinService extends MarketServicePreliq {

    private static final Logger logger = LoggerFactory.getLogger(OkCoinService.class);
    private static final Logger debugLog = LoggerFactory.getLogger(OkCoinService.class);

    private static final Logger ordersLogger = LoggerFactory.getLogger("OKCOIN_ORDERS_LOG");

    private static final MarketStaticData MARKET_STATIC_DATA = MarketStaticData.OKEX;
    public static final String NAME = MARKET_STATIC_DATA.getName();
    ArbitrageService arbitrageService;

    private volatile AtomicReference<PlaceOrderArgs> placeOrderArgsRef = new AtomicReference<>();

    private static final int MAX_SEC_CHECK_AFTER_TAKER = 5;
    private static final int MAX_ATTEMPTS_STATUS = 50;
    private static final int MAX_ATTEMPTS_FOR_MOVING = 2;
    private static final int MAX_MOVING_TIMEOUT_SEC = 2;
    private static final int MAX_MOVING_OVERLOAD_ATTEMPTS_TIMEOUT_SEC = 60;
    // Moving timeout
    private volatile ScheduledFuture<?> scheduledMovingErrorsReset;
    private volatile AtomicInteger movingErrorsOverloaded = new AtomicInteger(0);

    private volatile String ifDisconnetedString = "";
    private volatile boolean shutdown = false;
    private Disposable onDisconnectHook;

    private volatile BigDecimal markPrice = BigDecimal.ZERO;
    private volatile BigDecimal forecastPrice = BigDecimal.ZERO;
    private volatile OkcoinPriceRange priceRange;
    private volatile SwapSettlement swapSettlement = new SwapSettlement(
            LocalDateTime.MIN, BigDecimal.ZERO, BigDecimal.ZERO, LocalDateTime.MIN
    );

    public BigDecimal getMarkPrice() {
        return markPrice;
    }

    public BigDecimal getForecastPrice() {
        return forecastPrice;
    }

    public OkcoinPriceRange getPriceRange() {
        return priceRange;
    }

    private final SlackNotifications slackNotifications;
    private final LastPriceDeviationService lastPriceDeviationService;
    private final com.bitplay.persistance.TradeService fplayTradeService;
    private final OkcoinBalanceService okcoinBalanceService;
    private final PersistenceService persistenceService;
    private final SettingsRepositoryService settingsRepositoryService;
    private final OrderRepositoryService orderRepositoryService;

    // TODO remove circular dependencies
    @Autowired
    private OkexLimitsService okexLimitsService;
    @Autowired
    private OOHangedCheckerService ooHangedCheckerService;
    @Autowired
    private OkexTradeLogger tradeLogger;
    private final DefaultLogService defaultLogger;
    private final MonitoringDataService monitoringDataService;
    @Autowired
    private MetricsDictionary metricsDictionary;
    private final CumPersistenceService cumPersistenceService;
    private final OkexSettlementService okexSettlementService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final DealPricesRepositoryService dealPricesRepositoryService;

    @Override
    protected ApplicationEventPublisher getApplicationEventPublisher() {
        return applicationEventPublisher;
    }

    private OkExStreamingExchange exchange; // for streaming only
    private ApiCredentials apiCredentials;
    private FplayExchangeOkex fplayOkexExchange;
    private Disposable orderBookSubscription;
    private Disposable userPositionSub;
    private Disposable userAccountSub;
    private Disposable userOrderSub;
    private Disposable pingStatSub;
    private Disposable markPriceSubscription;
    private Disposable tickerSubscription;
    private Disposable priceRangeSub;
    private Disposable tickerEthSubscription;
    private Disposable indexPriceSub;
    private Observable<OkCoinDepth> orderBookObservable;
    private OkexContractType okexContractType;
    private OkexContractType okexContractTypeBTCUSD = OkexContractType.BTC_ThisWeek;
    private volatile Map<String, OkexContractType> instrIdToContractType = new HashMap<>();
    private volatile List<InstrumentDto> instrDtos = new ArrayList<>();
    private volatile BigDecimal leverage;
    private volatile boolean started = false;

    @Override
    public PosDiffService getPosDiffService() {
        return arbitrageService.getPosDiffService();
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
    public boolean isMarketStopped() {
        return getArbitrageService().isArbStateStopped() || getArbitrageService().isArbForbidden();
    }

    public OkexLimitsService getOkexLimitsService() {
        return okexLimitsService;
    }

    @Override
    public MarketStaticData getMarketStaticData() {
        return MARKET_STATIC_DATA;
    }

    @Override
    public String getFuturesContractName() {
        return okexContractType != null ? okexContractType.toString() : "";
    }

    @Override
    protected Exchange getExchange() {
        return exchange;
    }

    @Override
    public LimitsService getLimitsService() {
        return okexLimitsService;
    }

    @Override
    public SlackNotifications getSlackNotifications() {
        return slackNotifications;
    }

    @Override
    public void initializeMarket(String key, String secret, ContractType contractType, Object... exArgs) {
        okexContractType = (OkexContractType) contractType;
        logger.info("Starting okex with " + okexContractType);
        tradeLogger.info("Starting okex with " + okexContractType);
        if (okexContractType.isEth()) {
            this.usdInContract = 10;
        } else {
            this.usdInContract = 100;
        }
        // init instrumentIds
        final InstrumentDto mainInstr = new InstrumentDto(okexContractType.getCurrencyPair(), okexContractType.getFuturesContract());
        instrDtos.add(mainInstr);
        instrIdToContractType.put(mainInstr.getInstrumentId(), okexContractType);
        if (okexContractType != okexContractTypeBTCUSD) {
            final InstrumentDto extraInstr = new InstrumentDto(okexContractTypeBTCUSD.getCurrencyPair(), okexContractTypeBTCUSD.getFuturesContract());
            instrDtos.add(extraInstr);
            instrIdToContractType.put(extraInstr.getInstrumentId(), okexContractTypeBTCUSD);
        }

        apiCredentials = getApiCredentials(exArgs);
        initExchangeV3(apiCredentials);
        exchange = initExchange(key, secret, exArgs);

        initWebSocketAndAllSubscribers();

//        final Instrument instrument = bitplayOkexEchange.getMarketAPIService().getInstruments().get(0);
//        logger.info("BITPLAY_OKEX_EXCHANGE: first instrument: " + instrument);
        started = true;
    }

    @Scheduled(initialDelay = 120, fixedDelay = 60)
    public void checkExchange() {
        if (fplayOkexExchange == null || fplayOkexExchange.getPrivateApi() == null || fplayOkexExchange.getPrivateApi().notCreated()) {
            final String msg = "OkexExchange is not fully created. Re-create it.";
            logger.warn(msg);
            warningLogger.warn(msg);
            initExchangeV3(apiCredentials);
        }
    }

    private void initExchangeV3(ApiCredentials cred) {
        ApiConfiguration config = new ApiConfiguration();
        config.setEndpoint(ApiConfiguration.API_BASE_URL);
        config.setApiCredentials(cred);
        config.setPrint(true);
        config.setRetryOnConnectionFailure(true);
        config.setConnectTimeout(15);
        config.setReadTimeout(15);
        config.setWriteTimeout(15);

        fplayOkexExchange = FplayExchangeOkex.create(config, okexContractType.getFuturesContract().getName());
    }

    private ApiCredentials getApiCredentials(Object[] exArgs) {
        final ApiCredentials cred = new ApiCredentials();
        if (exArgs != null && exArgs.length == 3) {
            String exKey = (String) exArgs[0];
            String exSecret = (String) exArgs[1];
            String exPassphrase = (String) exArgs[2];
            cred.setApiKey(exKey);
            cred.setSecretKey(exSecret);
            cred.setPassphrase(exPassphrase);
        }
        return cred;
    }

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("okex-preliq-thread-%d").build());

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                if (okexSettlementService.isSettlementMode()) {
                    resetPreliqState();
                    dtPreliq.stop();
                } else {
                    checkForPreliq();
                }
            } catch (Exception e) {
                logger.error("Error on checkForDecreasePosition", e);
            }
        }, 30, 1, TimeUnit.SECONDS);
    }

    @Override
    public boolean isStarted() {
        return started;
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

        subscribeOnOrderBook();

        final boolean loginSuccess = exchange.getStreamingPrivateDataService()
                .login()
                .blockingAwait(5, TimeUnit.SECONDS);
        logger.info("Login success=" + loginSuccess);

        try {
            // workaround. some waiting after login.
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            logger.error("error while sleep after login");
        }

        if (loginSuccess) {
            userPositionSub = startUserPositionSub();
            userAccountSub = startAccountInfoSubscription();
            userOrderSub = startUserOrderSub();
        }
        pingStatSub = startPingStatSub();
        markPriceSubscription = startMarkPriceListener();
        tickerSubscription = startTickerListener();
        reSubscribePriceRangeListener();
        if (okexContractType.getBaseTool().equals("ETH")) {
            tickerEthSubscription = startEthTickerListener();
        }
        indexPriceSub = startIndexPriceSub();
        fetchOpenOrders();

        if (loginSuccess) {
            try {
                fetchUserInfoContracts();
            } catch (Exception e) {
                logger.error("On fetchUserInfoContracts", e);
            }
        }
    }

    private Completable closeAllSubscibers() {
        // Unsubscribe from data order book.
        orderBookSubscription.dispose();

//        orderSubscriptions.forEach((s, disposable) -> disposable.dispose());
        userPositionSub.dispose();
        userAccountSub.dispose();
        userOrderSub.dispose();
        pingStatSub.dispose();
        if (markPriceSubscription != null) {
            markPriceSubscription.dispose();
        }
        tickerSubscription.dispose();
        priceRangeSub.dispose();
        if (tickerEthSubscription != null) {
            tickerEthSubscription.dispose();
        }
        indexPriceSub.dispose();
        return exchange.disconnect();
    }

    private OkExStreamingExchange initExchange(String key, String secret, Object... exArgs) {
        ExchangeSpecification spec = new ExchangeSpecification(OkExStreamingExchange.class);
//        spec.setApiKey(key);
//        spec.setSecretKey(secret);
//
//        spec.setExchangeSpecificParametersItem("Use_Intl", true);
//        spec.setExchangeSpecificParametersItem("Use_Futures", true);
//        spec.setExchangeSpecificParametersItem("Futures_Contract", okexContractType.getFuturesContract());
        leverage = BigDecimal.valueOf(20); // default

        // init xchange-stream
        if (exArgs != null && exArgs.length == 3) {
            String exKey = (String) exArgs[0];
            String exSecret = (String) exArgs[1];
            String exPassphrase = (String) exArgs[2];
            spec.setExchangeSpecificParametersItem("okex-v3-as-extra", true);
            spec.setExchangeSpecificParametersItem("okex-v3-key", exKey);
            spec.setExchangeSpecificParametersItem("okex-v3-secret", exSecret);
            spec.setExchangeSpecificParametersItem("okex-v3-passphrase", exPassphrase);
        }

        return (OkExStreamingExchange) ExchangeFactory.INSTANCE.createExchange(spec);
    }

    private void initWebSocketConnection() {
        // Connect to the Exchange WebSocket API. Blocking wait for the connection.
        exchange.connect().blockingAwait();

        // Retry on disconnect. (It's disconneced each 5 min)
        onDisconnectHook = exchange.onDisconnect()
                .doOnComplete(() -> {
                    ifDisconnetedString += " okex disconnected at " + LocalTime.now();
                    if (!shutdown) {
                        logger.warn("onClientDisconnect okCoinService");
                        initWebSocketAndAllSubscribers();
                        logger.info("Exchange Reconnect finished");
                    } else {
                        logger.info("Exchange Disconnect finished");
                    }
                })
                .subscribe();
    }

    public String getIfDisconnetedString() {
        return ifDisconnetedString;
    }

    private void subscribeOnOrderBook() {

        orderBookObservable = ((OkExStreamingMarketDataService) exchange.getStreamingMarketDataService())
                .getOrderBooks(instrDtos, true)
                .doOnDispose(() -> logger.info("okex orderBook subscription doOnDispose"))
                .doOnTerminate(() -> logger.info("okex orderBook subscription doOnTerminate"))
                .doOnError(throwable -> logger.error("okex orderBook onError", throwable))
                .retryWhen(throwableObservable -> throwableObservable.delay(5, TimeUnit.SECONDS));

        orderBookSubscription = orderBookObservable
                .observeOn(stateUpdater)
                .subscribe(okcoinDepth -> {
                    boolean isExtra = isObExtra(okcoinDepth);
                    final OkexContractType ct = instrIdToContractType.get(okcoinDepth.getInstrumentId());
                    OrderBook newOrderBook = OkExAdapters.adaptOrderBook(okcoinDepth, ct.getCurrencyPair());
                    if (isExtra) {
                        this.orderBookXBTUSD = newOrderBook;
                        this.orderBookXBTUSDShort = this.orderBookXBTUSD;
                    } else {
                        metricsDictionary.incOkexObCounter();
                        this.orderBook = newOrderBook;
                        this.orderBookShort = newOrderBook;

                        final LimitOrder bestAsk = Utils.getBestAsk(newOrderBook);
                        final LimitOrder bestBid = Utils.getBestBid(newOrderBook);

                        stateRecalcInStateUpdaterThread(); // includes FullBalance and not only bestAsk/Bid
                        this.bestAsk = bestAsk != null ? bestAsk.getLimitPrice() : BigDecimal.ZERO;
                        this.bestBid = bestBid != null ? bestBid.getLimitPrice() : BigDecimal.ZERO;
                        logger.debug("ask: {}, bid: {}", this.bestAsk, this.bestBid);

                        Instant lastObTime = Instant.now();
                        getApplicationEventPublisher().publishEvent(new ObChangeEvent(new SigEvent(SigType.OKEX, lastObTime)));
                    }

                }, throwable -> logger.error("ERROR in getting order book: ", throwable));
    }

    private boolean isObExtra(OkCoinDepth okCoinDepth) {
        if (okexContractType == okexContractTypeBTCUSD) {
            return false;
        }
        final OkexContractType ct = instrIdToContractType.get(okCoinDepth.getInstrumentId());
        return ct == okexContractTypeBTCUSD;
    }

    @Override
    public OrderBook getOrderBookXBTUSD() {
        if (okexContractType == okexContractTypeBTCUSD) {
            return this.orderBookShort;
        }
        return this.orderBookXBTUSDShort;
    }

    @Override
    public LogService getTradeLogger() {
        return tradeLogger;
    }

    @Override
    public LogService getLogger() {
        return defaultLogger;
    }

    @PreDestroy
    public void preDestroy() {
        logger.info("OkCoinService preDestroy");
        shutdown = true;

        // Disconnect from exchange (non-blocking)
        //noinspection ResultOfMethodCallIgnored
        closeAllSubscibers().subscribe(() -> logger.info("Disconnected from the Exchange"));
    }

    @Scheduled(fixedDelay = 2000) // Request frequency 20 times/2s
    public void fetchEstimatedDeliveryPrice() {
        if (!isStarted()) {
            return;
        }

        Instant start = Instant.now();
        try {
            final InstrumentDto instrumentDto = instrDtos.get(0);
            if (instrumentDto.getFuturesContract() == FuturesContract.Swap) {
                return; // not in use for swap
            }
            final EstimatedPrice result = fplayOkexExchange.getPublicApi().getEstimatedPrice(instrumentDto.getInstrumentId());
            forecastPrice = result.getPrice();

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().endsWith("timeout")) {
                logger.error("On fetchEstimatedDeliveryPrice timeout");
            } else {
                logger.error("On fetchEstimatedDeliveryPrice", e);
            }
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "fetchEstimatedDeliveryPrice");
    }

    @Scheduled(fixedDelay = 200) // Request frequency 20 times/2s
    public void fetchSwapSettlement() {
        if (!isStarted()) {
            return;
        }

        Instant start = Instant.now();
        try {
            final InstrumentDto instrumentDto = instrDtos.get(0);
            if (instrumentDto.getFuturesContract() != FuturesContract.Swap) {
                return; // not in use for futures
            }
            swapSettlement = fplayOkexExchange.getPublicApi().getSwapSettlement(instrumentDto.getInstrumentId());
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().endsWith("timeout")) {
                logger.error("On fetchSwapSettlement timeout");
            } else {
                logger.error("On fetchSwapSettlement", e);
            }
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "fetchEstimatedDeliveryPrice");
    }

    public SwapSettlement getSwapSettlement() {
        return swapSettlement;
    }

    //    @Scheduled(fixedDelay = 1000) // Request frequency 20 times/2s
//    public void fetchIndexPrice() {
//        Instant start = Instant.now();
//        try {
//            final OkcoinIndexPrice mainIndexPrice = ((OkCoinFuturesMarketDataService) exchange.getMarketDataService())
//                    .getFuturesIndexPrice(okexContractType.getCurrencyPair());
//            this.contractIndex = new ContractIndex(mainIndexPrice.getIndexPrice().setScale(2, RoundingMode.HALF_UP), new Date());
//
//            if (okexContractType != okexContractTypeBTCUSD) {
//                final OkcoinIndexPrice exIndexPrice = ((OkCoinFuturesMarketDataService) exchange.getMarketDataService())
//                        .getFuturesIndexPrice(okexContractTypeBTCUSD.getCurrencyPair());
//
//                this.btcContractIndex = new ContractIndex(exIndexPrice.getIndexPrice().setScale(2, RoundingMode.HALF_UP), new Date());
//            }
//        } catch (Exception e) {
//            logger.error("On fetchEstimatedDeliveryPrice", e);
//        }
//        Instant end = Instant.now();
//        Utils.logIfLong(start, end, logger, "fetchEstimatedDeliveryPrice");
//    }

    @Scheduled(fixedDelay = 200)
    // Rate Limit: 20 requests per 2 seconds
    public void fetchPositionScheduled() {
        if (!isStarted()) {
            return;
        }
        Instant start = Instant.now();
        try {
            fetchPosition();
        } catch (Exception e) {
            if (e.getMessage().endsWith("timeout")) {
                logger.error("On fetchPositionScheduled timeout");
            } else {
                logger.error("On fetchPositionScheduled", e);
            }
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "fetchPositionScheduled");
    }

    @Override
    public String fetchPosition() throws Exception {
        final String instrumentId = instrDtos.get(0).getInstrumentId();
        final Pos pos = fplayOkexExchange.getPrivateApi().getPos(instrumentId);
        final Pos finalPos = setPosLeverage(pos);
        this.pos.set(finalPos);
        getApplicationEventPublisher().publishEvent(new NtUsdCheckEvent());
        stateRecalcInStateUpdaterThread();

        return this.pos.toString();
    }

    @Scheduled(fixedDelay = 200) // v3: Rate Limit: 20 requests per 2 seconds
    public void fetchUserInfoScheduled() {
        if (!isStarted()) {
            return;
        }

        Instant start = Instant.now();
        try {
            fetchUserInfoContracts();
        } catch (Exception e) {
            logger.error("On fetchPositionScheduled", e);
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "fetchPositionScheduled");
    }

    private void fetchUserInfoContracts() throws IOException {

        final AccountInfoContracts accountInfoContracts = getAccountApiV3();

        mergeAccountSafe(accountInfoContracts);
    }

    public AccountInfoContracts getAccountApiV3() {
        final String toolIdForApi = getToolIdForApi();
        return fplayOkexExchange.getPrivateApi().getAccount(toolIdForApi);
    }

    private String getToolIdForApi() {
        String toolName;
        if (okexContractType.getFuturesContract() == FuturesContract.Swap) {
            toolName = instrDtos.get(0).getInstrumentId();
        } else {
            toolName = okexContractType.getCurrencyPair().base.getCurrencyCode().toLowerCase();
        }
        return toolName;
    }

    private BigDecimal convertLiqPrice(String forceLiquPrice) {
        BigDecimal res = BigDecimal.ZERO;
        try {   // Example: "6,988.95"
            String thePrice = forceLiquPrice.replaceAll(",", "");
            res = new BigDecimal(thePrice);
        } catch (Exception e) {
            logger.error("Can not convert forceLiquPrice=" + forceLiquPrice);
        }
        return res;
    }

    private Disposable startUserPositionSub() {
        final InstrumentDto instrumentDto = new InstrumentDto(okexContractType.getCurrencyPair(), okexContractType.getFuturesContract());

        return exchange.getStreamingPrivateDataService()
                .getPositionObservable(instrumentDto)
                .doOnError(throwable -> logger.error("Error on PrivateData observing", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(newPos -> {
                    final Pos pos;
                    if (okexContractType.getFuturesContract() == FuturesContract.Swap) {
                        pos = mergeSwapPosSafe(newPos);
                    } else {
                        pos = positionStreamToPos(newPos);
                    }
                    this.pos.set(pos);

                    getApplicationEventPublisher().publishEvent(new NtUsdCheckEvent());
                    stateRecalcInStateUpdaterThread();

                }, throwable -> logger.error("PositionObservable.Exception: ", throwable));
    }

    protected Pos mergeSwapPosSafe(PositionStream newInfo) {
        int iter = 0;
        boolean success = false;
        while (!success) {
            Pos current = this.pos.get();
            logger.debug("Pos.Websocket: " + current.toString());
            final Pos updated = mergeSwapPos(newInfo, current);
            success = this.pos.compareAndSet(current, updated);
            if (++iter > 1) {
                logger.warn("merge account iter=" + iter);
            }
        }
        return this.pos.get();
    }

    private Pos setPosLeverage(Pos p) {
        final BigDecimal lv = p.getLeverage().signum() != 0 ? p.getLeverage() : this.leverage;
        return new Pos(
                p.getPositionLong(),
                p.getPositionShort(),
                p.getLongAvailToClose(),
                p.getShortAvailToClose(),
                lv,
                p.getLiquidationPrice(),
                BigDecimal.ZERO, //mark value
                p.getPriceAvgLong(),
                p.getPriceAvgShort(),
                p.getTimestamp(),
                p.getRaw(),
                p.getPlPos()
        );
    }


    private Pos positionStreamToPos(PositionStream n) {
        return new Pos(
                n.getPositionLong(),
                n.getPositionShort(),
                n.getLongAvailToClose(),
                n.getShortAvailToClose(),
                leverage,
                n.getLiquidationPrice(),
                BigDecimal.ZERO, //mark value
                n.getPriceAvgLong(),
                n.getPriceAvgShort(),
                n.getTimestamp(),
                n.getRaw(),
                n.getPlPos()
        );
    }

    private Pos mergeSwapPos(PositionStream n, Pos current) {
        final BigDecimal leverage = n.getLeverage().signum() != 0 ? n.getLeverage() : getLeverage();
        if (n.getPositionLong() != null) {
            return new Pos(
                    n.getPositionLong(),
                    current.getPositionShort(),
                    n.getLongAvailToClose(),
                    current.getShortAvailToClose(),
                    leverage,
                    n.getLiquidationPrice(),
                    BigDecimal.ZERO, //mark value
                    n.getPriceAvgLong(),
                    current.getPriceAvgShort(),
                    n.getTimestamp(),
                    n.getRaw(),
                    current.getPlPos()
            );
        }
        //else
        return new Pos(
                current.getPositionLong(),
                n.getPositionShort(),
                current.getLongAvailToClose(),
                n.getShortAvailToClose(),
                leverage,
                n.getLiquidationPrice(),
                BigDecimal.ZERO, //mark value
                current.getPriceAvgLong(),
                n.getPriceAvgShort(),
                n.getTimestamp(),
                n.getRaw(),
                current.getPlPos()
        );
    }

    @SuppressWarnings("Duplicates")
    private Disposable startAccountInfoSubscription() {
        final InstrumentDto instrumentDto = new InstrumentDto(okexContractType.getCurrencyPair(), okexContractType.getFuturesContract());
        return exchange.getStreamingPrivateDataService()
                .getAccountInfoObservable(okexContractType.getCurrencyPair(), instrumentDto)
                .doOnError(throwable -> logger.error("Error on PrivateData observing", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .filter(Objects::nonNull)
                .subscribe(newInfo -> {
                    logger.debug(newInfo.toString());
                    mergeAccountSafe(newInfo);

                }, throwable -> logger.error("AccountInfoObservable.Exception: ", throwable));
    }

    private Disposable startUserOrderSub() {
        final InstrumentDto instrumentDto = new InstrumentDto(okexContractType.getCurrencyPair(), okexContractType.getFuturesContract());

        return ((OkExStreamingPrivateDataService) exchange.getStreamingPrivateDataService())
                .getTradesObservableRaw(instrumentDto)
                .map(TmpAdapter::adaptTradeResult)
                .doOnError(throwable -> logger.error("Error on PrivateData observing", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(limitOrders -> {
                    logger.debug("got open orders: " + limitOrders.size());

                    final FplayOrder stub = new FplayOrder(this.getMarketId());
                    updateFplayOrdersToCurrStab(limitOrders, stub);

                    writeFilledOrderLog(limitOrders, getOpenOrders());

                    writeAvgPriceLog();

                    addCheckOoToFree();

                }, throwable -> logger.error("TradesObservable.Exception: ", throwable)); // TODO placingType is null!!!
    }

    private Disposable startPingStatSub() {
        return exchange.subscribePingStats()
                .map(PingStatEvent::getPingPongMs)
                .subscribe(ms -> {
                            metricsDictionary.putOkexPing(ms);
                            logger.debug("okex ping-pong(ms): " + ms);
                        },
                        e -> logger.error("ping stats error", e));
    }

    private Disposable startMarkPriceListener() {
        List<InstrumentDto> instrumentDtos = new ArrayList<>();
        instrumentDtos.add(new InstrumentDto(okexContractType.getCurrencyPair(), okexContractType.getFuturesContract()));
        return ((OkExStreamingMarketDataService) exchange.getStreamingMarketDataService())
                .getMarkPrices(instrumentDtos)
                .doOnError(throwable -> logger.error("Error on MarkPrice observing", throwable))
                .retryWhen(throwables -> throwables.delay(10, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(markPriceDto -> {
                    logger.debug(markPriceDto.toString());
                    markPrice = markPriceDto.getMarkPrice();
                }, throwable -> logger.error("MarkPrice.Exception: ", throwable));
    }

    // futures ticker
    private Disposable startTickerListener() {
        final InstrumentDto instrumentDto = new InstrumentDto(okexContractType.getCurrencyPair(), okexContractType.getFuturesContract());
        return exchange.getStreamingMarketDataService()
                .getTicker(okexContractType.getCurrencyPair(), instrumentDto)
                .doOnError(throwable -> logger.error("Error on Ticker observing", throwable))
                .retryWhen(throwables -> throwables.delay(10, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(ticker -> {
                    logger.debug(ticker.toString());
                    this.ticker = ticker;
                    lastPriceDeviationService.updateAndCheckDeviationAsync();
                }, throwable -> logger.error("OkexFutureTicker.Exception: ", throwable));
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void checkPriceRangeTime() {
        if (priceRange == null || priceRange.getTimestamp() == null ||
                priceRange.getTimestamp().plusSeconds(60).isBefore(Instant.now())) {
            final String warn = "ReSubscribe PriceRange: " + priceRange;
            warningLogger.warn(warn);
            getTradeLogger().warn(warn);
            logger.warn(warn);
            reSubscribePriceRangeListener();
        }
    }

    private void reSubscribePriceRangeListener() {
        logger.info("priceRange: " + (priceRange != null ? priceRange.getTimestamp() : "null"));
        if (priceRangeSub != null && !priceRangeSub.isDisposed()) {
            priceRangeSub.dispose();
            // TODO listen to priceRangeSub unsubscribe response
            // wait 10 sec to dispose
            int i = 0;
            try {
                Thread.sleep(1000); // noticed from logs that 'Unsubscribing from channel' takes about 400ms
                while (!priceRangeSub.isDisposed() && i++ < 100) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                logger.error("priceRangeSub.dispose() interrupted");
            }
        }
        priceRangeSub = startPriceRangeListener();
    }

    private Disposable startPriceRangeListener() {
        final InstrumentDto instrumentDto = new InstrumentDto(okexContractType.getCurrencyPair(), okexContractType.getFuturesContract());
        return ((OkExStreamingMarketDataService) exchange.getStreamingMarketDataService())
                .getPriceRange(instrumentDto)
                .doOnError(throwable -> logger.error("Error on PriceRange observing", throwable))
                .retryWhen(throwables -> throwables.delay(10, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(priceRange -> {
                    logger.debug(priceRange.toString());
                    this.priceRange = priceRange;
                }, throwable -> logger.error("OkexPriceRange.Exception: ", throwable));
    }

    // spot ticker
    private Disposable startEthTickerListener() {
        return exchange.getStreamingMarketDataService()
                .getTicker(CurrencyPair.ETH_BTC, null, "ETH-BTC")
                .doOnError(throwable -> logger.error("Error on Ticker observing", throwable))
                .retryWhen(throwables -> throwables.delay(10, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        ethTick -> this.ethBtcTicker = ethTick,
                        throwable -> logger.error("OkexSpotTicker.Exception: ", throwable)
                );
    }

    private Disposable startIndexPriceSub() {
        List<CurrencyPair> pairs = new ArrayList<>();
        pairs.add(okexContractType.getCurrencyPair());
        if (okexContractType.isEth()) {
            pairs.add(okexContractTypeBTCUSD.getCurrencyPair());
        }
        return ((OkExStreamingMarketDataService) exchange.getStreamingMarketDataService())
                .getIndexTickers(pairs)
                .doOnError(throwable -> logger.error("Error on Ticker observing", throwable))
                .retryWhen(throwables -> throwables.delay(10, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(indexTick -> {
                            final CurrencyPair currencyPair = indexTick.getCurrencyPair();
                            final BigDecimal indexPrice = indexTick.getLast();
                            if (currencyPair.equals(okexContractType.getCurrencyPair())) {
                                this.contractIndex.set(new ContractIndex(indexPrice.setScale(2, RoundingMode.HALF_UP), new Date()));
                            } else {
                                this.btcContractIndex.set(new ContractIndex(indexPrice.setScale(2, RoundingMode.HALF_UP), new Date()));
                            }
                        },
                        throwable -> logger.error("OkexIndexPriceSub.Exception: ", throwable)
                );
    }

    @Scheduled(fixedDelay = 2000)
    public void openOrdersCleaner() {
        Instant start = Instant.now();
        cleanOldOO();
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "openOrdersCleaner");
    }

    /**
     * See {@link OOHangedCheckerService}.
     */
    void openOrdersHangedChecker() {
        updateOOStatuses();

        addCheckOoToFree();
    }

    private TradeResponse takerOrder(Long tradeId, Order.OrderType inputOrderType, BigDecimal amount, BestQuotes bestQuotes, SignalType signalType,
            String counterName, Integer portionsQty, Integer portionsQtyMax, String counterNameWithPortion)
            throws Exception {

        TradeResponse tradeResponse = new TradeResponse();

        final Order.OrderType orderType = adjustOrderType(inputOrderType, amount);

        final String message = Utils.getTenAskBid(getOrderBook(), counterNameWithPortion,
                String.format("Before %s placing, orderType=%s,", orderType, Utils.convertOrderTypeName(orderType)));
        logger.info(message);
        tradeLogger.info(message);
//        synchronized (openOrdersLock)
        {

            // Option 1: REAL TAKER - okex does it different. It is similar to our HYBRID(BBO - ask1 or bid1)
//            final MarketOrder marketOrder = new MarketOrder(orderType, amount, currencyPair, new Date());
//            final String orderId = tradeService.placeMarketOrder(marketOrder);

            BigDecimal thePrice = createBestTakerPrice(orderType, null);

            getTradeLogger().info("The fake taker price is " + thePrice.toPlainString());

            // metrics
            final Mon monPlacing = monitoringDataService.fetchMon(getName(), "placeOrder");
            final Instant startReq = Instant.now();

            final InstrumentDto instrumentDto = new InstrumentDto(okexContractType.getCurrencyPair(), okexContractType.getFuturesContract());
            final OrderResultTiny orderResult = fplayOkexExchange.getPrivateApi().limitOrder(
                    instrumentDto.getInstrumentId(),
                    orderType,
                    thePrice,
                    amount,
                    leverage,
                    Collections.singletonList(FuturesOrderTypeEnum.NORMAL_LIMIT)
            );
            final String orderId = orderResult.getOrder_id();
            final LimitOrder limitOrder = new LimitOrder(orderType, amount, okexContractType.getCurrencyPair(), orderId, new Date(), thePrice);

            final Instant endReq = Instant.now();
            final long waitingMarketMs = endReq.toEpochMilli() - startReq.toEpochMilli();
            monPlacing.getWaitingMarket().add(BigDecimal.valueOf(waitingMarketMs));
            if (waitingMarketMs > 5000) {
                logger.warn("TAKER okexPlaceOrder waitingMarketMs=" + waitingMarketMs);
            }
            monitoringDataService.saveMon(monPlacing);
            metricsDictionary.putOkexPlacing(waitingMarketMs);
            fplayTradeService.addOkexPlacingMs(tradeId, waitingMarketMs);

            LimitOrder orderInfo = TmpAdapter.cloneWithId(limitOrder, orderId);
            tradeResponse.setLimitOrder(orderInfo);
            orderInfo.setOrderStatus(OrderStatus.NEW);
            final FplayOrder fPlayOrder = new FplayOrder(this.getMarketId(), tradeId, counterName, orderInfo, bestQuotes, PlacingType.TAKER, signalType,
                    portionsQty, portionsQtyMax);
            addOpenOrder(fPlayOrder);

            // double check. Do we need it?
            orderInfo = getFinalOrderInfoSync(orderId, counterNameWithPortion, "Taker:FinalStatus:");
            if (orderInfo == null) {
                orderInfo = TmpAdapter.cloneWithId(limitOrder, orderId);
                orderInfo.setOrderStatus(OrderStatus.NEW);
            }

            tradeResponse.setLimitOrder(orderInfo);
            final FplayOrder theUpdate = FplayOrderUtils.updateFplayOrder(fPlayOrder, orderInfo);
            addOpenOrder(theUpdate);

            persistenceService.getDealPricesRepositoryService().setSecondOpenPrice(tradeId, orderInfo.getAveragePrice());

            if (orderInfo.getStatus() == OrderStatus.CANCELED) { // Should not happen
                tradeResponse.setErrorCode(TradeResponse.TAKER_WAS_CANCELLED_MESSAGE);
                tradeResponse.addCancelledOrder(orderInfo);
                tradeResponse.setLimitOrder(null);
                warningLogger.warn("#{} Order was cancelled. orderId={}", counterNameWithPortion, orderId);
            } else if (orderInfo.getStatus() == OrderStatus.FILLED) { //FILLED by any (orderInfo or cancelledOrder)

                writeLogPlaceOrder(orderType, amount, "taker",
                        orderInfo.getAveragePrice(), orderId, orderInfo.getStatus().toString(), counterNameWithPortion, orderResult);

                tradeResponse.setOrderId(orderId);
                tradeResponse.setLimitOrder(orderInfo);
            } else { // NEW, PARTIALLY_FILLED
                tradeResponse.addCancelledOrder(orderInfo);
                tradeResponse.setErrorCode(TradeResponse.TAKER_BECAME_LIMIT);
                tradeResponse.setLimitOrder(orderInfo);
            }
        } // openOrdersLock

        return tradeResponse;
    }

    @Override
    protected Completable recalcAffordableContracts() {
        return Completable.fromAction(() -> {
            final BigDecimal reserveBtc = arbitrageService.getParams().getReserveBtc2();
            final BigDecimal volPlan = settingsRepositoryService.getSettings().getPlacingBlocks().getFixedBlockOkex();
//        final BigDecimal volPlan = arbitrageService.getParams().getBlock2();

            final Pos position = this.pos.get();
            final AccountBalance account = this.account.get();

            final OrderBook ob = this.orderBookShort;
            if (account != null && position != null && Utils.orderBookIsFull(ob)) {
                final BigDecimal available = account.getAvailable();
                final BigDecimal equity = account.getELast();

                final BigDecimal bestAsk = Utils.getBestAsks(ob, 1).get(0).getLimitPrice();
                final BigDecimal bestBid = Utils.getBestBids(ob, 1).get(0).getLimitPrice();
                final BigDecimal leverage = position.getLeverage().signum() != 0 ? position.getLeverage() : getLeverage();

                if (available != null && equity != null && leverage != null && position.getPositionLong() != null && position.getPositionShort() != null) {

//                if (available.signum() > 0) {
//                if (orderType.equals(Order.OrderType.BID) || orderType.equals(Order.OrderType.EXIT_ASK)) {
                    BigDecimal affordableContractsForLong;
                    final BigDecimal usdInContract = BigDecimal.valueOf(this.usdInContract);
                    if (position.getPositionShort().signum() != 0) { // there are sells
                        if (volPlan.compareTo(position.getPositionShort()) != 1) {
                            affordableContractsForLong = (position.getPositionShort().subtract(position.getPositionLong()).add(
                                    (equity.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage).divide(usdInContract, 0, BigDecimal.ROUND_DOWN)
                            )).setScale(0, BigDecimal.ROUND_DOWN);
                        } else {
                            affordableContractsForLong = (available.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage)
                                    .divide(usdInContract, 0, BigDecimal.ROUND_DOWN);
                        }
                        if (affordableContractsForLong.compareTo(position.getPositionShort()) == -1) {
                            affordableContractsForLong = position.getPositionShort();
                        }
                    } else { // no sells
                        affordableContractsForLong = (available.subtract(reserveBtc)).multiply(bestAsk).multiply(leverage)
                                .divide(usdInContract, 0, BigDecimal.ROUND_DOWN);
                    }
                    affordable.setForLong(affordableContractsForLong);
                    //TODO
//                }

//                if (orderType.equals(Order.OrderType.ASK) || orderType.equals(Order.OrderType.EXIT_BID)) {
                    BigDecimal affordableContractsForShort;
                    if (position.getPositionLong().signum() != 0) { // we have BIDs
                        if (volPlan.compareTo(position.getPositionLong()) != 1) { // если мы хотим закрыть меньше чем есть
                            final BigDecimal divide = (equity.subtract(reserveBtc)).multiply(bestBid.multiply(leverage))
                                    .divide(usdInContract, 0, BigDecimal.ROUND_DOWN);
                            affordableContractsForShort = (position.getPositionLong().subtract(position.getPositionShort()).add(
                                    divide
                            )).setScale(0, BigDecimal.ROUND_DOWN);
                        } else {
                            affordableContractsForShort = (available.subtract(reserveBtc)).multiply(bestBid).multiply(leverage)
                                    .divide(usdInContract, 0, BigDecimal.ROUND_DOWN);
                        }
                        if (affordableContractsForShort.compareTo(position.getPositionLong()) == -1) {
                            affordableContractsForShort = position.getPositionLong();
                        }
                    } else { // no BIDs
                        affordableContractsForShort = ((available.subtract(reserveBtc)).multiply(bestBid).multiply(leverage))
                                .divide(usdInContract, 0, BigDecimal.ROUND_DOWN);
                    }
                    affordable.setForShort(affordableContractsForShort);
//                }
//                }
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
    public boolean isAffordable(Order.OrderType orderType, BigDecimal tradableAmount) {
        boolean isAffordable;
        final BigDecimal affordableVol = (orderType == Order.OrderType.BID || orderType == Order.OrderType.EXIT_ASK)
                ? this.affordable.getForLong() : this.affordable.getForShort();
        isAffordable = affordableVol.compareTo(tradableAmount) != -1;

        return isAffordable;
    }

    public PlaceOrderArgs changeDeferredPlacingType(PlacingType placingType) {
        return placeOrderArgsRef.updateAndGet(currArgs -> {
            if (currArgs == null) {
                return null;
            }
            return currArgs.cloneWithPlacingType(placingType);
        });
    }

    public void changeDeferredAmountSubstract(BigDecimal placedBlock, Integer portionsQty) {
        placeOrderArgsRef.getAndUpdate(currArgs -> {
            if (currArgs == null) {
                return null;
            }
            final BigDecimal aLeft = currArgs.getAmount().subtract(placedBlock);
            if (aLeft.signum() == 0) {
                return null;
            }
            return currArgs.cloneWithAmountAndPortionsQty(aLeft, portionsQty);
        });
    }

    public void updateDeferredAmount(BigDecimal amount) {
        placeOrderArgsRef.getAndUpdate(currArgs -> {
            if (currArgs == null) {
                return null;
            }
            return currArgs.cloneWithAmount(amount);
        });
    }

    public PlaceOrderArgs updateFullDeferredAmount(BigDecimal fullAmount) {
        return placeOrderArgsRef.updateAndGet(currArgs -> {
            if (currArgs == null) {
                return null;
            }
            return currArgs.cloneWithFullAmount(fullAmount);
        });
    }

    /**
     * It is not thread safe. It uses <b>openOrdersLock</b>. Don't call it inside <b>arbStateLock</b>. That's why it is async.
     *
     * @return false when need reset of arbState, true otherwise.
     */
    public void tryPlaceDeferredOrder() {
//        ooSingleExecutor - may read with arbStateLock
        final PlaceOrderArgs currArgs = placeOrderArgsRef.get();
        if (currArgs != null && currArgs.getArbScheme() != ArbScheme.CON_B_O_PORTIONS) {
            addOoExecutorTask(this::tryPlaceDeferredOrderTask);
        } else {
            getApplicationEventPublisher().publishEvent(new NtUsdCheckEvent());
        }
    }

    public void addOoExecutorTask(Runnable task) {
        ooSingleExecutor.submit(task);
    }

    @Override
    public boolean hasDeferredOrders() {
        return placeOrderArgsRef.get() != null;
    }

    @Override
    public PlaceOrderArgs getDeferredOrder() {
        return placeOrderArgsRef.get();
    }

    public AtomicReference<PlaceOrderArgs> getPlaceOrderArgsRef() {
        return placeOrderArgsRef;
    }

    private Boolean tryPlaceDeferredOrderTask() {
        try {
            if (getMarketState() == MarketState.WAITING_ARB) {
                // check1
                final PlaceOrderArgs currArgs = placeOrderArgsRef.getAndSet(null);
                if (noDeferredOrderCheck(currArgs)) {
                    return false;
                }
                if (btmNotFullyFilledCheck(currArgs)) {
                    return false;
                }
                // do deferred placing
                beforeDeferredPlacing(currArgs);
                placeOrder(currArgs);

            } else {
                final PlaceOrderArgs currArgs = placeOrderArgsRef.get();
                if (currArgs != null) {
                    logger.warn("WAITING_ARB: deferredPlacingOrder warn. PlaceOrderArgs is not null. " + currArgs);
                }
            }
        } catch (Exception e) {
            logger.error("WAITING_ARB: deferredPlacingOrder error", e);
            resetWaitingArb();
            arbitrageService.resetArbState("deferredPlacingOrder");
            slackNotifications.sendNotify(NotifyType.RESET_TO_FREE, "WAITING_ARB: deferredPlacingOrder error. Set READY. " + e.getMessage());
            return false;
        }
        return true;
    }

    public boolean noDeferredOrderCheck(PlaceOrderArgs currArgs) {
        if (currArgs == null) {
            logger.error("WAITING_ARB: no deferred order. Set READY.");
            warningLogger.error("WAITING_ARB: no deferred order. Set READY.");
            resetWaitingArb();
            arbitrageService.resetArbState("deferredPlacingOrder");
            return true;
        }
        return false;
    }

    // check2
    public boolean btmNotFullyFilledCheck(PlaceOrderArgs currArgs) {
        final String counterName = currArgs.getCounterName();
        final Long tradeId = currArgs.getTradeId();
        final DealPrices dealPrices = arbitrageService.getDealPrices(tradeId);
        if (dealPrices.getBPriceFact().isNotFinished()) {
            final String msg = String.format("#%s tradeId=%s "
                            + "WAITING_ARB: bitmex is not fully filled. Try to update the filled amount for all orders.",
                    counterName,
                    tradeId
            );
            logger.info(msg);
            arbitrageService.getFirstMarketService().getTradeLogger().info(msg);
            getTradeLogger().info(msg);
            warningLogger.info(msg);
            final BitmexService bitmexService = (BitmexService) arbitrageService.getFirstMarketService();
            bitmexService.updateAvgPrice(dealPrices, true);

            if (dealPrices.getBPriceFact().isNotFinished()) {
                final String msg1 = String.format("#%s tradeId=%s "
                                + "WAITING_ARB: bitmex is not fully filled. Set READY.",
                        counterName,
                        tradeId
                );
                logger.error(msg1);
                arbitrageService.getFirstMarketService().getTradeLogger().info(msg1);
                getTradeLogger().info(msg1);
                warningLogger.error(msg1);
                resetWaitingArb();
                arbitrageService.resetArbState("deferredPlacingOrder");
                return true;
            }
        }
        return false;
    }

    public void beforeDeferredPlacing(PlaceOrderArgs currArgs) {
        final Long tradeId = currArgs.getTradeId();
        final DealPrices dealPrices = arbitrageService.getDealPrices(tradeId);

        setMarketState(MarketState.ARBITRAGE);
        tradeLogger.info(String.format("#%s MT2 start placing ", currArgs));
        logger.info(String.format("#%s MT2 start placing ", currArgs));

        boolean firstPortion = currArgs.getPortionsQty() == null || currArgs.getPortionsQty() == 1;
        if (currArgs.getPlacingType() == PlacingType.TAKER && firstPortion) {// set oPricePlanOnStart for Taker
            final BigDecimal oPricePlanOnStart;
            if (currArgs.getOrderType() == OrderType.BID || currArgs.getOrderType() == OrderType.EXIT_ASK) {
                oPricePlanOnStart = Utils.getBestAsk(this.orderBookShort).getLimitPrice(); // buy -> use the opposite price.
            } else {
                oPricePlanOnStart = Utils.getBestBid(this.orderBookShort).getLimitPrice(); // do sell -> use the opposite price.
            }
            persistenceService.getDealPricesRepositoryService().setOPricePlanOnStart(dealPrices.getTradeId(), oPricePlanOnStart);
        }

        fplayTradeService.setOkexStatus(tradeId, TradeMStatus.IN_PROGRESS);
        currArgs.setPricePlanOnStart(true);
    }

    @Override
    public TradeResponse placeOrder(PlaceOrderArgs placeOrderArgs) {
        TradeResponse tradeResponse = new TradeResponse();

        final Integer maxAttempts = settingsRepositoryService.getSettings().getOkexSysOverloadArgs().getPlaceAttempts();

        final Order.OrderType orderType = placeOrderArgs.getOrderType();
        final BigDecimal amount = placeOrderArgs.getAmount();
        final BestQuotes bestQuotes = placeOrderArgs.getBestQuotes();
        final SignalType signalType = placeOrderArgs.getSignalType();
        PlacingType placingType = placeOrderArgs.getPlacingType();
        if (placingType == null) {
            tradeLogger.warn("WARNING: placingType is null. " + placeOrderArgs);
            final Settings settings = settingsRepositoryService.getSettings();
            placingType = settings.getOkexPlacingType();
        }
        final String counterName = placeOrderArgs.getCounterName();
        final String counterNameWithPortion = placeOrderArgs.getCounterNameWithPortion();
        final Long tradeId = placeOrderArgs.getTradeId();
        final BeforeSignalMetrics beforeSignalMetrics = placeOrderArgs.getBeforeSignalMetrics();
        final Instant startPlacing = Instant.now();

        // SET STATE
        arbitrageService.setSignalType(signalType);
        setMarketState(MarketState.PLACING_ORDER);

        BigDecimal amountLeft = amount;
        shouldStopPlacing = false;
        for (int attemptCount = 1; attemptCount < maxAttempts
                && !getArbitrageService().isArbStateStopped()
                && !getArbitrageService().isArbForbidden()
                && !shouldStopPlacing;
                attemptCount++) {
            try {
                if (settingsRepositoryService.getSettings().getManageType().isManual() && !signalType.isRecoveryNtUsd()) {
                    if (!signalType.isManual() || attemptCount > 1) {
                        warningLogger.info("MangeType is MANUAL. Stop placing.");
                        break; // when MangeType is MANUAL, only the first manualSignal is accepted
                    }
                }
                if (attemptCount > 1) {
                    Thread.sleep(1000);
                }

                if (placingType != PlacingType.TAKER) {
                    tradeResponse = placeNonTakerOrder(tradeId, orderType, amountLeft, bestQuotes, false, signalType, placingType, counterName,
                            placeOrderArgs.isPricePlanOnStart(), placeOrderArgs.getPortionsQty(), placeOrderArgs.getPortionsQtyMax(),
                            counterNameWithPortion);
                } else {
                    tradeResponse = takerOrder(tradeId, orderType, amountLeft, bestQuotes, signalType, counterName,
                            placeOrderArgs.getPortionsQty(), placeOrderArgs.getPortionsQtyMax(), counterNameWithPortion);
                    if (tradeResponse.getErrorCode() != null && tradeResponse.getErrorCode().equals(TradeResponse.TAKER_WAS_CANCELLED_MESSAGE)) {
                        final BigDecimal filled = tradeResponse.getCancelledOrders().get(0).getCumulativeAmount();
                        amountLeft = amountLeft.subtract(filled);
                        continue;
                    }
                }
                break;

            } catch (Exception e) {
                String message = e.getMessage();

                String details = String.format("#%s/%s placeOrderOnSignal error. type=%s,a=%s,bestQuotes=%s,isMove=%s,signalT=%s. %s",
                        counterNameWithPortion, attemptCount,
                        orderType, amountLeft, bestQuotes, false, signalType, message);
                logger.error(details, e);
                details = details.length() < 400 ? details : details.substring(0, 400); // we can get html page as error message
                tradeLogger.error(details);

                tradeResponse.setOrderId(null);
                tradeResponse.setErrorCode(message);

                final NextStep nextStep = handlePlacingException(e, tradeResponse);

                if (nextStep == NextStep.CONTINUE) {
                    continue;
                }
                break; // no retry by default
            }
        }

        if (placeOrderArgs.isPreliqOrder()) {
            logger.info("restore marketState to PRELIQ");
            setMarketState(MarketState.PRELIQ, counterName);
        } else {
            // RESET STATE
            if (placingType != PlacingType.TAKER) {
                ooHangedCheckerService.startChecker();
                setMarketState(MarketState.ARBITRAGE, counterName);
                if (tradeResponse.getOrderId() == null) { // any exception.
                    setFree(placeOrderArgs.getTradeId()); // ARBITRAGE->READY and iterateOOToMove
                }
            } else {
                // TAKER ORDER
                if (placeOrderArgsRef.get() != null) {
                    setMarketState(MarketState.WAITING_ARB, counterName); // should be READY
                } else {
                    setMarketState(MarketState.ARBITRAGE, counterName); // should be READY
                    setFree(placeOrderArgs.getTradeId()); // ARBITRAGE->READY and iterateOOToMove
                }

            }
        }

        // metrics
        final Mon monPlacing = monitoringDataService.fetchMon(getName(), "placeOrder");
        if (beforeSignalMetrics.getLastObTime() != null) {
            long beforeMs = startPlacing.toEpochMilli() - beforeSignalMetrics.getLastObTime().toEpochMilli();
            monPlacing.getBefore().add(BigDecimal.valueOf(beforeMs));
            metricsDictionary.putOkexPlacingBefore(beforeMs);
            if (beforeMs > 5000) {
                logger.warn(placingType + "okex beforePlaceOrderMs=" + beforeMs);
            }
        }
        final Instant endPlacing = Instant.now();
        long wholeMs = endPlacing.toEpochMilli() - startPlacing.toEpochMilli();
        monPlacing.getWholePlacing().add(BigDecimal.valueOf(wholeMs));
        if (wholeMs > 5000) {
            logger.warn(placingType + "okex wholePlacingMs=" + wholeMs);
        }
        monPlacing.incCount();
        monitoringDataService.saveMon(monPlacing);
        metricsDictionary.putOkexPlacingWhole(wholeMs);

        return tradeResponse;
    }

    @Scheduled(fixedDelay = 10000)
    public void fetchLeverRate() {
        if (!isStarted()) {
            return;
        }
        Instant start = Instant.now();
        try {
            final String toolName = getToolIdForApi();
            final Leverage lv = fplayOkexExchange.getPrivateApi().getLeverage(toolName);
            if (lv == null) {
                logger.error("lv is null");
            } else {
                leverage = lv.getLeverage();
            }
        } catch (Exception e) {
            logger.error("fetchLeverRate error", e);
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "fetchLeverRate");
    }

    public BigDecimal getLeverage() {
        return leverage;
    }

    public String changeOkexLeverage(BigDecimal okexLeverage) {
        String resultDescription = "";
        try {
            final String toolIdForApi = getToolIdForApi();
            final Leverage r = fplayOkexExchange.getPrivateApi().changeLeverage(
                    toolIdForApi,
                    okexLeverage.setScale(0, RoundingMode.DOWN).toPlainString()
            );
            leverage = r.getLeverage();
            logger.info("Update okex leverage. " + r);
            resultDescription = r.getDescription();
        } catch (Exception e) {
            logger.error("Error updating okex leverage", e);
            resultDescription = e.getMessage();
        }
        return "result: " + resultDescription;
    }

    enum NextStep {CONTINUE, BREAK,}

    private NextStep handlePlacingException(Exception exception, TradeResponse tradeResponse) {
        if (exception instanceof HttpStatusIOException) {
            HttpStatusIOException e = (HttpStatusIOException) exception;
            final String httpBody = e.getHttpBody();
            tradeResponse.setOrderId(null);
            tradeResponse.setErrorCode(httpBody);
/*
                try {
//                    ObjectMapper objectMapper = new ObjectMapper();
//                    final Error error = objectMapper.readValue(httpBody, Error.class);
//                    final String marketResponseMessage = error.getError().getMessage();

//                    if (marketResponseMessage.contains("UnknownHostException: www.okex.com")) {
//                        if (attemptCount < maxAttempts) {
//                            continue; // retry!
//                        } else {
//                            setOverloaded(null);
//                        }
//                    }
                } catch (IOException e1) {
                    final String errMsg = String.format("On parse error:%s, %s", e.toString(), e.getHttpBody());
                    logger.error(errMsg, e1);
                    tradeLogger.error(errMsg);
                }
*/
            return NextStep.BREAK; // no retry by default
        } else {
            String message = exception.getMessage();
            // api v3 throws ApiException
            if (exception instanceof ApiException && exception.getCause() != null) {
                message = exception.getCause().getMessage();
            }
            if (message == null) {
                logger.error("null", exception);
            } else {
                if (message.contains("connect timed out") // SocketTimeoutException api-v1?
                        || message.contains("Read timed out") // SocketTimeoutException api-v1?
                        || message.contains("Remote host closed connection during handshake") // api-v1?: javax.net.ssl.SSLHandshakeException
                        || message.contains("Signature does not match") // api-v1
                        // api-v3:
                        || message.contains("Gateway Time-out")
                        || message.contains("Bad Gateway") // 502 / Bad Gateway
                        || message.contains("32019") // futures: Order price cannot be more than 103% or less than 97%
                        || message.contains("35014") // swap: {"error_message":"Order price is not within limit","result":"FALSE","error_code":"35014","order_id":"-1"}

                    // Code: 20018, translation: Order price differ more than 5% from the price in the last minute
                ) { // ExchangeException
                    return NextStep.CONTINUE;
                }
                // Api V3:
                // futures: 32014 : Positions that you are squaring exceeded the total no. of contracts allowed to close
                // swap: 35010 : Position closing too large,Closing position size larger than available size
                if (message.contains("32014") || message.contains("35010")) {
                    try {
                        fetchPosition();
                    } catch (Exception e1) {
                        logger.info("FetchPositionError:", e1);
                    }
                    return NextStep.CONTINUE;
                }
            }
            return NextStep.BREAK; // no retry by default
        }
    }

    public void deferredPlaceOrderOnSignal(PlaceOrderArgs currPlaceOrderArgs) {
        final String counterName = currPlaceOrderArgs.getCounterName();
        if (this.placeOrderArgsRef.compareAndSet(null, currPlaceOrderArgs)) {
            setMarketState(MarketState.WAITING_ARB);
            final String msgStart = String.format("#%s MT2 deferred placing %s", counterName, currPlaceOrderArgs);
            tradeLogger.info(msgStart);
            logger.info(msgStart);
            final Settings s = settingsRepositoryService.getSettings();
            if (s.getArbScheme() == ArbScheme.CON_B_O_PORTIONS) {
                final String msg = String.format("CON_B_O_PORTIONS: min to start nt_usd=%s, maxPortion=%s",
                        s.getConBoPortions().getMinNtUsdToStartOkex(),
                        s.getConBoPortions().getMaxPortionUsdOkex());
                tradeLogger.info(msg);
                logger.info(msg);
            }
        } else {
            setMarketState(MarketState.ARBITRAGE);
            final String errorMessage = String.format("#%s double placing-order for MT2. New:%s.", counterName, currPlaceOrderArgs);
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

    private TradeResponse placeNonTakerOrder(Long tradeId, Order.OrderType orderType, BigDecimal tradableAmount, BestQuotes bestQuotes,
                                             boolean isMoving, @NotNull SignalType signalType, PlacingType placingSubType, String counterName,
                                             boolean pricePlanOnStart, Integer portionsQty, Integer portionsQtyMax, String counterNameWithPortion)
            throws Exception {
        final TradeResponse tradeResponse = new TradeResponse();

        BigDecimal thePrice;

        if (tradableAmount.compareTo(BigDecimal.ZERO) == 0) {

            tradeResponse.setErrorCode("Not enough amount left. amount=" + tradableAmount.toPlainString());

        } else {
            // USING REST API
            orderType = adjustOrderType(orderType, tradableAmount);

            final String message = Utils.getTenAskBid(getOrderBook(), counterNameWithPortion,
                    String.format("Before %s placing, orderType=%s,", placingSubType, Utils.convertOrderTypeName(orderType)));
            logger.info(message);
            tradeLogger.info(message);

            if (placingSubType == null || placingSubType == PlacingType.TAKER) {
                tradeLogger.warn("placing maker, but subType is " + placingSubType);
                warningLogger.warn("placing maker, but subType is " + placingSubType);
            }

            String obTimestamp = " ";
            int attempt = 0;
            int maxAttempts = settingsRepositoryService.getSettings().getOkexPostOnlyArgs().getPostOnlyAttempts();
            while (attempt++ < maxAttempts) {
                final OkexPostOnlyArgs poArgs = settingsRepositoryService.getSettings().getOkexPostOnlyArgs();
                maxAttempts = poArgs.getPostOnlyAttempts();
                if (attempt > 1 && poArgs.getPostOnlyBetweenAttemptsMs() > 0) {
                    Thread.sleep(poArgs.getPostOnlyBetweenAttemptsMs());
                }

                final InstrumentDto instrumentDto = instrDtos.get(0);
                OrderBook orderBook = getOrderBook();
                // workaround. if OrderBook was not updated between attempts.
                if (obTimestamp.equals(orderBook.getTimeStamp().toInstant().toString())) {
                    final String msg = String.format("#%s/%s orderBook timestamp=%s is the same. Updating orderBook...",
                            counterNameWithPortion, attempt, obTimestamp);
                    tradeLogger.info(msg);
                    logger.info(msg);

                    final CurrencyPair currencyPair = instrumentDto.getCurrencyPair();
                    orderBook = fplayOkexExchange.getPublicApi().getInstrumentBook(instrumentDto.getInstrumentId(), currencyPair);
                    if (orderBook.getTimeStamp() == null) {
                        orderBook = new OrderBook(new Date(), orderBook.getAsks(), orderBook.getBids());
                    }
                }
                obTimestamp = orderBook.getTimeStamp().toInstant().toString();
                final String msgTimestamp = String.format("#%s/%s orderBook timestamp=%s.", counterNameWithPortion, attempt, obTimestamp);
                tradeLogger.info(msgTimestamp);
                logger.info(msgTimestamp);
                thePrice = createBestPrice(orderType, placingSubType, orderBook, getContractType());

                if (thePrice.compareTo(BigDecimal.ZERO) == 0) {
                    tradeResponse.setErrorCode("The new price is 0 ");
                } else {

                    final Instant startReq = Instant.now();
                    boolean postOnly = false;
                    FuturesOrderTypeEnum futuresOrderType = FuturesOrderTypeEnum.NORMAL_LIMIT;
                    if (placingSubType == PlacingType.MAKER || placingSubType == PlacingType.MAKER_TICK) {
                        final boolean theLastAndExcepted = (attempt == maxAttempts && poArgs.getPostOnlyWithoutLast());
                        if (poArgs.getPostOnlyEnabled() && !theLastAndExcepted) {
                            futuresOrderType = FuturesOrderTypeEnum.POST_ONLY;
                            postOnly = true;
                        }
                    }
                    final String msgPlacing = String.format("#%s/%s placing order inst=%s, t=%s, p=%s, a=%s, %s",
                            counterNameWithPortion, attempt,
                            instrumentDto.getInstrumentId(),
                            orderType,
                            thePrice,
                            tradableAmount,
                            futuresOrderType
                    );
                    tradeLogger.info(msgPlacing);
                    logger.info(msgPlacing);
                    final OrderResultTiny orderResult = fplayOkexExchange.getPrivateApi().limitOrder(
                            instrumentDto.getInstrumentId(),
                            orderType,
                            thePrice,
                            tradableAmount,
                            leverage,
                            Collections.singletonList(futuresOrderType)
                    );
                    final String orderId = orderResult.getOrder_id();

                    final Instant endReq = Instant.now();

                    // metrics
                    final long waitingMarketMs = endReq.toEpochMilli() - startReq.toEpochMilli();
                    final Mon monPlacing = monitoringDataService.fetchMon(getName(), "placeOrder");
                    monPlacing.getWaitingMarket().add(BigDecimal.valueOf(waitingMarketMs));
                    if (waitingMarketMs > 5000) {
                        logger.warn(placingSubType + " okexPlaceOrder waitingMarketMs=" + waitingMarketMs);
                    }
                    monitoringDataService.saveMon(monPlacing);
                    metricsDictionary.putOkexPlacing(waitingMarketMs);
                    fplayTradeService.addOkexPlacingMs(tradeId, waitingMarketMs);

                    tradeResponse.setOrderId(orderId);

                    final LimitOrder resultOrder = placingSubType == PlacingType.MAKER || placingSubType == PlacingType.MAKER_TICK
                            // do check
                            ? checkOrderStatus(counterNameWithPortion, attempt, orderType, tradableAmount, thePrice,
                            orderId, 1, postOnly)
                            // no check for taker
                            : new LimitOrder(orderType, tradableAmount, okexContractType.getCurrencyPair(), orderId, new Date(),
                                    thePrice, BigDecimal.ZERO, BigDecimal.ZERO, OrderStatus.PENDING_NEW);

                    tradeResponse.setLimitOrder(resultOrder);
                    final FplayOrder fplayOrder = new FplayOrder(this.getMarketId(), tradeId, counterName, resultOrder, bestQuotes, placingSubType, signalType,
                            portionsQty, portionsQtyMax);
                    addOpenOrder(fplayOrder);
                    if (resultOrder.getStatus() == OrderStatus.FILLED) {
                        addCheckOoToFree();
                    }

                    String placingTypeString = (isMoving ? "Moving3:Moved:" : "") + placingSubType;

                    final boolean cnlBecausePostOnly = resultOrder.getStatus() == OrderStatus.CANCELED;
                    if (!isMoving || cnlBecausePostOnly) {
                        final String msg = String.format("#%s/%s %s %s %s amount=%s, quote=%s, orderId=%s, status=%s",
                                counterNameWithPortion,
                                attempt,
                                cnlBecausePostOnly ? "CANCELED Post only" : "",
                                placingTypeString,
                                Utils.convertOrderTypeName(orderType),
                                tradableAmount.toPlainString(),
                                thePrice,
                                orderId,
                                orderResult.isResult());

                        tradeLogger.info(msg);
                        logger.info(msg);
                    }
                    boolean firstPortion = portionsQty == null || portionsQty == 1;
                    if (pricePlanOnStart && firstPortion) { // set oPricePlanOnStart for non-Taker
                        persistenceService.getDealPricesRepositoryService().setOPricePlanOnStart(tradeId, thePrice);
                    }

                    persistenceService.getDealPricesRepositoryService().setSecondOpenPrice(tradeId, thePrice);

                    orderIdToSignalInfo.put(orderId, bestQuotes);

                    writeLogPlaceOrder(orderType, tradableAmount,
                            placingTypeString,
                            thePrice, orderId,
                            (resultOrder.getStatus() != null) ? resultOrder.getStatus().toString() : null,
                            counterNameWithPortion + "/" + attempt,
                            orderResult);

                    if (!cnlBecausePostOnly) {
                        break;
                    } else {
                        tradeResponse.setLimitOrder(null);
                        tradeResponse.addCancelledOrder(resultOrder);
                        // continue;
                    }
                }
            }
        }

        return tradeResponse;
    }

    private LimitOrder updateOrderDetails(String counterNameWithPortion, LimitOrder limitOrder, int checkAttempt)
            throws IOException {
        final String orderId = limitOrder.getId();
        final Collection<Order> order = getApiOrders(new String[]{orderId});
        final String preStr = String.format("#%s updateOrderDetails id=%s: ", counterNameWithPortion, orderId);
        if (order.size() > 0) {
            Order theOrder = order.iterator().next();
            final String msg = String.format("%s status=%s, filled=%s.", preStr, theOrder.getStatus(), theOrder.getCumulativeAmount());
            tradeLogger.info(msg);
            logger.info(msg);
            return (LimitOrder) theOrder;
        }
        final String warn = preStr + " no orders in response";
        if (needRepeatCheckOrderStatus(checkAttempt, warn)) {
            updateOrderDetails(counterNameWithPortion, limitOrder, checkAttempt + 1);
        }
        return limitOrder;
    }

    private LimitOrder checkOrderStatus(String counterNameWithPortion, int attemptCount, OrderType orderType,
                                        BigDecimal tradableAmount, BigDecimal thePrice, String orderId, int checkAttempt, boolean postOnly) throws IOException {

        final Collection<Order> order = getApiOrders(new String[]{orderId});
        final String preStr = String.format("#%s/%s checkAfterPlacing(check=%s) id=%s: ", counterNameWithPortion, attemptCount, checkAttempt, orderId);
        if (order.size() > 0) {
            Order theOrder = order.iterator().next();
            final OrderStatus theOrderStatus = theOrder.getStatus();
            final String msg = String.format("%s status=%s, filled=%s. postOnly=%s",
                    preStr, theOrderStatus, theOrder.getCumulativeAmount(), postOnly);
            tradeLogger.info(msg);
            logger.info(msg);
            if (postOnly && (theOrderStatus == OrderStatus.NEW || theOrderStatus == OrderStatus.PENDING_NEW)) {
                final String warn = String.format("%s postOnly with status %s.", preStr, theOrderStatus);
                if (needRepeatCheckOrderStatus(checkAttempt, warn))
                    return checkOrderStatus(counterNameWithPortion, attemptCount, orderType, tradableAmount, thePrice, orderId,
                            checkAttempt + 1, postOnly);
            }
            return (LimitOrder) theOrder;
        }

        final String warn = preStr + "no orders in response";
        if (needRepeatCheckOrderStatus(checkAttempt, warn)) {
            return checkOrderStatus(counterNameWithPortion, attemptCount, orderType, tradableAmount, thePrice, orderId,
                    checkAttempt + 1, postOnly);
        }

        return new LimitOrder(orderType, tradableAmount, okexContractType.getCurrencyPair(), orderId, new Date(),
                thePrice, BigDecimal.ZERO, BigDecimal.ZERO, OrderStatus.PENDING_NEW);
    }

    private boolean needRepeatCheckOrderStatus(int checkAttempt, String warn) throws IOException {
        if (checkAttempt < 2) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                logger.error("Sleep interrupted", e);
            }
            final String warnTrue = warn + "do repeat; checkAttempt=" + checkAttempt;
            tradeLogger.info(warnTrue);
            logger.info(warnTrue);
            return true;
        }
        final String warnFalse = warn + "no checkAttempt left";
        tradeLogger.info(warnFalse);
        logger.info(warnFalse);
        return false;
    }

    private void writeLogPlaceOrder(Order.OrderType orderType, BigDecimal tradeableAmount,
                                    String placingType, BigDecimal thePrice, String orderId,
                                    String status, String counterForLogs, OrderResultTiny rawResult) {

        final String message = String.format("#%s/end: %s %s amount=%s, quote=%s, orderId=%s, status=%s; rawResult=%s",
                counterForLogs,
                placingType, //isMoving ? "Moving3:Moved" : "maker",
                Utils.convertOrderTypeName(orderType),
                tradeableAmount.toPlainString(),
                thePrice,
                orderId,
                status,
                rawResult);
        tradeLogger.info(message);
        logger.info(message);
        ordersLogger.info(message);
    }

    private Order.OrderType adjustOrderType(Order.OrderType orderType, BigDecimal tradeableAmount) {
        final Pos position = getPos();
        BigDecimal pLongBalance = (position != null && position.getPositionLong() != null)
                ? position.getPositionLong()
                : BigDecimal.ZERO;
        BigDecimal pShortBalance = (position != null && position.getPositionShort() != null)
                ? position.getPositionShort()
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

    private Order fetchOrderInfo(String orderId, String counterForLogs) {
        Order order = null;
        try {
            //NOT implemented yet
            final Collection<Order> orderCollection = getApiOrders(new String[]{orderId});
            if (!orderCollection.isEmpty()) {
                order = orderCollection.iterator().next();
                tradeLogger.info(String.format("#%s OrderInfo id=%s, status=%s, filled=%s",
                        counterForLogs,
                        orderId,
                        order.getStatus(),
                        order.getCumulativeAmount()));
            }
        } catch (Exception e) {
            logger.error("on fetch order info by id=" + orderId, e);
        }
        return order;
    }

    @Override
    public TradeService getTradeService() {
        return exchange.getTradeService();
    }

    private volatile CounterToDiff counterToDiff = new CounterToDiff(null, null);

    /**
     * Moves Taker orders as well.
     */
    @Override
    public MoveResponse moveMakerOrder(FplayOrder fOrderToCancel, BigDecimal bestMarketPrice, Object... reqMovingArgs) {
        final LimitOrder limitOrder = LimitOrder.Builder.from(fOrderToCancel.getOrder()).build();
        final SignalType signalType = fOrderToCancel.getSignalType() != null ? fOrderToCancel.getSignalType() : getArbitrageService().getSignalType();

        final Long tradeId = fOrderToCancel.getTradeId();
        final String counterWithPortion = fOrderToCancel.getCounterWithPortion();
        if (limitOrder.getStatus() == Order.OrderStatus.CANCELED || limitOrder.getStatus() == Order.OrderStatus.FILLED) {
            tradeLogger.error(String.format("#%s do not move ALREADY_CLOSED order", counterWithPortion));
            return new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, "", null, null, fOrderToCancel);
        }
        if (getMarketState() == MarketState.PLACING_ORDER) { // !arbitrageService.getParams().getOkCoinOrderType().equals("maker")
            return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "no moving for taker");
        }

        arbitrageService.setSignalType(signalType);

        final MarketState savedState = getMarketState();
        setMarketState(MarketState.MOVING);
        Instant startMoving = Instant.now();

        MoveResponse response;
        try {
            // IT doesn't support moving
            // Do cancel and place
            BestQuotes bestQuotes = orderIdToSignalInfo.get(limitOrder.getId());
            // TODO remove orderIdToSignalInfo
//            final BestQuotes bestQuotes = fOrderToCancel.getBestQuotes();

            // 1. cancel old order
            // 2. We got result on cancel(true/false), but double-check status of an old order
            CancelOrderRes cancelOrderRes = cancelOrderWithCheck(limitOrder.getId(), "Moving1:cancelling:", "Moving2:cancelStatus:", counterWithPortion);
            if (cancelOrderRes.getOrder().getId().equals("empty")) {
                return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "Failed to check status of cancelled order on moving id=" + limitOrder.getId());
            }
            LimitOrder cancelledOrder = cancelOrderRes.getOrder();

            final FplayOrder cancelledFplayOrd = FplayOrderUtils.updateFplayOrder(fOrderToCancel, cancelledOrder);
            final LimitOrder cancelledLimitOrder = (LimitOrder) cancelledFplayOrd.getOrder();
            orderRepositoryService.updateSync(cancelledFplayOrd);

            // 3. Already closed?
            final boolean alreadyFilled = cancelledLimitOrder.getStatus() == OrderStatus.FILLED;
            final boolean cancelFailed = !cancelOrderRes.getCancelSucceed(); // WORKAROUND: CANCELLED, but was cancelled/placedNew on a previous moving-iteration
            // workaround. PlacingType == null often occurs with old orders. If res was never answered true then skip it.
            final boolean oldOrder = cancelOrderRes.res != null && !cancelOrderRes.res && cancelledFplayOrd.getPlacingType() == null;
            if (cancelFailed || alreadyFilled || oldOrder) {
                response = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, "", null, null,
                        cancelledFplayOrd);

                final String logString = String.format("#%s %s %s status=%s,amount=%s/%s,quote=%s/%s,id=%s,lastException=%s",
                        counterWithPortion,
                        "Moving3:Already closed:",
                        limitOrder.getType(),
                        cancelledLimitOrder.getStatus(),
                        limitOrder.getTradableAmount(), cancelledLimitOrder.getCumulativeAmount(),
                        limitOrder.getLimitPrice().toPlainString(), cancelledLimitOrder.getAveragePrice(),
                        limitOrder.getId(),
                        null);
                tradeLogger.info(logString);
                logger.info(logString);

                // 3. Place order
            } else if (cancelledOrder.getStatus() == OrderStatus.CANCELED) {
                TradeResponse tradeResponse = new TradeResponse();
                if (cancelledFplayOrd.getPlacingType() == null) {
                    getTradeLogger().warn("WARNING: PlaceType is null." + cancelledFplayOrd);
                }

                tradeResponse = finishMovingSync(tradeId, limitOrder, signalType, bestQuotes, cancelledLimitOrder, tradeResponse, cancelledFplayOrd);

                if (tradeResponse.getLimitOrder() != null) {
                    final LimitOrder newOrder = tradeResponse.getLimitOrder();
                    final FplayOrder newFplayOrder = cancelledFplayOrd.cloneWithUpdate(newOrder);
                    response = new MoveResponse(MoveResponse.MoveOrderStatus.MOVED_WITH_NEW_ID, tradeResponse.getOrderId(),
                            newOrder, newFplayOrder, cancelledFplayOrd);
                } else {
                    final String warnMsg = String.format("#%s Can not move orderId=%s, ONLY_CANCEL!!!",
                            counterWithPortion, limitOrder.getId());
                    warningLogger.info(warnMsg);
                    logger.info(warnMsg);
                    response = new MoveResponse(MoveResponse.MoveOrderStatus.ONLY_CANCEL, tradeResponse.getOrderId(),
                            null, null, cancelledFplayOrd);
                }

            } else {
                response = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "wrong status on cancel/new: " + cancelledLimitOrder.getStatus());
            }
        } finally {
            setMarketState(savedState, counterWithPortion);
        }

        { // mon
            Instant lastEnd = Instant.now();
            Mon mon = monitoringDataService.fetchMon(getName(), "moveMakerOrder");
            if (reqMovingArgs != null && reqMovingArgs.length == 1 && reqMovingArgs[0] != null) {
                Instant lastObTime = (Instant) reqMovingArgs[0];
                long beforeMs = startMoving.toEpochMilli() - lastObTime.toEpochMilli();
                mon.getBefore().add(BigDecimal.valueOf(beforeMs));
                if (beforeMs > 5000) {
                    logger.warn("okex beforeMoveOrderMs=" + beforeMs);
                }
            }

            long wholeMovingMs = lastEnd.toEpochMilli() - startMoving.toEpochMilli();
            mon.getWholePlacing().add(new BigDecimal(wholeMovingMs));
            if (wholeMovingMs > 30000) {
                logger.warn("okex wholeMovingMs=" + wholeMovingMs);
            }
            mon.incCount();
            monitoringDataService.saveMon(mon);
            metricsDictionary.putOkexMovingWhole(wholeMovingMs);
        }

        return response;
    }

    @Override
    protected BigDecimal createBestTakerPrice(OrderType orderType, OrderBook orderBook) {
        final BigDecimal okexFakeTakerDev = settingsRepositoryService.getSettings().getOkexFakeTakerDev();
        final BigDecimal priceForTaker = Utils.createPriceForTaker(orderType, priceRange, okexFakeTakerDev);
        final BigDecimal thePrice = priceForTaker.setScale(okexContractType.getScale(), RoundingMode.HALF_UP); // .00 -> .000 for eth
        final String ftpdDetails = String.format("The fake taker price is %s; %s", thePrice.toPlainString(), priceRange);
        getTradeLogger().info(ftpdDetails);
        logger.info(ftpdDetails);
        return thePrice;
    }

    private TradeResponse finishMovingSync(Long tradeId, LimitOrder limitOrder, SignalType signalType, BestQuotes bestQuotes,
            Order cancelledOrder, TradeResponse tradeResponse, FplayOrder cnlOrder) {
        final String counterForLogs = cnlOrder.getCounterWithPortion();
        PlacingType placingType = cnlOrder.getPlacingType();
        BigDecimal newAmount = limitOrder.getTradableAmount().subtract(cancelledOrder.getCumulativeAmount())
                .setScale(0, RoundingMode.HALF_UP);

        int attemptCount = 0;
        while (attemptCount < MAX_ATTEMPTS_FOR_MOVING) {
            try {
                attemptCount++;
                if (attemptCount > 1) {
                    Thread.sleep(500 * attemptCount);
                }

                tradeLogger.info(String.format("#%s/%s Moving3:placingNew a=%s, placingType=%s, orderType=%s", counterForLogs, attemptCount, newAmount,
                        placingType, limitOrder.getType()));

                PlacingType okexPlacingType = placingType;
                if (okexPlacingType == null) {
                    placingType = persistenceService.getSettingsRepositoryService().getSettings().getOkexPlacingType();
                }

                if (placingType != PlacingType.TAKER) {
                    tradeResponse = placeNonTakerOrder(tradeId, limitOrder.getType(), newAmount, bestQuotes, true, signalType, okexPlacingType,
                            cnlOrder.getCounterName(),
                            false, cnlOrder.getPortionsQty(), cnlOrder.getPortionsQtyMax(), counterForLogs);
                } else {
                    tradeResponse = takerOrder(tradeId, limitOrder.getType(), newAmount, bestQuotes, signalType, cnlOrder.getCounterName(),
                            cnlOrder.getPortionsQty(), cnlOrder.getPortionsQtyMax(), counterForLogs);
                }

                if (tradeResponse.getErrorCode() != null && tradeResponse.getErrorCode().startsWith("Insufficient")) {
                    tradeLogger.info(String.format("#%s/%s Moving3:Failed %s amount=%s,quote=%s,id=%s,attempt=%s. Error: %s",
                            counterForLogs, attemptCount,
                            limitOrder.getType(), //== Order.OrderType.BID ? "BUY" : "SELL"
                            limitOrder.getTradableAmount(),
                            limitOrder.getLimitPrice().toPlainString(),
                            limitOrder.getId(),
                            attemptCount,
                            tradeResponse.getErrorCode()));
                }
                if (tradeResponse.getErrorCode() != null && tradeResponse.getErrorCode().equals(TradeResponse.TAKER_WAS_CANCELLED_MESSAGE)) {
                    final BigDecimal filled = tradeResponse.getCancelledOrders().get(0).getCumulativeAmount();
                    newAmount = newAmount.subtract(filled);
                    continue;
                }
                if (tradeResponse.getErrorCode() != null && tradeResponse.getErrorCode().equals(TradeResponse.TAKER_BECAME_LIMIT)) {
                    break;
                }
                if (tradeResponse.getErrorCode() != null) {
                    final String errMsg = String.format("#%s/%s Warning: Moving3:placingError %s", counterForLogs, attemptCount, tradeResponse.getErrorCode());
                    logger.error(errMsg);
                    tradeLogger.error(errMsg);
                    tradeResponse.setOrderId(null);
                    continue;
                }

                // Order placed successfully. Exit loop.
                break;

            } catch (Exception e) {
                logger.error("#{}/{} Moving3:placingError", counterForLogs, attemptCount, e);
                tradeLogger.error(String.format("#%s/%s Warning: Moving3:placingError %s", counterForLogs, attemptCount, e.toString()));
                tradeResponse.setOrderId(null);
                tradeResponse.setErrorCode(e.getMessage());

                final NextStep nextStep = handlePlacingException(e, tradeResponse);
                if (nextStep == NextStep.CONTINUE) {
                    continue;
                }
                break;
            }
        }
        return tradeResponse;
    }

    /**
     * Loop until status CANCELED or FILLED.
     */
    private LimitOrder getFinalOrderInfoSync(String orderId, String counterName, String logInfoId) {
        LimitOrder result = null;
        int attemptCount = 0;
        final Instant start = Instant.now();
        while (Duration.between(start, Instant.now()).getSeconds() < MAX_SEC_CHECK_AFTER_TAKER) {
            attemptCount++;
            try {

                Thread.sleep(200);

                final Collection<Order> order = getApiOrders(new String[]{orderId});
                result = (LimitOrder) order.iterator().next();
                tradeLogger.info(String.format("#%s/%s %s id=%s, status=%s, filled=%s",
                        counterName,
                        attemptCount,
                        logInfoId,
                        result.getId(),
                        result.getStatus(),
                        result.getCumulativeAmount()));

                if (result.getStatus() == Order.OrderStatus.CANCELED
                        || result.getStatus() == Order.OrderStatus.FILLED) {
                    break;
                }
            } catch (Exception e) {
                logger.error("#{}/{} error on get order status", counterName, attemptCount, e);
                tradeLogger.error(String.format("#%s/%s error on get order status: %s", counterName, attemptCount, e.toString()));
            }
        }
        return result;
    }

    @Override
    public List<LimitOrder> cancelAllOrders(FplayOrder stub, String logInfoId, boolean beforePlacing, boolean withResetWaitingArb) {
        List<LimitOrder> res = new ArrayList<>();
        final String counterForLogs = stub.getCounterWithPortion();

        getOnlyOpenOrders().forEach(order -> {

            final String orderId = order.getId();
            int attemptCount = 0;
            while (attemptCount < MAX_ATTEMPTS_CANCEL) {
                attemptCount++;
                try {
                    if (attemptCount > 1) {
                        Thread.sleep(1000);
                    }
                    final OrderResultTiny result = fplayOkexExchange.getPrivateApi().cnlOrder(instrDtos.get(0).getInstrumentId(), orderId);
                    final String translatedError = OkexOrderConverter.getErrorCodeTranslation(result);
                    final String msg = String.format("#%s/%s %s id=%s,res=%s,code=%s,details=%s(%s)",
                            counterForLogs, attemptCount,
                            logInfoId,
                            orderId,
                            result.isResult(),
                            result.getError_code(),
                            result.getError_message(),
                            translatedError);
                    tradeLogger.info(msg);
                    logger.info(msg);

                    if (result.isResult()
                            || result.getError_code().contains("32004") // result.getError_message()=="You have not uncompleted order at the moment"
                            || translatedError.contains("order does not exist")
                            || result.getError_message().contains("rder does not exist")) {

                        if (beforePlacing) { // need the real cumulativeAmount.
                            final LimitOrder updated = updateOrderDetails(counterForLogs, order, 1);
                            res.add(updated);
                        } else {
                            order.setOrderStatus(OrderStatus.CANCELED); // can be FILLED, but it's ok here.
                            res.add(order);
                        }
                        break;
                    }
                } catch (Exception e) {
                    logger.error("#{}/{} error cancel maker order", counterForLogs, attemptCount, e);
                    tradeLogger.error(String.format("#%s/%s error cancel maker order: %s", counterForLogs, attemptCount, e.toString()));
                }
            }
        });

        updateFplayOrdersToCurrStab(res, stub);
        final boolean cnlSuccess = res.size() > 0;
        if (beforePlacing && cnlSuccess) {
            final String msg = String.format("#%s (beforePlacing && cnlSuccess) changing to PLACING_ORDER...", stub.getCounterWithPortion());
            getTradeLogger().info(msg);
            logger.info(msg);
            setMarketState(MarketState.PLACING_ORDER);
        } else {
            addCheckOoToFree();
        }

        return res;
    }

//    private String getErrorCodeTranslationV1(OkCoinTradeResult result) {
//        String errorCodeTranslation = "";
//        if (result != null && result.getDetails() != null && result.getDetails().startsWith("Code: ")) { // Example: result.getDetails() == "Code: 20015"
//            String errorCode = result.getDetails().substring(6);
//            try {
//                errorCodeTranslation = OkCoinUtils.getErrorMessage(Integer.parseInt(errorCode));
//            } catch (NumberFormatException e) {
//                logger.error("can not translate code " + errorCode);
//            }
//        }
//        return errorCodeTranslation;
//    }

    @NotNull
    public OrderResultTiny cancelOrderSyncFromUi(String orderId, String logInfoId) {
        final String counterForLogs = getCounterName();
        OrderResultTiny result = new OrderResultTiny(false, orderId);
        try {
            result = fplayOkexExchange.getPrivateApi().cnlOrder(instrDtos.get(0).getInstrumentId(), orderId);
            if (result == null) {
                tradeLogger.info(String.format("#%s %s id=%s, no response", counterForLogs, logInfoId, orderId));
            } else {
                tradeLogger.info(String.format("#%s %s id=%s,res=%s(%s),code=%s,details=%s(%s)",
                        counterForLogs,
                        logInfoId,
                        orderId,
                        result.isResult(),
                        result.isResult() ? "cancelled" : "probably already filled",
                        result.getError_code(),
                        result.getError_message(),
                        OkexOrderConverter.getErrorCodeTranslation(result)));
                if (!result.isResult()) {
                    updateOOStatuses();
                }
            }

        } catch (Exception e) {
                logger.error("#{} error cancel maker order", counterForLogs, e);
                tradeLogger.error(String.format("#%s error cancel maker order: %s", counterForLogs, e.toString()));
                final Order order = fetchOrderInfo(orderId, counterForLogs);
                if (order != null) {
                    if (order.getStatus() == Order.OrderStatus.CANCELED) {
                        result = new OrderResultTiny(true, orderId);
                    }
                    if (order.getStatus() == Order.OrderStatus.FILLED) {
                        result = new OrderResultTiny(false, orderId);
                    }
                }
        }
        return result;
    }

    @Data
    class CancelOrderRes {

        private final Boolean cancelSucceed;
        private final Boolean res;
        private final LimitOrder order;
    }

    private CancelOrderRes cancelOrderWithCheck(String orderId, String logInfoId1, String logInfoId2, String counterForLogs) {
        LimitOrder resOrder = new LimitOrder.Builder(OrderType.ASK, okexContractType.getCurrencyPair()).id("empty").build(); //workaround

        boolean cancelSucceed = false;
        Boolean res = null;
        int attemptCount = 0;
        while (attemptCount < MAX_ATTEMPTS_CANCEL) { //90 attempts with 1 sec =>  1.5 min
            attemptCount++;
            try {
                if (attemptCount > 1) {
                    Thread.sleep(1000);
                }

                // 1. Cancel request
                if (!cancelSucceed) {
                    final OrderResultTiny result = fplayOkexExchange.getPrivateApi().cnlOrder(instrDtos.get(0).getInstrumentId(), orderId);

                    if (result == null) {
                        tradeLogger.info(String.format("#%s/%s %s id=%s, no response", counterForLogs, attemptCount, logInfoId1, orderId));
                        continue;
                    }

                    tradeLogger.info(String.format("#%s/%s %s id=%s,res=%s(%s),code=%s,details=%s(%s)",
                            counterForLogs, attemptCount,
                            logInfoId1,
                            orderId,
                            result.isResult(),
                            result.isResult() ? "cancelled" : "probably already filled",
                            result.getError_code(),
                            result.getError_message(),
                            OkexOrderConverter.getErrorCodeTranslation(result)));

                    if (result.isResult()) {
                        cancelSucceed = true;
                    }
                    if (res == null) {
                        res = result.isResult();
                    }
                }

                // 2. Status check
                final Collection<Order> order = getApiOrders(new String[]{orderId});
                if (order.size() == 0) {
                    tradeLogger.info(String.format("#%s/%s %s id=%s no orders in response",
                            counterForLogs,
                            attemptCount,
                            logInfoId2,
                            orderId));
                } else {
                    Order cancelledOrder = order.iterator().next();
                    tradeLogger.info(String.format("#%s/%s %s id=%s, status=%s, filled=%s",
                            counterForLogs,
                            attemptCount,
                            logInfoId2,
                            cancelledOrder.getId(),
                            cancelledOrder.getStatus(),
                            cancelledOrder.getCumulativeAmount()));

                    if (cancelledOrder.getStatus() == Order.OrderStatus.CANCELED) {
                        cancelSucceed = true;
                        resOrder = (LimitOrder) cancelledOrder;
                        break;
                    }
                    if (cancelledOrder.getStatus() == Order.OrderStatus.FILLED) {
                        resOrder = (LimitOrder) cancelledOrder;
                        break;
                    }
                }

            } catch (Exception e) {
                logger.error("#{}/{} error cancel maker order", counterForLogs, attemptCount, e);
                tradeLogger.error(String.format("#%s/%s error cancel maker order: %s", counterForLogs, attemptCount, e.toString()));
            }
        }
        return new CancelOrderRes(cancelSucceed, res, resOrder);
    }

    @Override
    public String getPositionAsString() {
        return null;
    }

    @Override
    protected Completable recalcLiqInfo() {
        return Completable.fromAction(() -> {
            final Pos position = this.pos.get();
            if (position == null || position.getPositionLong() == null) {
                return; // not yet initialized
            }
            final BigDecimal pos = position.getPositionLong().subtract(position.getPositionShort());
            final BigDecimal oMrLiq = persistenceService.fetchGuiLiqParams().getOMrLiq();

            final AccountBalance accountInfoContracts = getAccount();
            final BigDecimal equity = accountInfoContracts.getELast();
            final BigDecimal margin = accountInfoContracts.getMargin();
            final BigDecimal m = markPrice;//ticker != null ? ticker.getLast() : null;

            if (equity != null && margin != null && oMrLiq != null
                    && position.getPriceAvgShort() != null
                    && position.getPriceAvgLong() != null
                    && m != null) {
                BigDecimal dql = null;
                String dqlString;
                if (pos.signum() > 0) {
                    if (margin.signum() > 0 && equity.signum() > 0) {
                        if (position.getLiquidationPrice() == null || position.getLiquidationPrice().signum() == 0) {
                            dqlString = String.format("o_DQL = na(o_pos=%s, o_margin=%s, o_equity=%s, L=0)", pos, margin, equity);
                            dql = null;
                        } else {
                            final BigDecimal L = position.getLiquidationPrice();
                            dql = m.subtract(L);
                            dqlString = String.format("o_DQL = m%s - L%s = %s", m, L, dql);
                        }
                    } else {
                        dqlString = String.format("o_DQL = na(o_pos=%s, o_margin=%s, o_equity=%s)", pos, margin, equity);
                        dql = null;
                        warningLogger.info(String.format("Warning.All should be > 0: o_pos=%s, o_margin=%s, o_equity=%s, qu_ent=%s/%s",
                                pos.toPlainString(), margin.toPlainString(), equity.toPlainString(),
                                position.getPriceAvgLong(), position.getPriceAvgShort()));
                    }

                } else if (pos.signum() < 0) {
                    if (margin.signum() > 0 && equity.signum() > 0) {
                        if (position.getLiquidationPrice() == null || position.getLiquidationPrice().signum() == 0) {
                            dqlString = String.format("o_DQL = na(o_pos=%s, o_margin=%s, o_equity=%s, L=0)", pos, margin, equity);
                            dql = null;
                        } else {
                            final BigDecimal L = position.getLiquidationPrice();
                            dql = L.subtract(m);
                            dqlString = String.format("o_DQL = L%s - m%s = %s", L, m, dql);
                        }
                    } else {
                        dqlString = String.format("o_DQL = na(o_pos=%s, o_margin=%s, o_equity=%s)", pos, margin, equity);
                        dql = null;
                        warningLogger.info(String.format("Warning.All should be > 0: o_pos=%s, o_margin=%s, o_equity=%s, qu_ent=%s/%s",
                                pos.toPlainString(), margin.toPlainString(), equity.toPlainString(),
                                position.getPriceAvgLong(), position.getPriceAvgShort()));
                    }

                } else {
                    dqlString = "o_DQL = na(pos=0)";
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

                final LiqParams liqParams = getPersistenceService().fetchLiqParams(getName());
                if (dql != null && dql.compareTo(DQL_WRONG) != 0) {
                    if (liqParams.getDqlMax().compareTo(dql) < 0) {
                        liqParams.setDqlMax(dql);
                    }
                    if (liqParams.getDqlMin().compareTo(dql) > 0) {
                        liqParams.setDqlMin(dql);
                    }
                }
                liqInfo.setDqlCurr(dql);

                if (dmrl != null) {
                    if (liqParams.getDmrlMax().compareTo(dmrl) < 0) {
                        liqParams.setDmrlMax(dmrl);
                    }
                    if (liqParams.getDmrlMin().compareTo(dmrl) > 0) {
                        liqParams.setDmrlMin(dmrl);
                    }
                }
                liqInfo.setDmrlCurr(dmrl);

                liqInfo.setDqlString(dqlString);
                liqInfo.setDmrlString(dmrlString);

                storeLiqParams(liqParams);
                updateDqlState();
            }
        });
    }

    @Override
    public void updateDqlState() {
        final GuiLiqParams guiLiqParams = persistenceService.fetchGuiLiqParams();
        final BigDecimal oDQLCloseMin = guiLiqParams.getODQLCloseMin();
        final BigDecimal oDQLOpenMin = guiLiqParams.getODQLOpenMin();
        final LiqInfo liqInfo = getLiqInfo();
        arbitrageService.getDqlStateService().updateOkexDqlState(oDQLOpenMin, oDQLCloseMin, liqInfo.getDqlCurr());
    }

    /**
     * @param orderType - only ASK, BID. There are no CLOSE_* types.
     */
    @Override
    public boolean checkLiquidationEdge(Order.OrderType orderType) {
        final GuiLiqParams guiLiqParams = persistenceService.fetchGuiLiqParams();
        final BigDecimal oDQLCloseMin = guiLiqParams.getODQLCloseMin();
        final BigDecimal oDQLOpenMin = guiLiqParams.getODQLOpenMin();
        final Pos position = getPos();
        final LiqInfo liqInfo = getLiqInfo();

        boolean isOk;

        if (liqInfo.getDqlCurr() == null) {
            isOk = true;
        } else {
            if (orderType.equals(Order.OrderType.BID)) { // LONG
                if ((position.getPositionLong().subtract(position.getPositionShort())).signum() > 0) {
                    isOk = liqInfo.getDqlCurr().compareTo(oDQLOpenMin) >= 0;
                } else {
                    isOk = true;
                }
            } else if (orderType.equals(Order.OrderType.ASK)) {
                if ((position.getPositionLong().subtract(position.getPositionShort()).signum() < 0)) {
                    isOk = liqInfo.getDqlCurr().compareTo(oDQLOpenMin) >= 0;
                } else {
                    isOk = true;
                }
            } else {
                throw new IllegalArgumentException("Wrong orderType " + orderType);
            }
        }

//        debugLog.debug(String.format("CheckLiqEdge:%s(p%s/%s/%s)", isOk,
//                position.getPositionLong().subtract(position.getPositionShort()),
//                liqInfo.getDqlCurr(),
//                oDQLOpenMin));

        if (!isOk) {
            slackNotifications.sendNotify(NotifyType.OKEX_DQL_OPEN_MIN,
                    String.format("%s DQL(%s) < DQL_open_min(%s)", NAME, liqInfo.getDqlCurr(), oDQLOpenMin));
        } else {
            slackNotifications.resetThrottled(NotifyType.OKEX_DQL_OPEN_MIN);
        }

        arbitrageService.getDqlStateService().updateOkexDqlState(oDQLOpenMin, oDQLCloseMin, liqInfo.getDqlCurr());

        return isOk;
    }

    public void resetWaitingArb(Boolean... btmWasStarted) {
        if (getMarketState() == MarketState.WAITING_ARB) {
            final PlaceOrderArgs placeOrderArgs = placeOrderArgsRef.getAndSet(null);
            if (btmWasStarted != null && btmWasStarted.length > 0 && btmWasStarted[0]) {
                // no changes for Vert
            } else {
                final Long tradeId = placeOrderArgs != null && placeOrderArgs.getTradeId() != null
                        ? placeOrderArgs.getTradeId()
                        : arbitrageService.getTradeId();
                final DealPrices dealPrices = dealPricesRepositoryService.findByTradeId(tradeId);
                final TradingMode tradingMode = dealPrices.getTradingMode();
                final boolean notAbortedOrUnstartedSignal = dealPricesRepositoryService.isNotAbortedOrUnstartedSignal(tradeId);
                if (notAbortedOrUnstartedSignal) {
                    final String s = placeOrderArgs != null ? placeOrderArgs.getCounterName() : "";
                    if (dealPrices.getDeltaName() == DeltaName.B_DELTA) {
                        cumPersistenceService.incUnstartedVert1(tradingMode);
                    } else {
                        cumPersistenceService.incUnstartedVert2(tradingMode);
                    }
                    dealPricesRepositoryService.setUnstartedSignal(tradeId);
                    getTradeLogger().info("#" + s + " Unstarted");
                }
            }
            setMarketState(MarketState.READY);
            final Long tradeId = placeOrderArgs != null ? placeOrderArgs.getTradeId() : arbitrageService.getLastTradeId();
            setFree(tradeId); // we should reset ArbitrageState to READY
        }
    }

    @Override
    protected boolean onReadyState() {
        if (settingsRepositoryService.getSettings().getArbScheme() == ArbScheme.CON_B_O_PORTIONS
                && placeOrderArgsRef.get() != null) {
            logger.warn("WAITING_ARB was reset by onReadyState");
            tradeLogger.warn("WAITING_ARB was reset by onReadyState");
            setMarketState(MarketState.WAITING_ARB);
            getApplicationEventPublisher().publishEvent(new NtUsdCheckEvent());
            return false;
        }
        ooHangedCheckerService.stopChecker();
        iterateOpenOrdersMoveAsync();
        return true;
    }

    @Override
    public ContractType getContractType() {
        return okexContractType;
    }

    @Override
    protected void iterateOpenOrdersMoveAsync(Object... iterateArgs) { // if synchronized then the queue for moving could be long
        ooSingleExecutor.execute(() -> {
                    final Boolean hadOoToMove = getMetricsDictionary().getOkexMovingIter().record(() ->
                            iterateOpenOrdersMoveSync(iterateArgs));
                    if (hadOoToMove) {
                        setFreeIfNoOpenOrders("FreeAfterIterateOpenOrdersMove", 1); // shows in logs the source of 'free after openOrders check'
//                        addCheckOoToFree();
                    }
                }
        );
    }

    /**
     * @return false when no orders to move, true otherwise.
     */
    private boolean iterateOpenOrdersMoveSync(Object... iterateArgs) { // if synchronized then the queue for moving could be long
        if (getMarketState() == MarketState.SYSTEM_OVERLOADED
                || getMarketState() == MarketState.PLACING_ORDER
                || isMarketStopped()
                || getArbitrageService().getDqlStateService().isPreliq()) {
            return false;
        }

        final List<FplayOrder> onlyOpenFplayOrders = getOnlyOpenFplayOrders();

        if (onlyOpenFplayOrders.size() > 0) {

            List<FplayOrder> resultOOList = new ArrayList<>();

            for (FplayOrder openOrder : onlyOpenFplayOrders) {
                if (openOrder == null) {
                    warningLogger.warn("OO is null. ");
                } else if (openOrder.getOrder().getType() == null) {
                    warningLogger.warn("OO type is null. " + openOrder.toString());
                    // keep the order
                    resultOOList.add(openOrder);
                } else if (openOrder.getOrderDetail().getOrderStatus() != Order.OrderStatus.NEW
                        && openOrder.getOrderDetail().getOrderStatus() != Order.OrderStatus.PENDING_NEW
                        && openOrder.getOrderDetail().getOrderStatus() != Order.OrderStatus.PARTIALLY_FILLED) {
                    // keep the order
                    resultOOList.add(openOrder);
                } else {
                    final boolean okexOutsideLimits = okexLimitsService.outsideLimits(openOrder.getLimitOrder().getType(), openOrder.getPlacingType(),
                            openOrder.getSignalType());
                    if (okexOutsideLimits) {
                        resultOOList.add(openOrder); // keep the same
                    } else {
                        try {
                            Instant lastObTime = (iterateArgs != null && iterateArgs.length > 0)
                                    ? (Instant) iterateArgs[0]
                                    : null;

                            final MoveResponse response = moveMakerOrderIfNotFirst(openOrder, lastObTime);
                            if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.ALREADY_CLOSED
                                    || response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.ONLY_CANCEL // do nothing on such exception
                                    || response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.EXCEPTION // do nothing on such exception
                            ) {
                                // update the status
                                final FplayOrder cancelledFplayOrder = response.getCancelledFplayOrder();
                                if (cancelledFplayOrder != null) {
                                    // update the order
                                    resultOOList.add(cancelledFplayOrder);
                                }
                            } else if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.MOVED_WITH_NEW_ID) {
                                final FplayOrder newOrder = response.getNewFplayOrder();
                                final FplayOrder cancelledOrder = response.getCancelledFplayOrder();

                                resultOOList.add(cancelledOrder);
                                resultOOList.add(newOrder);
                            } else {
                                resultOOList.add(openOrder); // keep the same
                            }
                            final FplayOrder cancelledOrder = response.getCancelledFplayOrder();
                            if (cancelledOrder != null && cancelledOrder.getOrder().getCumulativeAmount().signum() > 0) {
                                writeAvgPriceLog();
                            }

                        } catch (Exception e) {
                            // use default OO
                            warningLogger.warn("Error on moving: " + e.getMessage());
                            logger.warn("Error on moving", e);

                            resultOOList.add(openOrder); // keep the same
                        }
                    }
                }
            }

            updateFplayOrders(resultOOList);

            return true;
        } // OO-size > 0

        return false;
    }

    private void writeFilledOrderLog(List<LimitOrder> limitOrders, List<FplayOrder> openOrders) {
        for (LimitOrder u : limitOrders) {
            final OrderStatus s = u.getStatus();
            if (s == OrderStatus.FILLED || s == OrderStatus.CANCELED) {
                final FplayOrder f = openOrders.stream()
                        .filter(o -> o.getOrderId().equals(u.getId())).findFirst().orElse(null);
                final String msg = String.format("#%s %s by subscirption, id=%s",
                        f != null ? f.getCounterWithPortion() : null,
                        s, u.getId());
                tradeLogger.info(msg);
                logger.info(msg);
            }
        }
    }

    public void writeAvgPriceLog() {
        final Long tradeId = arbitrageService.getTradeId();
        if (tradeId != null) {
            final DealPrices dealPrices = arbitrageService.getDealPrices();
            final DealPrices.Details diffO = dealPrices.getDiffO();
            final BigDecimal avg = dealPrices.getOPriceFact().getAvg();
            final String counterForLogs = getCounterName(tradeId);
            if ((counterToDiff == null || counterToDiff.counter == null || !counterToDiff.counter.equals(counterForLogs)
                    || counterToDiff.diff.compareTo(diffO.val) != 0)
                    && avg != null && avg.signum() != 0) {
                tradeLogger.info(String.format("#%s %s", counterForLogs, diffO.str));
                counterToDiff = new CounterToDiff(counterForLogs, diffO.val);
            }
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

    public void setAvgPriceFromDbOrders(DealPrices dealPrices) {
        final Map<String, AvgPriceItem> itemMap = getPersistenceService().getDealPricesRepositoryService().getPItems(dealPrices.getTradeId(), getMarketId());
        final FactPrice avgPrice = dealPrices.getOPriceFact();
        avgPrice.getPItems().putAll(itemMap);
        getPersistenceService().getDealPricesRepositoryService().updateOkexFactPrice(dealPrices.getTradeId(), avgPrice);
    }

    /**
     * Workaround! <br> Request orders details. Use it before ending of a Round.
     *
     * @param dealPrices the object to be updated.
     */
    public void updateAvgPrice(String counterName, DealPrices dealPrices) {
        final FactPrice avgPrice = dealPrices.getOPriceFact();
        if (avgPrice.isZeroOrder()) {
            String msg = String.format("#%s WARNING: no updateAvgPrice for okex orders tradeId=%s. Zero order", counterName, dealPrices.getTradeId());
            tradeLogger.info(msg);
            logger.warn(msg);
            return;
        }

        final Map<String, AvgPriceItem> itemMap = getPersistenceService().getDealPricesRepositoryService().getPItems(dealPrices.getTradeId(), getMarketId());
        final Set<String> orderIds = itemMap.keySet().stream()
                .filter(orderId -> !orderId.equals(FactPrice.FAKE_ORDER_ID))
                .collect(Collectors.toSet());
        Collection<Order> orderInfos = new ArrayList<>();

        String[] orderIdsArray = orderIds.toArray(new String[0]);
        if (orderIdsArray.length == 0) {
            logger.info("updateAvgPrice skipped(no orders)");
        } else {
            logger.info("updateAvgPrice of " + Arrays.toString(orderIdsArray));
            for (int attempt = 0; attempt < 3; attempt++) { // about 11 sec
                long sleepTime = 200;
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    tradeLogger.error("Error on sleeping");
                }

                orderInfos = getOrderInfos(orderIdsArray, counterName,
                        attempt, "updateAvgPrice:", getTradeLogger());

                if (orderInfos.size() == orderIds.size()
                        && orderInfos.stream()
                        .filter(order -> order.getAveragePrice() != null)
                        .anyMatch(order -> order.getAveragePrice().signum() > 0)) {
                    break;
                }
            }

            orderInfos.forEach(order -> avgPrice.addPriceItem(order.getId(), order.getCumulativeAmount(), order.getAveragePrice(), order.getStatus()));
        }

        getPersistenceService().getDealPricesRepositoryService().updateOkexFactPrice(dealPrices.getTradeId(), avgPrice);
    }

    @Override
    public TradeResponse closeAllPos() {
        final TradeResponse tradeResponse = new TradeResponse();
        final StringBuilder res = new StringBuilder();

        final Pos position = getPos();

        final String counterForLogs = "closeAllPos";
        final String logInfoId = "closeAllPos:cancel";

        final Instant start = Instant.now();
        try {
            //synchronized (openOrdersLock)
            {

                final List<LimitOrder> onlyOpenOrders = getOnlyOpenOrders();
                boolean specialHandling = false;

                // specialHanding when openOrder && one openPos
                // "closePos/cancelOpenOrder" steps in different order
                if (onlyOpenOrders.size() == 1) {
                    if (position.getPositionLong().signum() > 0 && position.getPositionShort().signum() == 0) { // one
                        specialHandling = true;
                        final LimitOrder oo = onlyOpenOrders.get(0);
                        final String orderId;
                        if ((oo.getType() == OrderType.BID || oo.getType() == OrderType.EXIT_ASK)
                                && position.getPositionLong().compareTo(position.getLongAvailToClose()) == 0) {
                            // если pos == long, ордер == long (avail == holding)
                            orderId = ftpdLimitOrder(counterForLogs, okexContractType, OrderType.EXIT_BID, position.getPositionLong());
                            cancelOrderOnMkt(counterForLogs, logInfoId, res, oo);
                        } else {
                            // если pos === long, ордер == short (avail < holding),
                            cancelOrderOnMkt(counterForLogs, logInfoId, res, oo);
                            orderId = ftpdLimitOrder(counterForLogs, okexContractType, OrderType.EXIT_BID, position.getPositionLong());
                        }
                        tradeResponse.setOrderId(orderId);

                    } else if (position.getPositionShort().signum() > 0 && position.getPositionLong().signum() == 0) {
                        specialHandling = true;
                        final LimitOrder oo = onlyOpenOrders.get(0);
                        final String orderId;
                        if ((oo.getType() == OrderType.ASK || oo.getType() == OrderType.EXIT_BID)
                                && position.getPositionShort().compareTo(position.getShortAvailToClose()) == 0) {
                            // если pos == short, ордер == short (avail == holding),
                            orderId = ftpdLimitOrder(counterForLogs, okexContractType, OrderType.EXIT_ASK, position.getPositionShort());
                            cancelOrderOnMkt(counterForLogs, logInfoId, res, oo);
                        } else {
                            // если pos === short, ордер == long (avail < holding),
                            cancelOrderOnMkt(counterForLogs, logInfoId, res, oo);
                            orderId = ftpdLimitOrder(counterForLogs, okexContractType, OrderType.EXIT_ASK, position.getPositionShort());
                        }
                        tradeResponse.setOrderId(orderId);
                    }
                }

                if (!specialHandling) {
                    if (onlyOpenOrders.size() > 0) {
                        cancelAllOrdersOnMkt(onlyOpenOrders, counterForLogs, logInfoId, res);
                    }

                    String orderId = null;
                    if (position.getPositionLong().compareTo(position.getPositionShort()) >= 0) { // long >= short => long first
                        if (position.getPositionLong().signum() > 0) {
                            orderId = ftpdLimitOrder(counterForLogs, okexContractType, OrderType.EXIT_BID, position.getPositionLong());
                        }
                        if (position.getPositionShort().signum() > 0) {
                            orderId = ftpdLimitOrder(counterForLogs, okexContractType, OrderType.EXIT_ASK, position.getPositionShort());
                        }
                    } else { // short first
                        if (position.getPositionShort().signum() > 0) {
                            orderId = ftpdLimitOrder(counterForLogs, okexContractType, OrderType.EXIT_ASK, position.getPositionShort());
                        }
                        if (position.getPositionLong().signum() > 0) {
                            orderId = ftpdLimitOrder(counterForLogs, okexContractType, OrderType.EXIT_BID, position.getPositionLong());
                        }
                    }
                    if (orderId != null) {
                        tradeResponse.setOrderId(orderId);
                    }
                }
            }

            final Instant end = Instant.now();
            final String timeStr = String.format("%s (%d ms)", res.toString(), Duration.between(start, end).toMillis());
            tradeResponse.setErrorCode(timeStr);
        } catch (Exception e) {
            final Instant end = Instant.now();
            final String timeStr = String.format("; (%d ms)", Duration.between(start, end).toMillis());
            final String message = res.toString() + " Error: " + e.getMessage() + timeStr;
            tradeResponse.setErrorCode(message);

            final String logString = String.format("#%s %s closeAllPos: %s", counterForLogs, getName(), message);
            logger.error(logString, e);
            tradeLogger.error(logString);
            warningLogger.error(logString);
        }

        // update order info with correct counterName
        final String orderId = tradeResponse.getOrderId();
        if (orderId != null) {
            final Optional<Order> orderInfoAttempts = getOrderInfo(orderId, counterForLogs, 1, "closeAllPos:updateOrderStatus", getLogger());
            if (orderInfoAttempts.isPresent()) {
                Order orderInfo = orderInfoAttempts.get();
                final LimitOrder limitOrder = (LimitOrder) orderInfo;
                final FplayOrder closeOrder = new FplayOrder(getMarketId(), null, counterForLogs, limitOrder, null, PlacingType.TAKER, null);
                addOpenOrder(closeOrder);
            }
        }
        return tradeResponse;
    }

    /**
     * fake taker price deviation limit order
     */
    private String ftpdLimitOrder(String counterForLogs, OkexContractType okexContractType, OrderType orderType,
            BigDecimal amount)
            throws IOException {
        //TODO use https://www.okex.com/docs/en/#futures-close_all
        if (amount.signum() != 0) {

            final BigDecimal okexFakeTakerDev = settingsRepositoryService.getSettings().getOkexFakeTakerDev();
            final BigDecimal thePrice = Utils.createPriceForTaker(orderType, priceRange, okexFakeTakerDev);
            final String ftpdDetails = String.format("The fake taker price is %s; %s", thePrice.toPlainString(), priceRange);
            getTradeLogger().info(ftpdDetails);
            logger.info(ftpdDetails);

            final InstrumentDto instrumentDto = new InstrumentDto(okexContractType.getCurrencyPair(), okexContractType.getFuturesContract());
            final OrderResultTiny orderResult = fplayOkexExchange.getPrivateApi().limitOrder(
                    instrumentDto.getInstrumentId(),
                    orderType,
                    thePrice,
                    amount,
                    leverage,
                    Collections.singletonList(FuturesOrderTypeEnum.NORMAL_LIMIT)
            );
            final String orderId = orderResult.getOrder_id();

            tradeLogger.info(String.format("#%s id=%s,res=%s,code=%s,details=%s",
                    counterForLogs,
                    orderId,
                    orderResult.isResult(),
                    orderResult.getError_code(),
                    orderResult.getError_message()
            ));

            return orderId;
        }
        return "amount is 0";
    }

    private void cancelAllOrdersOnMkt(List<LimitOrder> onlyOpenOrders, String counterForLogs, String logInfoId,
            StringBuilder res) throws IOException {
        for (LimitOrder oo : onlyOpenOrders) {
            cancelOrderOnMkt(counterForLogs, logInfoId, res, oo);
        }
    }

    private void cancelOrderOnMkt(String counterForLogs, String logInfoId, StringBuilder res, LimitOrder order)
            throws IOException {
        final String orderId = order.getId();
        final OrderResultTiny result = fplayOkexExchange.getPrivateApi().cnlOrder(instrDtos.get(0).getInstrumentId(), orderId);

        final String translatedError = OkexOrderConverter.getErrorCodeTranslation(result);
        tradeLogger.info(String.format("#%s %s id=%s,res=%s,code=%s,details=%s(%s)",
                counterForLogs,
                logInfoId,
                orderId,
                result.isResult(),
                result.getError_code(),
                result.getError_message(),
                translatedError));

        res.append(orderId);
        if (result.isResult()
                || result.getError_code().contains("32004") // result.getError_message()=="You have not uncompleted order at the moment"
                || translatedError.contains("order does not exist")
                || result.getError_message().contains("rder does not exist")) {
            order.setOrderStatus(OrderStatus.CANCELED); // may be FILLED, but it's ok here.
            res.append(":CANCELED");

            final FplayOrder stub = new FplayOrder(this.getMarketId(), null, "cancelOnMkt");
            updateFplayOrdersToCurrStab(Collections.singletonList(order), stub);

        } else {
            res.append(":").append(result.getError_message());
        }
    }

    @Override
    protected MetricsDictionary getMetricsDictionary() {
        return metricsDictionary;
    }

    @Override
    protected List<LimitOrder> getApiOpenOrders() throws IOException {
        final InstrumentDto instrumentDto = instrDtos.get(0);
        final String instrumentId = instrumentDto.getInstrumentId();
        final CurrencyPair currencyPair = instrumentDto.getCurrencyPair();
        return fplayOkexExchange.getPrivateApi().getOpenLimitOrders(instrumentId, currencyPair);
    }

    @Override
    protected Collection<Order> getApiOrders(String[] orderIds) throws IOException {
        final InstrumentDto instrumentDto = instrDtos.get(0);
        final String instrumentId = instrumentDto.getInstrumentId();
        final Collection<Order> orders = new ArrayList<>();
        for (String orderId : orderIds) {
            final CurrencyPair currencyPair = instrumentDto.getCurrencyPair();
            final LimitOrder order = fplayOkexExchange.getPrivateApi().getLimitOrder(instrumentId, orderId, currencyPair);
            if (order != null) {
                orders.add(order);
            }
        }

        return orders;
    }
}
