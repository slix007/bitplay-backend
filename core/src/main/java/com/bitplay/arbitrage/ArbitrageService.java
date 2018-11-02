package com.bitplay.arbitrage;

import com.bitplay.Config;
import com.bitplay.TwoMarketStarter;
import com.bitplay.arbitrage.BordersService.TradingSignal;
import com.bitplay.arbitrage.dto.AvgPrice;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.DealPrices;
import com.bitplay.arbitrage.dto.DeltaLogWriter;
import com.bitplay.arbitrage.dto.DeltaMon;
import com.bitplay.arbitrage.dto.PlBlocks;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.events.DeltaChange;
import com.bitplay.arbitrage.events.SignalEvent;
import com.bitplay.arbitrage.events.SignalEventBus;
import com.bitplay.arbitrage.events.SignalEventEx;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketState;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.BtsEventBox;
import com.bitplay.market.events.EventBus;
import com.bitplay.market.model.Affordable;
import com.bitplay.market.model.LiqInfo;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.DeltaRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.CumParams;
import com.bitplay.persistance.domain.DeltaParams;
import com.bitplay.persistance.domain.GuiLiqParams;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.SignalTimeParams;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BorderParams.Ver;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.fluent.FplayTrade;
import com.bitplay.persistance.domain.fluent.TradeMStatus;
import com.bitplay.persistance.domain.fluent.TradeStatus;
import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.OkexContractType;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.UsdQuoteType;
import com.bitplay.persistance.repository.FplayTradeRepository;
import com.bitplay.security.TraderPermissionsService;
import com.bitplay.utils.Utils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.Completable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.SerializationUtils;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 4/18/17.
 */
@Service
public class ArbitrageService {

