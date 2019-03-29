package com.bitplay.market.okcoin;

import static com.bitplay.market.model.LiqInfo.DQL_WRONG;

import com.bitplay.api.service.RestartService;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.arbitrage.dto.AvgPrice;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.DealPrices;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.events.SignalEvent;
import com.bitplay.arbitrage.events.SignalEventEx;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.ArbState;
import com.bitplay.market.BalanceService;
import com.bitplay.market.DefaultLogService;
import com.bitplay.market.LimitsService;
import com.bitplay.market.LogService;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.MarketState;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.BtsEventBox;
import com.bitplay.market.model.Affordable;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.metrics.MetricsDictionary;
import com.bitplay.persistance.LastPriceDeviationService;
import com.bitplay.persistance.MonitoringDataService;
import com.bitplay.persistance.OrderRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.fluent.FplayOrderUtils;
import com.bitplay.persistance.domain.fluent.TradeMStatus;
import com.bitplay.persistance.domain.mon.Mon;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.OkexContractType;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.settings.BitmexChangeOnSoService;
import com.bitplay.utils.Utils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import info.bitrich.xchangestream.okexv3.OkExAdapters;
import info.bitrich.xchangestream.okexv3.OkExStreamingExchange;
import info.bitrich.xchangestream.okexv3.OkExStreamingMarketDataService;
import info.bitrich.xchangestream.okexv3.dto.InstrumentDto;
import info.bitrich.xchangestream.okexv3.dto.marketdata.OkCoinDepth;
import info.bitrich.xchangestream.okexv3.dto.marketdata.OkcoinPriceRange;
import info.bitrich.xchangestream.service.ws.statistic.PingStatEvent;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.validation.constraints.NotNull;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderStatus;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.ContractIndex;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.okcoin.OkCoinAdapters;
import org.knowm.xchange.okcoin.OkCoinUtils;
import org.knowm.xchange.okcoin.dto.marketdata.OkcoinForecastPrice;
import org.knowm.xchange.okcoin.dto.trade.OkCoinPosition;
import org.knowm.xchange.okcoin.dto.trade.OkCoinPositionResult;
import org.knowm.xchange.okcoin.dto.trade.OkCoinTradeResult;
import org.knowm.xchange.okcoin.service.OkCoinFuturesAccountService;
import org.knowm.xchange.okcoin.service.OkCoinFuturesMarketDataService;
import org.knowm.xchange.okcoin.service.OkCoinFuturesTradeService;
import org.knowm.xchange.okcoin.service.OkCoinTradeService;
import org.knowm.xchange.okcoin.service.OkCoinTradeServiceRaw;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import si.mazi.rescu.HttpStatusIOException;

//import info.bitrich.xchangestream.okex.OkExStreamingExchange;
//import info.bitrich.xchangestream.okex.OkExStreamingMarketDataService;

/**
 * Created by Sergey Shurmin on 3/21/17.
 */
@Service("okcoin")
public class OkCoinService extends MarketServicePreliq {

    public static final String TAKER_WAS_CANCELLED_MESSAGE = "Taker wasn't filled. Cancelled";
    private static final Logger logger = LoggerFactory.getLogger(OkCoinService.class);
    private static final Logger debugLog = LoggerFactory.getLogger(OkCoinService.class);

    private static final Logger ordersLogger = LoggerFactory.getLogger("OKCOIN_ORDERS_LOG");

    public final static String NAME = "okcoin";
    ArbitrageService arbitrageService;

    private volatile AtomicReference<PlaceOrderArgs> placeOrderArgsRef = new AtomicReference<>();

    private static final int MAX_ATTEMPTS_STATUS = 50;
    private static final int MAX_ATTEMPTS_FOR_MOVING = 2;
    private static final int MAX_MOVING_TIMEOUT_SEC = 2;
    private static final int MAX_MOVING_OVERLOAD_ATTEMPTS_TIMEOUT_SEC = 60;
    // Moving timeout
    private volatile ScheduledFuture<?> scheduledMovingErrorsReset;
    private volatile boolean movingInProgress = false;
    private volatile AtomicInteger movingErrorsOverloaded = new AtomicInteger(0);

    private volatile String ifDisconnetedString = "";
    private volatile boolean shutdown = false;
    private Disposable onDisconnectHook;

    private volatile BigDecimal markPrice = BigDecimal.ZERO;
    private volatile BigDecimal forecastPrice = BigDecimal.ZERO;
    private volatile OkcoinPriceRange priceRange;

    public BigDecimal getMarkPrice() {
        return markPrice;
    }

    public BigDecimal getForecastPrice() {
        return forecastPrice;
    }

    public OkcoinPriceRange getPriceRange() {
        return priceRange;
    }

    @Autowired
    private SlackNotifications slackNotifications;
    @Autowired
    private LastPriceDeviationService lastPriceDeviationService;
    @Autowired
    private com.bitplay.persistance.TradeService fplayTradeService;
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
    @Autowired
    private OkexLimitsService okexLimitsService;
    @Autowired
    private OOHangedCheckerService ooHangedCheckerService;
    @Autowired
    private OkexTradeLogger tradeLogger;
    @Autowired
    private DefaultLogService defaultLogger;
    @Autowired
    private MonitoringDataService monitoringDataService;
    @Autowired
    private BitmexChangeOnSoService bitmexChangeOnSoService;
    @Autowired
    private MetricsDictionary metricsDictionary;

    private OkExStreamingExchange exchange;
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
    private Disposable deferredPlacingOrdersListener;
    private Observable<OkCoinDepth> orderBookObservable;
    private OkexContractType okexContractType;
    private OkexContractType okexContractTypeBTCUSD = OkexContractType.BTC_ThisWeek;
    private volatile Map<String, OkexContractType> instrIdToContractType = new HashMap<>();
    private volatile List<InstrumentDto> instrDtos = new ArrayList<>();

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
    public boolean isMarketStopped() {
        return getMarketState().isStopped();
    }

