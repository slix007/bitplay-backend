package com.bitplay.arbitrage;

import com.bitplay.TwoMarketStarter;
import com.bitplay.arbitrage.BordersService.BorderVer;
import com.bitplay.arbitrage.BordersService.TradeType;
import com.bitplay.arbitrage.BordersService.TradingSignal;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.DeltaLogWriter;
import com.bitplay.arbitrage.dto.DeltaMon;
import com.bitplay.arbitrage.dto.PlBlocks;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.events.DeltaChange;
import com.bitplay.arbitrage.events.ObChangeEvent;
import com.bitplay.arbitrage.events.SigEvent;
import com.bitplay.arbitrage.events.SigType;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.arbitrage.posdiff.DqlStateService;
import com.bitplay.arbitrage.posdiff.NtUsdRecoveryService;
import com.bitplay.arbitrage.posdiff.PosDiffService;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.MarketStaticData;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.BtsEventBox;
import com.bitplay.market.events.EventBus;
import com.bitplay.market.model.Affordable;
import com.bitplay.market.model.ArbState;
import com.bitplay.market.model.BtmFokAutoArgs;
import com.bitplay.market.model.DqlState;
import com.bitplay.market.model.LiqInfo;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.OrderBookShort;
import com.bitplay.market.model.PlBefore;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.market.okcoin.OkexLimitsService;
import com.bitplay.market.okcoin.OkexSettlementService;
import com.bitplay.metrics.MetricsDictionary;
import com.bitplay.model.AccountBalance;
import com.bitplay.model.Pos;
import com.bitplay.persistance.CumPersistenceService;
import com.bitplay.persistance.DealPricesRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SignalTimeService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.CumParams;
import com.bitplay.persistance.domain.DeltaParams;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.SignalTimeParams;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderParams.Ver;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.fluent.FplayTrade;
import com.bitplay.persistance.domain.fluent.TradeMStatus;
import com.bitplay.persistance.domain.fluent.TradeStatus;
import com.bitplay.persistance.domain.fluent.dealprices.DealPrices;
import com.bitplay.persistance.domain.fluent.dealprices.FactPrice;
import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.Dql;
import com.bitplay.persistance.domain.settings.OkexContractType;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.TradingMode;
import com.bitplay.persistance.domain.settings.UsdQuoteType;
import com.bitplay.security.TraderPermissionsService;
import com.bitplay.settings.BitmexChangeOnSoService;
import com.bitplay.utils.SchedulerUtils;
import com.bitplay.utils.Utils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.Completable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Created by Sergey Shurmin on 4/18/17.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArbitrageService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private boolean initialized = false;
    private boolean firstDeltasCalculated = false;

    private final DqlStateService dqlStateService;
    @Autowired
    private NtUsdRecoveryService ntUsdRecoveryService;
    @Autowired
    private BordersService bordersService;
    @Autowired
    private PlacingBlocksService placingBlocksService;
    @Autowired
    private PersistenceService persistenceService;
    @Autowired
    private CumService cumService;
    @Autowired
    private CumPersistenceService cumPersistenceService;
    @Autowired
    private SignalService signalService;
    @Autowired
    private DiffFactBrService diffFactBrService;
    @Autowired
    private DeltasCalcService deltasCalcService;
    @Autowired
    private TraderPermissionsService traderPermissionsService;
    @Autowired
    private AfterArbService afterArbService;
    @Autowired
    private SignalTimeService signalTimeService;
    @Autowired
    private TradeService fplayTradeService;
    @Autowired
    private DealPricesRepositoryService dealPricesRepositoryService;
    @Autowired
    private SlackNotifications slackNotifications;
    @Autowired
    private HedgeService hedgeService;
    @Autowired
    private BitmexChangeOnSoService bitmexChangeOnSoService;
    @Autowired
    private MetricsDictionary metricsDictionary;
    @Autowired
    private VolatileModeSwitcherService volatileModeSwitcherService;
    @Autowired
    private OkexSettlementService okexSettlementService;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;


//    @Autowired // WARNING - this leads to "successfully sent 23 metrics to InfluxDB." should be over 70 metrics.
//    private MeterRegistry meterRegistry;


    //    private Disposable schdeduleUpdateBorders;
//    private Instant startTimeToUpdateBorders;
//    private volatile int updateBordersCounter;
    private Disposable btmFreeListener;
    private Disposable okexFreeListener;

    private volatile MarketServicePreliq firstMarketService;
    private volatile MarketServicePreliq secondMarketService;
    private volatile PosDiffService posDiffService;
    private volatile BigDecimal delta1 = BigDecimal.ZERO;
    private volatile BigDecimal delta2 = BigDecimal.ZERO;
    private volatile GuiParams params = new GuiParams();
    private volatile BigDecimal bEbest = BigDecimal.ZERO;
    private volatile BigDecimal oEbest = BigDecimal.ZERO;
    private volatile BigDecimal sumEBestUsd = BigDecimal.valueOf(-1);
    private volatile String sumBalString = "";