    private static final Logger logger = LoggerFactory.getLogger(ArbitrageService.class);
    private static final Logger signalLogger = LoggerFactory.getLogger("SIGNAL_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    public static final String DELTA1 = "delta1";
    public static final String DELTA2 = "delta2";
    private static final Object calcLock = new Object();
    private final DealPrices dealPrices = new DealPrices();
    private boolean firstDeltasCalculated = false;
    @Autowired
    private BordersService bordersService;
    @Autowired
    private PlacingBlocksService placingBlocksService;
    @Autowired
    private PersistenceService persistenceService;
    @Autowired
    private DeltaRepositoryService deltaRepositoryService;
    @Autowired
    private SignalService signalService;
    @Autowired
    private DiffFactBrService diffFactBrService;
    @Autowired
    private DeltasCalcService deltasCalcService;
    @Autowired
    private TraderPermissionsService traderPermissionsService;
    @Autowired
    private Config config;
    @Autowired
    private AfterArbService afterArbService;
    @Autowired
    private PreliqUtilsService preliqUtilsService;
    @Autowired
    private SignalTimeService signalTimeService;
    @Autowired
    private TradeService tradeService;
    @Autowired
    private FplayTradeRepository fplayTradeRepository;
    @Autowired
    private SlackNotifications slackNotifications;
    @Autowired
    private HedgeService hedgeService;


    //    private Disposable schdeduleUpdateBorders;
//    private Instant startTimeToUpdateBorders;
//    private volatile int updateBordersCounter;
    //TODO rename them to first and second
    private MarketService firstMarketService;
    private MarketService secondMarketService;
    private PosDiffService posDiffService;
    private BigDecimal delta1 = BigDecimal.ZERO;
    private BigDecimal delta2 = BigDecimal.ZERO;
    private GuiParams params = new GuiParams();
    private BigDecimal bEbest = BigDecimal.ZERO;
    private BigDecimal oEbest = BigDecimal.ZERO;
    private BigDecimal sumEBestUsd = BigDecimal.valueOf(-1);
    private String sumBalString = "";
//    private volatile Boolean isReadyForTheArbitrage = true;
//    private Disposable theTimer;
    private Disposable theCheckBusyTimer;
    private volatile SignalType signalType = SignalType.AUTOMATIC;
    private SignalEventBus signalEventBus = new SignalEventBus();
    private volatile DeltaParams deltaParams = DeltaParams.createDefault();
    private volatile DeltaMon deltaMon = new DeltaMon();
    private final PublishSubject<DeltaChange> deltaChangesPublisher = PublishSubject.create();
    private final Object arbInProgressLock = new Object();
    private final AtomicBoolean arbInProgress = new AtomicBoolean();
    private volatile Instant startSignalTime = null;
    private volatile Instant lastCalcSumBal = null;

    private volatile Long tradeId;
    private volatile FplayTrade fplayTrade;

    // Signal delay
    private volatile Long signalDelayActivateTime;
    private volatile ScheduledFuture<?> futureSignal;
    private final ScheduledExecutorService signalDelayScheduler = Executors.newScheduledThreadPool(1,
            new ThreadFactoryBuilder().setNameFormat("signal-delay-thread-%d").build());
    // Signal delay end

    public DealPrices getDealPrices() {
        return dealPrices;
    }

    public void init(TwoMarketStarter twoMarketStarter) {
        loadParamsFromDb();
        this.firstMarketService = twoMarketStarter.getFirstMarketService();
        this.secondMarketService = twoMarketStarter.getSecondMarketService();
        this.posDiffService = twoMarketStarter.getPosDiffService();
//        startArbitrageMonitoring();
        initArbitrageStateListener();
        initSignalEventBus();
    }

    private Disposable initSignalEventBus() {
        return signalEventBus.toObserverable()
                .subscribe(eventQuant -> {
                    try {
                        SignalEvent signalEvent = eventQuant instanceof SignalEventEx
                                ? ((SignalEventEx) eventQuant).getSignalEvent()
                                : (SignalEvent) eventQuant;

                        if (signalEvent == SignalEvent.B_ORDERBOOK_CHANGED
                                || signalEvent == SignalEvent.O_ORDERBOOK_CHANGED) {

                            final OrderBook firstOrderBook = firstMarketService.getOrderBook();
                            final OrderBook secondOrderBook = secondMarketService.getOrderBook();

                            final BestQuotes bestQuotes = calcBestQuotesAndDeltas(firstOrderBook, secondOrderBook);
                            params.setLastOBChange(new Date());

                            doComparison(bestQuotes, firstOrderBook, secondOrderBook);

                        }
                    } catch (NotYetInitializedException e) {
                        // do nothing
                    } catch (Exception e) {
                        logger.error("ERROR: signalEventBus errorOnEvent", e);
//                        warningLogger.error("ERROR: signalEventBus errorOnEvent. Signals may not work at all." + e.toString());
                    }
                }, throwable -> {
                    logger.error("signalEventBus errorOnEvent", throwable);
                    initSignalEventBus();
                });
    }

    public SignalEventBus getSignalEventBus() {
        return signalEventBus;
    }

    private void initArbitrageStateListener() {
        final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("arb-done-starter-%d").build();
        final ExecutorService executor = Executors.newSingleThreadExecutor(namedThreadFactory);
        final Scheduler schedulerStarter = Schedulers.from(executor);

        Disposable btmFreeListener = gotFreeListener(firstMarketService.getEventBus(), schedulerStarter, BitmexService.NAME);
        Disposable okFreeListener = gotFreeListener(secondMarketService.getEventBus(), schedulerStarter, OkCoinService.NAME);
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
                        logger.error("On arb-done handling", e);
//                        deltasLogger.error("ERROR on arb-done handling" + e.toString());
                        warningLogger.error("ERROR on arb-done handling" + e.toString());
                    }
                }, throwable -> logger.error("On event handling", throwable));
    }

    private void onArbDone(@NotNull Long doneTradeId, String marketName) {

        // marketState may be not set yet because of race condition
//        if (!firstMarketService.isBusy() && !secondMarketService.isBusy()) {
        logger.info(String.format("onArbDone(%s, %s)", doneTradeId, marketName));

        long signalTimeSec = startSignalTime == null ? -1 : Duration.between(startSignalTime, Instant.now()).getSeconds();

        synchronized (arbInProgressLock) { // do not set arbInProgress=false until the whole block is done!
                // The other option is "doing stateSnapshot before doing set arbInProgress=false"

            if (marketName.equals(BitmexService.NAME)) {
                fplayTrade.setBitmexStatus(TradeMStatus.FINISHED);
                tradeService.setBitmexStatus(doneTradeId, TradeMStatus.FINISHED);
            } else {
                fplayTrade.setOkexStatus(TradeMStatus.FINISHED);
                tradeService.setOkexStatus(doneTradeId, TradeMStatus.FINISHED);
            }

            // read from DB to check isBothCompleted
            if (tradeId != null && tradeService.isBothCompleted(tradeId)) {

                if (arbInProgress.getAndSet(false)) {
                    final String counterNameSnap = String.valueOf(firstMarketService.getCounterName());
                    final long tradeIdSnap = tradeId.longValue();

                    if (signalTimeSec > 0) {
                        signalTimeService.addSignalTime(BigDecimal.valueOf(signalTimeSec));
                    }

                    // start writeLogArbitrageIsDone();
                    final String arbIsDoneMsg = String.format("#%s is done. SignalTime=%s sec. tradeId(curr/done): %s / %s---",
                            counterNameSnap, signalTimeSec, tradeIdSnap, doneTradeId);
                    tradeService.info(tradeIdSnap, counterNameSnap, arbIsDoneMsg);
                    logger.info(arbIsDoneMsg);

                    // use snapshot of Params
                    final DealPrices dealPricesSnap;
                    synchronized (dealPrices) {
                        dealPricesSnap = SerializationUtils.clone(dealPrices);
                    }
                    final SignalType signalTypeSnap = SignalType.valueOf(signalType.name());
                    // todo separate startSignalParams with endSignalParams (cumParams)
                    final GuiLiqParams guiLiqParams = persistenceService.fetchGuiLiqParams();
                    final DeltaName deltaName = params.getLastDelta().equals(DELTA1) ? DeltaName.B_DELTA : DeltaName.O_DELTA;
                    final Settings settings = persistenceService.getSettingsRepositoryService().getSettings()
                            .toBuilder().build();
                    final Position okexPosition = secondMarketService.getPosition();

                    AfterArbTask afterArbTask = new AfterArbTask(dealPricesSnap,
                            signalTypeSnap,
                            guiLiqParams,
                            deltaName,
                            tradeIdSnap,
                            counterNameSnap,
                            settings,
                            okexPosition,
                            (BitmexService) getFirstMarketService(),
                            (OkCoinService) getSecondMarketService(),
                            preliqUtilsService,
                            persistenceService,
                            this,
                            new DeltaLogWriter(tradeIdSnap, counterNameSnap, tradeService)
                    );

                    if (signalTypeSnap.isPreliq()) {
                        afterArbTask.preliqIsDone(); // sync ending
                    }

                    afterArbService.addTask(afterArbTask); // async ending

                }
            }
        }
        logger.info(String.format("onArbDone(%s, %s) finished", doneTradeId, marketName));
    }


    public MarketService getFirstMarketService() {
        return firstMarketService;
    }

    public MarketService getSecondMarketService() {
        return secondMarketService;
    }

    private void setTimeoutAfterStartTrading() {
//        isReadyForTheArbitrage = false;
//        if (theTimer != null) {
//            theTimer.dispose();
//        }
//        theTimer = Completable.timer(100, TimeUnit.MILLISECONDS)
//                .doOnComplete(() -> isReadyForTheArbitrage = true)
//                .doOnError(throwable -> logger.error("onError timer", throwable))
//                .repeat()
//                .retry()
//                .subscribe();
        setBusyStackChecker();
    }

    private void setBusyStackChecker() {

        if (theCheckBusyTimer != null) {
            theCheckBusyTimer.dispose();
        }

        theCheckBusyTimer = Completable.timer(6, TimeUnit.MINUTES, Schedulers.computation())
                .doOnComplete(() -> {

                    final String counterName = firstMarketService.getCounterName();

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
                                getCounter(),
                                firstMarketService.isBusy(),
                                firstMarketService.getOnlyOpenOrders().size(),
                                secondMarketService.isBusy(),
                                secondMarketService.getOnlyOpenOrders().size());
                        tradeService.warn(tradeId, counterName, logString);
                        warningLogger.warn(logString);

                        firstMarketService.isReadyForArbitrageWithOOFetch();

                        logString = String.format("#%s Warning: busy by isBusy for 6 min. first:%s(%s), second:%s(%s). After check of bitmex openOrders.",
                                getCounter(),
                                firstMarketService.isBusy(),
                                firstMarketService.getOnlyOpenOrders().size(),
                                secondMarketService.isBusy(),
                                secondMarketService.getOnlyOpenOrders().size());
                        tradeService.warn(tradeId, counterName, logString);
                        warningLogger.warn(logString);

                        boolean firstHanged = firstMarketService.isBusy() && !firstMarketService.hasOpenOrders();
                        boolean secondHanged = secondMarketService.isBusy() && !secondMarketService.hasOpenOrders();
                        boolean noOrders = !firstMarketService.hasOpenOrders() && !secondMarketService.hasOpenOrders();
                        if (firstHanged && noOrders) {
                            tradeService.warn(tradeId, counterName, "Warning: Free Bitmex");
                            warningLogger.warn("Warning: Free Bitmex");
                            logger.warn("Warning: Free Bitmex");
                            firstMarketService.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE_FORCE_RESET, firstMarketService.tryFindLastTradeId()));
                        }
                        if (secondHanged && noOrders) {
                            tradeService.warn(tradeId, counterName, "Warning: Free Okcoin");
                            warningLogger.warn("Warning: Free Okcoin");
                            logger.warn("Warning: Free Okcoin");
                            secondMarketService.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE_FORCE_RESET, secondMarketService.tryFindLastTradeId()));
                        }

                    } else if (!firstMarketService.isReadyForArbitrageWithOOFetch() || !secondMarketService.isReadyForArbitrage()) {
                        final String logString = String.format("#%s Warning: busy for 6 min. first:isReady=%s(Orders=%s), second:isReady=%s(Orders=%s)",
                                getCounter(),
                                firstMarketService.isReadyForArbitrage(), firstMarketService.getOnlyOpenOrders().size(),
                                secondMarketService.isReadyForArbitrage(), secondMarketService.getOnlyOpenOrders().size());
                        tradeService.warn(tradeId, counterName, logString);
                        warningLogger.warn(logString);
                        logger.warn(logString);
                    }

                    if (firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage()) {
                        synchronized (arbInProgressLock) {
                            boolean wasReset = arbInProgress.compareAndSet(true, false);
                            if (wasReset) {
                                releaseArbInProgress(counterName, "'busy for 6 min'");
                            }
                        }
                    }

                })
                .repeat()
                .retry()
                .subscribe();
    }

    private BestQuotes calcBestQuotesAndDeltas(OrderBook bitmexOrderBook, OrderBook okCoinOrderBook) {
        BestQuotes bestQuotes = new BestQuotes(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        if (bitmexOrderBook != null && okCoinOrderBook != null) {

            if (okCoinOrderBook.getAsks().size() == 0 || okCoinOrderBook.getBids().size() == 0
                    || bitmexOrderBook.getAsks().size() == 0 || bitmexOrderBook.getBids().size() == 0) {
                return BestQuotes.empty();
            }

            // 1. Calc deltas
            bestQuotes = Utils.createBestQuotes(okCoinOrderBook, bitmexOrderBook);
            if (!bestQuotes.hasEmpty()) {
                if (!firstDeltasCalculated) {
                    firstDeltasCalculated = true;
                    logger.info("Started: First delta calculated");
                    warningLogger.info("Started: First delta calculated");
                    slackNotifications.sendNotify("Started: First delta calculated");
                }

                if (!deltasCalcService.isStarted()) {
                    BorderParams borderParams = persistenceService.fetchBorders();
                    deltasCalcService.initDeltasCache(borderParams.getBorderDelta());
                }

                BigDecimal delta1Update = bestQuotes.getBid1_p().subtract(bestQuotes.getAsk1_o());
                BigDecimal delta2Update = bestQuotes.getBid1_o().subtract(bestQuotes.getAsk1_p());

                if (delta1Update.compareTo(delta1) != 0 || delta2Update.compareTo(delta2) != 0) {
                    deltaChangesPublisher.onNext(new DeltaChange(
                            delta1Update.compareTo(delta1) != 0 ? delta1Update : null,
                            delta2Update.compareTo(delta2) != 0 ? delta2Update : null));
                }

                delta1 = delta1Update;
                delta2 = delta2Update;

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

    private void doComparison(BestQuotes bestQuotes, OrderBook bitmexOrderBook, OrderBook okCoinOrderBook) {

        if (firstMarketService.isMarketStopped() || secondMarketService.isMarketStopped()) {
            // do nothing
            stopSignalDelay();

//        } else if (!isReadyForTheArbitrage) {
            // debugLog.info("isReadyForTheArbitrage=false");
            // do not stopSignalDelay
        } else {
            if (Thread.holdsLock(calcLock)) {
                logger.warn("calcLock is in progress");
            }
            synchronized (calcLock) {

                if (firstMarketService.isStarted()
                        && bitmexOrderBook != null
                        && okCoinOrderBook != null
                        && firstMarketService.getAccountInfoContracts() != null
                        && secondMarketService.getAccountInfoContracts() != null) {
                    calcAndDoArbitrage(bestQuotes, bitmexOrderBook, okCoinOrderBook);
                } else {
                    stopSignalDelay();
                }
            }
        }

    }

    private BestQuotes calcAndDoArbitrage(BestQuotes bestQuotes, OrderBook bitmexOrderBook, OrderBook okCoinOrderBook) {

        final BigDecimal bP = firstMarketService.getPosition().getPositionLong();
        final BigDecimal oPL = secondMarketService.getPosition().getPositionLong();
        final BigDecimal oPS = secondMarketService.getPosition().getPositionShort();

        final BorderParams borderParams = persistenceService.fetchBorders();
        if (borderParams == null || borderParams.getActiveVersion() == Ver.V1) {
            BigDecimal border1 = params.getBorder1();
            BigDecimal border2 = params.getBorder2();

            if (delta1.compareTo(border1) >= 0) {
                if (signalDelayActivateTime == null) {
                    startSignalDelay(0);
                }

                PlBlocks plBlocks = placingBlocksService.getPlacingBlocks(bitmexOrderBook, okCoinOrderBook, border1, DeltaName.B_DELTA, oPL, oPS);
                if (plBlocks.getBlockOkex().signum() == 0) {
                    return bestQuotes;
                }
                String dynDeltaLogs = null;
                if (plBlocks.isDynamic()) {
                    plBlocks = dynBlockDecriseByAffordable(DeltaName.B_DELTA, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex());
                    dynDeltaLogs = composeDynBlockLogs("b_delta", bitmexOrderBook, okCoinOrderBook, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex())
                            + plBlocks.getDebugLog();
                }

                if (plBlocks.getBlockOkex().signum() > 0) {
                    Instant lastObTime = Utils.getLastObTime(bitmexOrderBook, okCoinOrderBook);
                    checkAndStartTradingOnDelta1(borderParams, SignalType.AUTOMATIC, bestQuotes, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex(),
                            null, dynDeltaLogs, null, false, lastObTime);
                    return bestQuotes;
                }
            } else if (delta2.compareTo(border2) >= 0) {
                if (signalDelayActivateTime == null) {
                    startSignalDelay(0);
                }

                PlBlocks plBlocks = placingBlocksService.getPlacingBlocks(bitmexOrderBook, okCoinOrderBook, border2, DeltaName.O_DELTA, oPL, oPS);
                if (plBlocks.getBlockOkex().signum() == 0) {
                    return bestQuotes;
                }
                String dynDeltaLogs = null;
                if (plBlocks.isDynamic()) {
                    plBlocks = dynBlockDecriseByAffordable(DeltaName.O_DELTA, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex());
                    dynDeltaLogs = composeDynBlockLogs("o_delta", bitmexOrderBook, okCoinOrderBook, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex())
                            + plBlocks.getDebugLog();
                }
                if (plBlocks.getBlockOkex().signum() > 0) {
                    Instant lastObTime = Utils.getLastObTime(bitmexOrderBook, okCoinOrderBook);
                    checkAndStartTradingOnDelta2(borderParams, SignalType.AUTOMATIC, bestQuotes, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex(),
                            null, dynDeltaLogs, null, false, lastObTime);
                    return bestQuotes;
                }
            } else {
                stopSignalDelay();
            }

        } else if (borderParams.getActiveVersion() == Ver.V2) {

            boolean withWarningLogs =
                    firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage() && posDiffService.checkIsPositionsEqual();

            final BordersService.TradingSignal tradingSignal = bordersService.checkBorders(
                    bitmexOrderBook, okCoinOrderBook, delta1, delta2, bP, oPL, oPS, withWarningLogs);

            if (tradingSignal.okexBlock == 0) {
                stopSignalDelay();
                return bestQuotes;
            }

            if (tradingSignal.tradeType == BordersService.TradeType.DELTA1_B_SELL_O_BUY
                    || tradingSignal.tradeType == BordersService.TradeType.DELTA2_B_BUY_O_SELL) {
                if (signalDelayActivateTime == null) {
                    startSignalDelay(0);
                }

                if (tradingSignal.tradeType == BordersService.TradeType.DELTA1_B_SELL_O_BUY) {
                    if (tradingSignal.ver == PlacingBlocks.Ver.DYNAMIC) {
                        final PlBlocks bl = dynBlockDecriseByAffordable(DeltaName.B_DELTA, BigDecimal.valueOf(tradingSignal.bitmexBlock),
                                BigDecimal.valueOf(tradingSignal.okexBlock));
                        if (bl.getBlockOkex().signum() > 0) {
                            final BordersService.TradingSignal ts = bordersService.setNewBlock(tradingSignal, bl.getBlockOkex().intValueExact());
                            final BigDecimal b_block = BigDecimal.valueOf(ts.bitmexBlock);
                            final BigDecimal o_block = BigDecimal.valueOf(ts.okexBlock);
                            if (b_block.signum() > 0 && o_block.signum() > 0) {
                                final String dynDeltaLogs = composeDynBlockLogs("b_delta", bitmexOrderBook, okCoinOrderBook, b_block, o_block)
                                        + bl.getDebugLog();
                                Instant lastObTime = Utils.getLastObTime(bitmexOrderBook, okCoinOrderBook);
                                checkAndStartTradingOnDelta1(borderParams, SignalType.AUTOMATIC, bestQuotes, b_block, o_block,
                                        tradingSignal, dynDeltaLogs, null, false, lastObTime);
                                return bestQuotes;
                            } else {
                                warningLogger.warn("Block calc(after border2Calc): Block should be > 0, but okexBlock=" + bl.getBlockOkex());
                            }
                        }
                    } else {
                        final BigDecimal b_block = BigDecimal.valueOf(tradingSignal.bitmexBlock);
                        final BigDecimal o_block = BigDecimal.valueOf(tradingSignal.okexBlock);
                        Instant lastObTime = Utils.getLastObTime(bitmexOrderBook, okCoinOrderBook);
                        checkAndStartTradingOnDelta1(borderParams, SignalType.AUTOMATIC, bestQuotes, b_block, o_block,
                                tradingSignal, null, null, false, lastObTime);
                        return bestQuotes;
                    }
                }

                if (tradingSignal.tradeType == BordersService.TradeType.DELTA2_B_BUY_O_SELL) {
                    if (tradingSignal.ver == PlacingBlocks.Ver.DYNAMIC) {
                        final PlBlocks bl = dynBlockDecriseByAffordable(DeltaName.O_DELTA, BigDecimal.valueOf(tradingSignal.bitmexBlock),
                                BigDecimal.valueOf(tradingSignal.okexBlock));
                        if (bl.getBlockOkex().signum() > 0) {
                            final BordersService.TradingSignal ts = bordersService.setNewBlock(tradingSignal, bl.getBlockOkex().intValueExact());
                            final BigDecimal b_block = BigDecimal.valueOf(ts.bitmexBlock);
                            final BigDecimal o_block = BigDecimal.valueOf(ts.okexBlock);
                            if (b_block.signum() > 0 && o_block.signum() > 0) {
                                final String dynDeltaLogs = composeDynBlockLogs("b_delta", bitmexOrderBook, okCoinOrderBook, b_block, o_block)
                                        + bl.getDebugLog();
                                Instant lastObTime = Utils.getLastObTime(bitmexOrderBook, okCoinOrderBook);
                                checkAndStartTradingOnDelta2(borderParams, SignalType.AUTOMATIC, bestQuotes, b_block, o_block,
                                        tradingSignal, dynDeltaLogs, null, false, lastObTime);
                                return bestQuotes;
                            } else {
                                warningLogger.warn("Block calc(after border2Calc): Block should be > 0, but okexBlock=" + bl.getBlockOkex());
                            }
                        }
                    } else {
                        final BigDecimal b_block = BigDecimal.valueOf(tradingSignal.bitmexBlock);
                        final BigDecimal o_block = BigDecimal.valueOf(tradingSignal.okexBlock);
                        Instant lastObTime = Utils.getLastObTime(bitmexOrderBook, okCoinOrderBook);
                        checkAndStartTradingOnDelta2(borderParams, SignalType.AUTOMATIC, bestQuotes, b_block, o_block,
                                tradingSignal, null, null, false, lastObTime);
                        return bestQuotes;
                    }
                }
            } else {
                stopSignalDelay();
            }
        }

        return bestQuotes;
    }

    private String composeDynBlockLogs(String deltaName, OrderBook bitmexOrderBook, OrderBook okCoinOrderBook, BigDecimal b_block, BigDecimal o_block) {
        final String bMsg = Utils.getTenAskBid(bitmexOrderBook, "",
                "Bitmex OrderBook");
        final String oMsg = Utils.getTenAskBid(okCoinOrderBook, "",
                "Okex OrderBook");
        final PlacingBlocks placingBlocks = persistenceService.getSettingsRepositoryService().getSettings().getPlacingBlocks();
        return String.format("%s: Dynamic: dynMaxBlockOkex=%s, b_block=%s, o_block=%s\n%s\n%s. ",
                deltaName,
                placingBlocks.getDynMaxBlockOkex(),
                b_block, o_block,
                bMsg, oMsg);
    }

    public void startPreliqOnDelta1(SignalType signalType, BestQuotes bestQuotes) {
        // border V1
        PreliqBlocks preliqBlocks = new PreliqBlocks().getPreliqBlocks(DeltaName.B_DELTA);
        if (preliqBlocks.isForbidden()) {
            return;
        }
        final BigDecimal b_block = preliqBlocks.getB_block();
        final BigDecimal o_block = preliqBlocks.getO_block();

        final BorderParams borderParams = persistenceService.fetchBorders();
        checkAndStartTradingOnDelta1(borderParams, signalType, bestQuotes, b_block, o_block, null, null, PlacingType.TAKER,
                true, null);
    }

    private void checkAndStartTradingOnDelta1(BorderParams borderParams, SignalType signalType, final BestQuotes bestQuotes, final BigDecimal b_block,
            final BigDecimal o_block, final TradingSignal tradingSignal, String dynamicDeltaLogs, PlacingType predefinedPlacingType, boolean isImmediate,
            final Instant lastObTime) {
        final BigDecimal ask1_o = bestQuotes.getAsk1_o();
        final BigDecimal bid1_p = bestQuotes.getBid1_p();
        if (checkBalanceBorder1(DeltaName.B_DELTA, b_block, o_block)
                && firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage()
                && posDiffService.checkIsPositionsEqual()
                && !firstMarketService.isMarketStopped() && !secondMarketService.isMarketStopped()
                && // liqEdge violation only with non-AUTOMATIC signals(corr,preliq,etc)
                (signalType != SignalType.AUTOMATIC ||
                        (firstMarketService.checkLiquidationEdge(OrderType.ASK)
                                && secondMarketService.checkLiquidationEdge(OrderType.BID))
                )) {

            synchronized (arbInProgressLock) {
                if (!arbInProgress.get()) {

                    if (isImmediate) {
                        arbInProgress.set(true);
                        startTradingOnDelta1(borderParams, signalType, bestQuotes, b_block, o_block, tradingSignal, dynamicDeltaLogs, predefinedPlacingType,
                                ask1_o,
                                bid1_p, lastObTime);
                    } else if (signalDelayActivateTime == null) {
                        startSignalDelay(0);
                    } else if (isSignalDelayExceeded()) {
                        arbInProgress.set(true);
                        startTradingOnDelta1(borderParams, signalType, bestQuotes, b_block, o_block, tradingSignal, dynamicDeltaLogs, predefinedPlacingType,
                                ask1_o,
                                bid1_p, lastObTime);
                    }
                }
            }

        } else {
            bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.ONLY_SIGNAL);
        }
    }

    public void restartSignalDelay() {
        if (signalDelayActivateTime != null) {
            long passedDelay = Instant.now().toEpochMilli() - signalDelayActivateTime;
            stopSignalDelay();
            startSignalDelay(passedDelay);
        }
    }

    private void startSignalDelay(long passedDelayMs) {
        signalDelayActivateTime = Instant.now().toEpochMilli() - passedDelayMs;
        long signalDelayMs = persistenceService.getSettingsRepositoryService().getSettings().getSignalDelayMs() - passedDelayMs;
        if (signalDelayMs <= 0) {
            signalDelayMs = 1;
        }

        futureSignal = signalDelayScheduler.schedule(() -> {
            signalEventBus.send(SignalEvent.B_ORDERBOOK_CHANGED); // to make sure that it will happen in the 'signalDeltayMs period'
        }, signalDelayMs, TimeUnit.MILLISECONDS);

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

    private void stopSignalDelay() {
        signalDelayActivateTime = null;
        if (futureSignal != null && !futureSignal.isDone()) {
            futureSignal.cancel(false);
        }
    }

    private boolean isSignalDelayExceeded() {
        if (signalDelayActivateTime == null) {
            return false;
        }
        final Integer signalDelayMs = persistenceService.getSettingsRepositoryService().getSettings().getSignalDelayMs();
        return Instant.now().toEpochMilli() - signalDelayActivateTime > signalDelayMs;
    }

    private void startTradingOnDelta1(BorderParams borderParams, SignalType signalType, BestQuotes bestQuotes, BigDecimal b_block, BigDecimal o_block,
            TradingSignal tradingSignal, String dynamicDeltaLogs, PlacingType predefinedPlacingType, BigDecimal ask1_o, BigDecimal bid1_p,
            Instant lastObTime) {

        logger.info("START SIGNAL 1");
        startSignalTime = Instant.now();

        int pos_bo = diffFactBrService.getCurrPos(borderParams.getPosMode());

        bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
        setSignalType(signalType);
        params.setLastDelta(DELTA1);

        firstMarketService.setBusy();
        secondMarketService.setBusy();

        final String counterName = createCounterOnStartTrade(ask1_o, bid1_p, tradingSignal, params.getBorder1(), delta1, DeltaName.B_DELTA);

        if (dynamicDeltaLogs != null) {
            tradeService.info(tradeId, counterName, String.format("#%s %s", counterName, dynamicDeltaLogs));
        }

        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        final PlacingType okexPlacingType = predefinedPlacingType != null ? predefinedPlacingType : settings.getOkexPlacingType();
        final PlacingType btmPlacingType = predefinedPlacingType != null ? predefinedPlacingType : settings.getBitmexPlacingType();

        synchronized (dealPrices) {
            dealPrices.setBtmPlacingType(btmPlacingType);
            dealPrices.setOkexPlacingType(okexPlacingType);
            dealPrices.setBorder1(params.getBorder1());
            dealPrices.setBorder2(params.getBorder2());
            dealPrices.setoBlock(o_block);
            dealPrices.setbBlock(b_block);
            dealPrices.setDelta1Plan(delta1);
            dealPrices.setDelta2Plan(delta2);
            dealPrices.setbPricePlan(bid1_p);
            dealPrices.setoPricePlan(ask1_o);
            dealPrices.setDeltaName(DeltaName.B_DELTA);
            dealPrices.setBestQuotes(bestQuotes);

            dealPrices.setbPriceFact(new AvgPrice(counterName, b_block, "bitmex"));
            dealPrices.setoPriceFact(new AvgPrice(counterName, o_block, "okex"));

            dealPrices.setBorderParamsOnStart(borderParams);
            dealPrices.setPos_bo(pos_bo);
            dealPrices.calcPlanPosAo();

            if (dealPrices.getPlan_pos_ao().equals(dealPrices.getPos_bo())) {
                tradeService.warn(tradeId, counterName, "WARNING: pos_bo==pos_ao==" + dealPrices.getPos_bo() + ". " + dealPrices.toString());
                warningLogger.warn("WARNING: pos_bo==pos_ao==" + dealPrices.getPos_bo() + ". " + dealPrices.toString());
            }
        }

        tradeService.info(tradeId, counterName, String.format("#%s is started ---", counterName));
        // in scheme MT2 Okex should be the first
        signalService.placeOkexOrderOnSignal(secondMarketService, Order.OrderType.BID, o_block, bestQuotes, signalType, okexPlacingType,
                counterName, tradeId, lastObTime);
        signalService.placeBitmexOrderOnSignal(firstMarketService, Order.OrderType.ASK, b_block, bestQuotes, signalType, btmPlacingType,
                counterName, tradeId, lastObTime);

        setTimeoutAfterStartTrading();

        saveParamsToDb();
    }

    public void startPerliqOnDelta2(SignalType signalType, BestQuotes bestQuotes) {
        PreliqBlocks preliqBlocks = new PreliqBlocks().getPreliqBlocks(DeltaName.O_DELTA);
        if (preliqBlocks.isForbidden()) {
            return;
        }
        final BigDecimal b_block = preliqBlocks.getB_block();
        final BigDecimal o_block = preliqBlocks.getO_block();

        final BorderParams borderParams = persistenceService.fetchBorders();
        checkAndStartTradingOnDelta2(borderParams, signalType, bestQuotes, b_block, o_block,
                null, null, PlacingType.TAKER, true, null);
    }

    private void checkAndStartTradingOnDelta2(BorderParams borderParams, final SignalType signalType,
            final BestQuotes bestQuotes, final BigDecimal b_block, final BigDecimal o_block,
            final TradingSignal tradingSignal, String dynamicDeltaLogs, PlacingType predefinedPlacingType, boolean isImmediate,
            final Instant lastObTime) {
        final BigDecimal ask1_p = bestQuotes.getAsk1_p();
        final BigDecimal bid1_o = bestQuotes.getBid1_o();

        if (checkBalanceBorder1(DeltaName.O_DELTA, b_block, o_block)
                && firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage()
                && posDiffService.checkIsPositionsEqual()
                && !firstMarketService.isMarketStopped() && !secondMarketService.isMarketStopped()
                && // liqEdge violation only with non-AUTOMATIC signals(corr,preliq,etc)
                (signalType != SignalType.AUTOMATIC ||
                        (firstMarketService.checkLiquidationEdge(OrderType.BID)
                                && secondMarketService.checkLiquidationEdge(OrderType.ASK))
                )) {

            synchronized (arbInProgressLock) {
                if (!arbInProgress.get()) {

                    if (isImmediate) {
                        arbInProgress.set(true);
                        startTradingOnDelta2(borderParams, signalType, bestQuotes, b_block, o_block, tradingSignal, dynamicDeltaLogs, predefinedPlacingType,
                                ask1_p,
                                bid1_o, lastObTime);
                    } else if (signalDelayActivateTime == null) {
                        startSignalDelay(0);
                    } else if (isSignalDelayExceeded()) {
                        arbInProgress.set(true);
                        startTradingOnDelta2(borderParams, signalType, bestQuotes, b_block, o_block, tradingSignal, dynamicDeltaLogs, predefinedPlacingType,
                                ask1_p,
                                bid1_o, lastObTime);
                    }
                }
            }

        } else {
            bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.ONLY_SIGNAL);
        }
    }

    private void startTradingOnDelta2(BorderParams borderParams, SignalType signalType, BestQuotes bestQuotes, BigDecimal b_block, BigDecimal o_block,
            TradingSignal tradingSignal, String dynamicDeltaLogs, PlacingType predefinedPlacingType, BigDecimal ask1_p, BigDecimal bid1_o,
            Instant lastObTime) {

        logger.info("START SIGNAL 2");
        startSignalTime = Instant.now();

        int pos_bo = diffFactBrService.getCurrPos(borderParams.getPosMode());

        bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
        setSignalType(signalType);
        params.setLastDelta(DELTA2);

        firstMarketService.setBusy();
        secondMarketService.setBusy();

        final String counterName = createCounterOnStartTrade(ask1_p, bid1_o, tradingSignal, params.getBorder2(), delta2, DeltaName.O_DELTA);

        if (dynamicDeltaLogs != null) {
            tradeService.info(tradeId, counterName, String.format("#%s %s", counterName, dynamicDeltaLogs));
        }

        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        final PlacingType okexPlacingType = predefinedPlacingType != null ? predefinedPlacingType : settings.getOkexPlacingType();
        final PlacingType btmPlacingType = predefinedPlacingType != null ? predefinedPlacingType : settings.getBitmexPlacingType();

        synchronized (dealPrices) {
            dealPrices.setBtmPlacingType(btmPlacingType);
            dealPrices.setOkexPlacingType(okexPlacingType);
            dealPrices.setBorder1(params.getBorder1());
            dealPrices.setBorder2(params.getBorder2());
            dealPrices.setoBlock(o_block);
            dealPrices.setbBlock(b_block);
            dealPrices.setDelta1Plan(delta1);
            dealPrices.setDelta2Plan(delta2);
            dealPrices.setbPricePlan(ask1_p);
            dealPrices.setoPricePlan(bid1_o);
            dealPrices.setDeltaName(DeltaName.O_DELTA);
            dealPrices.setBestQuotes(bestQuotes);

            dealPrices.setbPriceFact(new AvgPrice(counterName, b_block, "bitmex"));
            dealPrices.setoPriceFact(new AvgPrice(counterName, o_block, "okex"));

            dealPrices.setBorderParamsOnStart(borderParams);
            dealPrices.setPos_bo(pos_bo);
            dealPrices.calcPlanPosAo();

            if (dealPrices.getPlan_pos_ao().equals(dealPrices.getPos_bo())) {
                tradeService.warn(tradeId, counterName, "WARNING: pos_bo==pos_ao==" + dealPrices.getPos_bo() + ". " + dealPrices.toString());
                warningLogger.warn("WARNING: pos_bo==pos_ao==" + dealPrices.getPos_bo() + ". " + dealPrices.toString());
            }
        }

        tradeService.info(tradeId, counterName, String.format("#%s is started ---", counterName));
        // in scheme MT2 Okex should be the first
        signalService.placeOkexOrderOnSignal(secondMarketService, Order.OrderType.ASK, o_block, bestQuotes, signalType, okexPlacingType,
                counterName, tradeId, lastObTime);
        signalService.placeBitmexOrderOnSignal(firstMarketService, Order.OrderType.BID, b_block, bestQuotes, signalType, btmPlacingType,
                counterName, tradeId, lastObTime);

        setTimeoutAfterStartTrading();

        saveParamsToDb();
    }

    private String createCounterOnStartTrade(BigDecimal ask1_X, BigDecimal bid1_X, final BordersService.TradingSignal tradingSignal,
            final BigDecimal borderX, final BigDecimal deltaX, final DeltaName deltaName) {

        Integer counter1 = params.getCounter1();
        Integer counter2 = params.getCounter2();

        final CumParams cumParams = persistenceService.fetchCumParams();
        final Integer cc1 = cumParams.getCompletedCounter1();
        final Integer cc2 = cumParams.getCompletedCounter2();

        if (deltaName.getDeltaNumber().equals("1")) {
            counter1 += 1;
            params.setCounter1(counter1);
        } else {
            counter2 += 1;
            params.setCounter2(counter2);
        }

        String iterationMarker = "";
        if (counter1.equals(counter2)) {
            iterationMarker = "whole iteration";
        }

        if (signalType.isPreliq()) {
            CorrParams corrParams = persistenceService.fetchCorrParams();
            corrParams.getPreliq().incTotalCount();
            persistenceService.saveCorrParams(corrParams);
        }
        final String counterName = firstMarketService.getCounterName();

        fplayTrade = tradeService.createTrade(counterName, deltaName,
                ((BitmexContractType) firstMarketService.getContractType()),
                ((OkexContractType) secondMarketService.getContractType()));
        tradeId = fplayTrade.getId();
        tradeService.info(tradeId, counterName, "------------------------------------------");

        tradeService.info(tradeId, counterName, String.format("count=%s+%s=%s(completed=%s+%s=%s) %s",
                counter1, counter2, counter1 + counter2,
                cc1, cc2, cc1 + cc2,
                iterationMarker));

        tradeService.info(tradeId, counterName, String.format("delta%s=%s-%s=%s; %s",
                deltaName.getDeltaNumber(),
                bid1_X.toPlainString(), ask1_X.toPlainString(),
                deltaX.toPlainString(),
                tradingSignal == null
                        ? (String.format("b%s=%s", deltaName.getDeltaNumber(), borderX.toPlainString()))
                        : ("borderV2:" + tradingSignal.toString())
        ));

        printSumBal(tradeId, counterName);

        return counterName;
    }

    @Scheduled(initialDelay = 10 * 1000, fixedDelay = 1000)
    public void calcSumBalForGui() {
        lastCalcSumBal = Instant.now();
        final AccountInfoContracts firstAccount = firstMarketService.calcFullBalance().getAccountInfoContracts();
        final AccountInfoContracts secondAccount = secondMarketService.calcFullBalance().getAccountInfoContracts();
        if (firstAccount != null && secondAccount != null) {
            final BigDecimal bW = firstAccount.getWallet();
            final BigDecimal bEMark = firstAccount.geteMark() != null ? firstAccount.geteMark() : BigDecimal.ZERO;
            bEbest = firstAccount.geteBest() != null ? firstAccount.geteBest() : BigDecimal.ZERO;
            final BigDecimal bEAvg = firstAccount.geteAvg() != null ? firstAccount.geteAvg() : BigDecimal.ZERO;
            final BigDecimal bU = firstAccount.getUpl();
            final BigDecimal bM = firstAccount.getMargin();
            final BigDecimal bA = firstAccount.getAvailable();

            BigDecimal oW = secondAccount.getWallet();
            BigDecimal oELast = secondAccount.geteLast() != null ? secondAccount.geteLast() : BigDecimal.ZERO;
            oEbest = secondAccount.geteBest() != null ? secondAccount.geteBest() : BigDecimal.ZERO;
            BigDecimal oEAvg = secondAccount.geteAvg() != null ? secondAccount.geteAvg() : BigDecimal.ZERO;
            BigDecimal oM = secondAccount.getMargin();
            BigDecimal oU = secondAccount.getUpl();
            BigDecimal oA = secondAccount.getAvailable();

            if (bW == null || oW == null) {
                throw new IllegalStateException(String.format("Balance is not yet defined. bW=%s, oW=%s", bW, oW));
            }

            boolean isEth = secondMarketService.getEthBtcTicker() != null && firstMarketService.getContractType().isEth();

            if (isEth) {
                BigDecimal ethBtcBid1 = secondMarketService.getEthBtcTicker().getBid();
                oW = oW.multiply(ethBtcBid1);
                oELast = oELast.multiply(ethBtcBid1);
                oEbest = oEbest.multiply(ethBtcBid1);
                oEAvg = oEAvg.multiply(ethBtcBid1);
                oM = oM.multiply(ethBtcBid1);
                oU = oU.multiply(ethBtcBid1);
                oA = oA.multiply(ethBtcBid1);
            }

            final BigDecimal coldStorageBtc = persistenceService.getSettingsRepositoryService().getSettings().getColdStorageBtc();
            final BigDecimal sumW = bW.add(oW).add(coldStorageBtc).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumE = bEMark.add(oELast).add(coldStorageBtc).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumEBest = bEbest.add(oEbest).add(coldStorageBtc).setScale(8, BigDecimal.ROUND_HALF_UP);
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

            if (!traderPermissionsService.isEBestMinOk()) {
                Integer eBestMin = persistenceService.getSettingsRepositoryService().getSettings().getEBestMin();
                warningLogger.warn("WARNING: sumEBestUsd({}) < e_best_min({})", sumEBestUsd, eBestMin);

                firstMarketService.setMarketState(MarketState.FORBIDDEN);
                secondMarketService.setMarketState(MarketState.FORBIDDEN);
            }

            // calc auto hedge
            if (firstMarketService.getContractType().isEth()) {
                BigDecimal he_usd = oEbest.multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP);
                hedgeService.setHedgeEth(he_usd);
                BigDecimal hb_usd = (bEbest.add(coldStorageBtc)).multiply(usdQuote).setScale(2, BigDecimal.ROUND_HALF_UP);
                hedgeService.setHedgeBtc(hb_usd);
            } else {
                hedgeService.setHedgeBtc(sumEBestUsdCurr);
                hedgeService.setHedgeEth(BigDecimal.ZERO);
            }
        }

        Instant end = Instant.now();
        Utils.logIfLong(lastCalcSumBal, end, logger, "calcSumBalForGui");
    }

    public Long printToCurrentDeltaLog(String msg) {
        final Long tradeIdSnap = getLastTradeId();
        final String counterName = firstMarketService.getCounterName();
        tradeService.info(tradeIdSnap, counterName, msg);
        return tradeIdSnap;
    }

    public void printSumBal(Long tradeId, String counterName) {
        if (tradeId == null) {
            tradeId = this.tradeId;
            if (tradeId == null) {
                logger.warn("printSumBal with tradeId==null");
                return;
            }
        }
        try {
            final AccountInfoContracts firstAccount = firstMarketService.calcFullBalance().getAccountInfoContracts();
            final AccountInfoContracts secondAccount = secondMarketService.calcFullBalance().getAccountInfoContracts();
            if (firstAccount != null && secondAccount != null) {
                final BigDecimal bW = firstAccount.getWallet();
                final BigDecimal bEmark = firstAccount.geteMark() != null ? firstAccount.geteMark() : BigDecimal.ZERO;
                bEbest = firstAccount.geteBest() != null ? firstAccount.geteBest() : BigDecimal.ZERO;
                final BigDecimal bEavg = firstAccount.geteAvg() != null ? firstAccount.geteAvg() : BigDecimal.ZERO;
                final BigDecimal bU = firstAccount.getUpl();
                final BigDecimal bM = firstAccount.getMargin();
                final BigDecimal bA = firstAccount.getAvailable();
                final BigDecimal bP = firstMarketService.getPosition().getPositionLong();
                final BigDecimal bLv = firstMarketService.getPosition().getLeverage();
                final BigDecimal bAL = firstMarketService.getAffordable().getForLong();
                final BigDecimal bAS = firstMarketService.getAffordable().getForShort();
                final BigDecimal usdQuote = getUsdQuote();
                final OrderBook bOrderBook = firstMarketService.getOrderBook();
                final BigDecimal bBestAsk = Utils.getBestAsks(bOrderBook, 1).get(0).getLimitPrice();
                final BigDecimal bBestBid = Utils.getBestBids(bOrderBook, 1).get(0).getLimitPrice();
                tradeService.info(tradeId, counterName, String.format(
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
                BigDecimal oElast = secondAccount.geteLast() != null ? secondAccount.geteLast() : BigDecimal.ZERO;
                oEbest = secondAccount.geteBest() != null ? secondAccount.geteBest() : BigDecimal.ZERO;
                BigDecimal oEavg = secondAccount.geteAvg() != null ? secondAccount.geteAvg() : BigDecimal.ZERO;
                BigDecimal oM = secondAccount.getMargin();
                BigDecimal oU = secondAccount.getUpl();
                BigDecimal oA = secondAccount.getAvailable();
                if (secondMarketService.getEthBtcTicker() != null) {
                    BigDecimal ethBtcBid1 = secondMarketService.getEthBtcTicker().getBid();
                    oW = oW.multiply(ethBtcBid1);
                    oElast = oElast.multiply(ethBtcBid1);
                    oEbest = oEbest.multiply(ethBtcBid1);
                    oEavg = oEavg.multiply(ethBtcBid1);
                    oM = oM.multiply(ethBtcBid1);
                    oU = oU.multiply(ethBtcBid1);
                    oA = oA.multiply(ethBtcBid1);
                }
                final BigDecimal oPL = secondMarketService.getPosition().getPositionLong();
                final BigDecimal oPS = secondMarketService.getPosition().getPositionShort();
                final BigDecimal oLv = secondMarketService.getPosition().getLeverage();
                final BigDecimal oAL = secondMarketService.getAffordable().getForLong();
                final BigDecimal oAS = secondMarketService.getAffordable().getForShort();
                final OrderBook oOrderBook = secondMarketService.getOrderBook();
                final BigDecimal oBestAsk = Utils.getBestAsks(oOrderBook, 1).get(0).getLimitPrice();
                final BigDecimal oBestBid = Utils.getBestBids(oOrderBook, 1).get(0).getLimitPrice();

                tradeService.info(tradeId, counterName, String.format(
                        "#%s o_bal=w%s_%s, e_mark%s_%s, e_best%s_%s, e_avg%s_%s, u%s_%s, m%s_%s, a%s_%s, p+%s-%s, lv%s, lg%s, st%s, ask[1]%s, bid[1]%s, usd_qu%s",
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
                        oBestAsk,
                        oBestBid,
                        usdQuote.toPlainString()
                ));

                final BigDecimal coldStorageBtc = persistenceService.getSettingsRepositoryService().getSettings().getColdStorageBtc();
                final BigDecimal sumW = bW.add(oW).add(coldStorageBtc).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumE = bEmark.add(oElast).add(coldStorageBtc).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumEBest = bEbest.add(oEbest).add(coldStorageBtc).setScale(8, BigDecimal.ROUND_HALF_UP);
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
                tradeService.info(tradeId, counterName, sBalStr);

                final String bDQLMin;
                final String oDQLMin;
                GuiLiqParams guiLiqParams = persistenceService.fetchGuiLiqParams();
                if (signalType == SignalType.B_PRE_LIQ || signalType == SignalType.O_PRE_LIQ) {
                    bDQLMin = String.format("b_DQL_close_min=%s", guiLiqParams.getBDQLCloseMin());
                    oDQLMin = String.format("o_DQL_close_min=%s", guiLiqParams.getODQLCloseMin());
                } else {
                    bDQLMin = String.format("b_DQL_open_min=%s", guiLiqParams.getBDQLOpenMin());
                    oDQLMin = String.format("o_DQL_open_min=%s", guiLiqParams.getODQLOpenMin());
                }

                tradeService.info(tradeId, counterName, String.format("#%s %s", counterName, getFullPosDiff()));
                final LiqInfo bLiqInfo = getFirstMarketService().getLiqInfo();
                tradeService
                        .info(tradeId, counterName, String.format("#%s %s; %s; %s", counterName, bLiqInfo.getDqlString(), bLiqInfo.getDmrlString(), bDQLMin));
                final LiqInfo oLiqInfo = getSecondMarketService().getLiqInfo();
                tradeService
                        .info(tradeId, counterName, String.format("#%s %s; %s; %s", counterName, oLiqInfo.getDqlString(), oLiqInfo.getDmrlString(), oDQLMin));
            }
        } catch (Exception e) {
            tradeService.error(tradeId, counterName, "Error on printSumBal");
            logger.error("Error on printSumBal", e);
        }
    }

    public BigDecimal getUsdQuote() {
        UsdQuoteType usdQuoteType = persistenceService.getSettingsRepositoryService().getSettings().getUsdQuoteType();
        BigDecimal usdQuote;
        switch (usdQuoteType) {
            case BITMEX:
                usdQuote = Utils.calcQuAvg(firstMarketService.getOrderBookXBTUSD());
                break;
            case OKEX:
                usdQuote = Utils.calcQuAvg(secondMarketService.getOrderBookXBTUSD());
                break;
            case INDEX_BITMEX:
                if (firstMarketService.getContractType().isEth()) {
                    usdQuote = firstMarketService.getBtcContractIndex() != null && firstMarketService.getBtcContractIndex().getIndexPrice() != null
                            ? firstMarketService.getBtcContractIndex().getIndexPrice()
                            : BigDecimal.ZERO;
                } else {
                    usdQuote = firstMarketService.getContractIndex() != null && firstMarketService.getContractIndex().getIndexPrice() != null
                            ? firstMarketService.getContractIndex().getIndexPrice()
                            : BigDecimal.ZERO;
                }
                break;
            case INDEX_OKEX:
                if (secondMarketService.getContractType().isEth()) {
                    usdQuote = secondMarketService.getBtcContractIndex() != null && secondMarketService.getBtcContractIndex().getIndexPrice() != null
                            ? secondMarketService.getBtcContractIndex().getIndexPrice()
                            : BigDecimal.ZERO;
                } else {
                    usdQuote = secondMarketService.getContractIndex() != null && secondMarketService.getContractIndex().getIndexPrice() != null
                            ? secondMarketService.getContractIndex().getIndexPrice()
                            : BigDecimal.ZERO;
                }
                break;
            case AVG:
            default:
                usdQuote = Utils.calcQuAvg(firstMarketService.getOrderBookXBTUSD(), secondMarketService.getOrderBookXBTUSD());
                break;
        }
        return usdQuote;
    }

    public String getFullPosDiff() {
        return getMainSetStr() + getMainSetSource() + getExtraSetStr() + getExtraSetSource();
    }

    public String getMainSetStr() {
        // Notional(usd) = b_pos * 10 / CM + o_pos * 10 - ha;
        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        final BigDecimal cm = settings.getPlacingBlocks().getBitmexBlockFactor();
        final BigDecimal adj = settings.getPosAdjustment().getPosAdjustmentMin();
        final BigDecimal adjMax = settings.getPosAdjustment().getPosAdjustmentMax();

        MarketService bitmexService = getFirstMarketService();
        MarketService okcoinService = getSecondMarketService();

        boolean isEth = bitmexService.getContractType().isEth();

        final BigDecimal bP = bitmexService.getPosition().getPositionLong();
        final BigDecimal oPL = okcoinService.getPosition().getPositionLong();
        final BigDecimal oPS = okcoinService.getPosition().getPositionShort();
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
        final BigDecimal bP = getFirstMarketService().getPosition().getPositionLong();
        final BigDecimal oPL = getSecondMarketService().getPosition().getPositionLong();
        final BigDecimal oPS = getSecondMarketService().getPosition().getPositionShort();
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

    private PlBlocks dynBlockDecriseByAffordable(DeltaName deltaRef, BigDecimal blockSize1, BigDecimal blockSize2) {
        BigDecimal b1 = BigDecimal.ZERO;
        BigDecimal b2 = BigDecimal.ZERO;
        final BigDecimal cm = persistenceService.getSettingsRepositoryService().getSettings().getPlacingBlocks().getBitmexBlockFactor();
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

        String debugLog = String.format("dynBlockDecriseByAffordable: %s, %s. bitmex %s, okex %s",
                b1, b2, firstAffordable, secondAffordable);

        return new PlBlocks(b1, b2, PlacingBlocks.Ver.DYNAMIC, debugLog);
    }

    private boolean checkBalanceBorder1(DeltaName deltaRef, BigDecimal blockSize1, BigDecimal blockSize2) {
        boolean affordable = false;
        if (deltaRef == DeltaName.B_DELTA) {
            // sell p, buy o
            if (firstMarketService.isAffordable(Order.OrderType.ASK, blockSize1)
                    && secondMarketService.isAffordable(Order.OrderType.BID, blockSize2)) {
                affordable = true;
            }
        } else if (deltaRef == DeltaName.O_DELTA) {
            // buy p , sell o
            if (firstMarketService.isAffordable(Order.OrderType.BID, blockSize1)
                    && secondMarketService.isAffordable(Order.OrderType.ASK, blockSize2)) {
                affordable = true;
            }
        }

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

    public int getCounter() {
        return params.getCounter1() + params.getCounter2();
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
        return firstMarketService.getMarketState() == MarketState.FORBIDDEN
                || secondMarketService.getMarketState() == MarketState.FORBIDDEN;
    }

    public boolean getArbInProgress() {
        return arbInProgress.get();
    }

    public void releaseArbInProgress(String counterName, String from) {
        String msg = "Arbitrage state was reset READY from " + from + ". ";
        tradeService.warn(tradeId, counterName, msg);
        warningLogger.warn(msg);
        logger.warn(msg);

        synchronized (arbInProgressLock) {
            try {
                onArbDone(tradeId, BitmexService.NAME);
                onArbDone(tradeId, OkCoinService.NAME);
            } catch (Exception e) {
                logger.error("Error " + msg, e);
                warningLogger.error("Error " + msg + e.toString());
            }

            arbInProgress.set(false);
        }
    }

    public boolean isFirstDeltasCalculated() {
        return firstDeltasCalculated;
    }

    public String getStartSignalTimer() {
        String res = "_";
        if (startSignalTime != null && arbInProgress.get()) {
            res = String.valueOf(Duration.between(startSignalTime, Instant.now()).getSeconds());
        }
        SignalTimeParams signalTimeParams = signalTimeService.getSignalTimeParams();
        int count = signalTimeParams != null ? signalTimeParams.getAvgDen().intValue() : 0;
        return String.format("Signal(%s) started %s sec ago", count, res);
    }

    public Instant getLastCalcSumBal() {
        return lastCalcSumBal;
    }

    private class PreliqBlocks {

        private boolean forbidden;
        private BigDecimal b_block;
        private BigDecimal o_block;

        boolean isForbidden() {
            return forbidden;
        }

        public BigDecimal getB_block() {
            return b_block;
        }

        public BigDecimal getO_block() {
            return o_block;
        }

        public PreliqBlocks getPreliqBlocks(DeltaName deltaName) {
            final BigDecimal cm = persistenceService.getSettingsRepositoryService().getSettings().getPlacingBlocks().getBitmexBlockFactor();
            final CorrParams corrParams = persistenceService.fetchCorrParams();
            b_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockBitmex(cm));
            o_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockOkex());

            final BigDecimal btmPos = firstMarketService.getPosition().getPositionLong();
            if (btmPos.signum() == 0) {
                String posDetails = String.format("Bitmex %s; Okex %s", firstMarketService.getPosition(), secondMarketService.getPosition());
                logger.error("WARNING: Preliq was not started, because Bitmex pos=0. Details:" + posDetails);
                warningLogger.error("WARNING: Preliq was not started, because Bitmex pos=0. Details:" + posDetails);
                forbidden = true;
                return this;
            }
            if ((deltaName == DeltaName.B_DELTA && btmPos.signum() > 0 && btmPos.compareTo(b_block) < 0)
                    || (deltaName == DeltaName.O_DELTA && btmPos.signum() < 0 && btmPos.abs().compareTo(b_block) < 0)) {
                b_block = btmPos.abs();
                o_block = b_block.divide(cm, 0, RoundingMode.HALF_UP);
            }
            final BigDecimal okLong = secondMarketService.getPosition().getPositionLong();
            final BigDecimal okShort = secondMarketService.getPosition().getPositionShort();
            if (okLong.signum() == 0 && okShort.signum() == 0) {
                String posDetails = String.format("Bitmex %s; Okex %s", firstMarketService.getPosition(), secondMarketService.getPosition());
                logger.error("WARNING: Preliq was not started, because Okex pos=0. Details:" + posDetails);
                warningLogger.error("WARNING: Preliq was not started, because Okex pos=0. Details:" + posDetails);
                forbidden = true;
                return this;
            }
            BigDecimal okMeaningPos = deltaName == DeltaName.B_DELTA ? okShort : okLong;
            if (okMeaningPos.compareTo(o_block) < 0) {
                o_block = okMeaningPos;
                b_block = o_block.multiply(cm).setScale(0, RoundingMode.HALF_UP);
            }

            // increasing position on any market is forbidden
            if (deltaName == DeltaName.B_DELTA) {
                // bitmex sell, okex buy
                if (btmPos.signum() < 0 || okLong.subtract(okShort).signum() > 0) {
                    String posDetails = String.format("Bitmex %s; Okex %s", firstMarketService.getPosition(), secondMarketService.getPosition());
                    logger.error("WARNING: Preliq by delta1 was not started. Can not increase position. Details:" + posDetails);
                    warningLogger.error("WARNING: Preliq by delta1 was not started. Can not increase position. Details:" + posDetails);
                    forbidden = true;
                    return this;
                }
            } else {
                // bitmex buy, okex sell
                if (btmPos.signum() > 0 || okLong.subtract(okShort).signum() < 0) {
                    String posDetails = String.format("Bitmex %s; Okex %s", firstMarketService.getPosition(), secondMarketService.getPosition());
                    logger.error("WARNING: Preliq by delta2 was not started. Can not increase position. Details:" + posDetails);
                    warningLogger.error("WARNING: Preliq  by delta2 was not started. Can not increase position. Details:" + posDetails);
                    forbidden = true;
                    return this;
                }
            }

            forbidden = false;
            return this;
        }
    }

    public FplayTrade getFplayTrade() {
        return fplayTrade;
    }

    public Long getTradeId() {
        return tradeId;
    }

    public Long getLastTradeId() {
        return tradeId != null ? tradeId.longValue() : fplayTradeRepository.getLastId();
    }

    public Long getLastInProgressTradeId() {
        final Long tradeIdSnap = getLastTradeId();
        FplayTrade one = fplayTradeRepository.findOne(tradeIdSnap);
        if (one.getTradeStatus() == TradeStatus.IN_PROGRESS) {
            return tradeIdSnap;
        }
        return null;
    }
}