    public OkexLimitsService getOkexLimitsService() {
        return okexLimitsService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getFuturesContractName() {
        return okexContractType.toString();
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

        exchange = initExchange(key, secret, exArgs);
        loadLiqParams();

        initWebSocketAndAllSubscribers();
        deferredPlacingOrdersListener = initDeferredPlacingOrder();
    }

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("okex-preliq-thread-%d").build());

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                checkForDecreasePosition();
            } catch (Exception e) {
                logger.error("Error on checkForDecreasePosition", e);
            }
        }, 30, 1, TimeUnit.SECONDS);
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
        priceRangeSub = startPriceRangeListener();
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
        spec.setApiKey(key);
        spec.setSecretKey(secret);

        spec.setExchangeSpecificParametersItem("Use_Intl", true);
        spec.setExchangeSpecificParametersItem("Use_Futures", true);
        spec.setExchangeSpecificParametersItem("Futures_Contract", okexContractType.getFuturesContract());
        spec.setExchangeSpecificParametersItem("Futures_Leverage", "20");

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
                    if (!shutdown) {
                        ifDisconnetedString += " okex disconnected at " + LocalTime.now();
                        logger.warn("onClientDisconnect okCoinService");
                        initWebSocketAndAllSubscribers();
                    }
                    logger.info("Exchange Disconnect finished");
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
                .subscribeOn(Schedulers.io())
                .subscribe(okcoinDepth -> {
                    boolean isExtra = isObExtra(okcoinDepth);
                    final OkexContractType ct = instrIdToContractType.get(okcoinDepth.getInstrumentId());
                    if (isExtra) {
                        this.orderBookXBTUSD = OkExAdapters.adaptOrderBook(okcoinDepth, ct.getCurrencyPair());
                    } else {
                        this.orderBook = OkExAdapters.adaptOrderBook(okcoinDepth, ct.getCurrencyPair());

                        final LimitOrder bestAsk = Utils.getBestAsk(this.orderBook);
                        final LimitOrder bestBid = Utils.getBestBid(this.orderBook);

                        if (this.bestAsk != null && bestAsk != null && this.bestBid != null && bestBid != null
                                && this.bestAsk.compareTo(bestAsk.getLimitPrice()) != 0
                                && this.bestBid.compareTo(bestBid.getLimitPrice()) != 0) {
                            recalcAffordableContracts();
                        }
                        this.bestAsk = bestAsk != null ? bestAsk.getLimitPrice() : BigDecimal.ZERO;
                        this.bestBid = bestBid != null ? bestBid.getLimitPrice() : BigDecimal.ZERO;
                        logger.debug("ask: {}, bid: {}", this.bestAsk, this.bestBid);

                        Instant lastObTime = Instant.now();
                        getArbitrageService().getSignalEventBus().send(new SignalEventEx(SignalEvent.O_ORDERBOOK_CHANGED, lastObTime));
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
            return getShortOrderBook(this.orderBook);
        }
        return getShortOrderBook(this.orderBookXBTUSD);
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
        shutdown = true;

        deferredPlacingOrdersListener.dispose();
        // Disconnect from exchange (non-blocking)
        //noinspection ResultOfMethodCallIgnored
        closeAllSubscibers().subscribe(() -> logger.info("Disconnected from the Exchange"));
    }

    @Scheduled(fixedDelay = 2000) // Request frequency 20 times/2s
    public void fetchEstimatedDeliveryPrice() {
        Instant start = Instant.now();
        try {
            final OkcoinForecastPrice result = ((OkCoinFuturesMarketDataService) exchange.getMarketDataService())
                    .getFuturesForecastPrice(okexContractType.getCurrencyPair());
            forecastPrice = result.getPrice() != null ? result.getPrice() : BigDecimal.ZERO;

        } catch (Exception e) {
            logger.error("On fetchEstimatedDeliveryPrice", e);
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "fetchEstimatedDeliveryPrice");
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

    @Scheduled(fixedDelay = 2000)
    public void fetchPositionScheduled() {
        Instant start = Instant.now();
        try {
            fetchPosition();
        } catch (Exception e) {
            logger.error("On fetchPositionScheduled", e);
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "fetchPositionScheduled");
    }

    @Override
    public String fetchPosition() throws Exception {
        final OkCoinPositionResult positionResult = ((OkCoinTradeServiceRaw) exchange.getTradeService())
                .getFuturesPosition(
                        OkCoinAdapters.adaptSymbol(okexContractType.getCurrencyPair()),
                        okexContractType.getFuturesContract());
        mergePosition(positionResult, null);

        recalcAffordableContracts();
        recalcLiqInfo();
        return position != null ? position.toString() : "";
    }

    @Scheduled(fixedDelay = 500) // URL https://www.okex.com/api/v1/future_userinfo.do Request frequency 5 times/2s
    public void fetchUserInfoScheduled() {
        Instant start = Instant.now();
        try {
            fetchUserInfoContracts();
        } catch (Exception e) {
            logger.error("On fetchPositionScheduled", e);
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, logger, "fetchPositionScheduled");
    }

    @SuppressWarnings("Duplicates")
    private void fetchUserInfoContracts() throws IOException {

        final AccountInfoContracts accountInfoContracts = ((OkCoinFuturesAccountService) exchange.getAccountService())
                .getAccountInfoContracts(okexContractType.getCurrencyPair().base);

        mergeAccountInfoContracts(accountInfoContracts);
    }

    private synchronized void mergePosition(OkCoinPositionResult restUpdate, Position websocketUpdate) {
        if (restUpdate != null) {
            if (restUpdate.getPositions().length > 1) {
                final String counterForLogs = getCounterName();
                logger.warn("#{} More than one positions found", counterForLogs);
                tradeLogger.warn(String.format("#%s More than one positions found", counterForLogs));
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
                final BigDecimal forceLiquPrice = convertLiqPrice(restUpdate.getForceLiquPrice());
                final OkCoinPosition okCoinPosition = restUpdate.getPositions()[0];
                position = new Position(
                        okCoinPosition.getBuyAmount(),
                        okCoinPosition.getSellAmount(),
                        okCoinPosition.getRate(),
                        forceLiquPrice,
                        BigDecimal.ZERO,
                        okCoinPosition.getBuyPriceAvg(),
                        okCoinPosition.getSellPriceAvg(),
                        okCoinPosition.toString()
                );
//                if (okCoinPosition.getBuyAmount().subtract(okCoinPosition.getSellAmount()).signum() == 0) {
//                    logger.info("restUpdate: pos==0. " + getName());
//                }
            }
        } else if (websocketUpdate != null) { // TODO does it worth it
            position = new Position(
                    websocketUpdate.getPositionLong(),
                    websocketUpdate.getPositionShort(),
                    websocketUpdate.getLeverage(),
                    websocketUpdate.getLiquidationPrice(),
                    websocketUpdate.getMarkValue(),
                    websocketUpdate.getPriceAvgLong(),
                    websocketUpdate.getPriceAvgShort(),
                    websocketUpdate.getRaw());
//            if (websocketUpdate.getPositionLong().subtract(websocketUpdate.getPositionShort()).signum() == 0) {
//                logger.info("websocketUpdate: pos==0. " + getName());
//            }

        }

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

    @SuppressWarnings("Duplicates")
    private void mergeAccountInfoContracts(AccountInfoContracts newInfo) {
        AccountInfoContracts current = this.accountInfoContracts;
        logger.debug("AccountInfo.Websocket: " + current.toString());

        BigDecimal eLast = newInfo.geteLast() != null ? newInfo.geteLast() : current.geteLast();
        this.accountInfoContracts = new AccountInfoContracts(
                newInfo.getWallet() != null ? newInfo.getWallet() : current.getWallet(),
                newInfo.getAvailable() != null ? newInfo.getAvailable() : current.getAvailable(),
                eLast,
                eLast,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                newInfo.getMargin() != null ? newInfo.getMargin() : current.getMargin(),
                newInfo.getUpl() != null ? newInfo.getUpl() : current.getUpl(),
                newInfo.getRpl() != null ? newInfo.getRpl() : current.getRpl(),
                newInfo.getRiskRate() != null ? newInfo.getRiskRate() : current.getRiskRate()
        );
    }

    private Disposable startUserPositionSub() {
        final InstrumentDto instrumentDto = new InstrumentDto(okexContractType.getCurrencyPair(), okexContractType.getFuturesContract());

        return exchange.getStreamingPrivateDataService()
                .getPositionObservable(instrumentDto)
                .doOnError(throwable -> logger.error("Error on PrivateData observing", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(positionInfo -> {
                    logger.debug(positionInfo.toString());
                    final Position pos = new Position(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "");
                    BeanUtils.copyProperties(positionInfo, pos);
                    mergePosition(null, pos);
                    recalcAffordableContracts();
                    recalcLiqInfo();
                }, throwable -> logger.error("PositionObservable.Exception: ", throwable));
    }

    @SuppressWarnings("Duplicates")
    private Disposable startAccountInfoSubscription() {
        return exchange.getStreamingPrivateDataService()
                .getAccountInfoObservable(okexContractType.getCurrencyPair())
                .doOnError(throwable -> logger.error("Error on PrivateData observing", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(newInfo -> {
                    logger.debug(newInfo.toString());
//                    AccountInfoContracts newInfo = new AccountInfoContracts();
//                    BeanUtils.copyProperties(userAccountInfo, newInfo);
//                    mergeAccountInfoContracts(newInfo);
                    AccountInfoContracts current = this.accountInfoContracts;
                    logger.debug("AccountInfo.Websocket: " + current.toString());

                    BigDecimal eLast = newInfo.geteLast() != null ? newInfo.geteLast() : current.geteLast();
                    this.accountInfoContracts = new AccountInfoContracts(
                            newInfo.getWallet() != null ? newInfo.getWallet() : current.getWallet(),
                            newInfo.getAvailable() != null ? newInfo.getAvailable() : current.getAvailable(),
                            eLast,
                            eLast,
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            newInfo.getMargin() != null ? newInfo.getMargin() : current.getMargin(),
                            newInfo.getUpl() != null ? newInfo.getUpl() : current.getUpl(),
                            newInfo.getRpl() != null ? newInfo.getRpl() : current.getRpl(),
                            newInfo.getRiskRate() != null ? newInfo.getRiskRate() : current.getRiskRate()
                    );

                }, throwable -> logger.error("AccountInfoObservable.Exception: ", throwable));
    }

    private Disposable startUserOrderSub() {
        final InstrumentDto instrumentDto = new InstrumentDto(okexContractType.getCurrencyPair(), okexContractType.getFuturesContract());

        return exchange.getStreamingPrivateDataService()
                .getTradesObservable(instrumentDto)
                .doOnError(throwable -> logger.error("Error on PrivateData observing", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(limitOrders -> {
                    logger.debug("got open orders: " + limitOrders.size());
                    synchronized (openOrdersLock) {
                        // do not repeat for already 'FILLED' orders.
                        limitOrders
                                .forEach(update -> openOrders.stream()
                                        .filter(o -> o.getOrderId().equals(update.getId()))
                                        .filter(o -> o.getOrder().getStatus() != Order.OrderStatus.FILLED)
                                        .forEach(o -> {
                                            arbitrageService.getDealPrices().getoPriceFact()
                                                    .addPriceItem(o.getCounterName(),
                                                            update.getId(),
                                                            update.getCumulativeAmount(),
                                                            update.getAveragePrice(),
                                                            update.getStatus());
                                            writeAvgPriceLog();
                                        })
                                );
                    }

                    final Long tradeId = arbitrageService.getTradeId();
                    final FplayOrder fPlayOrderStub = new FplayOrder(tradeId, getCounterName(),
                            null,
                            null,
                            null);
                    updateOpenOrders(limitOrders, fPlayOrderStub);
                }, throwable -> logger.error("TradesObservable.Exception: ", throwable));
    }

    private Disposable startPingStatSub() {
        return exchange.subscribePingStats()
                .map(PingStatEvent::getPingPongMs)
                .subscribe(ms -> metricsDictionary.setOkexPing(ms),
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
                                this.contractIndex = new ContractIndex(indexPrice.setScale(2, RoundingMode.HALF_UP), new Date());
                            } else {
                                this.btcContractIndex = new ContractIndex(indexPrice.setScale(2, RoundingMode.HALF_UP), new Date());
                            }
                        },
                        throwable -> logger.error("OkexIndexPriceSub.Exception: ", throwable)
                );
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

    /**
     * See {@link OOHangedCheckerService}.
     */
    void openOrdersHangedChecker() {
        updateOOStatuses();

        if (!hasOpenOrders()) {
            final Long tradeId = tryFindLastTradeId();
            eventBus.send(new BtsEventBox(BtsEvent.MARKET_FREE_FROM_CHECKER, tradeId));
        }
    }

    private TradeResponse takerOrder(Long tradeId, Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, SignalType signalType,
            String counterName)
            throws Exception {

        TradeResponse tradeResponse = new TradeResponse();

        final TradeService tradeService = exchange.getTradeService();

        orderType = adjustOrderType(orderType, amount);

        synchronized (openOrdersLock) {

            // Option 1: REAL TAKER - okex does it different. Probably, it is similar to our HYBRID.
//            final MarketOrder marketOrder = new MarketOrder(orderType, amount, currencyPair, new Date());
//            final String orderId = tradeService.placeMarketOrder(marketOrder);

            // Option 2: FAKE LIMIT ORDER
            BigDecimal okexFakeTakerDev = settingsRepositoryService.getSettings().getOkexFakeTakerDev();
            BigDecimal thePrice = Utils.createPriceForTaker(orderType, priceRange, okexFakeTakerDev);
            getTradeLogger().info("The fake taker price is " + thePrice.toPlainString());
            final LimitOrder limitOrder = new LimitOrder(orderType, amount, okexContractType.getCurrencyPair(), "123", new Date(), thePrice);

            // metrics
            final Mon monPlacing = monitoringDataService.fetchMon(getName(), "placeOrder");
            final Instant startReq = Instant.now();

            String orderId = tradeService.placeLimitOrder(limitOrder);

            final Instant endReq = Instant.now();
            final long waitingMarketMs = endReq.toEpochMilli() - startReq.toEpochMilli();
            monPlacing.getWaitingMarket().add(BigDecimal.valueOf(waitingMarketMs));
            if (waitingMarketMs > 5000) {
                logger.warn("TAKER okexPlaceOrder waitingMarketMs=" + waitingMarketMs);
            }
            monitoringDataService.saveMon(monPlacing);
//            CounterAndTimer placeOrderMetrics = MetricFactory.getCounterAndTimer(getName(), "placeOrderTAKER");
//            placeOrderMetrics.durationMs(waitingMarketMs);

            Order orderInfo = getFinalOrderInfoSync(orderId, counterName, "Taker:FinalStatus:");
            if (orderInfo == null) {
                throw new ResetToReadyException("Failed to check final status of taker-maker id=" + orderId);
            }

            final FplayOrder fPlayOrder = new FplayOrder(tradeId, counterName, orderInfo, bestQuotes, PlacingType.TAKER, signalType);
            addOpenOrder(fPlayOrder);

            arbitrageService.getDealPrices().setSecondOpenPrice(orderInfo.getAveragePrice());
            arbitrageService.getDealPrices().getoPriceFact()
                    .addPriceItem(counterName, orderInfo.getId(), orderInfo.getCumulativeAmount(), orderInfo.getAveragePrice(), orderInfo.getStatus());

            if (orderInfo.getStatus() == OrderStatus.NEW) { // 1. Try cancel then
                Pair<Boolean, Order> orderPair = cancelOrderWithCheck(orderId, "Taker:Cancel_maker:", "Taker:Cancel_makerStatus:", counterName);

                if (orderPair.getSecond().getId().equals("empty")) {
                    throw new Exception("Failed to check status of cancelled taker-maker id=" + orderId);
                }
                orderInfo = orderPair.getSecond();

                updateOpenOrder((LimitOrder) orderInfo);
                arbitrageService.getDealPrices().getoPriceFact()
                        .addPriceItem(counterName, orderInfo.getId(), orderInfo.getCumulativeAmount(), orderInfo.getAveragePrice(), orderInfo.getStatus());
            }

            if (orderInfo.getStatus() == OrderStatus.CANCELED) { // Should not happen
                tradeResponse.setErrorCode(TAKER_WAS_CANCELLED_MESSAGE);
                tradeResponse.addCancelledOrder((LimitOrder) orderInfo);
                warningLogger.warn("#{} Order was cancelled. orderId={}", counterName, orderId);
            } else { //FILLED by any (orderInfo or cancelledOrder)

                writeLogPlaceOrder(orderType, amount, "taker",
                        orderInfo.getAveragePrice(), orderId, orderInfo.getStatus().toString(), counterName);

                tradeResponse.setOrderId(orderId);
                tradeResponse.setLimitOrder((LimitOrder) orderInfo);
            }
        } // openOrdersLock

        return tradeResponse;
    }


    private synchronized void recalcAffordableContracts() {
        final BigDecimal reserveBtc = arbitrageService.getParams().getReserveBtc2();
        final BigDecimal volPlan = settingsRepositoryService.getSettings().getPlacingBlocks().getFixedBlockOkex();
//        final BigDecimal volPlan = arbitrageService.getParams().getBlock2();

        if (accountInfoContracts != null && position != null && Utils.orderBookIsFull(orderBook)) {
            final BigDecimal available = accountInfoContracts.getAvailable();
            final BigDecimal equity = accountInfoContracts.geteLast();

            final BigDecimal bestAsk = Utils.getBestAsks(orderBook, 1).get(0).getLimitPrice();
            final BigDecimal bestBid = Utils.getBestBids(orderBook, 1).get(0).getLimitPrice();
            final BigDecimal leverage = position.getLeverage();

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

    public void changeDeferredPlacingType(PlacingType placingType) {
        placeOrderArgsRef.getAndUpdate(placeOrderArgs -> {
            if (placeOrderArgs == null) {
                return null;
            }
            return placeOrderArgs.cloneWithPlacingType(placingType);
        });
    }

    private Disposable initDeferredPlacingOrder() {
        return getArbitrageService().getSignalEventBus().toObserverable()
                .subscribe(eventQuant -> {
                    try {
                        SignalEvent signalEvent = eventQuant instanceof SignalEventEx
                                ? ((SignalEventEx) eventQuant).getSignalEvent()
                                : (SignalEvent) eventQuant;

                        if (signalEvent == SignalEvent.MT2_BITMEX_ORDER_FILLED) {
                            if (getMarketState() == MarketState.WAITING_ARB) {
                                final PlaceOrderArgs currArgs = placeOrderArgsRef.getAndSet(null);
                                if (currArgs != null) {
                                    setMarketState(MarketState.ARBITRAGE);
                                    tradeLogger.info(String.format("#%s MT2 start placing ", currArgs));

                                    {// set oPricePlanOnStart
                                        final BigDecimal oPricePlanOnStart;
                                        if (currArgs.getOrderType() == OrderType.BID || currArgs.getOrderType() == OrderType.EXIT_ASK) {
                                            oPricePlanOnStart = Utils.getBestAsk(orderBook).getLimitPrice(); // buy -> use the opposite price.
                                        } else {
                                            oPricePlanOnStart = Utils.getBestBid(orderBook).getLimitPrice(); // do sell -> use the opposite price.
                                        }
                                        arbitrageService.getDealPrices().setoPricePlanOnStart(oPricePlanOnStart);
                                    }

                                    fplayTradeService.setOkexStatus(currArgs.getTradeId(), TradeMStatus.IN_PROGRESS);
                                    placeOrder(currArgs);
                                } else {
                                    logger.error("WAITING_ARB: no deferred order. Set READY.");
                                    warningLogger.error("WAITING_ARB: no deferred order. Set READY.");
                                    arbitrageService.releaseArbInProgress("", "WAITING_ARB deferred-placing-order");
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
        PlacingType placingType = placeOrderArgs.getPlacingType();
        if (placingType == null) {
            tradeLogger.warn("WARNING: placingType is null. " + placeOrderArgs);
            final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
            placingType = settings.getOkexPlacingType();
        }
        final String counterName = placeOrderArgs.getCounterName();
        final Long tradeId = placeOrderArgs.getTradeId();
        final Instant lastObTime = placeOrderArgs.getLastObTime();
        final Instant startPlacing = Instant.now();

        // SET STATE
        arbitrageService.setSignalType(signalType);
        MarketState nextState = getMarketState();
        setMarketState(MarketState.PLACING_ORDER);

        BigDecimal amountLeft = amount;
        shouldStopPlacing = false;
        for (int attemptCount = 1; attemptCount < maxAttempts && !getMarketState().isStopped() && !shouldStopPlacing; attemptCount++) {
            try {
                if (attemptCount > 1) {
                    Thread.sleep(1000);
                }

                if (placingType != PlacingType.TAKER) {
                    tradeResponse = placeNonTakerOrder(tradeId, orderType, amountLeft, bestQuotes, false, signalType, placingType, counterName);
                } else {
                    tradeResponse = takerOrder(tradeId, orderType, amountLeft, bestQuotes, signalType, counterName);
                    if (tradeResponse.getErrorCode() != null && tradeResponse.getErrorCode().equals(TAKER_WAS_CANCELLED_MESSAGE)) {
                        final BigDecimal filled = tradeResponse.getCancelledOrders().get(0).getCumulativeAmount();
                        amountLeft = amountLeft.subtract(filled);
                        continue;
                    }
                }
                break;

            } catch (Exception e) {
                String message = e.getMessage();

                String details = String.format("#%s/%s placeOrderOnSignal error. type=%s,a=%s,bestQuotes=%s,isMove=%s,signalT=%s. %s",
                        counterName, attemptCount,
                        orderType, amountLeft, bestQuotes, false, signalType, message);
                logger.error(details, e);
                details = details.length() < 300 ? details : details.substring(0, 300); // we can get html page as error message
                tradeLogger.error(details);

                tradeResponse.setOrderId(message);
                tradeResponse.setErrorCode(message);

                final NextStep nextStep = handlePlacingException(e, tradeResponse);

                if (e instanceof ResetToReadyException) {
                    nextState = MarketState.READY;
                }

                if (nextStep == NextStep.CONTINUE) {
                    continue;
                }
                break; // no retry by default
            }
        }

        try {
//            if (placeOrderArgs.getSignalType().isCorr()) { // It's only TAKER, so it should be DONE, if no errors
//                if (tradeResponse.getErrorCode() == null && tradeResponse.getOrderId() != null) {
//                    posDiffService.finishCorr(true); // - when market is READY
//                } else {
//                    posDiffService.finishCorr(false);
//                }
//                nextState = MarketState.READY;
//                setMarketState(nextState, counterName);
//                eventBus.send(BtsEvent.MARKET_FREE);
//            }
        } finally {
            // RESET STATE
            if (placingType != PlacingType.TAKER) {
                ooHangedCheckerService.startChecker();
                setMarketState(MarketState.ARBITRAGE, counterName);
            } else {
                if ((nextState == MarketState.WAITING_ARB && placeOrderArgsRef.get() == null)
                        || nextState == MarketState.PLACING_ORDER
                        || nextState == MarketState.MOVING
                        || nextState == MarketState.STOPPED
                        || nextState == MarketState.FORBIDDEN
                        || nextState == MarketState.ARBITRAGE
                ) {
                    nextState = MarketState.ARBITRAGE;
                }
                // fix WAITING_ARB -> PRELIQ -> WAITING_ARB
                if (nextState == MarketState.PRELIQ && placeOrderArgsRef.get() != null) {
                    nextState = MarketState.WAITING_ARB;
                }

                setMarketState(nextState, counterName); // should be READY
                if (tradeResponse.getOrderId() != null) {
                    setFree(placeOrderArgs.getTradeId()); // ARBITRAGE->READY and iterateOOToMove
                }
            }
        }

        // metrics
        final Mon monPlacing = monitoringDataService.fetchMon(getName(), "placeOrder");
        if (lastObTime != null) {
            long beforeMs = startPlacing.toEpochMilli() - lastObTime.toEpochMilli();
            monPlacing.getBefore().add(BigDecimal.valueOf(beforeMs));
//            CounterAndTimer metrics = MetricFactory.getCounterAndTimer(getName(), "beforePlaceOrder");
//            metrics.durationMs(beforeMs);
            if (beforeMs > 5000) {
                logger.warn(placingType + "okex beforePlaceOrderMs=" + beforeMs);
            }
        }
        final Instant endPlacing = Instant.now();
        long wholeMs = endPlacing.toEpochMilli() - startPlacing.toEpochMilli();
        monPlacing.getWholePlacing().add(BigDecimal.valueOf(wholeMs));
//        CounterAndTimer metrics = MetricFactory.getCounterAndTimer(getName(), "wholePlacing" + placingType);
//        metrics.durationMs(wholeMs);
        if (wholeMs > 5000) {
            logger.warn(placingType + "okex wholePlacingMs=" + wholeMs);
        }
        monPlacing.incCount();
        monitoringDataService.saveMon(monPlacing);

        return tradeResponse;
    }

    enum NextStep {CONTINUE, BREAK,}

    private NextStep handlePlacingException(Exception exception, TradeResponse tradeResponse) {
        if (exception instanceof HttpStatusIOException) {
            HttpStatusIOException e = (HttpStatusIOException) exception;
            final String httpBody = e.getHttpBody();
            tradeResponse.setOrderId(httpBody);
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
            if (message.contains("connect timed out") // SocketTimeoutException
                    || message.contains("Read timed out") // SocketTimeoutException
                    || message.contains("Signature does not match")
                    || message.contains("Remote host closed connection during handshake") // javax.net.ssl.SSLHandshakeException
            ) { // ExchangeException
                return NextStep.CONTINUE;
            }
            if (message.contains("Close amount bigger than your open positions")) {
                try {
                    fetchPosition();
                } catch (Exception e1) {
                    logger.info("FetchPositionError:", e1);
                }
                return NextStep.CONTINUE;
            }
            return NextStep.BREAK; // no retry by default
        }
    }

    public void deferredPlaceOrderOnSignal(PlaceOrderArgs currPlaceOrderArgs) {
        final String counterName = currPlaceOrderArgs.getCounterName();
        if (this.placeOrderArgsRef.compareAndSet(null, currPlaceOrderArgs)) {
            setMarketState(MarketState.WAITING_ARB);
            tradeLogger.info(String.format("#%s MT2 deferred placing %s", counterName, currPlaceOrderArgs));
        } else {
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

    private TradeResponse placeNonTakerOrder(Long tradeId, Order.OrderType orderType, BigDecimal tradeableAmount, BestQuotes bestQuotes,
                                          boolean isMoving, @NotNull SignalType signalType, PlacingType placingSubType, String counterName) throws IOException {
        final TradeResponse tradeResponse = new TradeResponse();

        BigDecimal thePrice;

        if (tradeableAmount.compareTo(BigDecimal.ZERO) == 0) {

            tradeResponse.setErrorCode("Not enough amount left. amount=" + tradeableAmount.toPlainString());

        } else {
            // USING REST API
            orderType = adjustOrderType(orderType, tradeableAmount);

            final String message = Utils.getTenAskBid(getOrderBook(), counterName,
                    String.format("Before %s placing, orderType=%s,", placingSubType, Utils.convertOrderTypeName(orderType)));
            logger.info(message);
            tradeLogger.info(message);

            if (placingSubType == null || placingSubType == PlacingType.TAKER) {
                tradeLogger.warn("placing maker, but subType is " + placingSubType);
                warningLogger.warn("placing maker, but subType is " + placingSubType);
            }
            thePrice = createNonTakerPrice(orderType, placingSubType, getOrderBook(), getContractType());

            if (thePrice.compareTo(BigDecimal.ZERO) == 0) {
                tradeResponse.setErrorCode("The new price is 0 ");
            } else {

                final Instant startReq = Instant.now();
                final String orderId = exchange.getTradeService().placeLimitOrder(
                        new LimitOrder(orderType, tradeableAmount, okexContractType.getCurrencyPair(), "0", new Date(), thePrice));
                final Instant endReq = Instant.now();

                // metrics
                final long waitingMarketMs = endReq.toEpochMilli() - startReq.toEpochMilli();
                final Mon monPlacing = monitoringDataService.fetchMon(getName(), "placeOrder");
                monPlacing.getWaitingMarket().add(BigDecimal.valueOf(waitingMarketMs));
                if (waitingMarketMs > 5000) {
                    logger.warn(placingSubType + " okexPlaceOrder waitingMarketMs=" + waitingMarketMs);
                }
                monitoringDataService.saveMon(monPlacing);
//                CounterAndTimer placeOrderMetrics = MetricFactory.getCounterAndTimer(getName(), "placeOrder" + placingSubType);
//                placeOrderMetrics.durationMs(waitingMarketMs);

                tradeResponse.setOrderId(orderId);

                final LimitOrder resultOrder = new LimitOrder(orderType, tradeableAmount, okexContractType.getCurrencyPair(), orderId, new Date(),
                        thePrice);
                tradeResponse.setLimitOrder(resultOrder);
                final FplayOrder fplayOrder = new FplayOrder(tradeId, counterName, resultOrder, bestQuotes, placingSubType, signalType);
                addOpenOrder(fplayOrder);

                String placingTypeString = (isMoving ? "Moving3:Moved:" : "") + placingSubType;

                if (!isMoving) {

                    final Order.OrderStatus status = resultOrder.getStatus();
                    final String msg = String.format("#%s: %s %s amount=%s, quote=%s, orderId=%s, status=%s",
                            counterName,
                            placingTypeString,
                            Utils.convertOrderTypeName(orderType),
                            tradeableAmount.toPlainString(),
                            thePrice,
                            orderId,
                            status);

//                    debugLog.debug("placeOrder1 " + msg);

//                    synchronized (openOrdersLock) {

//                        debugLog.debug("placeOrder2 " + msg);

                        tradeLogger.info(msg);
//                        openOrders.replaceAll(exists -> {
//                            if (fplayOrder.getOrderId().equals(exists.getOrderId())) {
//                                return FplayOrderUtils.updateFplayOrder(exists, fplayOrder);
//                            }
//                            return exists;
//                        });
//
//                        if (openOrders.stream().noneMatch(o -> o.getOrderId().equals(fplayOrder.getOrderId()))) {
//                            debugLog.debug("placeOrder2 Order was missed " + msg);
//                            logger.warn("placeOrder2 Order was missed " + msg);
//                            tradeLogger.warn("placeOrder2 Order was missed " + msg);
//
//                            openOrders.add(fplayOrder);
//                        }
//                    }

//                    debugLog.debug("placeOrder3 " + msg);
                }

                arbitrageService.getDealPrices().setSecondOpenPrice(thePrice);
                arbitrageService.getDealPrices().getoPriceFact()
                        .addPriceItem(counterName, orderId, resultOrder.getCumulativeAmount(), resultOrder.getAveragePrice(), resultOrder.getStatus());

                orderIdToSignalInfo.put(orderId, bestQuotes);

                writeLogPlaceOrder(orderType, tradeableAmount,
                        placingTypeString,
                        thePrice, orderId,
                        (resultOrder.getStatus() != null) ? resultOrder.getStatus().toString() : null,
                        counterName);
            }
        }

        return tradeResponse;
    }

    private void writeLogPlaceOrder(Order.OrderType orderType, BigDecimal tradeableAmount,
            String placingType, BigDecimal thePrice, String orderId, String status, String counterName) {

        final String message = String.format("#%s/end: %s %s amount=%s, quote=%s, orderId=%s, status=%s",
                counterName,
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

    private Order fetchOrderInfo(String orderId, String counterForLogs) {
        Order order = null;
        try {
            //NOT implemented yet
            final Collection<Order> orderCollection = exchange.getTradeService().getOrder(orderId);
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
    public UserTrades fetchMyTradeHistory() {
//        returnTradeHistory
        UserTrades tradeHistory = null;
        try {
            tradeHistory = exchange.getTradeService()
                    .getTradeHistory(new OkCoinTradeService.OkCoinTradeHistoryParams(
                            10, 1, okexContractType.getCurrencyPair()));
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
    public MoveResponse moveMakerOrder(FplayOrder fOrderToCancel, BigDecimal bestMarketPrice, Object... reqMovingArgs) {
        final LimitOrder limitOrder = LimitOrder.Builder.from(fOrderToCancel.getOrder()).build();
        final SignalType signalType = fOrderToCancel.getSignalType() != null ? fOrderToCancel.getSignalType() : getArbitrageService().getSignalType();

        final Long tradeId = fOrderToCancel.getTradeId();
        final String counterName = fOrderToCancel.getCounterName();
        if (limitOrder.getStatus() == Order.OrderStatus.CANCELED || limitOrder.getStatus() == Order.OrderStatus.FILLED) {
            tradeLogger.error(String.format("#%s do not move ALREADY_CLOSED order", counterName));
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

            // 1. cancel old order
            // 2. We got result on cancel(true/false), but double-check status of an old order
            Pair<Boolean, Order> orderPair = cancelOrderWithCheck(limitOrder.getId(), "Moving1:cancelling:", "Moving2:cancelStatus:", counterName);
            if (orderPair.getSecond().getId().equals("empty")) {
                return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "Failed to check status of cancelled order on moving id=" + limitOrder.getId());
            }
            Boolean cancelSucceed = orderPair.getFirst();
            LimitOrder cancelledOrder = (LimitOrder) orderPair.getSecond();

            final FplayOrder cancelledFplayOrd = FplayOrderUtils.updateFplayOrder(fOrderToCancel, cancelledOrder);
            final LimitOrder cancelledLimitOrder = (LimitOrder) cancelledFplayOrd.getOrder();
            orderRepositoryService.update(cancelledLimitOrder, cancelledFplayOrd);

            arbitrageService.getDealPrices().getoPriceFact().addPriceItem(counterName, cancelledLimitOrder.getId(), cancelledLimitOrder.getCumulativeAmount(),
                    cancelledLimitOrder.getAveragePrice(), cancelledLimitOrder.getStatus());

            // 3. Already closed?
            if (!cancelSucceed // WORKAROUND: CANCELLED, but was cancelled/placedNew on a previous moving-iteration
                    || cancelledLimitOrder.getStatus() == Order.OrderStatus.FILLED) {
                response = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, "", null, null,
                        cancelledFplayOrd);

                final String logString = String.format("#%s %s %s status=%s,amount=%s/%s,quote=%s/%s,id=%s,lastException=%s",
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
            } else if (cancelledOrder.getStatus() == OrderStatus.CANCELED) {
                TradeResponse tradeResponse = new TradeResponse();
                if (cancelledFplayOrd.getPlacingType() == null) {
                    getTradeLogger().warn("WARNING: PlaceType is null." + cancelledFplayOrd);
                }

                tradeResponse = finishMovingSync(tradeId, limitOrder, signalType, bestQuotes, counterName, cancelledLimitOrder,
                        tradeResponse,
                        cancelledFplayOrd.getPlacingType());

                if (tradeResponse.getLimitOrder() != null) {
                    final LimitOrder newOrder = tradeResponse.getLimitOrder();
                    final FplayOrder newFplayOrder = new FplayOrder(tradeId, counterName, newOrder, cancelledFplayOrd.getBestQuotes(),
                            cancelledFplayOrd.getPlacingType(), cancelledFplayOrd.getSignalType());
                    response = new MoveResponse(MoveResponse.MoveOrderStatus.MOVED_WITH_NEW_ID, tradeResponse.getOrderId(),
                            newOrder, newFplayOrder, cancelledFplayOrd);
                } else {
                    warningLogger.info(String.format("#%s Can not move orderId=%s, ONLY_CANCEL!!!",
                            counterName, limitOrder.getId()));
                    response = new MoveResponse(MoveResponse.MoveOrderStatus.ONLY_CANCEL, tradeResponse.getOrderId(),
                            null, null, cancelledFplayOrd);
                }

            } else {
                response = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "wrong status on cancel/new: " + cancelledLimitOrder.getStatus());
            }
        } finally {
            setMarketState(savedState);
        }

        { // mon
            Instant lastEnd = Instant.now();
            Mon mon = monitoringDataService.fetchMon(getName(), "moveMakerOrder");
            if (reqMovingArgs != null && reqMovingArgs.length == 1 && reqMovingArgs[0] != null) {
                Instant lastObTime = (Instant) reqMovingArgs[0];
                long beforeMs = startMoving.toEpochMilli() - lastObTime.toEpochMilli();
                mon.getBefore().add(BigDecimal.valueOf(beforeMs));
//                CounterAndTimer metrics = MetricFactory.getCounterAndTimer(getName(), "beforeMoveOrder");
//                metrics.durationMs(beforeMs);
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
        }


        return response;
    }

    private TradeResponse finishMovingSync(Long tradeId, LimitOrder limitOrder, SignalType signalType, BestQuotes bestQuotes, String counterName,
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

                tradeLogger.info(String.format("#%s/%s Moving3:placingNew a=%s, placingType=%s", counterName, attemptCount, newAmount, placingType));

                PlacingType okexPlacingType = placingType;
                if (okexPlacingType == null) {
                    placingType = persistenceService.getSettingsRepositoryService().getSettings().getOkexPlacingType();
                }

                tradeResponse = placeNonTakerOrder(tradeId, limitOrder.getType(), newAmount, bestQuotes, true, signalType, okexPlacingType, counterName);

                if (tradeResponse.getErrorCode() != null && tradeResponse.getErrorCode().startsWith("Insufficient")) {
                    tradeLogger.info(String.format("#%s/%s Moving3:Failed %s amount=%s,quote=%s,id=%s,attempt=%s. Error: %s",
                            counterName, attemptCount,
                            limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                            limitOrder.getTradableAmount(),
                            limitOrder.getLimitPrice().toPlainString(),
                            limitOrder.getId(),
                            attemptCount,
                            tradeResponse.getErrorCode()));
                }
                if (tradeResponse.getErrorCode() == null) {
                    break;
                }
            } catch (Exception e) {
                logger.error("#{}/{} Moving3:placingError", counterName, attemptCount, e);
                tradeLogger.error(String.format("#%s/%s Warning: Moving3:placingError %s", counterName, attemptCount, e.toString()));
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
    @NotNull
    private Order getFinalOrderInfoSync(String orderId, String counterName, String logInfoId) {
        final OkCoinFuturesTradeService tradeService = (OkCoinFuturesTradeService) exchange.getTradeService();
        Order result = null;
        int attemptCount = 0;
        while (attemptCount < MAX_ATTEMPTS_STATUS) {
            attemptCount++;
            try {

                Thread.sleep(200);

                final Collection<Order> order = tradeService.getOrder(orderId);
                Order cancelledOrder = order.iterator().next();
                tradeLogger.info(String.format("#%s/%s %s id=%s, status=%s, filled=%s",
                        counterName,
                        attemptCount,
                        logInfoId,
                        cancelledOrder.getId(),
                        cancelledOrder.getStatus(),
                        cancelledOrder.getCumulativeAmount()));

                if (cancelledOrder.getStatus() == Order.OrderStatus.CANCELED
                        || cancelledOrder.getStatus() == Order.OrderStatus.FILLED) {
                    result = cancelledOrder;
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
    public List<LimitOrder> cancelAllOrders(String logInfoId, boolean beforePlacing) {
        List<LimitOrder> res = new ArrayList<>();
        final String counterForLogs = getCounterName();
        synchronized (openOrdersLock) {
            openOrders.stream()
                    .filter(Objects::nonNull)
                    .filter(FplayOrder::isOpen)
                    .map(FplayOrder::getLimitOrder)
                    .forEach(order -> {

                final String orderId = order.getId();
                int attemptCount = 0;
                while (attemptCount < MAX_ATTEMPTS_CANCEL) {
                    attemptCount++;
                    try {
                        if (attemptCount > 1) {
                            Thread.sleep(1000);
                        }
                        final OkCoinFuturesTradeService tradeService = (OkCoinFuturesTradeService) exchange.getTradeService();
                        OkCoinTradeResult result = tradeService.cancelOrderWithResult(orderId,
                                okexContractType.getCurrencyPair(),
                                okexContractType.getFuturesContract());

                        tradeLogger.info(String.format("#%s/%s %s id=%s,res=%s,code=%s,details=%s(%s)",
                                counterForLogs, attemptCount,
                                logInfoId,
                                orderId,
                                result.isResult(),
                                result.getErrorCode(),
                                result.getDetails(),
                                getErrorCodeTranslation(result)));

                        if (result.isResult() || result.getDetails().contains("20015") /* "Order does not exist"*/) {
                            order.setOrderStatus(OrderStatus.CANCELED); // can be FILLED, but it's ok here.
                            res.add(order);
                            break;
                        }
                    } catch (Exception e) {
                        logger.error("#{}/{} error cancel maker order", counterForLogs, attemptCount, e);
                        tradeLogger.error(String.format("#%s/%s error cancel maker order: %s", counterForLogs, attemptCount, e.toString()));
                    }
                }
            });
            final boolean cnlSuccess = res.size() > 0;
            if (beforePlacing && cnlSuccess) {
                setMarketState(MarketState.PLACING_ORDER);
            }
        }

        return res;
    }

    private String getErrorCodeTranslation(OkCoinTradeResult result) {
        String errorCodeTranslation = "";
        if (result != null && result.getDetails() != null && result.getDetails().startsWith("Code: ")) { // Example: result.getDetails() == "Code: 20015"
            String errorCode = result.getDetails().substring(6);
            try {
                errorCodeTranslation = OkCoinUtils.getErrorMessage(Integer.parseInt(errorCode));
            } catch (NumberFormatException e) {
                logger.error("can not translate code " + errorCode);
            }
        }
        return errorCodeTranslation;
    }

    @NotNull
    public OkCoinTradeResult cancelOrderSync(String orderId, String logInfoId) {
        final String counterForLogs = getCounterName();
        OkCoinTradeResult result = new OkCoinTradeResult(false, 0, 0);

        int attemptCount = 0;
        while (attemptCount < MAX_ATTEMPTS_CANCEL) {
            attemptCount++;
            try {
                if (attemptCount > 1) {
                    Thread.sleep(1000);
                }
                final OkCoinFuturesTradeService tradeService = (OkCoinFuturesTradeService) exchange.getTradeService();
                result = tradeService.cancelOrderWithResult(orderId,
                        okexContractType.getCurrencyPair(),
                        okexContractType.getFuturesContract());

                if (result == null) {
                    tradeLogger.info(String.format("#%s/%s %s id=%s, no response", counterForLogs, attemptCount, logInfoId, orderId));
                    continue;
                }

                tradeLogger.info(String.format("#%s/%s %s id=%s,res=%s(%s),code=%s,details=%s(%s)",
                        counterForLogs, attemptCount,
                        logInfoId,
                        orderId,
                        result.isResult(),
                        result.isResult() ? "cancelled" : "probably already filled",
                        result.getErrorCode(),
                        result.getDetails(),
                        getErrorCodeTranslation(result)));

                if (result.isResult()) {
                    break;
                }

            } catch (Exception e) {
                logger.error("#{}/{} error cancel maker order", counterForLogs, attemptCount, e);
                tradeLogger.error(String.format("#%s/%s error cancel maker order: %s", counterForLogs, attemptCount, e.toString()));
                final Order order = fetchOrderInfo(orderId, counterForLogs);
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

    private Pair<Boolean, Order> cancelOrderWithCheck(String orderId, String logInfoId1, String logInfoId2, String counterName) {
        Order resOrder = new LimitOrder.Builder(OrderType.ASK, okexContractType.getCurrencyPair()).id("empty").build(); //workaround

        Boolean cancelSucceed = false;
        int attemptCount = 0;
        while (attemptCount < MAX_ATTEMPTS_CANCEL) { // 1.5 min
            attemptCount++;
            try {
                if (attemptCount > 1) {
                    Thread.sleep(1000);
                }

                final OkCoinFuturesTradeService tradeService = (OkCoinFuturesTradeService) exchange.getTradeService();
                // 1. Cancel request
                if (!cancelSucceed) {
                    final OkCoinTradeResult result = tradeService.cancelOrderWithResult(orderId,
                            okexContractType.getCurrencyPair(),
                            okexContractType.getFuturesContract());

                    if (result == null) {
                        tradeLogger.info(String.format("#%s/%s %s id=%s, no response", counterName, attemptCount, logInfoId1, orderId));
                        continue;
                    }

                    tradeLogger.info(String.format("#%s/%s %s id=%s,res=%s(%s),code=%s,details=%s(%s)",
                            counterName, attemptCount,
                            logInfoId1,
                            orderId,
                            result.isResult(),
                            result.isResult() ? "cancelled" : "probably already filled",
                            result.getErrorCode(),
                            result.getDetails(),
                            getErrorCodeTranslation(result)));

                    if (result.isResult()) {
                        cancelSucceed = true;
                    }
                }

                // 2. Status check
                final Collection<Order> order = tradeService.getOrder(orderId);
                if (order.size() == 0) {
                    tradeLogger.info(String.format("#%s/%s %s id=%s no orders in response",
                            counterName,
                            attemptCount,
                            logInfoId2,
                            orderId));
                } else {
                    Order cancelledOrder = order.iterator().next();
                    tradeLogger.info(String.format("#%s/%s %s id=%s, status=%s, filled=%s",
                            counterName,
                            attemptCount,
                            logInfoId2,
                            cancelledOrder.getId(),
                            cancelledOrder.getStatus(),
                            cancelledOrder.getCumulativeAmount()));

                    if (cancelledOrder.getStatus() == Order.OrderStatus.CANCELED
                            || cancelledOrder.getStatus() == Order.OrderStatus.FILLED) {
                        resOrder = cancelledOrder;
                        break;
                    }
                }

            } catch (Exception e) {
                logger.error("#{}/{} error cancel maker order", counterName, attemptCount, e);
                tradeLogger.error(String.format("#%s/%s error cancel maker order: %s", counterName, attemptCount, e.toString()));
            }
        }
        return Pair.of(cancelSucceed, resOrder);
    }

    @Override
    public String getPositionAsString() {
        return null;
    }

    private synchronized void recalcLiqInfo() {
        final BigDecimal pos = position.getPositionLong().subtract(position.getPositionShort());
        final BigDecimal oMrLiq = persistenceService.fetchGuiLiqParams().getOMrLiq();

        final AccountInfoContracts accountInfoContracts = getAccountInfoContracts();
        final BigDecimal equity = accountInfoContracts.geteLast();
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
                        return;
                    }

                    final BigDecimal L = position.getLiquidationPrice();
                        dql = m.subtract(L);
                        dqlString = String.format("o_DQL = m%s - L%s = %s", m, L, dql);
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
                        return;
                    }

                    final BigDecimal L = position.getLiquidationPrice();
                    dql = L.subtract(m);
                    dqlString = String.format("o_DQL = L%s - m%s = %s", L, m, dql);
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

    /**
     * @param orderType - only ASK, BID. There are no CLOSE_* types.
     */
    @Override
    public boolean checkLiquidationEdge(Order.OrderType orderType) {
        final BigDecimal oDQLOpenMin = persistenceService.fetchGuiLiqParams().getODQLOpenMin();

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

        return isOk;
    }

    public void resetWaitingArb() {
        if (getMarketState() == MarketState.WAITING_ARB) {
            final PlaceOrderArgs placeOrderArgs = placeOrderArgsRef.getAndSet(null);
            setMarketState(MarketState.READY);
            setFree(placeOrderArgs.getTradeId()); // we should reset ArbitrageState to READY
        }
    }

    @Override
    protected void onReadyState() {
        final PlaceOrderArgs prevArgs = placeOrderArgsRef.getAndSet(null);
        if (prevArgs != null) {
            logger.warn("WAITING_ARB was reset by onReadyState");
            tradeLogger.warn("WAITING_ARB was reset by onReadyState");
        }
        ooHangedCheckerService.stopChecker();
        iterateOpenOrdersMove();
    }

    @Override
    public ContractType getContractType() {
        return okexContractType;
    }

    @Override
    protected void iterateOpenOrdersMove(Object... iterateArgs) { // if synchronized then the queue for moving could be long
        if (getMarketState() == MarketState.SYSTEM_OVERLOADED
                || getMarketState() == MarketState.PLACING_ORDER
                || isMarketStopped()
                || getArbitrageService().getArbState() == ArbState.PRELIQ) {
            return;
        }

        if (movingInProgress) {

            // Should not happen ever, because 'synch' on method
            final String counterForLogs = getCounterName();
            final String logString = String.format("#%s No moving. Too often requests.", counterForLogs);
            logger.error(logString);
            return;

        } else {
            movingInProgress = true;
        }

        try {
            synchronized (openOrdersLock) {
                if (hasOpenOrders()) {

                    List<FplayOrder> resultOOList = new ArrayList<>();

                    for (FplayOrder openOrder : openOrders) {

//                        openOrders = openOrders.stream()
//                            .flatMap(openOrder -> {
//                        Stream<FplayOrder> optionalOrder = Stream.of(openOrder); // default -> keep the order
//                        resultOOList = Collections.singletonList(openOrder); // default -> keep the order

                        if (openOrder == null) {
                            warningLogger.warn("OO is null. ");
                            // empty, do not add
                            continue;

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
                                    //TODO keep an eye on 'hang open orders'
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
//                                            movingErrorsOverloaded.set(0);
                                    } else {
                                        resultOOList.add(openOrder); // keep the same
                                    }
//                                        } else if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.EXCEPTION_SYSTEM_OVERLOADED) {
//
//                                            if (movingErrorsOverloaded.incrementAndGet() >= maxAttempts) {
//                                                setOverloaded(null);
//                                                movingErrorsOverloaded.set(0);
//                                            }
//                                        }

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

                    Long tradeId = null;
                    // update all fplayOrders
                    for (FplayOrder resultOO : resultOOList) {
                        final LimitOrder order = resultOO.getLimitOrder();
                        arbitrageService.getDealPrices().getoPriceFact()
                                .addPriceItem(resultOO.getCounterName(), order.getId(),
                                        order.getCumulativeAmount(),
                                        order.getAveragePrice(), order.getStatus());
                        tradeId = Utils.lastTradeId(tradeId, resultOO.getTradeId());
                    }

                    this.openOrders = resultOOList;

                    if (!hasOpenOrders()) {
                        tradeLogger.warn("Free by iterateOpenOrdersMove");
                        logger.warn("Free by iterateOpenOrdersMove");
                        eventBus.send(new BtsEventBox(BtsEvent.MARKET_FREE, tradeId));
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
        final String counterForLogs = getCounterName();
        if ((counterToDiff == null || counterToDiff.counter == null || !counterToDiff.counter.equals(counterForLogs)
                || counterToDiff.diff.compareTo(diffO.val) != 0)
                && avg != null && avg.signum() != 0) {
            tradeLogger.info(String.format("#%s %s", counterForLogs, diffO.str));
            counterToDiff = new CounterToDiff(counterForLogs, diffO.val);
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
     * Workaround! <br> Request orders details. Use it before ending of a Round.
     *
     * @param avgPrice the object to be updated.
     */
    public void updateAvgPrice(String counterName, AvgPrice avgPrice) {
        final Set<String> orderIds = avgPrice.getpItems().keySet().stream()
                .filter(orderId -> !orderId.equals(AvgPrice.FAKE_ORDER_ID))
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

            orderInfos.forEach(
                    order -> avgPrice.addPriceItem(counterName, order.getId(), order.getCumulativeAmount(), order.getAveragePrice(), order.getStatus()));
        }
    }
}