//    private volatile Boolean isReadyForTheArbitrage = true;
//    private Disposable theTimer;
    private Disposable theCheckBusyTimer;
    private volatile SignalType signalType = SignalType.AUTOMATIC;
    private volatile DeltaParams deltaParams = DeltaParams.createDefault();
    private volatile DeltaMon deltaMon = new DeltaMon();
    private final PublishSubject<DeltaChange> deltaChangesPublisher = PublishSubject.create();
    private final Object arbStateLock = new Object();
    private volatile ArbState arbState = ArbState.READY;
    private volatile Instant startSignalCheck = null;
    private volatile Instant startSignalTime = null;
    private volatile Instant lastCalcSumBal = null;

    private volatile Long tradeId;
    private volatile FplayTrade fplayTrade;

    // Signal delay
    private volatile Long signalDelayActivateTime;
    private volatile ScheduledFuture<?> futureSignal;
    private final ScheduledExecutorService signalDelayScheduler = SchedulerUtils.singleThreadExecutor("signal-delay-thread-%d");
    // Signal delay end
    // Affordable for UI
    private volatile DeltaName signalStatusDelta = null;
    private volatile boolean isAffordableBitmex = true;
    private volatile boolean isAffordableOkex = true;
    // Affordable for UI end

    private volatile boolean preSignalRecheckInProgress = false;

    public DealPrices getDealPrices() {
        return getDealPrices(tradeId);
    }

    public DealPrices getDealPrices(long tradeId) {
        return dealPricesRepositoryService.getFullDealPrices(tradeId);
    }

    public void init(TwoMarketStarter twoMarketStarter) {
        loadParamsFromDb();
        this.firstMarketService = twoMarketStarter.getFirstMarketService();
        this.secondMarketService = twoMarketStarter.getSecondMarketService();
        this.posDiffService = twoMarketStarter.getPosDiffService();
//        startArbitrageMonitoring();
        initArbitrageStateListener();
        initialized = true;
    }

    void sigEventCheck(SigEvent e) {
        if (!initialized) {
            return;
        }
        try {
            if (preSignalRecheckInProgress) {
                if (!e.isPreSignalReChecked()) {
                    return;
                } else {
                    preSignalRecheckInProgress = false;
                }
            }
            final OrderBook firstOrderBook;
            final PlBefore plBeforeBtm = new PlBefore();
            if (e.getBtmOrderBook() != null) {
                firstOrderBook = e.getBtmOrderBook();
            } else {
                final OrderBookShort orderBookShort = firstMarketService.getOrderBookShort();
                firstOrderBook = orderBookShort.getOb();
                if (e.getSigType() == SigType.BTM) {
                    plBeforeBtm.setCreateQuote(orderBookShort.getCreateQuoteInstant());
                    plBeforeBtm.setGetQuote(orderBookShort.getGetQuote());
                    plBeforeBtm.setSaveQuote(orderBookShort.getSaveObTime());
                }
            }

            final OrderBook secondOrderBook = e.getOkOrderBook() != null ? e.getOkOrderBook() : secondMarketService.getOrderBook();


            final BestQuotes bestQuotes = calcBestQuotesAndDeltas(firstOrderBook, secondOrderBook);

            final Boolean preSignalObReFetch = persistenceService.getSettingsRepositoryService().getSettings().getPreSignalObReFetch();
            TradingSignal prevTradingSignal = null;
            if (preSignalObReFetch != null && preSignalObReFetch) {
                if (e.isPreSignalReChecked()) {
                    final DeltaName deltaName = e.getDeltaName();
                    final TradingMode tradingMode = e.getTradingMode();
                    bestQuotes.setPreSignalReChecked(deltaName, tradingMode);
                    prevTradingSignal = e.getPrevTradingSignal();
                } else {
                    bestQuotes.setNeedPreSignalReCheck();
                }
            }

            params.setLastOBChange(new Date());
            startSignalCheck = Instant.now();
            plBeforeBtm.setSignalCheck(Instant.now());

            resetArbStatePreliq();

            doComparison(bestQuotes, firstOrderBook, secondOrderBook, prevTradingSignal, plBeforeBtm);

        } catch (NotYetInitializedException ex) {
            // do nothing
        } catch (Exception ex) {
            log.error("ERROR: sigEventCheck", ex);
            warningLogger.error("ERROR: sigEventCheck." + ex.toString());
        }
    }

    private void initArbitrageStateListener() {
        final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("arb-done-starter-%d").build();
        final ExecutorService executor = Executors.newSingleThreadExecutor(namedThreadFactory);
        final Scheduler schedulerStarter = Schedulers.from(executor);

        btmFreeListener = gotFreeListener(firstMarketService.getEventBus(), schedulerStarter, BitmexService.NAME);
        okexFreeListener = gotFreeListener(secondMarketService.getEventBus(), schedulerStarter, OkCoinService.NAME);
    }

    private Disposable gotFreeListener(EventBus eventBus, Scheduler scheduler, String marketName) {
        return eventBus.toObserverable()
                .subscribeOn(scheduler)
                .observeOn(scheduler)
                .subscribe(btsEventBox -> {
                    try {
                        if (btsEventBox.getBtsEvent() == BtsEvent.MARKET_FREE
                                || btsEventBox.getBtsEvent() == BtsEvent.MARKET_FREE_FOR_ARB) {
                            Long doneTradeId = btsEventBox.getTradeId();
                            if (doneTradeId != null) {
                                onArbDone(doneTradeId, marketName);
                            }
                        }
                    } catch (Exception e) {
                        log.error("On arb-done handling", e);
//                        deltasLogger.error("ERROR on arb-done handling" + e.toString());
                        warningLogger.error("ERROR on arb-done handling" + e.toString());
                    }
                }, throwable -> log.error("On event handling", throwable));
    }

    private void onArbDone(@NonNull Long doneTradeId, String marketName) {

        // marketState may be not set yet because of race condition
//        if (!firstMarketService.isBusy() && !secondMarketService.isBusy()) {
        log.info(String.format("onArbDone(%s, %s)", doneTradeId, marketName));

        long signalTimeSec = startSignalTime == null ? -1 : Duration.between(startSignalTime, Instant.now()).getSeconds();

        boolean conBoTryDeferredOrder = false;

        synchronized (arbStateLock) { // do not set arbInProgress=false until the whole block is done!
                // The other option is "doing stateSnapshot before doing set arbInProgress=false"

            if (fplayTrade == null) {
                log.info(String.format("onArbDone(%s, %s) finished fplayTrade == null", doneTradeId, marketName));
                return;
            }

            if (marketName.equals(BitmexService.NAME)) {
                fplayTrade.setBitmexFinishTime(new Date());
                fplayTrade.setBitmexStatus(TradeMStatus.FINISHED);
                fplayTradeService.setBitmexStatus(doneTradeId, TradeMStatus.FINISHED);
                conBoTryDeferredOrder = true;
            } else {
                fplayTrade.setOkexStatus(TradeMStatus.FINISHED);
                fplayTradeService.setOkexStatus(doneTradeId, TradeMStatus.FINISHED);
            }

            // read from DB to check isReadyToComplete
            if (tradeId != null && fplayTradeService.isReadyToComplete(tradeId)) {

                if (arbState == ArbState.IN_PROGRESS) {
                    arbState = ArbState.READY;

                    final SignalType signalTypeSnap = SignalType.valueOf(signalType.name());
                    final String counterNameSnap = fplayTrade.getCounterName();
                    final long tradeIdSnap = tradeId;

                    if (signalTimeSec > 0) {
                        signalTimeService.addSignalTime(BigDecimal.valueOf(signalTimeSec));
                    }

                    // start writeLogArbitrageIsDone();
                    final String arbIsDoneMsg = String.format("#%s is done. SignalTime=%s sec. tradeId(curr/done): %s / %s---",
                            counterNameSnap, signalTimeSec, tradeIdSnap, doneTradeId);
                    fplayTradeService.info(tradeIdSnap, counterNameSnap, arbIsDoneMsg);
                    log.info(arbIsDoneMsg);

                    // use snapshot of Params
                    final DealPrices dealPricesSnap = dealPricesRepositoryService.findByTradeId(tradeIdSnap);
                    final Settings settings = persistenceService.getSettingsRepositoryService().getSettings()
                            .toBuilder().build();
                    final Pos okexPosition = secondMarketService.getPos();

                    AfterArbTask afterArbTask = new AfterArbTask(dealPricesSnap,
                            signalTypeSnap,
                            tradeIdSnap,
                            counterNameSnap,
                            settings,
                            okexPosition,
                            (BitmexService) getFirstMarketService(),
                            (OkCoinService) getSecondMarketService(),
                            dealPricesRepositoryService,
                            cumService,
                            this,
                            new DeltaLogWriter(tradeIdSnap, counterNameSnap, fplayTradeService),
                            slackNotifications
                    );

                    afterArbService.addTask(afterArbTask); // async ending

                }
            }
        }
        log.info(String.format("onArbDone(%s, %s) finished", doneTradeId, marketName));
        if (conBoTryDeferredOrder) {
            ((OkCoinService) secondMarketService).tryPlaceDeferredOrder();// when CON_B_O
        }
    }


    public MarketServicePreliq getFirstMarketService() {
        return firstMarketService;
    }

    public MarketServicePreliq getSecondMarketService() {
        return secondMarketService;
    }

    public boolean isInitialized() {
        return getFirstMarketService() != null && getSecondMarketService() != null;
    }

    private void setTimeoutAfterStartTrading() {
//        isReadyForTheArbitrage = false;
//        if (theTimer != null) {
//            theTimer.dispose();
//        }
//        theTimer = Completable.timer(100, TimeUnit.MILLISECONDS)
//                .doOnComplete(() -> isReadyForTheArbitrage = true)
//                .doOnError(throwable -> log.error("onError timer", throwable))
//                .repeat()
//                .retry()
//                .subscribe();
        setBusyStackChecker();
    }

    public void setBusyStackChecker() {

        if (theCheckBusyTimer != null) {
            theCheckBusyTimer.dispose();
        }

        log.info("starting isBusy-6min-checker");

        theCheckBusyTimer = Completable.timer(6, TimeUnit.MINUTES, Schedulers.computation())
                .doOnComplete(() -> {

                    final String counterName = firstMarketService.getCounterName(signalType, tradeId);

                    if (firstMarketService.isMarketStopped()
                            || secondMarketService.isMarketStopped()
                            || firstMarketService.getMarketState() == MarketState.SWAP_AWAIT
                            || secondMarketService.getMarketState() == MarketState.SWAP_AWAIT
                            || firstMarketService.getMarketState() == MarketState.SWAP
                            || secondMarketService.getMarketState() == MarketState.SWAP
                            ) {
                        // do nothing

                    } else if (firstMarketService.isBusy() || secondMarketService.isBusy()) {
                        String logString = String.format("#%s Warning: busy by isBusy for 6 min. first:%s(%s), second:%s(%s). Checking bitmex openOrders...",
                                getCounter(tradeId),
                                firstMarketService.isBusy(),
                                firstMarketService.getOnlyOpenOrders().size(),
                                secondMarketService.isBusy(),
                                secondMarketService.getOnlyOpenOrders().size());
                        warningLogger.warn(logString);
                        fplayTradeService.warn(tradeId, counterName, logString);

                        firstMarketService.isReadyForArbitrageWithOOFetch();

                        logString = String.format("#%s Warning: busy by isBusy for 6 min. first:%s(%s), second:%s(%s). After check of bitmex openOrders.",
                                getCounter(tradeId),
                                firstMarketService.isBusy(),
                                firstMarketService.getOnlyOpenOrders().size(),
                                secondMarketService.isBusy(),
                                secondMarketService.getOnlyOpenOrders().size());
                        fplayTradeService.warn(tradeId, counterName, logString);
                        warningLogger.warn(logString);

                        slackNotifications.sendNotify(NotifyType.BUSY_6_MIN, logString);

                        boolean firstHanged = firstMarketService.isBusy() && !firstMarketService.hasOpenOrders();
                        boolean secondHanged = secondMarketService.isBusy() && !secondMarketService.hasOpenOrders();
                        boolean noOrders = !firstMarketService.hasOpenOrders() && !secondMarketService.hasOpenOrders();
                        if (firstHanged && noOrders) {
                            fplayTradeService.warn(tradeId, counterName, "Warning: Free Bitmex");
                            warningLogger.warn("Warning: Free Bitmex");
                            log.warn("Warning: Free Bitmex");
                            firstMarketService.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE_FORCE_RESET, firstMarketService.tryFindLastTradeId()));
                        }
                        if (secondHanged && noOrders) {
                            fplayTradeService.warn(tradeId, counterName, "Warning: Free Okcoin");
                            warningLogger.warn("Warning: Free Okcoin");
                            log.warn("Warning: Free Okcoin");
                            ((OkCoinService)getSecondMarketService()).resetWaitingArb();
                            secondMarketService.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE_FORCE_RESET, secondMarketService.tryFindLastTradeId()));
                        }

                    } else if (!firstMarketService.isReadyForArbitrageWithOOFetch() || !secondMarketService.isReadyForArbitrage()) {
                        final String logString = String.format("#%s Warning: busy for 6 min. first:isReady=%s(Orders=%s), second:isReady=%s(Orders=%s)",
                                getCounter(tradeId),
                                firstMarketService.isReadyForArbitrage(), firstMarketService.getOnlyOpenOrders().size(),
                                secondMarketService.isReadyForArbitrage(), secondMarketService.getOnlyOpenOrders().size());
                        fplayTradeService.warn(tradeId, counterName, logString);
                        warningLogger.warn(logString);
                        log.warn(logString);
                        slackNotifications.sendNotify(NotifyType.BUSY_6_MIN, logString);
                    }

                    if (firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage()) {
                        synchronized (arbStateLock) {
                            if (arbState == ArbState.IN_PROGRESS) {
                                arbState = ArbState.READY;
                                resetArbState("'busy for 6 min'");
                                slackNotifications.sendNotify(NotifyType.BUSY_6_MIN,
                                        counterName + " busy for 6 min. Arbitrage state was reset to READY");
                            }
                        }
                    }

                })
                .doOnError(throwable -> log.error("Error in isBusy-6min-checker", throwable))
                .repeat()
                .retry()
                .subscribe();
    }

    private BestQuotes calcBestQuotesAndDeltas(OrderBook bitmexOrderBook, OrderBook okCoinOrderBook) {
        BestQuotes bestQuotes = BestQuotes.empty();

        if (bitmexOrderBook != null && okCoinOrderBook != null) {

            if (okCoinOrderBook.getAsks().size() == 0 || okCoinOrderBook.getBids().size() == 0
                    || bitmexOrderBook.getAsks().size() == 0 || bitmexOrderBook.getBids().size() == 0) {
                return BestQuotes.empty();
            }

            // 1. Calc deltas
            bestQuotes = Utils.createBestQuotes(okCoinOrderBook, bitmexOrderBook);
            bestQuotes.setBtmOrderBook(bitmexOrderBook);
            if (!bestQuotes.hasEmpty()) {
                if (!firstDeltasCalculated) {
                    firstDeltasCalculated = true;
                    log.info("Started: First delta calculated");
                    warningLogger.info("Started: First delta calculated");
                    slackNotifications.sendNotify(NotifyType.AT_STARTUP, "Started: First delta calculated");
                }

                if (!deltasCalcService.isStarted()) {
                    BorderParams borderParams = persistenceService.fetchBorders();
                    deltasCalcService.initDeltasCache(borderParams.getBorderDelta());
                }

                BigDecimal delta1Update = bestQuotes.getBDelta();
                BigDecimal delta2Update = bestQuotes.getODelta();

                if (delta1Update.compareTo(delta1) != 0 || delta2Update.compareTo(delta2) != 0) {
                    deltaChangesPublisher.onNext(new DeltaChange(
                            delta1Update.compareTo(delta1) != 0 ? delta1Update : null,
                            delta2Update.compareTo(delta2) != 0 ? delta2Update : null));
                }

                delta1 = delta1Update;
                delta2 = delta2Update;
                metricsDictionary.setDeltas(delta1Update, delta2Update);
//                Metrics.counter("fplay.b_delta").increment(delta1Update.doubleValue());
//                Set<Tag> tags = Sets.newHashSet(new ImmutableTag("o_delta", "o_delta"));
//                Metrics.gauge("fplay.b_delta", delta1Update.doubleValue());
//                Metrics.gauge("fplay.o_delta", delta2Update.doubleValue());
//                meterRegistry.gauge(Dictionary.B_DELTA, delta1Update.doubleValue());
//                meterRegistry.gauge(Dictionary.O_DELTA, delta2Update.doubleValue());

                if (delta1.compareTo(deltaParams.getBDeltaMin()) < 0) {
                    deltaParams.setBDeltaMin(delta1);
                }
                if (delta1.compareTo(deltaParams.getBDeltaMax()) > 0) {
                    deltaParams.setBDeltaMax(delta1);
                }
                if (delta2.compareTo(deltaParams.getODeltaMin()) < 0) {
                    deltaParams.setODeltaMin(delta2);
                }
                if (delta2.compareTo(deltaParams.getODeltaMax()) > 0) {
                    deltaParams.setODeltaMax(delta2);
                }

                if (!Thread.interrupted()) {
                    persistenceService.storeDeltaParams(deltaParams);
                }
            } else {
                return bestQuotes;
            }
        }

        return bestQuotes;
    }

    private void doComparison(BestQuotes bestQuotes, OrderBook bitmexOrderBook, OrderBook okCoinOrderBook, TradingSignal prevTradingSignal,
                              PlBefore plBeforeBtm) {

        if (firstMarketService.isMarketStopped() || secondMarketService.isMarketStopped()
                || dqlStateService.getCommonDqlState().isActiveClose()
                || persistenceService.getSettingsRepositoryService().getSettings().getManageType().isManual()
                || okexSettlementService.isSettlementMode()
        ) {
            // do nothing
            stopSignalDelay(bestQuotes, prevTradingSignal, "market is stopped or manualType is active");
        } else {
            if (firstMarketService.isStarted()
                    && bitmexOrderBook != null
                    && okCoinOrderBook != null
                    && Utils.isObOk(bitmexOrderBook)
                    && Utils.isObOk(okCoinOrderBook)
                    && firstMarketService.accountInfoIsReady()
                    && secondMarketService.accountInfoIsReady()) {
                calcAndDoArbitrage(bestQuotes, bitmexOrderBook, okCoinOrderBook, prevTradingSignal, plBeforeBtm);
            } else {
                stopSignalDelay(bestQuotes, prevTradingSignal, "markets are not started");
            }
        }

    }

    private BordersService.TradingSignal applyMaxDelta(final BordersService.TradingSignal tradingSignal,
            BigDecimal btmMaxDelta, BigDecimal okMaxDelta, Boolean onlyOpen) {
        if (tradingSignal.tradeType != TradeType.NONE && tradingSignal.deltaVal != null && !tradingSignal.deltaVal.isEmpty()) {
            final BigDecimal delta = new BigDecimal(tradingSignal.deltaVal);
            boolean btmMaxDeltaViolate = (tradingSignal.tradeType == TradeType.DELTA1_B_SELL_O_BUY && delta.compareTo(btmMaxDelta) >= 0);
            boolean okMaxDeltaViolate = (tradingSignal.tradeType == TradeType.DELTA2_B_BUY_O_SELL && delta.compareTo(okMaxDelta) >= 0);
            if (btmMaxDeltaViolate || okMaxDeltaViolate) {
                boolean applyForOnlyOpen = onlyOpen != null ? onlyOpen : false;
                boolean isClose = tradingSignal.borderName != null && tradingSignal.borderName.contains("close");
                if (applyForOnlyOpen && isClose) { // don't apply MaxBorder  when 'onlyOpen' with 'close signal'
                    return tradingSignal;
                }
                return TradingSignal.none();
            }
        }
        return tradingSignal;
    }

    public boolean isMaxDeltaViolated(DeltaName deltaName) {
        // borders V1 only
        final BorderParams borderParams = bordersService.getBorderParams();
        if (borderParams == null) {
            return false;
        }
        final Boolean onlyOpen = borderParams.getOnlyOpen();
        final boolean applyForOnlyOpen = onlyOpen != null ? onlyOpen : false;
        final BigDecimal defaultMaxVal = BigDecimal.valueOf(9999);
        final BigDecimal posBtm = getFirstMarketService().getPos().getPositionLong();
        final BigDecimal posOk = getSecondMarketService().getPos().getPositionLong().subtract(getSecondMarketService().getPos().getPositionShort());
        final boolean isCloseBtm;
        final boolean isCloseOk;
        final BigDecimal maxDelta;
        final BigDecimal currentDelta;
        if (deltaName == DeltaName.B_DELTA) { // bitmex sell, okex buy
            isCloseBtm = posBtm.signum() > 0;
            isCloseOk = posOk.signum() < 0;
            maxDelta = (borderParams.getBtmMaxDelta() == null) ? defaultMaxVal : borderParams.getBtmMaxDelta();
            currentDelta = delta1;
        } else if (deltaName == DeltaName.O_DELTA) { // bitmex buy, okex sell
            isCloseBtm = posBtm.signum() < 0;
            isCloseOk = posOk.signum() > 0;
            maxDelta = (borderParams.getOkMaxDelta() == null) ? defaultMaxVal : borderParams.getOkMaxDelta();
            currentDelta = delta2;
        } else {
            isCloseBtm = false;
            isCloseOk = false;
            maxDelta = defaultMaxVal;
            currentDelta = delta1;
        }
        boolean isClose = isCloseBtm && isCloseOk;
        boolean anyDeltaIsOk = isClose && applyForOnlyOpen;
        boolean maxDeltaViolated = currentDelta.compareTo(maxDelta) > 0;
        return maxDeltaViolated && !anyDeltaIsOk;
    }

    private BigDecimal borderAdj(Settings settings, BigDecimal addBorder, BigDecimal source) {
        if (settings.getTradingModeState().getTradingMode() == TradingMode.VOLATILE && addBorder != null && addBorder.signum() > 0) {
            return source.add(addBorder);
        }
        return source;
    }

    public BigDecimal getBorder1() {
        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        return borderAdj(settings, settings.getSettingsVolatileMode().getBAddBorder(), params.getBorder1());
    }

    public BigDecimal getBorder2() {
        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        return borderAdj(settings, settings.getSettingsVolatileMode().getOAddBorder(), params.getBorder2());
    }

    private void calcAndDoArbitrage(BestQuotes bestQuotes, OrderBook bitmexOrderBook, OrderBook okCoinOrderBook, TradingSignal prevTradingSignal,
                                    PlBefore plBeforeBtm) {

        final BigDecimal bP = firstMarketService.getPos().getPositionLong();
        final BigDecimal oPL = secondMarketService.getPos().getPositionLong();
        final BigDecimal oPS = secondMarketService.getPos().getPositionShort();

        BorderParams borderParams = bordersService.getBorderParams();
        BigDecimal defaultMax = BigDecimal.valueOf(9999);
        BigDecimal btmMaxDelta = (borderParams == null || borderParams.getBtmMaxDelta() == null) ? defaultMax : borderParams.getBtmMaxDelta();
        BigDecimal okMaxDelta = (borderParams == null || borderParams.getOkMaxDelta() == null) ? defaultMax : borderParams.getOkMaxDelta();

        if (borderParams != null && borderParams.getActiveVersion() == Ver.V1) {
            if (bordersV1(bestQuotes, bitmexOrderBook, okCoinOrderBook, prevTradingSignal, oPL, oPS, borderParams, btmMaxDelta, okMaxDelta, plBeforeBtm)) {
                return;
            }

        } else if (borderParams != null && borderParams.getActiveVersion() == Ver.V2) {

            if (bordersV2(bestQuotes, bitmexOrderBook, okCoinOrderBook, prevTradingSignal, bP, oPL, oPS, borderParams, btmMaxDelta, okMaxDelta, plBeforeBtm)) {
                return;
            }
        } else {
            stopSignalDelay(bestQuotes, prevTradingSignal, "borders version is OFF");
        }

        return;
    }

    private boolean bordersV2(BestQuotes bestQuotes, OrderBook bitmexOrderBook, OrderBook okCoinOrderBook, TradingSignal prevTradingSignal, BigDecimal bP,
                              BigDecimal oPL, BigDecimal oPS, BorderParams borderParams, BigDecimal btmMaxDelta, BigDecimal okMaxDelta,
                              PlBefore plBeforeBtm) {
        BigDecimal defaultMax;
        boolean withWarningLogs =
                firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage() && posDiffService.checkIsPositionsEqual();

        final Affordable firstAffordable = firstMarketService.recalcAffordable();
        final Affordable secondAffordable = secondMarketService.recalcAffordable();
        TradingSignal tradingSignal;
        tradingSignal = bordersService.checkBorders(bitmexOrderBook, okCoinOrderBook, delta1, delta2, bP, oPL, oPS, withWarningLogs, firstAffordable,
                secondAffordable);
        tradingSignal = applyMaxDelta(tradingSignal, btmMaxDelta, okMaxDelta, borderParams.getOnlyOpen());

        final boolean justGotVolatileMode = volatileModeSwitcherService.trySwitchToVolatileModeBorderV2(tradingSignal);
        if (justGotVolatileMode) {
            borderParams = bordersService.getBorderParams();
            defaultMax = BigDecimal.valueOf(9999);
            btmMaxDelta = borderParams.getBtmMaxDelta() == null ? defaultMax : borderParams.getBtmMaxDelta();
            okMaxDelta = borderParams.getOkMaxDelta() == null ? defaultMax : borderParams.getOkMaxDelta();
            tradingSignal = bordersService.checkBorders(bitmexOrderBook, okCoinOrderBook, delta1, delta2, bP, oPL, oPS, withWarningLogs, firstAffordable,
                    secondAffordable);
            tradingSignal = applyMaxDelta(tradingSignal, btmMaxDelta, okMaxDelta, borderParams.getOnlyOpen());
        }

        if (tradingSignal.okexBlock == 0) {
            stopSignalDelay(bestQuotes, prevTradingSignal, "deltas v2: okexBlock==0");
            return true;
        }

        if (tradingSignal.tradeType != TradeType.NONE) {
            if (signalDelayActivateTime == null) {
                startSignalDelay(0);
            }

            if (tradingSignal.tradeType == TradeType.DELTA1_B_SELL_O_BUY) {
                signalStatusDelta = DeltaName.B_DELTA;
                if (tradingSignal.ver == PlacingBlocks.Ver.DYNAMIC) {
                    final PlBlocks bl = dynBlockDecreaseByAffordable(DeltaName.B_DELTA, BigDecimal.valueOf(tradingSignal.bitmexBlock),
                            BigDecimal.valueOf(tradingSignal.okexBlock));
                    if (bl.getBlockOkex().signum() > 0) {
                        final TradingSignal ts = bordersService.setNewBlock(tradingSignal, bl.getBlockOkex().intValueExact());
                        final BigDecimal b_block = BigDecimal.valueOf(ts.bitmexBlock);
                        final BigDecimal o_block = BigDecimal.valueOf(ts.okexBlock);
                        if (b_block.signum() > 0 && o_block.signum() > 0) {
                            final String dynDeltaLogs = composeDynBlockLogs("b_delta", bitmexOrderBook, okCoinOrderBook, b_block, o_block)
                                    + bl.getDebugLog();
//                            Instant lastObTime = Utils.getLastObTime(bitmexOrderBook, okCoinOrderBook);
                            checkAndStartTradingOnDelta1(borderParams, bestQuotes, b_block, o_block,
                                    tradingSignal, dynDeltaLogs, plBeforeBtm, oPL, oPS);
                            return true;
                        } else {
                            isAffordableBitmex = false;
                            isAffordableOkex = false;
                            warningLogger.warn("Block calc(after border2Calc): Block should be > 0, but okexBlock=" + bl.getBlockOkex());
                        }
                    } else {
                        isAffordableBitmex = false;
                        isAffordableOkex = false;
                    }
                } else {
                    final BigDecimal b_block = BigDecimal.valueOf(tradingSignal.bitmexBlock);
                    final BigDecimal o_block = BigDecimal.valueOf(tradingSignal.okexBlock);
                    checkAndStartTradingOnDelta1(borderParams, bestQuotes, b_block, o_block,
                            tradingSignal, null, plBeforeBtm, oPL, oPS);
                    return true;
                }

            } else if (tradingSignal.tradeType == TradeType.DELTA2_B_BUY_O_SELL) {
                signalStatusDelta = DeltaName.O_DELTA;
                if (tradingSignal.ver == PlacingBlocks.Ver.DYNAMIC) {
                    final PlBlocks bl = dynBlockDecreaseByAffordable(DeltaName.O_DELTA, BigDecimal.valueOf(tradingSignal.bitmexBlock),
                            BigDecimal.valueOf(tradingSignal.okexBlock));
                    if (bl.getBlockOkex().signum() > 0) {
                        final TradingSignal ts = bordersService.setNewBlock(tradingSignal, bl.getBlockOkex().intValueExact());
                        final BigDecimal b_block = BigDecimal.valueOf(ts.bitmexBlock);
                        final BigDecimal o_block = BigDecimal.valueOf(ts.okexBlock);
                        if (b_block.signum() > 0 && o_block.signum() > 0) {
                            final String dynDeltaLogs = composeDynBlockLogs("b_delta", bitmexOrderBook, okCoinOrderBook, b_block, o_block)
                                    + bl.getDebugLog();
                            checkAndStartTradingOnDelta2(borderParams, bestQuotes, b_block, o_block,
                                    tradingSignal, dynDeltaLogs, plBeforeBtm, oPL, oPS);
                            return true;
                        } else {
                            isAffordableBitmex = false;
                            isAffordableOkex = false;
                            warningLogger.warn("Block calc(after border2Calc): Block should be > 0, but okexBlock=" + bl.getBlockOkex());
                        }
                    } else {
                        isAffordableBitmex = false;
                        isAffordableOkex = false;
                    }
                } else {
                    final BigDecimal b_block = BigDecimal.valueOf(tradingSignal.bitmexBlock);
                    final BigDecimal o_block = BigDecimal.valueOf(tradingSignal.okexBlock);
                    checkAndStartTradingOnDelta2(borderParams, bestQuotes, b_block, o_block,
                            tradingSignal, null, plBeforeBtm, oPL, oPS);
                    return true;
                }
            }
        } else {
            stopSignalDelay(bestQuotes, prevTradingSignal, "deltas v2: tradingSignal.tradeType == NONE");
        }
        return false;
    }

    private boolean bordersV1(BestQuotes bestQuotes, OrderBook bitmexOrderBook, OrderBook okCoinOrderBook, TradingSignal prevTradingSignal, BigDecimal oPL,
                              BigDecimal oPS, BorderParams borderParams, BigDecimal btmMaxDelta, BigDecimal okMaxDelta,
                              PlBefore plBeforeBtm) {
        BigDecimal border1 = getBorder1();
        BigDecimal border2 = getBorder2();

        if (delta1.compareTo(border1) >= 0 && delta1.compareTo(btmMaxDelta) < 0) {
            volatileModeSwitcherService.trySwitchToVolatileMode(delta1, border1, new BtmFokAutoArgs(delta1, border1, border1.toPlainString()));
            signalStatusDelta = DeltaName.B_DELTA;

            if (signalDelayActivateTime == null) {
                startSignalDelay(0);
            }

            PlBlocks plBlocks = placingBlocksService.getPlacingBlocks(bitmexOrderBook, okCoinOrderBook, border1, DeltaName.B_DELTA, oPL, oPS);
            if (plBlocks.getBlockOkex().signum() == 0) {
                //noinspection Duplicates
                isAffordableBitmex = false;
                isAffordableOkex = false;
                return true;
            }
            String dynDeltaLogs = null;
            if (plBlocks.isDynamic()) {
                plBlocks = dynBlockDecreaseByAffordable(DeltaName.B_DELTA, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex());
                dynDeltaLogs = composeDynBlockLogs("b_delta", bitmexOrderBook, okCoinOrderBook, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex())
                        + plBlocks.getDebugLog();
            }

            if (plBlocks.getBlockOkex().signum() > 0) {
                final TradingSignal tradingSignal = TradingSignal.createOnBorderV1(plBlocks.getVer(),
                        plBlocks.getBlockBitmex(), plBlocks.getBlockOkex(), TradeType.DELTA1_B_SELL_O_BUY, delta1, border1);
                checkAndStartTradingOnDelta1(borderParams, bestQuotes, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex(),
                        tradingSignal, dynDeltaLogs, plBeforeBtm, oPL, oPS);
                return true;
            } else {
                isAffordableBitmex = false;
                isAffordableOkex = false;
            }
        } else if (delta2.compareTo(border2) >= 0 && delta2.compareTo(okMaxDelta) < 0) {
            volatileModeSwitcherService.trySwitchToVolatileMode(delta2, border2, new BtmFokAutoArgs(delta2, border2, border2.toPlainString()));
            signalStatusDelta = DeltaName.O_DELTA;

            if (signalDelayActivateTime == null) {
                startSignalDelay(0);
            }

            PlBlocks plBlocks = placingBlocksService.getPlacingBlocks(bitmexOrderBook, okCoinOrderBook, border2, DeltaName.O_DELTA, oPL, oPS);
            if (plBlocks.getBlockOkex().signum() == 0) {
                //noinspection Duplicates
                isAffordableBitmex = false;
                isAffordableOkex = false;
                return true;
            }
            String dynDeltaLogs = null;
            if (plBlocks.isDynamic()) {
                plBlocks = dynBlockDecreaseByAffordable(DeltaName.O_DELTA, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex());
                dynDeltaLogs = composeDynBlockLogs("o_delta", bitmexOrderBook, okCoinOrderBook, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex())
                        + plBlocks.getDebugLog();
            }
            if (plBlocks.getBlockOkex().signum() > 0) {
                final TradingSignal tradingSignal = TradingSignal.createOnBorderV1(plBlocks.getVer(),
                        plBlocks.getBlockBitmex(), plBlocks.getBlockOkex(), TradeType.DELTA2_B_BUY_O_SELL, delta2, border2);
                checkAndStartTradingOnDelta2(borderParams, bestQuotes, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex(),
                        tradingSignal, dynDeltaLogs, plBeforeBtm, oPL, oPS);
                return true;
            } else {
                isAffordableBitmex = false;
                isAffordableOkex = false;
            }
        } else {
            stopSignalDelay(bestQuotes, prevTradingSignal, "deltas v1 do not cross borders");
        }
        return false;
    }

    private String composeDynBlockLogs(String deltaName, OrderBook bitmexOrderBook, OrderBook okCoinOrderBook, BigDecimal b_block, BigDecimal o_block) {
        final String bMsg = Utils.getTenAskBid(bitmexOrderBook, "",
                "Bitmex OrderBook");
        final String oMsg = Utils.getTenAskBid(okCoinOrderBook, "",
                "Okex OrderBook");
        final PlacingBlocks placingBlocks = persistenceService.getSettingsRepositoryService().getSettings().getPlacingBlocks();
        return String.format("%s: Dynamic: dynMaxBlockUsd=%s, isEth=%s, cm=%s, b_block=%s, o_block=%s\n%s\n%s. ",
                deltaName,
                placingBlocks.getDynMaxBlockUsd(),
                placingBlocks.isEth(),
                placingBlocks.getCm(),
                b_block, o_block,
                bMsg, oMsg);
    }

    public boolean isArbStateStopped() {
        synchronized (arbStateLock) {
            return arbState == ArbState.STOPPED;
        }
    }

    public void setArbStateStopped() {
        setArbState(ArbState.STOPPED);
    }

    private void setArbState(ArbState newState) {
        synchronized (arbStateLock) {
            log.info("set ArbState." + newState);
            arbState = newState;
        }
    }

    public void resetArbStatePreliq() {
        if (dqlStateService.isPreliq()) {
            if (firstMarketService.noPreliq() && secondMarketService.noPreliq()) {
                final DqlState dqlState1 = firstMarketService.updateDqlState();
                final DqlState dqlState2 = secondMarketService.updateDqlState();
                if (firstMarketService.getMarketState().isActiveClose() && !dqlState1.isActiveClose()) {
                    firstMarketService.setMarketState(MarketState.READY);
                }
                if (secondMarketService.getMarketState().isActiveClose() && !dqlState2.isActiveClose()) {
                    secondMarketService.setMarketState(MarketState.READY);
                }
            }
        }
    }

    private void checkAndStartTradingOnDelta1(BorderParams borderParams, final BestQuotes bestQuotes, final BigDecimal b_block_input,
                                              final BigDecimal o_block_input, final TradingSignal tradingSignal, String dynamicDeltaLogs,
                                              final PlBefore beforeSignalMetrics, BigDecimal oPL, BigDecimal oPS) {
        final BigDecimal ask1_o = bestQuotes.getAsk1_o();
        final BigDecimal bid1_p = bestQuotes.getBid1_p();

        final OkexLimitsService okLimits = ((OkCoinService) this.secondMarketService).getOkexLimitsService();
        final PlacingType okexPlacingType = persistenceService.getSettingsRepositoryService().getSettings().getOkexPlacingType();
        final boolean okexOutsideLimits = okLimits.outsideLimitsOnSignal(DeltaName.B_DELTA, okexPlacingType);
        //noinspection Duplicates
        if (firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage()
                && posDiffService.checkIsPositionsEqual()
                && !firstMarketService.isMarketStopped()
                && !secondMarketService.isMarketStopped() && !okexOutsideLimits
                && firstMarketService.checkLiquidationEdge(OrderType.ASK)
                && secondMarketService.checkLiquidationEdge(OrderType.BID)
        ) {

            final PlBlocks plBlocks = adjustByNtUsd(DeltaName.B_DELTA, b_block_input, o_block_input, oPL, oPS);
            final BigDecimal b_block = plBlocks.getBlockBitmex();
            final BigDecimal o_block = plBlocks.getBlockOkex();

            if (checkAffordable(DeltaName.B_DELTA, b_block, o_block)) {

                synchronized (arbStateLock) {
                    if (arbState == ArbState.READY) {

                        if (signalDelayActivateTime == null) {
                            startSignalDelay(0);
                        } else if (isSignalDelayExceeded()) {
                            if (bestQuotes.isNeedPreSignalReCheck()) {
                                preSignalReCheck(DeltaName.B_DELTA, tradingSignal);
                            } else {
                                arbState = ArbState.IN_PROGRESS;
                                final TradingSignal trSig = tradingSignal.changeBlocks(b_block, o_block);
                                startTradingOnDelta1(borderParams, bestQuotes, b_block, o_block, trSig, dynamicDeltaLogs,
                                        ask1_o,
                                        bid1_p, beforeSignalMetrics, b_block_input, o_block_input);
                            }
                        }
                    }
                }
            }

        } else {
//            stopSignalDelay(); - do not use. It reset the signalDelay during a signal.
        }
    }

    public void restartSignalDelay() {
        if (signalDelayActivateTime != null) {
            long passedDelay = Instant.now().toEpochMilli() - signalDelayActivateTime;
            stopSignalDelay(null, null, "restart signal delay from UI");
            startSignalDelay(passedDelay);
        }
    }

    private void startSignalDelay(long passedDelayMs) {
        signalDelayActivateTime = Instant.now().toEpochMilli() - passedDelayMs;
        Integer signalDelayMs = persistenceService.getSettingsRepositoryService().getSettings().getSignalDelayMs();
        long timeToSignalMs = signalDelayMs - passedDelayMs;
        if (timeToSignalMs <= 0) {
            timeToSignalMs = 1;
        }

        futureSignal = signalDelayScheduler.schedule(() -> {
            // to make sure that it will happen in the 'signalDeltayMs period'
            applicationEventPublisher.publishEvent(new ObChangeEvent(new SigEvent(SigType.BTM, null)));
        }, timeToSignalMs, TimeUnit.MILLISECONDS);

    }

    public String getTimeToSignal() {
        if (futureSignal != null && signalDelayActivateTime != null) {
            if(!futureSignal.isDone()) {
                long delay = futureSignal.getDelay(TimeUnit.MILLISECONDS);
                return String.valueOf(delay);
            } else {
                return "_ready_";
            }
        }
        return "_none_";
    }

    private void stopSignalDelay(BestQuotes bestQuotes, TradingSignal prevTradingSignal, String stopReason) {
        printLogsAfterPreSignalRecheckStop(bestQuotes, prevTradingSignal, stopReason);
        signalDelayActivateTime = null;
        if (futureSignal != null && !futureSignal.isDone()) {
            futureSignal.cancel(false);
        }
        signalStatusDelta = null;
        isAffordableBitmex = true;
        isAffordableOkex = true;

        volatileModeSwitcherService.stopVmTimer();
    }

    private void printLogsAfterPreSignalRecheckStop(BestQuotes bestQuotes, TradingSignal prevTradingSignal, String stopReason) {
        if (bestQuotes != null && bestQuotes.isPreSignalReChecked()) {
            if (bestQuotes.getDeltaName() == null || bestQuotes.getTradingMode() == null) {
                // Illegal arguments
                log.warn("IllegalArguments on stopSignalDelay. " + bestQuotes);
            } else {
                // Stop when was pre signal re-check
                if (bestQuotes.getDeltaName() == DeltaName.B_DELTA) {
                    cumPersistenceService.incObRecheckUnstartedVert1(bestQuotes.getTradingMode());
                } else {
                    cumPersistenceService.incObRecheckUnstartedVert2(bestQuotes.getTradingMode());
                }

                // Vertical was not started, b_delta (xx) <> b_border (xx), o_delta (xx) <> o_border (xx)
                // (писать знаки > или < в соответствии с реальными значениями).
                String msg = String.format("After 'Recheck OB after SD' Vertical was not started %s. Stop reason: %s ",
                        bestQuotes.toStringEx(), stopReason);
//                BorderParams borderParams = bordersService.getBorderParams();
//                msg += borderParams.getBordersV2().toStringTables();
                if (prevTradingSignal != null) {
                    final BigDecimal minBorder = prevTradingSignal.getMinBorder();
                    if (minBorder != null) {
                        if (prevTradingSignal.tradeType == TradeType.DELTA1_B_SELL_O_BUY) {
                            final BigDecimal bDelta = bestQuotes.getBDelta();
                            msg += String.format("b_delta (%s) %s b_border (%s)", bDelta,
                                    bDelta.compareTo(minBorder) > 0 ? ">" : "<",
                                    minBorder);
                        } else {
                            final BigDecimal oDelta = bestQuotes.getODelta();
                            msg += String.format("o_delta (%s) %s o_border (%s)", oDelta,
                                    oDelta.compareTo(minBorder) > 0 ? ">" : "<",
                                    minBorder);
                        }
                    } else {
                        msg += "previous tradingSignal: " + prevTradingSignal.toString();
                    }
                } else {
                    msg += "no previous tradingSignal";
                }
                log.info(msg);
                warningLogger.info(msg);
            }
        }
    }

    private boolean isSignalDelayExceeded() {
        if (signalDelayActivateTime == null) {
            return false;
        }
        final Integer signalDelayMs = persistenceService.getSettingsRepositoryService().getSettings().getSignalDelayMs();
        return Instant.now().toEpochMilli() - signalDelayActivateTime > signalDelayMs;
    }

    private boolean getIsConBo() {
        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        return settings.getArbScheme().isConBo()
                || bitmexChangeOnSoService.toConBoActive();
    }

    private void startTradingOnDelta1(BorderParams borderParams, BestQuotes bestQuotes, BigDecimal b_block, BigDecimal o_block,
                                      TradingSignal tradingSignal, String dynamicDeltaLogs, BigDecimal ask1_o, BigDecimal bid1_p,
                                      PlBefore plBeforeBtm,
                                      BigDecimal b_block_input, BigDecimal o_block_input) {

        log.info("START SIGNAL 1");
        startSignalTime = Instant.now();
        plBeforeBtm.setSignalTime(Instant.now());

        printAdjWarning(b_block_input, o_block_input, b_block, o_block);

        final DeltaName deltaName = DeltaName.B_DELTA;
        final Settings s = persistenceService.getSettingsRepositoryService().getSettings();
        final TradingMode tradingMode = s.getTradingModeState().getTradingMode();
        final BigDecimal delta1 = this.delta1;
        final BigDecimal delta2 = this.delta2;
        final FplayTrade fplayTrade = createCounterOnStartTrade(ask1_o, bid1_p, tradingSignal, getBorder1(), delta1, deltaName, tradingMode);
        final String counterName = fplayTrade.getCounterName();

        final DealPrices dealPrices = setTradeParamsOnStart(borderParams, bestQuotes, b_block, o_block, dynamicDeltaLogs, bid1_p, ask1_o, b_block_input,
                o_block_input, deltaName,
                counterName, tradingMode, delta1, delta2, tradingSignal.toBtmFokAutoArgs(), fplayTrade);

        slackNotifications.sendNotify(NotifyType.TRADE_SIGNAL, String.format("#%s TRADE_SIGNAL(b_delta) b_block=%s o_block=%s", counterName, b_block, o_block));

        // in scheme MT2 Okex should be the first
        final boolean isConBo = getIsConBo();
        signalService.placeOkexOrderOnSignal(Order.OrderType.BID, o_block, bestQuotes, dealPrices.getOkexPlacingType(),
                counterName, tradeId, isConBo, null, s.getArbScheme());
        final CompletableFuture<Void> btmStartPromise =
                signalService.placeBitmexOrderOnSignal(Order.OrderType.ASK, b_block, bestQuotes, dealPrices.getBtmPlacingType(),
                        counterName, tradeId, plBeforeBtm, isConBo, tradingSignal.toBtmFokAutoArgs());

        setTimeoutAfterStartTrading();

        saveParamsToDb();

        btmStartPromise.whenComplete((aVoid, e) -> vertHasStartedLog(tradeId, counterName));
    }

    private DealPrices setTradeParamsOnStart(BorderParams borderParams, BestQuotes bestQuotes, BigDecimal b_block, BigDecimal o_block, String dynamicDeltaLogs,
                                             BigDecimal bPricePlan, BigDecimal oPricePlan, BigDecimal b_block_input, BigDecimal o_block_input,
                                             DeltaName deltaName, String counterName,
                                             TradingMode tradingMode, BigDecimal delta1, BigDecimal delta2,
                                             BtmFokAutoArgs btmFokAutoArgs, FplayTrade fplayTrade) {
        final Long tradeId = fplayTrade.getId();
        int pos_bo = diffFactBrService.getCurrPos(borderParams.getPosMode());

        firstMarketService.setBusy(counterName, MarketState.STARTING_VERT);
        secondMarketService.setBusy(counterName, MarketState.STARTING_VERT);

        setSignalType(SignalType.AUTOMATIC);
        this.fplayTrade = fplayTrade;
        this.tradeId = tradeId;

        if (dynamicDeltaLogs != null) {
            fplayTradeService.info(tradeId, counterName, String.format("#%s %s", counterName, dynamicDeltaLogs));
        }

        // persistenceService.fetchBorders(); // this is for Current mode (not volatile)
//        final BorderParams borderParamsForCurrentMode = bordersService.getBorderParams(); // current/volatile
        final BigDecimal border1 = params.getBorder1();
        final BigDecimal border2 = params.getBorder2();

        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        final PlacingType okexPlacingType = settings.getOkexPlacingType();
        final PlacingType btmPlacingType = bitmexChangeOnSoService.getPlacingType();

        Integer bitmexScale = firstMarketService.getContractType().getScale();
        Integer okexScale = secondMarketService.getContractType().getScale();
        FactPrice btmPriceFact = new FactPrice(MarketStaticData.BITMEX, b_block, bitmexScale);
        FactPrice okPriceFact = new FactPrice(MarketStaticData.OKEX, o_block, okexScale);

        Integer plan_pos_ao = DealPrices.calcPlanAfterOrderPos(b_block_input, o_block_input, pos_bo, borderParams.getPosMode(), deltaName);
        BigDecimal btmBlock = b_block;
        BigDecimal okBlock = o_block;

        if (b_block.signum() == 0) {
            btmBlock = b_block_input;
            btmPriceFact = new FactPrice(MarketStaticData.BITMEX, b_block, bitmexScale);
            btmPriceFact.setOpenPrice(bPricePlan);
            btmPriceFact.setFakeOrder(b_block_input, bPricePlan);
        }
        if (o_block.signum() == 0) {
            okBlock = o_block_input;
            okPriceFact = new FactPrice(MarketStaticData.OKEX, o_block, okexScale);
            okPriceFact.setOpenPrice(oPricePlan);
            okPriceFact.setFakeOrder(o_block_input, oPricePlan);
        }

        final DealPrices dealPrices = DealPrices.builder()
                .btmPlacingType(btmPlacingType)
                .okexPlacingType(okexPlacingType)
                .border1(border1)
                .border2(border2)
                .bBlock(btmBlock)
                .oBlock(okBlock)
                .delta1Plan(delta1)
                .delta2Plan(delta2)
                .bPricePlan(bPricePlan)
                .oPricePlan(oPricePlan)
                .oPricePlanOnStart(oPricePlan)
                .deltaName(deltaName)
                .bestQuotes(bestQuotes)
                .bPriceFact(btmPriceFact)
                .oPriceFact(okPriceFact)
                .borderParamsOnStart(borderParams)
                .pos_bo(pos_bo)
                .plan_pos_ao(plan_pos_ao)
                .tradingMode(tradingMode)
                .tradeId(tradeId)
                .counterName(counterName)
                .btmFokAutoArgs(btmFokAutoArgs)
                .build();

        if (dealPrices.getPlan_pos_ao().equals(dealPrices.getPos_bo())) {
            fplayTradeService.warn(tradeId, counterName, "WARNING: pos_bo==pos_ao==" + dealPrices.getPos_bo() + ". " + dealPrices.toString());
            warningLogger.warn("WARNING: pos_bo==pos_ao==" + dealPrices.getPos_bo() + ". " + dealPrices.toString());
        }

        dealPricesRepositoryService.saveNew(dealPrices);
        fplayTradeService.setTradingMode(tradeId, tradingMode);

        fplayTradeService.info(tradeId, counterName, String.format("#%s Trading mode = %s", counterName, tradingMode.getFullName()));
        fplayTradeService.info(tradeId, counterName, String.format("#%s is started, tradeId=%s ---", counterName, tradeId));
        return dealPrices;
    }

    private void checkAndStartTradingOnDelta2(BorderParams borderParams,
                                              final BestQuotes bestQuotes, final BigDecimal b_block_input, final BigDecimal o_block_input,
                                              final TradingSignal tradingSignal, String dynamicDeltaLogs,
                                              final PlBefore beforeSignalMetrics, BigDecimal oPL, BigDecimal oPS) {
        final BigDecimal ask1_p = bestQuotes.getAsk1_p();
        final BigDecimal bid1_o = bestQuotes.getBid1_o();

        final OkCoinService okCoinService = (OkCoinService) this.secondMarketService;
        final OkexLimitsService okLimits = okCoinService.getOkexLimitsService();
        final PlacingType okexPlacingType = persistenceService.getSettingsRepositoryService().getSettings().getOkexPlacingType();
        final boolean okexOutsideLimits = okLimits.outsideLimitsOnSignal(DeltaName.O_DELTA, okexPlacingType);
        //noinspection Duplicates
        if (firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage()
                && posDiffService.checkIsPositionsEqual()
                && !firstMarketService.isMarketStopped()
                && !secondMarketService.isMarketStopped() && !okexOutsideLimits
                && firstMarketService.checkLiquidationEdge(OrderType.BID)
                && secondMarketService.checkLiquidationEdge(OrderType.ASK)
        ) {

            final PlBlocks plBlocks = adjustByNtUsd(DeltaName.O_DELTA, b_block_input, o_block_input, oPL, oPS);
            final BigDecimal b_block = plBlocks.getBlockBitmex();
            final BigDecimal o_block = plBlocks.getBlockOkex();

            if (checkAffordable(DeltaName.O_DELTA, b_block, o_block)) {

                synchronized (arbStateLock) {
                    if (arbState == ArbState.READY) {

                        if (signalDelayActivateTime == null) {
                            startSignalDelay(0);
                        } else if (isSignalDelayExceeded()) {
                            if (bestQuotes.isNeedPreSignalReCheck()) {
                                preSignalReCheck(DeltaName.O_DELTA, tradingSignal);
                            } else {
                                arbState = ArbState.IN_PROGRESS;
                                final TradingSignal trSig = tradingSignal.changeBlocks(b_block, o_block);
                                startTradingOnDelta2(borderParams, bestQuotes, b_block, o_block, trSig, dynamicDeltaLogs,
                                        ask1_p,
                                        bid1_o, beforeSignalMetrics, b_block_input, o_block_input);
                            }
                        }
                    }
                }
            }

        } else {
//            stopSignalDelay(); - do not use. It reset the signalDelay during a signal.
        }
    }

    private void preSignalReCheck(DeltaName deltaName, TradingSignal prevTradingSignal) {
        OrderBook btmOb = firstMarketService.getOrderBook();
        OrderBook okOb = secondMarketService.getOrderBook();
        try {
            preSignalRecheckInProgress = true;
            final Instant start = Instant.now();
            btmOb = firstMarketService.fetchOrderBookMain();
            okOb = secondMarketService.fetchOrderBookMain();
            final BestQuotes bestQuotes = calcBestQuotesAndDeltas(btmOb, okOb);
            bestQuotes.setBtmOrderBook(btmOb);
            final Instant end = Instant.now();
            final long ms = Duration.between(start, end).toMillis();
            final String msg = String.format("Recheck OB after SD %s ms. %s, btmObTimestamp=%s, okexObTimestamp=%s",
                    ms, bestQuotes.toStringEx(), Utils.dateToString(btmOb.getTimeStamp()), Utils.dateToString(okOb.getTimeStamp()));
            log.info(msg);
            warningLogger.info(msg);
        } finally {
            final TradingMode tradingMode = persistenceService.getSettingsRepositoryService().getSettings().getTradingModeState().getTradingMode();
            applicationEventPublisher.publishEvent((new ObChangeEvent(
                    new SigEvent(SigType.BTM, Instant.now(), true, deltaName, tradingMode, prevTradingSignal, btmOb, okOb)
            )));
        }

//        params.setLastOBChange(new Date());
//        resetArbStatePreliq();
//        doComparison(bestQuotes, btmOb, okOb, prevTradingSignal);
    }

    private void printAdjWarning(BigDecimal b_block_input, BigDecimal o_block_input, BigDecimal b_block, BigDecimal o_block) {
        if (b_block.compareTo(b_block_input) != 0 || o_block.compareTo(o_block_input) != 0) {
            final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
            BigDecimal multiplicity = settings.getNtUsdMultiplicityOkex();

            String msg = String.format("adjustByNtUsd. Before: b=%s, o=%s. After: b=%s, o=%s. ntUsd=%s, ntUsdMultiplicityOkex=%s",
                    b_block_input, o_block_input, b_block, o_block, getNtUsd(), multiplicity);
            log.info(msg);
        }
    }

    private void startTradingOnDelta2(BorderParams borderParams, BestQuotes bestQuotes, BigDecimal b_block, BigDecimal o_block,
                                      TradingSignal tradingSignal, String dynamicDeltaLogs, BigDecimal ask1_p, BigDecimal bid1_o,
                                      PlBefore plBeforeBtm,
                                      BigDecimal b_block_input, BigDecimal o_block_input) {

        log.info("START SIGNAL 2");
        startSignalTime = Instant.now();
        plBeforeBtm.setSignalTime(Instant.now());

        printAdjWarning(b_block_input, o_block_input, b_block, o_block);

        final DeltaName deltaName = DeltaName.O_DELTA;
        final Settings s = persistenceService.getSettingsRepositoryService().getSettings();
        final TradingMode tradingMode = s.getTradingModeState().getTradingMode();
        final BigDecimal delta1 = this.delta1;
        final BigDecimal delta2 = this.delta2;
        final FplayTrade fplayTrade = createCounterOnStartTrade(ask1_p, bid1_o, tradingSignal, getBorder2(), delta2, deltaName, tradingMode);
        final String counterName = fplayTrade.getCounterName();

        final DealPrices dealPrices = setTradeParamsOnStart(borderParams, bestQuotes, b_block, o_block, dynamicDeltaLogs, ask1_p, bid1_o, b_block_input,
                o_block_input, deltaName,
                counterName, tradingMode, delta1, delta2, tradingSignal.toBtmFokAutoArgs(), fplayTrade);

        slackNotifications.sendNotify(NotifyType.TRADE_SIGNAL, String.format("#%s TRADE_SIGNAL(o_delta) b_block=%s o_block=%s", counterName, b_block, o_block));

        // in scheme MT2 Okex should be the first
        final boolean isConBo = getIsConBo();
        signalService.placeOkexOrderOnSignal(Order.OrderType.ASK, o_block, bestQuotes, dealPrices.getOkexPlacingType(),
                counterName, tradeId, isConBo, null, s.getArbScheme());
        final CompletableFuture<Void> btmStartPromise = signalService.placeBitmexOrderOnSignal(OrderType.BID, b_block, bestQuotes,
                dealPrices.getBtmPlacingType(), counterName, tradeId, plBeforeBtm, isConBo, tradingSignal.toBtmFokAutoArgs());

        setTimeoutAfterStartTrading();

        saveParamsToDb();

        btmStartPromise.whenComplete((aVoid, e) -> vertHasStartedLog(tradeId, counterName));
    }

    private void vertHasStartedLog(Long tradeId, String counterName) {
        final String msg = String.format("#%s:%s bitmex orders sent. ---", counterName, tradeId);
        log.info(msg);
        fplayTradeService.info(tradeId, counterName, msg);
    }

    private FplayTrade createCounterOnStartTrade(BigDecimal ask1_X, BigDecimal bid1_X, final TradingSignal tradingSignal,
                                                 final BigDecimal borderX, final BigDecimal deltaX, final DeltaName deltaName, TradingMode tradingMode) {

        if (deltaName.getDeltaNumber().equals("1")) {
            cumPersistenceService.incCounter1(tradingMode);
        } else {
            cumPersistenceService.incCounter2(tradingMode);
        }

        final CumParams cumParams = cumService.getTotalCommon();
        final Integer counter1 = cumParams.getVert1();
        final Integer counter2 = cumParams.getVert2();
        final Integer cc1 = cumParams.getCompletedVert1();
        final Integer cc2 = cumParams.getCompletedVert2();

        String iterationMarker = "";
        if (counter1.equals(counter2)) {
            iterationMarker = "whole iteration";
        }

        setSignalType(SignalType.AUTOMATIC);
        final String counterName = String.valueOf(counter1 + counter2);

        final FplayTrade fplayTrade = fplayTradeService.createTrade(counterName, deltaName,
                ((BitmexContractType) firstMarketService.getContractType()),
                ((OkexContractType) secondMarketService.getContractType()));
        final Long tradeId = fplayTrade.getId();
        fplayTradeService.info(tradeId, counterName, "------------------------------------------");

        fplayTradeService.info(tradeId, counterName, String.format("count=%s+%s=%s(completed=%s+%s=%s) %s",
                counter1, counter2, counter1 + counter2,
                cc1, cc2, cc1 + cc2,
                iterationMarker));

        fplayTradeService.info(tradeId, counterName, String.format("delta%s=%s-%s=%s; %s",
                deltaName.getDeltaNumber(),
                bid1_X.toPlainString(), ask1_X.toPlainString(),
                deltaX.toPlainString(),
                (tradingSignal == null || tradingSignal.borderVer != BorderVer.borderV2)
                        ? (String.format("b%s=%s, %s: ", deltaName.getDeltaNumber(), borderX.toPlainString(), tradingSignal))
                        : ("borderV2:" + tradingSignal.toString())
        ));

        if (tradingSignal != null && tradingSignal.blockOnceWarn != null && tradingSignal.blockOnceWarn.length() > 0) {
            warningLogger.warn("block_once warn: " + tradingSignal.blockOnceWarn + "; " + tradingSignal.toString());
        }

        printSumBal(tradeId, counterName);

        return fplayTrade;
    }

    @SuppressWarnings("Duplicates")
    @Scheduled(initialDelay = 10 * 1000, fixedDelay = 1000)
    public void calcSumBalForGui() {
        if (firstMarketService == null || secondMarketService == null) {
            return; // NotYetInitializedException
        }

        lastCalcSumBal = Instant.now();
        final AccountBalance firstAccount = firstMarketService.getFullBalance().getAccountBalance();
        final AccountBalance secondAccount = secondMarketService.getFullBalance().getAccountBalance();
        if (firstAccount != null && secondAccount != null) {
            final BigDecimal bW = firstAccount.getWallet();
            final BigDecimal bEMark = firstAccount.getEMark() != null ? firstAccount.getEMark() : BigDecimal.ZERO;
            bEbest = firstAccount.getEBest() != null ? firstAccount.getEBest() : BigDecimal.ZERO;
            final BigDecimal bEAvg = firstAccount.getEAvg() != null ? firstAccount.getEAvg() : BigDecimal.ZERO;
            final BigDecimal bU = firstAccount.getUpl();
            final BigDecimal bM = firstAccount.getMargin();
            final BigDecimal bA = firstAccount.getAvailable();

            BigDecimal oW = secondAccount.getWallet();
            BigDecimal oELast = secondAccount.getELast() != null ? secondAccount.getELast() : BigDecimal.ZERO;
            oEbest = secondAccount.getEBest() != null ? secondAccount.getEBest() : BigDecimal.ZERO;
            BigDecimal oEAvg = secondAccount.getEAvg() != null ? secondAccount.getEAvg() : BigDecimal.ZERO;
            BigDecimal oM = secondAccount.getMargin();
            BigDecimal oU = secondAccount.getUpl();
            BigDecimal oA = secondAccount.getAvailable();

            if (bW == null || oW == null) {
                throw new IllegalStateException(String.format("Balance is not yet defined. bW=%s, oW=%s", bW, oW));
            }

            boolean isEth = secondMarketService.getEthBtcTicker() != null && firstMarketService.getContractType().isEth();

            BigDecimal oEbestWithColdStorageEth = oEbest;
            if (isEth) {
                final BigDecimal coldStorageEth = persistenceService.getSettingsRepositoryService().getSettings().getColdStorageEth();
                oW = oW.add(coldStorageEth);
                oELast = oELast.add(coldStorageEth);
                oEbestWithColdStorageEth = oEbest.add(coldStorageEth);
                oEAvg = oEAvg.add(coldStorageEth);

                // okex: convert eth to btc
                BigDecimal ethBtcBid1 = secondMarketService.getEthBtcTicker().getBid();
                oW = oW.multiply(ethBtcBid1);
                oELast = oELast.multiply(ethBtcBid1);
                oEbest = oEbest.multiply(ethBtcBid1);
                oEbestWithColdStorageEth = oEbestWithColdStorageEth.multiply(ethBtcBid1);
                oEAvg = oEAvg.multiply(ethBtcBid1);
                oM = oM.multiply(ethBtcBid1);
                oU = oU.multiply(ethBtcBid1);
                oA = oA.multiply(ethBtcBid1);
            }

            final BigDecimal coldStorageBtc = persistenceService.getSettingsRepositoryService().getSettings().getColdStorageBtc();
            final BigDecimal sumW = bW.add(oW).add(coldStorageBtc).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumE = bEMark.add(oELast).add(coldStorageBtc).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumEBest = bEbest.add(oEbestWithColdStorageEth).add(coldStorageBtc).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumEAvg = bEAvg.add(oEAvg).add(coldStorageBtc).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumUpl = bU.add(oU).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumM = bM.add(oM).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumA = bA.add(oA).setScale(8, BigDecimal.ROUND_HALF_UP);

            final BigDecimal usdQuote = getUsdQuote();

            final BigDecimal sumEBestUsdCurr = sumEBest.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP);

            final BigDecimal btmQu = isEth
                    ? Utils.calcQuAvg(firstMarketService.getOrderBookXBTUSD())
                    : Utils.calcQuAvg(firstMarketService.getOrderBook());
            sumEBestUsd = sumEBest.multiply(btmQu).setScale(2, BigDecimal.ROUND_HALF_UP);

            sumBalString = String.format("s_bal=w%s_%s, s_e_%s_%s, s_e_best%s_%s, s_e_avg%s_%s, u%s_%s, m%s_%s, a%s_%s, usd_qu%s",
                    sumW.toPlainString(), sumW.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumE.toPlainString(), sumE.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumEBest.toPlainString(), sumEBestUsdCurr,
                    sumEAvg.toPlainString(), sumEAvg.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumUpl.toPlainString(), sumUpl.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumM.toPlainString(), sumM.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumA.toPlainString(), sumA.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                    usdQuote.toPlainString());

            traderPermissionsService.checkEBestMin();

            // calc auto hedge
            if (firstMarketService.getContractType().isEth()) {
                BigDecimal he_usd = oEbestWithColdStorageEth.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP);
                hedgeService.setHedgeEth(he_usd);
                BigDecimal hb_usd = (bEbest.add(coldStorageBtc)).multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP);
                hedgeService.setHedgeBtc(hb_usd);
            } else {
                hedgeService.setHedgeBtc(sumEBestUsdCurr);
                hedgeService.setHedgeEth(BigDecimal.ZERO);
            }

            // notifications
            // 0.5 < e_best bitmex / e_best okex < 1 - correct interval
            if (oEbest.signum() != 0) {
                BigDecimal divRes = bEbest.divide(oEbest, 2, RoundingMode.HALF_UP);
                if (divRes.subtract(BigDecimal.valueOf(0.4)).signum() <= 0 || divRes.subtract(BigDecimal.valueOf(1)).signum() >= 0) {
                    String divResStr = String.format("e_best_bitmex(%s)/e_best_okex(%s)=res(%s). Correct interval: 0.4 < res < 1",
                            bEbest, oEbest, divRes);
                    slackNotifications.sendNotify(NotifyType.E_BEST_VIOLATION, divResStr);
                }
            }

        }

        // max delta violation
        if (isMaxDeltaViolated(DeltaName.B_DELTA) || isMaxDeltaViolated(DeltaName.O_DELTA)) {
            slackNotifications.sendNotify(NotifyType.MAX_DELTA_VIOLATED, "max delta violated");
        }

        Instant end = Instant.now();
        Utils.logIfLong(lastCalcSumBal, end, log, "calcSumBalForGui");
    }

    public Long printToCurrentDeltaLog(String msg) {
        final Long tradeIdSnap = getLastTradeId();
        final String counterName = fplayTradeService.getCounterName(tradeIdSnap);
        fplayTradeService.info(tradeIdSnap, counterName, msg);
        return tradeIdSnap;
    }

    @SuppressWarnings("Duplicates")
    public void printSumBal(Long tradeId, String counterName) {
        if (tradeId == null) {
            tradeId = this.tradeId;
            if (tradeId == null) {
                log.warn("printSumBal with tradeId==null");
                return;
            }
        }
        try {
            final AccountBalance firstAccount = firstMarketService.getFullBalance().getAccountBalance();
            final AccountBalance secondAccount = secondMarketService.getFullBalance().getAccountBalance();
            if (firstAccount != null && secondAccount != null) {
                final BigDecimal bW = firstAccount.getWallet();
                final BigDecimal bEmark = firstAccount.getEMark() != null ? firstAccount.getEMark() : BigDecimal.ZERO;
                bEbest = firstAccount.getEBest() != null ? firstAccount.getEBest() : BigDecimal.ZERO;
                final BigDecimal bEavg = firstAccount.getEAvg() != null ? firstAccount.getEAvg() : BigDecimal.ZERO;
                final BigDecimal bU = firstAccount.getUpl();
                final BigDecimal bM = firstAccount.getMargin();
                final BigDecimal bA = firstAccount.getAvailable();
                final BigDecimal bP = firstMarketService.getPos().getPositionLong();
                final BigDecimal bLv = firstMarketService.getPos().getLeverage();
                final BigDecimal bAL = firstMarketService.getAffordable().getForLong();
                final BigDecimal bAS = firstMarketService.getAffordable().getForShort();
                final BigDecimal usdQuote = getUsdQuote();
                final OrderBook bOrderBook = firstMarketService.getOrderBook();
                final BigDecimal bBestAsk = Utils.getBestAsks(bOrderBook, 1).get(0).getLimitPrice();
                final BigDecimal bBestBid = Utils.getBestBids(bOrderBook, 1).get(0).getLimitPrice();
                fplayTradeService.info(tradeId, counterName, String.format(
                        "#%s b_bal=w%s_%s, e_mark%s_%s, e_best%s_%s, e_avg%s_%s, u%s_%s, m%s_%s, a%s_%s, p%s, lv%s, lg%s, st%s, ask[1]%s, bid[1]%s, usd_qu%s",
                        counterName,
                        bW.toPlainString(), bW.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bEmark.toPlainString(), bEmark.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bEbest.toPlainString(), bEbest.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bEavg.toPlainString(), bEavg.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bU.toPlainString(), bU.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bM.toPlainString(), bM.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bA.toPlainString(), bA.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        Utils.withSign(bP),
                        bLv.toPlainString(),
                        Utils.withSign(bAL),
                        Utils.withSign(bAS),
                        bBestAsk,
                        bBestBid,
                        usdQuote.toPlainString()
                ));

                BigDecimal oW = secondAccount.getWallet();
                BigDecimal oElast = secondAccount.getELast() != null ? secondAccount.getELast() : BigDecimal.ZERO;
                oEbest = secondAccount.getEBest() != null ? secondAccount.getEBest() : BigDecimal.ZERO;
                BigDecimal oEavg = secondAccount.getEAvg() != null ? secondAccount.getEAvg() : BigDecimal.ZERO;
                BigDecimal oM = secondAccount.getMargin();
                BigDecimal oU = secondAccount.getUpl();
                BigDecimal oA = secondAccount.getAvailable();

                boolean isEth = secondMarketService.getEthBtcTicker() != null && firstMarketService.getContractType().isEth();
                BigDecimal oEbestWithColdStorageEth = oEbest;
                if (isEth) {
                    final BigDecimal coldStorageEth = persistenceService.getSettingsRepositoryService().getSettings().getColdStorageEth();
                    oW = oW.add(coldStorageEth);
                    oElast = oElast.add(coldStorageEth);
                    oEbestWithColdStorageEth = oEbest.add(coldStorageEth);
                    oEavg = oEavg.add(coldStorageEth);

                    // okex: convert eth to btc
                    BigDecimal ethBtcBid1 = secondMarketService.getEthBtcTicker().getBid();
                    oW = oW.multiply(ethBtcBid1);
                    oElast = oElast.multiply(ethBtcBid1);
                    oEbest = oEbest.multiply(ethBtcBid1);
                    oEbestWithColdStorageEth = oEbestWithColdStorageEth.multiply(ethBtcBid1);
                    oEavg = oEavg.multiply(ethBtcBid1);
                    oM = oM.multiply(ethBtcBid1);
                    oU = oU.multiply(ethBtcBid1);
                    oA = oA.multiply(ethBtcBid1);
                }
                final BigDecimal oPL = secondMarketService.getPos().getPositionLong();
                final BigDecimal oPS = secondMarketService.getPos().getPositionShort();
                final BigDecimal oLv = secondMarketService.getPos().getLeverage();
                final BigDecimal oAL = secondMarketService.getAffordable().getForLong();
                final BigDecimal oAS = secondMarketService.getAffordable().getForShort();
                final BigDecimal longAvailToClose = secondMarketService.getPos().getLongAvailToClose();
                final BigDecimal shortAvailToClose = secondMarketService.getPos().getShortAvailToClose();
                final OrderBook oOrderBook = secondMarketService.getOrderBook();
                final BigDecimal oBestAsk = Utils.getBestAsks(oOrderBook, 1).get(0).getLimitPrice();
                final BigDecimal oBestBid = Utils.getBestBids(oOrderBook, 1).get(0).getLimitPrice();

                fplayTradeService.info(tradeId, counterName, String.format(
                        "#%s o_bal=w%s_%s, e_mark%s_%s, e_best%s_%s, e_avg%s_%s, u%s_%s, m%s_%s, a%s_%s, p+%s-%s, lv%s, lg%s, st%s, lgMkt%s, stMkt%s, ask[1]%s, bid[1]%s, usd_qu%s",
                        counterName,
                        oW.toPlainString(), oW.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oElast.toPlainString(), oElast.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oEbest.toPlainString(), oEbest.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oEavg.toPlainString(), oEavg.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oU.toPlainString(), oU.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oM.toPlainString(), oM.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oA.toPlainString(), oA.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oPL, oPS,
                        oLv.toPlainString(),
                        Utils.withSign(oAL),
                        Utils.withSign(oAS),
                        Utils.withSign(longAvailToClose),
                        Utils.withSign(shortAvailToClose),
                        oBestAsk,
                        oBestBid,
                        usdQuote.toPlainString()
                ));

                final BigDecimal coldStorageBtc = persistenceService.getSettingsRepositoryService().getSettings().getColdStorageBtc();
                final BigDecimal sumW = bW.add(oW).add(coldStorageBtc).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumE = bEmark.add(oElast).add(coldStorageBtc).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumEBest = bEbest.add(oEbestWithColdStorageEth).add(coldStorageBtc).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumEavg = bEavg.add(oEavg).add(coldStorageBtc).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumUpl = bU.add(oU).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumM = bM.add(oM).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumA = bA.add(oA).setScale(8, BigDecimal.ROUND_HALF_UP);

                final BigDecimal sumEBestUsdCurr = sumEBest.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP);

                final BigDecimal btmQu = Utils.calcQuAvg(firstMarketService.getOrderBook());
                sumEBestUsd = sumEBest.multiply(btmQu).setScale(2, BigDecimal.ROUND_HALF_UP);

                final String sBalStr = String.format("#%s s_bal=w%s_%s, s_e%s_%s, s_e_best%s_%s, s_e_avg%s_%s, u%s_%s, m%s_%s, a%s_%s, usd_qu%s",
                        counterName,
                        sumW.toPlainString(), sumW.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumE.toPlainString(), sumE.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumEBest.toPlainString(), sumEBestUsdCurr,
                        sumEavg.toPlainString(), sumEavg.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumUpl.toPlainString(), sumUpl.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumM.toPlainString(), sumM.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumA.toPlainString(), sumA.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP),
                        usdQuote.toPlainString());
                fplayTradeService.info(tradeId, counterName, sBalStr);

                final String bDQLMin;
                final String oDQLMin;
                Dql dql = persistenceService.getSettingsRepositoryService().getSettings().getDql();
                if (signalType == SignalType.B_PRE_LIQ || signalType == SignalType.O_PRE_LIQ) {
                    bDQLMin = String.format("b_DQL_close_min=%s", dql.getBDQLCloseMin());
                    oDQLMin = String.format("o_DQL_close_min=%s", dql.getODQLCloseMin());
                } else {
                    bDQLMin = String.format("b_DQL_open_min=%s", dql.getBDQLOpenMin());
                    oDQLMin = String.format("o_DQL_open_min=%s", dql.getODQLOpenMin());
                }

                fplayTradeService.info(tradeId, counterName, String.format("#%s %s", counterName, getFullPosDiff()));
                final LiqInfo bLiqInfo = getFirstMarketService().getLiqInfo();
                fplayTradeService
                        .info(tradeId, counterName, String.format("#%s %s; %s; %s", counterName, bLiqInfo.getDqlString(), bLiqInfo.getDmrlString(), bDQLMin));
                final LiqInfo oLiqInfo = getSecondMarketService().getLiqInfo();
                fplayTradeService
                        .info(tradeId, counterName, String.format("#%s %s; %s; %s", counterName, oLiqInfo.getDqlString(), oLiqInfo.getDmrlString(), oDQLMin));
            }
        } catch (Exception e) {
            fplayTradeService.error(tradeId, counterName, "Error on printSumBal");
            log.error("Error on printSumBal", e);
        }
    }

    public BigDecimal getUsdQuote() {
        UsdQuoteType usdQuoteType = persistenceService.getSettingsRepositoryService().getSettings().getUsdQuoteType();
        BigDecimal usdQuote;
        switch (usdQuoteType) {
            case BITMEX:
                if (firstMarketService == null || !firstMarketService.isStarted()) {
                    usdQuote = BigDecimal.ZERO;
                } else {
                    usdQuote = Utils.calcQuAvg(firstMarketService.getOrderBookXBTUSD());
                }
                break;
            case OKEX:
                if (secondMarketService == null || !secondMarketService.isStarted()) {
                    usdQuote = BigDecimal.ZERO;
                } else {
                    usdQuote = Utils.calcQuAvg(secondMarketService.getOrderBookXBTUSD());
                }
                break;
            case INDEX_BITMEX:
                usdQuote = getOneMarketUsdQuote(firstMarketService);
                break;
            case INDEX_OKEX:
                usdQuote = getOneMarketUsdQuote(secondMarketService);
                break;
            case AVG:
            default:
                if (firstMarketService == null || !firstMarketService.isStarted()
                        || secondMarketService == null || !secondMarketService.isStarted()) {
                    usdQuote = BigDecimal.ZERO;
                } else {
                    usdQuote = Utils.calcQuAvg(firstMarketService.getOrderBookXBTUSD(), secondMarketService.getOrderBookXBTUSD());
                }
                break;
        }
        return usdQuote;
    }

    private BigDecimal getOneMarketUsdQuote(MarketService marketService) {
        BigDecimal usdQuote;
        if (marketService == null || !marketService.isStarted()) {
            usdQuote = BigDecimal.ZERO;
        } else {
            if (marketService.getContractType().isEth()) {
                usdQuote = marketService.getBtcContractIndex() != null && marketService.getBtcContractIndex().getIndexPrice() != null
                        ? marketService.getBtcContractIndex().getIndexPrice()
                        : BigDecimal.ZERO;
            } else {
                usdQuote = marketService.getContractIndex() != null && marketService.getContractIndex().getIndexPrice() != null
                        ? marketService.getContractIndex().getIndexPrice()
                        : BigDecimal.ZERO;
            }
        }
        return usdQuote;
    }

    public String getFullPosDiff() {
        return getMainSetStr() + getMainSetSource() + getExtraSetStr() + getExtraSetSource();
    }

    @SuppressWarnings("Duplicates")
    public String getMainSetStr() {
        // nt_usd = b_pos * 10 / CM + o_pos * 10 - ha;
        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        final BigDecimal cm = settings.getPlacingBlocks().getCm();
        final BigDecimal adj = settings.getPosAdjustment().getPosAdjustmentMin();
        final BigDecimal adjMax = settings.getPosAdjustment().getPosAdjustmentMax();

        MarketService bitmexService = getFirstMarketService();
        MarketService okcoinService = getSecondMarketService();

        boolean isEth = bitmexService.getContractType().isEth();

        final BigDecimal bP = bitmexService.getPos().getPositionLong();
        final BigDecimal oPL = okcoinService.getPos().getPositionLong();
        final BigDecimal oPS = okcoinService.getPos().getPositionShort();
        final BigDecimal ha = isEth ? hedgeService.getHedgeEth() : hedgeService.getHedgeBtc();
        final BigDecimal bitmexUsd = isEth
                ? bP.multiply(BigDecimal.valueOf(10)).divide(cm, 2, RoundingMode.HALF_UP)
                : bP;
        final BigDecimal okexUsd = isEth
                ? (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(10))
                : (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(100));
        final BigDecimal notionalUsd = (bitmexUsd.add(okexUsd).subtract(ha)).negate();

        final String modeName = settings.getContractMode().getModeName();
        final String setName = settings.getContractMode().getMainSetName();
        final BigDecimal mdc = getParams().getMaxDiffCorr();

        if (isEth) {
            return String.format("%s, %s, nt_usd = -(b(%s) + o(%s) - h(%s)) = %s, mdc=%s, cm=%s, adjMin=%s, adjMax=%s. ",
                    modeName,
                    setName,
                    Utils.withSign(bitmexUsd),
                    Utils.withSign(okexUsd),
                    Utils.withSign(ha),
                    Utils.withSign(notionalUsd),
                    mdc, cm, adj, adjMax
            );
        } else {
            return String.format("%s, %s, nt_usd = -(b(%s) + o(%s) - h(%s)) = %s, mdc=%s. ",
                    modeName,
                    setName,
                    Utils.withSign(bitmexUsd),
                    Utils.withSign(okexUsd),
                    Utils.withSign(ha),
                    Utils.withSign(notionalUsd),
                    mdc
            );
        }
    }

    public String getExtraSetStr() {
        // M10, set_bu11, nt_usd = - (b(-1200) + o(+900) - h(-300)) = 0;
        final MarketService bitmexService = getFirstMarketService();
        if (bitmexService.getContractType().isEth()) {
            final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
            final BigDecimal b_pos_usd = bitmexService.getHbPosUsd();
            final BigDecimal hb_usd = hedgeService.getHedgeBtc();
            final BigDecimal nt_usd = (b_pos_usd.subtract(hb_usd)).negate();

            return String.format("%s, %s, nt_usd = -(b(%s) + o(+0) - h(%s)) = %s. ",
                    settings.getContractMode().getModeName(),
                    "set_bu10",
                    Utils.withSign(b_pos_usd),
                    Utils.withSign(hb_usd),
                    Utils.withSign(nt_usd)
            );
        }
        return "";
    }

    public String getMainSetSource() {
        // M10, set_bu11, nt_usd = - (b(-1200) + o(+900) - h(-300)) = 0;
        // M10, set_bu11, cont: b_pos(-1200); o_pos(+900, -0).
        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        final BigDecimal bP = getFirstMarketService().getPos().getPositionLong();
        final BigDecimal oPL = getSecondMarketService().getPos().getPositionLong();
        final BigDecimal oPS = getSecondMarketService().getPos().getPositionShort();
        return String.format("%s, %s, cont: b(%s) o(+%s, -%s). ",
                settings.getContractMode().getModeName(),
                settings.getContractMode().getMainSetName(),
                Utils.withSign(bP),
                oPL.toPlainString(),
                oPS.toPlainString());
    }

    public String getExtraSetSource() {
        // M10, set_bu11, nt_usd = - (b(-1200) + o(+900) - h(-300)) = 0;
        // M10, set_bu11, cont: b_pos(-1200); o_pos(+900, -0).
        if (getFirstMarketService().getContractType().isEth()) {
            final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
            final BigDecimal bP = getFirstMarketService().getHbPosUsd();
            return String.format("%s, %s, cont: b(%s) o(+0, -0). ",
                    settings.getContractMode().getModeName(),
                    "set_bu10",
                    Utils.withSign(bP));
        }
        return "";
    }

    private PlBlocks dynBlockDecreaseByAffordable(DeltaName deltaRef, BigDecimal blockSize1, BigDecimal blockSize2) {
        BigDecimal b1 = BigDecimal.ZERO;
        BigDecimal b2 = BigDecimal.ZERO;
        final BigDecimal cm = persistenceService.getSettingsRepositoryService().getSettings().getPlacingBlocks().getCm();
        final Affordable firstAffordable = firstMarketService.recalcAffordable();
        final Affordable secondAffordable = secondMarketService.recalcAffordable();
        if (deltaRef == DeltaName.B_DELTA) {
            // b_sell, o_buy
            final BigDecimal b_sell_lim = firstAffordable.getForShort().signum() < 0 ? BigDecimal.ZERO : firstAffordable.getForShort();
            final BigDecimal o_buy_lim = secondAffordable.getForLong().signum() < 0 ? BigDecimal.ZERO : secondAffordable.getForLong();
            b1 = blockSize1.compareTo(b_sell_lim) < 0 ? blockSize1 : b_sell_lim;
            b2 = blockSize2.compareTo(o_buy_lim) < 0 ? blockSize2 : o_buy_lim;
        } else if (deltaRef == DeltaName.O_DELTA) {
            // buy p , sell o
            final BigDecimal b_buy_lim = firstAffordable.getForLong().signum() < 0 ? BigDecimal.ZERO : firstAffordable.getForLong();
            final BigDecimal o_sell_lim = secondAffordable.getForShort().signum() < 0 ? BigDecimal.ZERO : secondAffordable.getForShort();
            b1 = blockSize1.compareTo(b_buy_lim) < 0 ? blockSize1 : b_buy_lim;
            b2 = blockSize2.compareTo(o_sell_lim) < 0 ? blockSize2 : o_sell_lim;
        }

        if (b1.signum() == 0 || b2.signum() == 0) {
            b1 = BigDecimal.ZERO;
            b2 = BigDecimal.ZERO;
        } else if (b1.compareTo(b2.multiply(cm)) != 0) {
            b2 = b2.min(b1.divide(cm, 0, RoundingMode.HALF_UP));
            b1 = b2.multiply(cm).setScale(0, RoundingMode.HALF_UP);
        }

        b1 = b1.setScale(0, RoundingMode.HALF_UP);
        b2 = b2.setScale(0, RoundingMode.HALF_UP);

        String debugLog = String.format("dynBlockDecreaseByAffordable: %s, %s. bitmex %s, okex %s",
                b1, b2, firstAffordable, secondAffordable);

        return new PlBlocks(b1, b2, PlacingBlocks.Ver.DYNAMIC, debugLog);
    }

    private PlBlocks adjustByNtUsd(DeltaName deltaRef, BigDecimal blockSize1, BigDecimal blockSize2, BigDecimal oPL, BigDecimal oPS) {
        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        if (settings.getAdjustByNtUsd() == null || !settings.getAdjustByNtUsd()) {
            return new PlBlocks(blockSize1, blockSize2);
        }

        final BigDecimal cm = settings.getPlacingBlocks().getCm();
        boolean isEth = firstMarketService.getContractType().isEth();

        // convert to usd
        BigDecimal b_block_usd;
        BigDecimal o_block_usd;
        if (isEth) {
            b_block_usd = blockSize1.multiply(BigDecimal.valueOf(10)).divide(cm, 2, RoundingMode.HALF_UP);
            o_block_usd = blockSize2.multiply(BigDecimal.valueOf(10));
        } else {
            b_block_usd = blockSize1;
            o_block_usd = blockSize2.multiply(BigDecimal.valueOf(100));
        }

        // do the adjustment
        BigDecimal multiplicity = settings.getNtUsdMultiplicityOkex();
        BigDecimal ntUsd = getNtUsd();
        if (ntUsd.signum() > 0) {
            if (deltaRef == DeltaName.B_DELTA) {
//                b_block_usd = b_block_usd;
                final BigDecimal ntUsdOkex = getNtUsdMult(ntUsd, multiplicity);
                o_block_usd = o_block_usd.add(ntUsdOkex);
            } else if (deltaRef == DeltaName.O_DELTA) {
                b_block_usd = b_block_usd.add(ntUsd);
//                o_block_usd = o_block_usd;
            }
        } else if (ntUsd.signum() < 0) {
            if (deltaRef == DeltaName.B_DELTA) {
                b_block_usd = b_block_usd.subtract(ntUsd);
//                o_block_usd = o_block_usd;
            } else if (deltaRef == DeltaName.O_DELTA) {
//                b_block_usd = b_block_usd;
                final BigDecimal ntUsdOkex = getNtUsdMult(ntUsd, multiplicity);
                o_block_usd = o_block_usd.subtract(ntUsdOkex);
            }
        }

        // go back to contracts
        BigDecimal b_block;
        BigDecimal o_block;
        if (isEth) {
            b_block = b_block_usd.multiply(cm).divide(BigDecimal.valueOf(10), 0, RoundingMode.HALF_UP);
            o_block = o_block_usd.divide(BigDecimal.valueOf(10), 0, RoundingMode.HALF_UP);
        } else {
            b_block = b_block_usd.setScale(0, RoundingMode.HALF_UP);
            o_block = o_block_usd.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        }

        b_block = b_block.signum() < 0 ? BigDecimal.ZERO : b_block;
        o_block = o_block.signum() < 0 ? BigDecimal.ZERO : o_block;

        // if okex cross zero, we set okex block without ntUsd adj
        if (deltaRef == DeltaName.B_DELTA && oPS.signum() > 0 && o_block.subtract(oPS).signum() > 0) { // okex buy
            o_block = oPS;
            b_block = getBitmexBlockByOkexBlock(cm, isEth, o_block);
        } else if (deltaRef == DeltaName.O_DELTA && oPL.signum() > 0 && o_block.subtract(oPL).signum() > 0) { // okex sell
            o_block = oPL;
            b_block = getBitmexBlockByOkexBlock(cm, isEth, o_block);
        }

        return new PlBlocks(b_block, o_block);
    }

    private BigDecimal getBitmexBlockByOkexBlock(BigDecimal cm, boolean isEth, BigDecimal o_block) {
        BigDecimal b_block;
        if (isEth) {
            final BigDecimal usd = o_block.multiply(BigDecimal.valueOf(10)); // okexCont to usd
            b_block = usd.multiply(cm).divide(BigDecimal.valueOf(10), 0, RoundingMode.HALF_UP); // usd to bitmexCont
        } else {
            final BigDecimal usd = o_block.multiply(BigDecimal.valueOf(100)); // okexCont to usd
            b_block = usd.setScale(0, RoundingMode.HALF_UP); // usd to bitmexCont
        }
        return b_block;
    }

    private BigDecimal getNtUsdMult(BigDecimal ntUsd, BigDecimal multiplicity) {
        if (multiplicity == null || multiplicity.signum() <= 0) {
            return ntUsd;
        }
        BigDecimal mult = multiplicity.setScale(0, RoundingMode.DOWN);
        // nt=237.65, mult=100 ==> 200
        // nt=237.65, mult=10  ==> 230
        // nt=237.65, mult=20  ==> 220
        // nt=237.65, mult=1   ==> 237
        // nt=237.65, mult=0   ==> 237.65
        // nt=237.65, mult=-1  ==> 237.65
        // nt=237.65, mult=-12 ==> 237.65
        BigDecimal num = ntUsd.divide(mult, 0, RoundingMode.DOWN);
        return num.multiply(mult);
    }

    @SuppressWarnings("Duplicates")
    private BigDecimal getNtUsd() {
        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        final BigDecimal cm = settings.getPlacingBlocks().getCm();

        MarketService bitmexService = getFirstMarketService();
        MarketService okcoinService = getSecondMarketService();

        boolean isEth = bitmexService.getContractType().isEth();

        final BigDecimal bP = bitmexService.getPos().getPositionLong();
        final BigDecimal oPL = okcoinService.getPos().getPositionLong();
        final BigDecimal oPS = okcoinService.getPos().getPositionShort();
        final BigDecimal ha = isEth ? hedgeService.getHedgeEth() : hedgeService.getHedgeBtc();
        final BigDecimal bitmexUsd = isEth
                ? bP.multiply(BigDecimal.valueOf(10)).divide(cm, 2, RoundingMode.HALF_UP)
                : bP;
        final BigDecimal okexUsd = isEth
                ? (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(10))
                : (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(100));
        //noinspection UnnecessaryLocalVariable
        final BigDecimal notionalUsd = (bitmexUsd.add(okexUsd).subtract(ha)).negate();
        return notionalUsd;
    }


    private boolean checkAffordable(DeltaName deltaRef, BigDecimal blockSize1, BigDecimal blockSize2) {
        boolean affordable = false;

        final boolean btm;
        final boolean ok;
        if (deltaRef == DeltaName.B_DELTA) {
            // sell p, buy o
            btm = firstMarketService.isAffordable(OrderType.ASK, blockSize1);
            ok = secondMarketService.isAffordable(OrderType.BID, blockSize2);
        } else { // if (deltaRef == DeltaName.O_DELTA) {
            // buy p , sell o
            btm = firstMarketService.isAffordable(OrderType.BID, blockSize1);
            ok = secondMarketService.isAffordable(OrderType.ASK, blockSize2);
        }

        if (btm && ok) {
            affordable = true;
        }
        isAffordableBitmex = btm;
        isAffordableOkex = ok;
        return affordable;
    }

    public BigDecimal getDelta1() {
        return delta1;
    }

    public BigDecimal getDelta2() {
        return delta2;
    }

    public void loadParamsFromDb() {
        final GuiParams deltas = persistenceService.fetchGuiParams();
        if (deltas != null) {
            params = deltas;
        } else {
            params = new GuiParams();
        }

        final DeltaParams deltaParams = persistenceService.fetchDeltaParams();
        if (deltaParams != null) {
            this.deltaParams = deltaParams;
        } else {
            this.deltaParams = DeltaParams.createDefault();
        }
    }

    public void saveParamsToDb() {
        persistenceService.saveGuiParams(params);
    }

    public void resetDeltaParams() {
        deltaParams.setBDeltaMin(delta1);
        deltaParams.setBDeltaMax(delta1);
        deltaParams.setODeltaMin(delta2);
        deltaParams.setODeltaMax(delta2);
    }

    public GuiParams getParams() {
        return params;
    }

    public void setParams(GuiParams params) {
        this.params = params;
    }

    public DeltaParams getDeltaParams() {
        return deltaParams;
    }

    public SignalType getSignalType() {
        return signalType;
    }

    public void setSignalType(SignalType signalType) {
        this.signalType = signalType != null ? signalType : SignalType.AUTOMATIC;
    }

    public String getCounter(Long... tradeId) {
        final Long trId;
        if (tradeId != null && tradeId.length > 0) {
            trId = tradeId[0];
        } else {
            trId = this.tradeId;
        }
        return fplayTradeService.getCounterName(trId);
    }

    public int getCounter() {
        int i = -10; // just to show that something wrong
        final String counterName = fplayTradeService.getCounterName(this.tradeId);
        try {
            i = Integer.parseInt(counterName);
        } catch (NumberFormatException e) {
            log.error("counterName={} error: {}", counterName, e.getMessage());
        }
        return i;
//        final CumParams totalCommon = cumService.getTotalCommon();
//        return totalCommon.getVert1Val() + totalCommon.getVert2Val();
    }

    public String getSumBalString() {
        return sumBalString;
    }

    public DeltaMon getDeltaMon() {
        return deltaMon;
    }

    public void setDeltaMon(DeltaMon deltaMon) {
        this.deltaMon = deltaMon;
    }

    public PublishSubject<DeltaChange> getDeltaChangesPublisher() {
        return deltaChangesPublisher;
    }

    public BigDecimal getbEbest() {
        return bEbest;
    }

    public BigDecimal getoEbest() {
        return oEbest;
    }

    public BigDecimal getSumEBestUsd() {
        return sumEBestUsd;
    }

    public boolean isArbForbidden() {
        return traderPermissionsService.isForbidden();
    }

    public boolean isArbForbidden(SignalType signalType) {
        return isArbForbidden() && !signalType.isButton() && !signalType.isPreliq() && !signalType.isAdj();
    }

    public ArbState getArbState() {
        return arbState;
    }

    public void resetArbState(String from) {
        String msg = "Arbitrage state was reset READY from " + from + ". ";
        warningLogger.warn(msg);
        log.warn(msg);
        final String counterName = fplayTradeService.getCounterName(tradeId);
        fplayTradeService.warn(tradeId, counterName, msg);

        synchronized (arbStateLock) {
            if (tradeId != null && arbState == ArbState.IN_PROGRESS) {
                try {
                    onArbDone(tradeId, BitmexService.NAME);
                    onArbDone(tradeId, OkCoinService.NAME);
                } catch (Exception e) {
                    log.error("Error " + msg, e);
                    warningLogger.error("Error " + msg + e.toString());
                }
            }

            arbState = ArbState.READY;
        }

    }

    public boolean isFirstDeltasCalculated() {
        return firstDeltasCalculated;
    }

    public String getStartSignalTimer() {
        String res = "_";
        if (startSignalTime != null && arbState == ArbState.IN_PROGRESS) {
            res = String.valueOf(Duration.between(startSignalTime, Instant.now()).getSeconds());
        }
        SignalTimeParams signalTimeParams = signalTimeService.getSignalTimeParams();
        int count = signalTimeParams != null ? signalTimeParams.getAvgDen().intValue() : 0;
        return String.format("Signal(%s) started %s sec ago", count, res);
    }

    public Instant getLastCalcSumBal() {
        return lastCalcSumBal;
    }


    public FplayTrade getFplayTrade() {
        return fplayTrade;
    }

    public Long getTradeId() {
        return tradeId;
    }

    public Long getLastTradeId() {
        return tradeId != null ? tradeId : fplayTradeService.getLastId();
    }

    public Long getLastInProgressTradeId() {
        final Long tradeIdSnap = getLastTradeId();
        FplayTrade one = fplayTradeService.getById(tradeIdSnap);
        if (one != null && one.getTradeStatus() == TradeStatus.IN_PROGRESS) {
            return tradeIdSnap;
        }
        return null;
    }

    public boolean isAffordableBitmex() {
        return isAffordableBitmex;
    }

    public boolean isAffordableOkex() {
        return isAffordableOkex;
    }

    public DeltaName getSignalStatusDelta() {
        return signalStatusDelta;
    }

    public SlackNotifications getSlackNotifications() {
        return slackNotifications;
    }

    public PosDiffService getPosDiffService() {
        return posDiffService;
    }

    public DqlStateService getDqlStateService() {
        return dqlStateService;
    }

    public NtUsdRecoveryService getNtUsdRecoveryService() {
        return ntUsdRecoveryService;
    }
}
