package com.bitplay.arbitrage;

import com.bitplay.TwoMarketStarter;
import com.bitplay.arbitrage.dto.PlBlocks;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketState;
import com.bitplay.market.dto.LiqInfo;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.SignalEvent;
import com.bitplay.market.events.SignalEventBus;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.BorderParams;
import com.bitplay.persistance.domain.DeltaParams;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.utils.Utils;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Sergey Shurmin on 4/18/17.
 */
@Service
public class ArbitrageService {

    private static final Logger logger = LoggerFactory.getLogger(ArbitrageService.class);
    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");
    private static final Logger signalLogger = LoggerFactory.getLogger("SIGNAL_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");
    private static final Logger debugLog = LoggerFactory.getLogger("DEBUG_LOG");

    private static final String DELTA1 = "delta1";
    private static final String DELTA2 = "delta2";
    private static final Object calcLock = new Object();
    private final BigDecimal FEE_FIRST_MAKER = new BigDecimal("0.075");//Bitmex
    private final BigDecimal FEE_SECOND_TAKER = new BigDecimal("0.015");//OkCoin
    private boolean firstDeltasAfterStart = true;
    @Autowired
    private BordersService bordersService;
    @Autowired
    private PlacingBlocksService placingBlocksService;
    @Autowired
    private PersistenceService persistenceService;
    @Autowired
    private SignalService signalService;
    private Disposable schdeduleUpdateBorders;
    private Instant startTimeToUpdateBorders;
    private volatile int updateBordersCounter;
    //TODO rename them to first and second
    private MarketService firstMarketService;
    private MarketService secondMarketService;
    private PosDiffService posDiffService;
    private BigDecimal delta1 = BigDecimal.ZERO;
    private BigDecimal delta2 = BigDecimal.ZERO;
    private GuiParams params = new GuiParams();
    private Instant previousEmitTime = Instant.now();
    private String sumBalString = "";

    private volatile Boolean isReadyForTheArbitrage = true;
    private Disposable theTimer;
    private Disposable theCheckBusyTimer;

    private final OpenPrices openPrices = new OpenPrices();
    private final OpenPrices openDiffs = new OpenPrices();
    private volatile SignalType signalType = SignalType.AUTOMATIC;
    private SignalEventBus signalEventBus = new SignalEventBus();
    private volatile DeltaParams deltaParams = new DeltaParams();

    public OpenPrices getOpenPrices() {
        return openPrices;
    }

    public void init(TwoMarketStarter twoMarketStarter) {
        loadParamsFromDb();
        this.firstMarketService = twoMarketStarter.getFirstMarketService();
        this.secondMarketService = twoMarketStarter.getSecondMarketService();
        this.posDiffService = twoMarketStarter.getPosDiffService();
//        startArbitrageMonitoring();
        scheduleRecalculateBorders();
        initArbitrageStateListener();
        initSignalEventBus();
    }

    private void initSignalEventBus() {
        signalEventBus.toObserverable()
                .sample(100, TimeUnit.MILLISECONDS)
                .subscribe(signalEvent -> {
                    try {
                        if (signalEvent == SignalEvent.B_ORDERBOOK_CHANGED
                                || signalEvent == SignalEvent.O_ORDERBOOK_CHANGED) {

                            final BestQuotes bestQuotes = doComparison();

                            // Logging not often then 5 sec
                            if (Duration.between(previousEmitTime, Instant.now()).getSeconds() > 5
                                    && bestQuotes != null
                                    && bestQuotes.getArbitrageEvent() != BestQuotes.ArbitrageEvent.NONE) {

                                previousEmitTime = Instant.now();
                                signalLogger.info(bestQuotes.toString());
                            }
                        }
                    } catch (Exception e) {
                        logger.error("signalEventBus errorOnEvent", e);
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
        firstMarketService.getEventBus().toObserverable()
                .subscribe(btsEvent -> {
                    if (btsEvent == BtsEvent.MARKET_GOT_FREE) {
                        if (!secondMarketService.isBusy()) {
                            writeLogArbitrageIsDone();
                        }
                    }
                }, throwable -> logger.error("On event handling", throwable));
        secondMarketService.getEventBus().toObserverable()
                .subscribe(btsEvent -> {
                    if (btsEvent == BtsEvent.MARKET_GOT_FREE) {
                        if (!firstMarketService.isBusy()) {
                            writeLogArbitrageIsDone();
                        }
                    }
                }, throwable -> logger.error("On event handling", throwable));
    }

    private void writeLogArbitrageIsDone() {
        if (signalType == SignalType.AUTOMATIC
                && openPrices != null && openDiffs != null && params.getLastDelta() != null) {
            if (params.getLastDelta().equals(DELTA1)) {

                printP1AvgDeltaLogs(delta1, openPrices.getDelta1Fact(), openPrices);
                printP2CumBitmexMCom();

                // this should be after
                final String deltaFactStr = String.format("delta1_fact=%s-%s=%s", openPrices.getFirstOpenPrice(), openPrices.getSecondOpenPrice(), openPrices.getDelta1Fact());
                printP3DeltaFact(openPrices.getDelta1Fact(), deltaFactStr);

                printOAvgPrice();

            } else if (params.getLastDelta().equals(DELTA2)) {

                printP1AvgDeltaLogs(delta2, openPrices.getDelta2Fact(), openPrices);
                printP2CumBitmexMCom();

                final String deltaFactStr = String.format("delta2_fact=%s-%s=%s", openPrices.getSecondOpenPrice(), openPrices.getFirstOpenPrice(), openPrices.getDelta2Fact());
                printP3DeltaFact(openPrices.getDelta2Fact(), deltaFactStr);

                printOAvgPrice();

            }
        }

        printSumBal(false);

        saveParamsToDb();
    }

    private void printP3DeltaFact(BigDecimal deltaFact, String deltaFactString) {
        params.setCumDeltaFact(params.getCumDeltaFact().add(deltaFact));
        if (params.getCumDeltaFact().compareTo(params.getCumDeltaFactMin()) == -1) params.setCumDeltaFactMin(params.getCumDeltaFact());
        if (params.getCumDeltaFact().compareTo(params.getCumDeltaFactMax()) == 1) params.setCumDeltaFactMax(params.getCumDeltaFact());

        BigDecimal diffFact = openDiffs.getFirstOpenPrice().add(openDiffs.getSecondOpenPrice());
        if (openDiffs.getFirstOpenPrice().compareTo(params.getDiffFact1Min()) == -1) params.setDiffFact1Min(openDiffs.getFirstOpenPrice());
        if (openDiffs.getFirstOpenPrice().compareTo(params.getDiffFact1Max()) == 1) params.setDiffFact1Max(openDiffs.getFirstOpenPrice());
        if (openDiffs.getSecondOpenPrice().compareTo(params.getDiffFact2Min()) == -1) params.setDiffFact2Min(openDiffs.getSecondOpenPrice());
        if (openDiffs.getSecondOpenPrice().compareTo(params.getDiffFact2Max()) == 1) params.setDiffFact2Max(openDiffs.getSecondOpenPrice());
        if (diffFact.compareTo(params.getDiffFactMin()) == -1) params.setDiffFactMin(diffFact);
        if (diffFact.compareTo(params.getDiffFactMax()) == 1) params.setDiffFactMax(diffFact);

        params.setCumDiffFact1(params.getCumDiffFact1().add(openDiffs.getFirstOpenPrice()));
        params.setCumDiffFact2(params.getCumDiffFact2().add(openDiffs.getSecondOpenPrice()));

        if (params.getCumDiffFact1().compareTo(params.getCumDiffFact1Min()) == -1) params.setCumDiffFact1Min(params.getCumDiffFact1());
        if (params.getCumDiffFact1().compareTo(params.getCumDiffFact1Max()) == 1) params.setCumDiffFact1Max(params.getCumDiffFact1());

        if (params.getCumDiffFact2().compareTo(params.getCumDiffFact2Min()) == -1) params.setCumDiffFact2Min(params.getCumDiffFact2());
        if (params.getCumDiffFact2().compareTo(params.getCumDiffFact2Max()) == 1) params.setCumDiffFact2Max(params.getCumDiffFact2());
        BigDecimal cumDiffsFact = params.getCumDiffFact1().add(params.getCumDiffFact2());
        if (cumDiffsFact.compareTo(params.getCumDiffsFactMin()) == -1) params.setCumDiffsFactMin(cumDiffsFact);
        if (cumDiffsFact.compareTo(params.getCumDiffsFactMax()) == 1) params.setCumDiffsFactMax(cumDiffsFact);

        // 1. diff_fact_br = delta_fact - b (писать после diff_fact) cum_diff_fact_br = sum(diff_fact_br)
        final ArbUtils.DiffFactBr diffFactBr = ArbUtils.getDeltaFactBr(deltaFact, openPrices.getBorderList());
        params.setCumDiffFactBr(params.getCumDiffFactBr().add(diffFactBr.val));
        if (params.getCumDiffFactBr().compareTo(params.getCumDiffFactBrMin()) == -1) params.setCumDiffFactBrMin(params.getCumDiffFactBr());
        if (params.getCumDiffFactBr().compareTo(params.getCumDiffFactBrMax()) == 1) params.setCumDiffFactBrMax(params.getCumDiffFactBr());

        // avg_diff_fact_br = (b_block / b_price_fact + b_block / ok_price_fact) * diff_fact_br
        final BigDecimal b_block = openPrices.getbBlock();
        final BigDecimal b_price_fact = openPrices.getFirstOpenPrice();
        final BigDecimal o_price_fact = openPrices.getSecondOpenPrice();
        BigDecimal avgDiffFactBr = (b_block.divide(b_price_fact, 2, RoundingMode.HALF_UP).add(
                b_block.divide(o_price_fact, 2, RoundingMode.HALF_UP)
        )).multiply(diffFactBr.val);
        params.setCumAvgDiffFactBr(params.getCumAvgDiffFactBr().add(avgDiffFactBr));

        // 5. Добавить значения:
        //1) slip_m = (avg_diff_fact_br + avg_com2 - avg_Bitmex_m_com) / (count1 + count2)
        //2) cum_slip_m = sum(slip_m)
        //3) slip_t = (avg_diff_fact_br + avg_com) / (count1 + count2)
        //4) cum_slip_t = sum(slip_t)
        //На UI добавить строчки cum_slip_m, cum_slip_t
        final BigDecimal slip_m = (avgDiffFactBr.add(params.getAvgCom2()).subtract(params.getAvgBitmexMCom())).divide(
                BigDecimal.valueOf(getCounter()), 2, RoundingMode.HALF_UP);
        params.setCumSlipM(params.getCumSlipM().add(slip_m));
        final BigDecimal slip_t = (avgDiffFactBr.add(params.getAvgCom())).divide(
                BigDecimal.valueOf(getCounter()), 2, RoundingMode.HALF_UP);
        params.setCumSlipT(params.getCumSlipT().add(slip_t));

        // avg_diff_fact = avg_delta_fact - avg_delta
        BigDecimal avgDiffFact1 = openDiffs.getFirstOpenPrice().multiply(openPrices.getbBlock()).divide(openPrices.getFirstOpenPrice(), 2, RoundingMode.HALF_UP);
        BigDecimal avgDiffFact2 = openDiffs.getSecondOpenPrice().multiply(openPrices.getbBlock()).divide(openPrices.getSecondOpenPrice(), 2, RoundingMode.HALF_UP);
        BigDecimal avgDiffFact = params.getAvgDeltaFact().subtract(params.getAvgDelta());
        params.setCumAvgDiffFact1(params.getCumAvgDiffFact1().add(avgDiffFact1));
        params.setCumAvgDiffFact2(params.getCumAvgDiffFact2().add(avgDiffFact2));
        params.setCumAvgDiffFact(params.getCumAvgDiffFact().add(avgDiffFact));

        deltasLogger.info(String.format("#%s %s; " +
                        "cum_delta_fact=%s/%s/%s; " +
                        "diff_fact=%s/%s/%s+%s/%s/%s=%s/%s/%s; " +
                        "cum_diff_fact=%s/%s/%s+%s/%s/%s=%s/%s/%s; " +
                        "diff_fact_br=%s=%s\n" +
                        "cum_diff_fact_br=%s/%s/%s; " +
                        "avg_diff_fact1=%s, avg_diff_fact2=%s, avg_diff_fact=%s, " +
                        "cum_avg_diff_fact1=%s, cum_avg_diff_fact2=%s, cum_avg_diff_fact=%s, " +
                        "avg_diff_fact_br=%s, cum_avg_diff_fact_br=%s, " +
                        "slip_m=%s, cum_slip_m=%s, " +
                        "slip_t=%s, cum_slip_t=%s",
                getCounter(),
                deltaFactString,
                params.getCumDeltaFact().toPlainString(),
                params.getCumDeltaFactMin().toPlainString(),
                params.getCumDeltaFactMax().toPlainString(),
                openDiffs.getFirstOpenPrice(), params.getDiffFact1Min(), params.getDiffFact1Max(),
                openDiffs.getSecondOpenPrice(), params.getDiffFact2Min(), params.getDiffFact2Max(),
                diffFact, params.getDiffFactMin(), params.getDiffFactMax(),
                params.getCumDiffFact1(), params.getCumDiffFact1Min(), params.getCumDiffFact1Max(),
                params.getCumDiffFact2(), params.getCumDiffFact2Min(), params.getCumDiffFact2Max(),
                cumDiffsFact, params.getCumDiffsFactMin(), params.getCumDiffsFactMax(),
                diffFactBr.str, diffFactBr.val,
                params.getCumDiffFactBr(), params.getCumDiffFactBrMin(), params.getCumDiffFactBrMax(),
                avgDiffFact1, avgDiffFact2, avgDiffFact,
                params.getCumAvgDiffFact1(), params.getCumAvgDiffFact2(), params.getCumAvgDiffFact(),
                avgDiffFactBr, params.getCumAvgDiffFactBr(),
                slip_m, params.getCumSlipM(),
                slip_t, params.getCumSlipT()
        ));
    }

    private void printOAvgPrice() {
        deltasLogger.info(String.format("o_avg_price_long=%s, o_avg_price_short=%s ",
                getSecondMarketService().getPosition().getPriceAvgLong(),
                getSecondMarketService().getPosition().getPriceAvgShort()));
    }

    public MarketService getFirstMarketService() {
        return firstMarketService;
    }

    public MarketService getSecondMarketService() {
        return secondMarketService;
    }

    private void setTimeoutAfterStartTrading() {
        isReadyForTheArbitrage = false;
        if (theTimer != null) {
            theTimer.dispose();
        }
        theTimer = Completable.timer(100, TimeUnit.MILLISECONDS)
                .doOnComplete(() -> isReadyForTheArbitrage = true)
                .doOnError(throwable -> logger.error("onError timer", throwable))
                .repeat()
                .retry()
                .subscribe();
        setBusyStackChecker();
    }

    private void setBusyStackChecker() {

        if (theCheckBusyTimer != null) {
            theCheckBusyTimer.dispose();
        }

        theCheckBusyTimer = Completable.timer(6, TimeUnit.MINUTES, Schedulers.computation())
                .doOnComplete(() -> {
                    if (firstMarketService.getMarketState() == MarketState.STOPPED
                            || secondMarketService.getMarketState() == MarketState.STOPPED
                            || firstMarketService.getMarketState() == MarketState.SWAP_AWAIT
                            || secondMarketService.getMarketState() == MarketState.SWAP_AWAIT
                            || firstMarketService.getMarketState() == MarketState.SWAP
                            || secondMarketService.getMarketState() == MarketState.SWAP
                            ) {
                        // do nothing

                    } else if (firstMarketService.isBusy() || secondMarketService.isBusy()) {
                        final String logString = String.format("#%s Warning: busy by isBusy for 6 min. first:%s(%s), second:%s(%s)",
                                getCounter(),
                                firstMarketService.isBusy(),
                                firstMarketService.getOnlyOpenOrders().size(),
                                secondMarketService.isBusy(),
                                secondMarketService.getOnlyOpenOrders().size());
                        deltasLogger.warn(logString);
                        warningLogger.warn(logString);


                        if (firstMarketService.isBusy() && !firstMarketService.hasOpenOrders()) {
                            deltasLogger.warn("Warning: Free Bitmex");
                            warningLogger.warn("Warning: Free Bitmex");
                            firstMarketService.getEventBus().send(BtsEvent.MARKET_FREE);
                        }

                        if (secondMarketService.isBusy() && !secondMarketService.hasOpenOrders()) {
                            deltasLogger.warn("Warning: Free Okcoin");
                            warningLogger.warn("Warning: Free Okcoin");
                            secondMarketService.getEventBus().send(BtsEvent.MARKET_FREE);
                        }

                    } else if (!firstMarketService.isReadyForArbitrage() || !secondMarketService.isReadyForArbitrage()) {
                        final String logString = String.format("#%s Warning: busy for 6 min. first:isReady=%s(Orders=%s), second:isReady=%s(Orders=%s)",
                                getCounter(),
                                firstMarketService.isReadyForArbitrage(), firstMarketService.getOnlyOpenOrders().size(),
                                secondMarketService.isReadyForArbitrage(), secondMarketService.getOnlyOpenOrders().size());
                        deltasLogger.warn(logString);
                        warningLogger.warn(logString);
                    }
                })
                .repeat()
                .retry()
                .subscribe();
    }

    /*
        private void startArbitrageMonitoring() {

            final Observable<OrderBook> firstOrderBook = firstMarketService.getOrderBookObservable();
            final Observable<OrderBook> secondOrderBook = secondMarketService.getOrderBookObservable();

            // Observable.combineLatest - doesn't work while observable isn't completed
            Observable
                    .merge(firstOrderBook, secondOrderBook)
                    .subscribeOn(Schedulers.computation())
                    .observeOn(Schedulers.computation())
                    //Do not use .retry(), because observableOrderBooks can be changed
                    .subscribe(orderBook -> {

                        final BestQuotes bestQuotes = doComparison();

                        // Logging not often then 5 sec
                        if (Duration.between(previousEmitTime, Instant.now()).getSeconds() > 5
                                && bestQuotes != null
                                && bestQuotes.getArbitrageEvent() != BestQuotes.ArbitrageEvent.NONE) {

                            previousEmitTime = Instant.now();
                            signalLogger.info(bestQuotes.toString());
                        }
                    }, throwable -> {
                        logger.error("OnCombine orderBooks", throwable);
                        startArbitrageMonitoring();
                    }, () -> {
                        logger.error("OnComplete orderBooks");
                        startArbitrageMonitoring();
                    });
        }
    */
    private BestQuotes doComparison() {
        BestQuotes bestQuotes = new BestQuotes(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        if (firstMarketService.getMarketState() == MarketState.STOPPED || secondMarketService.getMarketState() == MarketState.STOPPED) {
            // do nothing

        } else if (!isReadyForTheArbitrage) {
            debugLog.info("isReadyForTheArbitrage=false");
        } else {
            if (Thread.holdsLock(calcLock)) {
                logger.warn("calcLock is in progress");
            }
            synchronized (calcLock) {
                final OrderBook firstOrderBook = firstMarketService.getOrderBook();
                final OrderBook secondOrderBook = secondMarketService.getOrderBook();

                if (firstOrderBook != null
                        && secondOrderBook != null
                        && firstMarketService.getAccountInfoContracts() != null
                        && secondMarketService.getAccountInfoContracts() != null) {
                    bestQuotes = calcAndDoArbitrage(firstOrderBook, secondOrderBook);
                }
            }
        }
        return bestQuotes;
    }

    private BestQuotes calcAndDoArbitrage(OrderBook bitmexOrderBook, OrderBook okCoinOrderBook) {
        if (okCoinOrderBook.getAsks().size() == 0 || okCoinOrderBook.getBids().size() == 0
                || bitmexOrderBook.getAsks().size() == 0 || bitmexOrderBook.getBids().size() == 0) {
            return BestQuotes.empty();
        }

        // 1. Calc deltas
        final BestQuotes bestQuotes = Utils.createBestQuotes(okCoinOrderBook, bitmexOrderBook);
        if (!bestQuotes.hasEmpty()) {
            if (firstDeltasAfterStart) {
                firstDeltasAfterStart = false;
                warningLogger.info("Started: First delta calculated");
            }

            delta1 = bestQuotes.getBid1_p().subtract(bestQuotes.getAsk1_o());
            delta2 = bestQuotes.getBid1_o().subtract(bestQuotes.getAsk1_p());
            if (delta1.compareTo(deltaParams.getbDeltaMin()) < 0) {
                deltaParams.setbDeltaMin(delta1);
            }
            if (delta1.compareTo(deltaParams.getbDeltaMax()) > 0) {
                deltaParams.setbDeltaMax(delta1);
            }
            if (delta2.compareTo(deltaParams.getoDeltaMin()) < 0) {
                deltaParams.setoDeltaMin(delta2);
            }
            if (delta2.compareTo(deltaParams.getoDeltaMax()) > 0) {
                deltaParams.setoDeltaMax(delta2);
            }

            if (!Thread.interrupted()) {
                storeDeltaParams();
            } else {
                return bestQuotes;
            }
        } else {
            return bestQuotes;
        }

        final BigDecimal bP = firstMarketService.getPosition().getPositionLong();
        final BigDecimal oPL = secondMarketService.getPosition().getPositionLong();
        final BigDecimal oPS = secondMarketService.getPosition().getPositionShort();

        final BorderParams borderParams = persistenceService.fetchBorders();
        if (borderParams == null || borderParams.getActiveVersion() == BorderParams.Ver.V1) {
            BigDecimal border1 = params.getBorder1();
            BigDecimal border2 = params.getBorder2();

            if (delta1.compareTo(border1) >= 0) {
                PlBlocks plBlocks = placingBlocksService.getPlacingBlocks(bitmexOrderBook, okCoinOrderBook, border1,
                        PlacingBlocks.DeltaBase.B_DELTA, oPL, oPS);
                final String dynDeltaLogs = plBlocks.isDynamic()
                        ? composeDynBlockLogs("b_delta", bitmexOrderBook, okCoinOrderBook, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex())
                        : null;
                if (plBlocks.getBlockOkex().signum() == 0) {
                    return bestQuotes;
                }
                if (plBlocks.isDynamic()) {
                    plBlocks = dynBlockDecriseByAffordable(DELTA1, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex());
                }

                if (plBlocks.getBlockOkex().signum() > 0) {
                    openPrices.setBorder(border1);
                    startTradingOnDelta1(SignalType.AUTOMATIC, bestQuotes, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex(), null, dynDeltaLogs);
                } else {
                    warningLogger.warn("Block should be < 0, but okexBlock=" + plBlocks.getBlockOkex());
                }
            }
            if (delta2.compareTo(border2) >= 0) {
                PlBlocks plBlocks = placingBlocksService.getPlacingBlocks(bitmexOrderBook, okCoinOrderBook, border2,
                        PlacingBlocks.DeltaBase.O_DELTA, oPL, oPS);
                final String dynDeltaLogs = plBlocks.isDynamic()
                        ? composeDynBlockLogs("o_delta", bitmexOrderBook, okCoinOrderBook, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex())
                        : null;
                if (plBlocks.getBlockOkex().signum() == 0) {
                    return bestQuotes;
                }
                if (plBlocks.isDynamic()) {
                    plBlocks = dynBlockDecriseByAffordable(DELTA2, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex());
                }
                if (plBlocks.getBlockOkex().signum() > 0) {
                    openPrices.setBorder(border2);
                    startTradingOnDelta2(SignalType.AUTOMATIC, bestQuotes, plBlocks.getBlockBitmex(), plBlocks.getBlockOkex(), null, dynDeltaLogs);
                } else {
                    warningLogger.warn("Block should be < 0, but okexBlock=" + plBlocks.getBlockOkex());
                }
            }

        } else { // BorderParams.Ver.V2
            final BordersService.TradingSignal tradingSignal = bordersService.checkBorders(
                    bitmexOrderBook, okCoinOrderBook, delta1, delta2, bP, oPL, oPS);

            if (tradingSignal.okexBlock == 0) {
                return bestQuotes;
            }

            if (tradingSignal.tradeType == BordersService.TradeType.DELTA1_B_SELL_O_BUY) {
                if (tradingSignal.ver == PlacingBlocks.Ver.DYNAMIC) {
                    final PlBlocks bl = dynBlockDecriseByAffordable(DELTA1, BigDecimal.valueOf(tradingSignal.bitmexBlock), BigDecimal.valueOf(tradingSignal.okexBlock));
                    if (bl.getBlockOkex().signum() > 0) {
                        final BordersService.TradingSignal ts = bordersService.setNewBlock(tradingSignal, bl.getBlockOkex().intValueExact());
                        final BigDecimal b_block = BigDecimal.valueOf(ts.bitmexBlock);
                        final BigDecimal o_block = BigDecimal.valueOf(ts.okexBlock);
                        final String dynDeltaLogs = composeDynBlockLogs("b_delta", bitmexOrderBook, okCoinOrderBook,
                                b_block, o_block);

                        openPrices.setBorderList(tradingSignal.borderValueList);
                        startTradingOnDelta1(SignalType.AUTOMATIC, bestQuotes, b_block, o_block, tradingSignal, dynDeltaLogs);
                    } else {
                        warningLogger.warn("Block should be < 0, but okexBlock=" + bl.getBlockOkex());
                    }
                } else {
                    final BigDecimal b_block = BigDecimal.valueOf(tradingSignal.bitmexBlock);
                    final BigDecimal o_block = BigDecimal.valueOf(tradingSignal.okexBlock);
                    openPrices.setBorderList(tradingSignal.borderValueList);
                    startTradingOnDelta1(SignalType.AUTOMATIC, bestQuotes, b_block, o_block, tradingSignal, null);
                }
            }

            if (tradingSignal.tradeType == BordersService.TradeType.DELTA2_B_BUY_O_SELL) {
                if (tradingSignal.ver == PlacingBlocks.Ver.DYNAMIC) {
                    final PlBlocks bl = dynBlockDecriseByAffordable(DELTA2, BigDecimal.valueOf(tradingSignal.bitmexBlock), BigDecimal.valueOf(tradingSignal.okexBlock));
                    if (bl.getBlockOkex().signum() > 0) {
                        final BordersService.TradingSignal ts = bordersService.setNewBlock(tradingSignal, bl.getBlockOkex().intValueExact());
                        final BigDecimal b_block = BigDecimal.valueOf(ts.bitmexBlock);
                        final BigDecimal o_block = BigDecimal.valueOf(ts.okexBlock);
                        final String dynDeltaLogs = composeDynBlockLogs("b_delta", bitmexOrderBook, okCoinOrderBook,
                                b_block, o_block);

                        openPrices.setBorderList(tradingSignal.borderValueList);
                        startTradingOnDelta2(SignalType.AUTOMATIC, bestQuotes, b_block, o_block, tradingSignal, dynDeltaLogs);
                    } else {
                        warningLogger.warn("Block should be < 0, but okexBlock=" + bl.getBlockOkex());
                    }

                } else {
                    final BigDecimal b_block = BigDecimal.valueOf(tradingSignal.bitmexBlock);
                    final BigDecimal o_block = BigDecimal.valueOf(tradingSignal.okexBlock);
                    openPrices.setBorderList(tradingSignal.borderValueList);
                    startTradingOnDelta2(SignalType.AUTOMATIC, bestQuotes, b_block, o_block, tradingSignal, null);
                }
            }
        }

        return bestQuotes;
    }

    private String composeDynBlockLogs(String deltaName, OrderBook bitmexOrderBook, OrderBook okCoinOrderBook, BigDecimal b_block, BigDecimal o_block) {
        final String bMsg = Utils.getTenAskBid(bitmexOrderBook, signalType.getCounterName(),
                "Bitmex OrderBook");
        final String oMsg = Utils.getTenAskBid(okCoinOrderBook, signalType.getCounterName(),
                "Okex OrderBook");
        final PlacingBlocks placingBlocks = persistenceService.getSettingsRepositoryService().getSettings().getPlacingBlocks();
        return String.format("%s: Dynamic: dynMaxBlockOkex=%s, b_block=%s, o_block=%s\n%s\n%s",
                deltaName,
                placingBlocks.getDynMaxBlockOkex(),
                b_block, o_block,
                bMsg, oMsg);
    }

    public void startTradingOnDelta1(SignalType signalType, BestQuotes bestQuotes) {
        // border V1
        final PlacingBlocks placingBlocks = persistenceService.getSettingsRepositoryService().getSettings().getPlacingBlocks();
        final BigDecimal b_block = placingBlocks.getFixedBlockBitmex();
        final BigDecimal o_block = placingBlocks.getFixedBlockOkex();
        startTradingOnDelta1(signalType, bestQuotes, b_block, o_block, null, null);
    }

    private void startTradingOnDelta1(SignalType signalType, final BestQuotes bestQuotes, final BigDecimal b_block, final BigDecimal o_block,
                                      final BordersService.TradingSignal tradingSignal, String dynamicDeltaLogs) {
        final BigDecimal ask1_o = bestQuotes.getAsk1_o();
        final BigDecimal bid1_p = bestQuotes.getBid1_p();
        if (checkBalanceBorder1(DELTA1, b_block, o_block) //) {
                && firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage()
                && posDiffService.isPositionsEqual()
                &&
                (signalType != SignalType.AUTOMATIC ||
                        (firstMarketService.checkLiquidationEdge(Order.OrderType.ASK)
                                && secondMarketService.checkLiquidationEdge(Order.OrderType.BID))
                )) {

            bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
            setSignalType(signalType);
            firstMarketService.setBusy();
            secondMarketService.setBusy();
            params.setLastDelta(DELTA1);
            // Market specific params
            params.setPosBefore(new BigDecimal(firstMarketService.getPositionAsString()));
            params.setVolPlan(b_block); // buy

            openPrices.setoBlock(o_block);
            openPrices.setbBlock(b_block);
            openPrices.setDelta1Plan(delta1);
            openPrices.setDelta2Plan(delta2);
            writeLogDelta1(ask1_o, bid1_p, tradingSignal);
            if (dynamicDeltaLogs != null) {
                deltasLogger.info(String.format("#%s %s", getCounter(), dynamicDeltaLogs));
            }

            // in scheme MT2 Okex should be the first
            signalService.placeOkexOrderOnSignal(secondMarketService, Order.OrderType.BID, o_block, bestQuotes, signalType);
            signalService.placeBitmexOrderOnSignal(firstMarketService, Order.OrderType.ASK, b_block, bestQuotes, signalType);

            setTimeoutAfterStartTrading();

            saveParamsToDb();
        } else {
            bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.ONLY_SIGNAL);
        }
    }

    public void startTradingOnDelta2(SignalType signalType, BestQuotes bestQuotes) {
        final PlacingBlocks placingBlocks = persistenceService.getSettingsRepositoryService().getSettings().getPlacingBlocks();
        final BigDecimal b_block = placingBlocks.getFixedBlockBitmex();
        final BigDecimal o_block = placingBlocks.getFixedBlockOkex();
        startTradingOnDelta2(signalType, bestQuotes, b_block, o_block, null, null);
    }

    private void startTradingOnDelta2(final SignalType signalType, final BestQuotes bestQuotes, final BigDecimal b_block, final BigDecimal o_block,
                                      final BordersService.TradingSignal tradingSignal, String dynamicDeltaLogs) {
        final BigDecimal ask1_p = bestQuotes.getAsk1_p();
        final BigDecimal bid1_o = bestQuotes.getBid1_o();

        if (checkBalanceBorder1(DELTA2, b_block, o_block) //) {
                && firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage()
                && posDiffService.isPositionsEqual()
                &&
                (signalType != SignalType.AUTOMATIC ||
                        (firstMarketService.checkLiquidationEdge(Order.OrderType.BID)
                                && secondMarketService.checkLiquidationEdge(Order.OrderType.ASK))
                )) {

            bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
            setSignalType(signalType);
            firstMarketService.setBusy();
            secondMarketService.setBusy();
            params.setLastDelta(DELTA2);
            // Market specific params
            params.setPosBefore(new BigDecimal(firstMarketService.getPositionAsString()));
            params.setVolPlan(b_block.negate());//sell

            openPrices.setoBlock(o_block);
            openPrices.setbBlock(b_block);
            openPrices.setDelta1Plan(delta1);
            openPrices.setDelta2Plan(delta2);
            writeLogDelta2(ask1_p, bid1_o, tradingSignal);
            if (dynamicDeltaLogs != null) {
                deltasLogger.info(String.format("#%s %s", getCounter(), dynamicDeltaLogs));
            }

            // in scheme MT2 Okex should be the first
            signalService.placeOkexOrderOnSignal(secondMarketService, Order.OrderType.ASK, o_block, bestQuotes, signalType);
            signalService.placeBitmexOrderOnSignal(firstMarketService, Order.OrderType.BID, b_block, bestQuotes, signalType);

            setTimeoutAfterStartTrading();

            saveParamsToDb();

        } else {
            bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.ONLY_SIGNAL);
        }
    }

    private void writeLogDelta1(BigDecimal ask1_o, BigDecimal bid1_p, final BordersService.TradingSignal tradingSignal) {
        deltasLogger.info("------------------------------------------");

        Integer counter1 = params.getCounter1();
        Integer counter2 = params.getCounter2();
        BigDecimal border1 = params.getBorder1();

        counter1 += 1;
        params.setCounter1(counter1);

        String iterationMarker = "";
        if (counter1.equals(counter2)) {
            iterationMarker = "whole iteration";
        }
        deltasLogger.info(String.format("#%s count=%s+%s=%s %s", counter1 + counter2, counter1, counter2, counter1 + counter2, iterationMarker));

        params.setCumDelta(params.getCumDelta().add(delta1));
        if (params.getCumDelta().compareTo(params.getCumDeltaMin()) == -1) params.setCumDeltaMin(params.getCumDelta());
        if (params.getCumDelta().compareTo(params.getCumDeltaMax()) == 1) params.setCumDeltaMax(params.getCumDelta());
        deltasLogger.info(String.format("#%s delta1=%s-%s=%s; %s; cum_delta=%s/%s/%s;",
                //usdP=%s; btcO=%s; usdO=%s; w=%s; ",
                getCounter(),
                bid1_p.toPlainString(), ask1_o.toPlainString(),
                delta1.toPlainString(),
                tradingSignal == null ? ("b1=" + border1.toPlainString()) : ("borderV2:" + tradingSignal.toString()),
                params.getCumDelta().toPlainString(),
                params.getCumDeltaMin().toPlainString(),
                params.getCumDeltaMax().toPlainString()
        ));

        printSumBal(false);
    }

    private void writeLogDelta2(BigDecimal ask1_p, BigDecimal bid1_o, final BordersService.TradingSignal tradingSignal) {
        deltasLogger.info("------------------------------------------");

        Integer counter1 = params.getCounter1();
        Integer counter2 = params.getCounter2();
        BigDecimal border2 = params.getBorder2();

        counter2 += 1;
        params.setCounter2(counter2);

        String iterationMarker = "";
        if (counter1.equals(counter2)) {
            iterationMarker = "whole iteration";
        }
        deltasLogger.info(String.format("#%s count=%s+%s=%s %s", getCounter(), counter1, counter2, counter1 + counter2, iterationMarker));

        params.setCumDelta(params.getCumDelta().add(delta2));
        if (params.getCumDelta().compareTo(params.getCumDeltaMin()) == -1) params.setCumDeltaMin(params.getCumDelta());
        if (params.getCumDelta().compareTo(params.getCumDeltaMax()) == 1) params.setCumDeltaMax(params.getCumDelta());
        deltasLogger.info(String.format("#%s delta2=%s-%s=%s; %s; cum_delta=%s/%s/%s;",
                getCounter(),
                bid1_o.toPlainString(), ask1_p.toPlainString(),
                delta2.toPlainString(),
                tradingSignal == null ? ("b2=" + border2.toPlainString()) : ("borderV2:" + tradingSignal.toString()),
                params.getCumDelta().toPlainString(),
                params.getCumDeltaMin().toPlainString(),
                params.getCumDeltaMax().toPlainString()
        ));

        printSumBal(false);
    }

    private void printP1AvgDeltaLogs(BigDecimal deltaPlan, BigDecimal deltaFact, OpenPrices openPrices) {
        printCom(openPrices);

        final BigDecimal b_block = openPrices.getbBlock();
        final BigDecimal b_price_plan = openPrices.getFirstOpenPrice();
        final BigDecimal o_price_plan = openPrices.getSecondOpenPrice();

        //avg_delta = (b_block / b_price_plan + b_block / ok_price_plan) * delta_plan
        final BigDecimal sum = b_block.divide(b_price_plan, 2, RoundingMode.HALF_UP).add(b_block.divide(o_price_plan, 2, RoundingMode.HALF_UP));
        params.setAvgDelta(sum.multiply(deltaPlan));
        params.setCumAvgDelta(params.getCumAvgDelta().add(params.getAvgDelta()));
        params.setAvgDeltaFact(sum.multiply(deltaFact));
        params.setCumAvgDeltaFact(params.getCumAvgDeltaFact().add(params.getAvgDeltaFact()));

        deltasLogger.info(String.format("avg_delta=(%s/%s+%s/%s)*%s=%s, cum_avg_delta=%s, " +
                        "avg_delta_fact=(%s)*%s=%s, cum_avg_delta_fact=%s",
                b_block, b_price_plan, b_block, o_price_plan, deltaPlan, params.getAvgDelta(), params.getCumAvgDelta(),
                sum, deltaFact, params.getAvgDeltaFact(), params.getCumAvgDeltaFact()));
    }

    private void printCom(OpenPrices openPrices) {
        final BigDecimal price1 = openPrices.getFirstOpenPrice();
        final BigDecimal price2 = openPrices.getSecondOpenPrice();
        final BigDecimal com1 = price1.multiply(FEE_FIRST_MAKER).divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP);
        final BigDecimal com2 = price2.multiply(FEE_SECOND_TAKER).divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP);
        params.setCom1(com1);
        params.setCom2(com2);
        params.setAvgCom1(com1.multiply(openPrices.getbBlock().divide(openPrices.getFirstOpenPrice(), 2, RoundingMode.HALF_UP)));
        params.setAvgCom2(com2.multiply(openPrices.getbBlock().divide(openPrices.getSecondOpenPrice(), 2, RoundingMode.HALF_UP)));
        params.setAvgCom(params.getAvgCom1().add(params.getAvgCom2()));
        params.setCumAvgCom1(params.getCumAvgCom1().add(params.getAvgCom1()));
        params.setCumAvgCom2(params.getCumAvgCom2().add(params.getAvgCom2()));
        params.setCumAvgCom(params.getCumAvgCom().add(params.getAvgCom()));

        BigDecimal com = com1.add(com2);

        if (com1.compareTo(params.getCom1Min()) == -1) params.setCom1Min(com1);
        if (com1.compareTo(params.getCom1Max()) == 1) params.setCom1Max(com1);
        if (com2.compareTo(params.getCom2Min()) == -1) params.setCom2Min(com2);
        if (com2.compareTo(params.getCom2Max()) == 1) params.setCom2Max(com2);
        if (com.compareTo(params.getComMin()) == -1) params.setComMin(com);
        if (com.compareTo(params.getComMax()) == 1) params.setComMax(com);

        params.setCumCom1(params.getCumCom1().add(com1));
        params.setCumCom2(params.getCumCom2().add(com2));
        BigDecimal cumCom = params.getCumCom1().add(params.getCumCom2());

        deltasLogger.info(String.format("#%s com=%s/%s/%s+%s/%s/%s=%s/%s/%s; cum_com=%s+%s=%s; " +
                        "avg_com=%s+%s=%s; cum_avg_com=%s",
                getCounter(),
                com1, params.getCom1Min(), params.getCom1Max(),
                com2, params.getCom2Min(), params.getCom2Max(),
                com, params.getComMin(), params.getComMax(),
                params.getCumCom1(),
                params.getCumCom2(),
                cumCom,
                params.getAvgCom1(), params.getAvgCom2(), params.getAvgCom(), params.getCumAvgCom()
        ));
    }

    private void printP2CumBitmexMCom() {
        // bitmex_m_com = round(open_price_fact * 0.025 / 100; 4),
        BigDecimal bitmexMCom = openPrices.getFirstOpenPrice().multiply(new BigDecimal(0.025))
                .divide(new BigDecimal(100), 2, BigDecimal.ROUND_HALF_UP);
        if (bitmexMCom.compareTo(BigDecimal.ZERO) != 0 && bitmexMCom.compareTo(params.getBitmexMComMin()) == -1) params.setBitmexMComMin(bitmexMCom);
        if (bitmexMCom.compareTo(params.getBitmexMComMax()) == 1) params.setBitmexMComMax(bitmexMCom);

        params.setCumBitmexMCom(params.getCumBitmexMCom().add(bitmexMCom));

        params.setAvgBitmexMCom(bitmexMCom.multiply(openPrices.getbBlock().divide(openPrices.getFirstOpenPrice(), 2, RoundingMode.HALF_UP)));
        params.setCumAvgBitmexMCom(params.getCumAvgBitmexMCom().add(params.getAvgBitmexMCom()));

        deltasLogger.info(String.format("#%s bitmex_m_com=%s/%s/%s; cum_bitmex_m_com=%s; " +
                        "avg_Bitmex_m_com=%s; cum_avg_Bitmex_m_com=%s",
                getCounter(),
                bitmexMCom, params.getBitmexMComMin(), params.getBitmexMComMax(),
                params.getCumBitmexMCom(),
                params.getAvgBitmexMCom(),
                params.getCumAvgBitmexMCom()
        ));
    }

    @Scheduled(fixedRate = 1000)
    public void calcSumBalForGui() {
        final AccountInfoContracts firstAccount = firstMarketService.calcFullBalance().getAccountInfoContracts();
        final AccountInfoContracts secondAccount = secondMarketService.calcFullBalance().getAccountInfoContracts();
        if (firstAccount != null && secondAccount != null) {
            final BigDecimal bW = firstAccount.getWallet();
            final BigDecimal bEMark = firstAccount.geteMark() != null ? firstAccount.geteMark() : BigDecimal.ZERO;
            final BigDecimal bEbest = firstAccount.geteBest() != null ? firstAccount.geteBest() : BigDecimal.ZERO;
            final BigDecimal bEAvg = firstAccount.geteAvg() != null ? firstAccount.geteAvg() : BigDecimal.ZERO;
            final BigDecimal bU = firstAccount.getUpl();
            final BigDecimal bM = firstAccount.getMargin();
            final BigDecimal bA = firstAccount.getAvailable();

            final BigDecimal oW = secondAccount.getWallet();
            final BigDecimal oELast = secondAccount.geteLast() != null ? secondAccount.geteLast() : BigDecimal.ZERO;
            final BigDecimal oEbest = secondAccount.geteBest() != null ? secondAccount.geteBest() : BigDecimal.ZERO;
            final BigDecimal oEAvg = secondAccount.geteAvg() != null ? secondAccount.geteAvg() : BigDecimal.ZERO;
            final BigDecimal oM = secondAccount.getMargin();
            final BigDecimal oU = secondAccount.getUpl();
            final BigDecimal oA = secondAccount.getAvailable();

            if (bW == null || oW == null) {
                throw new IllegalStateException(String.format("Balance is not yet defined. bW=%s, oW=%s", bW, oW));
            }
            final BigDecimal sumW = bW.add(oW).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumE = bEMark.add(oELast).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumEBest = bEbest.add(oEbest).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumEAvg = bEAvg.add(oEAvg).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumUpl = bU.add(oU).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumM = bM.add(oM).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumA = bA.add(oA).setScale(8, BigDecimal.ROUND_HALF_UP);

            final BigDecimal quAvg = Utils.calcQuAvg(firstMarketService.getOrderBook(), secondMarketService.getOrderBook());

            sumBalString = String.format("s_bal=w%s_%s, s_e_%s_%s, s_e_best%s_%s, s_e_avg%s_%s, u%s_%s, m%s_%s, a%s_%s",
                    sumW.toPlainString(), sumW.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumE.toPlainString(), sumE.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumEBest.toPlainString(), sumEBest.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumEAvg.toPlainString(), sumEAvg.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumUpl.toPlainString(), sumUpl.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumM.toPlainString(), sumM.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumA.toPlainString(), sumA.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP));
        }
    }

    public void printSumBal(boolean isGuiButton) {
        try {
            String counterName = String.valueOf(getCounter());
            if (isGuiButton) {
                counterName = "button";
            } else if (signalType != SignalType.AUTOMATIC) {
                counterName = signalType.getCounterName();
            }

            final AccountInfoContracts firstAccount = firstMarketService.calcFullBalance().getAccountInfoContracts();
            final AccountInfoContracts secondAccount = secondMarketService.calcFullBalance().getAccountInfoContracts();
            if (firstAccount != null && secondAccount != null) {
                final BigDecimal bW = firstAccount.getWallet();
                final BigDecimal bEmark = firstAccount.geteMark() != null ? firstAccount.geteMark() : BigDecimal.ZERO;
                final BigDecimal bEbest = firstAccount.geteBest() != null ? firstAccount.geteBest() : BigDecimal.ZERO;
                final BigDecimal bEavg = firstAccount.geteAvg() != null ? firstAccount.geteAvg() : BigDecimal.ZERO;
                final BigDecimal bU = firstAccount.getUpl();
                final BigDecimal bM = firstAccount.getMargin();
                final BigDecimal bA = firstAccount.getAvailable();
                final BigDecimal bP = firstMarketService.getPosition().getPositionLong();
                final BigDecimal bLv = firstMarketService.getPosition().getLeverage();
                final BigDecimal bAL = firstMarketService.getAffordableContractsForLong();
                final BigDecimal bAS = firstMarketService.getAffordableContractsForShort();
                final BigDecimal quAvg = Utils.calcQuAvg(firstMarketService.getOrderBook(), secondMarketService.getOrderBook());
                final OrderBook bOrderBook = firstMarketService.getOrderBook();
                final BigDecimal bBestAsk = Utils.getBestAsks(bOrderBook, 1).get(0).getLimitPrice();
                final BigDecimal bBestBid = Utils.getBestBids(bOrderBook, 1).get(0).getLimitPrice();
                deltasLogger.info(String.format("#%s b_bal=w%s_%s, e_mark%s_%s, e_best%s_%s, e_avg%s_%s, u%s_%s, m%s_%s, a%s_%s, p%s, lv%s, lg%s, st%s, ask[1]%s, bid[1]%s",
                        counterName,
                        bW.toPlainString(), bW.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bEmark.toPlainString(), bEmark.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bEbest.toPlainString(), bEbest.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bEavg.toPlainString(), bEavg.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bU.toPlainString(), bU.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bM.toPlainString(), bM.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        bA.toPlainString(), bA.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        Utils.withSign(bP),
                        bLv.toPlainString(),
                        Utils.withSign(bAL),
                        Utils.withSign(bAS),
                        bBestAsk,
                        bBestBid
                ));

                final BigDecimal oW = secondAccount.getWallet();
                final BigDecimal oElast = secondAccount.geteLast() != null ? secondAccount.geteLast() : BigDecimal.ZERO;
                final BigDecimal oEbest = secondAccount.geteBest() != null ? secondAccount.geteBest() : BigDecimal.ZERO;
                final BigDecimal oEavg = secondAccount.geteAvg() != null ? secondAccount.geteAvg() : BigDecimal.ZERO;
                final BigDecimal oM = secondAccount.getMargin();
                final BigDecimal oU = secondAccount.getUpl();
                final BigDecimal oA = secondAccount.getAvailable();
                final BigDecimal oPL = secondMarketService.getPosition().getPositionLong();
                final BigDecimal oPS = secondMarketService.getPosition().getPositionShort();
                final BigDecimal oLv = secondMarketService.getPosition().getLeverage();
                final BigDecimal oAL = secondMarketService.getAffordableContractsForLong();
                final BigDecimal oAS = secondMarketService.getAffordableContractsForShort();
                final OrderBook oOrderBook = secondMarketService.getOrderBook();
                final BigDecimal oBestAsk = Utils.getBestAsks(oOrderBook, 1).get(0).getLimitPrice();
                final BigDecimal oBestBid = Utils.getBestBids(oOrderBook, 1).get(0).getLimitPrice();
                deltasLogger.info(String.format("#%s o_bal=w%s_%s, e_mark%s_%s, e_best%s_%s, e_avg%s_%s, u%s_%s, m%s_%s, a%s_%s, p+%s-%s, lv%s, lg%s, st%s, ask[1]%s, bid[1]%s",
                        counterName,
                        oW.toPlainString(), oW.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oElast.toPlainString(), oElast.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oEbest.toPlainString(), oEbest.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oEavg.toPlainString(), oEavg.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oU.toPlainString(), oU.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oM.toPlainString(), oM.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oA.toPlainString(), oA.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        oPL, oPS,
                        oLv.toPlainString(),
                        Utils.withSign(oAL),
                        Utils.withSign(oAS),
                        oBestAsk,
                        oBestBid
                ));

                final BigDecimal sumW = bW.add(oW).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumE = bEmark.add(oElast).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumEbest = bEbest.add(oEbest).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumEavg = bEavg.add(oEavg).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumUpl = bU.add(oU).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumM = bM.add(oM).setScale(8, BigDecimal.ROUND_HALF_UP);
                final BigDecimal sumA = bA.add(oA).setScale(8, BigDecimal.ROUND_HALF_UP);

                final String sBalStr = String.format("#%s s_bal=w%s_%s, s_e%s_%s, s_e_best%s_%s, s_e_avg%s_%s, u%s_%s, m%s_%s, a%s_%s",
                        counterName,
                        sumW.toPlainString(), sumW.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumE.toPlainString(), sumE.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumEbest.toPlainString(), sumEbest.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumEavg.toPlainString(), sumEavg.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumUpl.toPlainString(), sumUpl.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumM.toPlainString(), sumM.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                        sumA.toPlainString(), sumA.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP));
                deltasLogger.info(sBalStr);

                final String bDQLMin;
                final String oDQLMin;
                if (signalType == SignalType.B_PRE_LIQ || signalType == SignalType.O_PRE_LIQ) {
                    bDQLMin = String.format("b_DQL_close_min=%s", getParams().getbDQLCloseMin());
                    oDQLMin = String.format("o_DQL_close_min=%s", getParams().getoDQLCloseMin());
                } else {
                    bDQLMin = String.format("b_DQL_open_min=%s", getParams().getbDQLOpenMin());
                    oDQLMin = String.format("o_DQL_open_min=%s", getParams().getoDQLOpenMin());
                }

                deltasLogger.info(String.format("#%s Pos diff: %s", counterName, getPosDiffString()));
                final LiqInfo bLiqInfo = getFirstMarketService().getLiqInfo();
                deltasLogger.info(String.format("#%s %s; %s; %s", counterName, bLiqInfo.getDqlString(), bLiqInfo.getDmrlString(), bDQLMin));
                final LiqInfo oLiqInfo = getSecondMarketService().getLiqInfo();
                deltasLogger.info(String.format("#%s %s; %s; %s", counterName, oLiqInfo.getDqlString(), oLiqInfo.getDmrlString(), oDQLMin));
            }
        } catch (Exception e) {
            deltasLogger.info("Error on printSumBal");
            logger.error("Error on printSumBal", e);
        }
    }

    public String getPosDiffString() {
        final BigDecimal posDiff = posDiffService.getPositionsDiffSafe();
        final BigDecimal bP = getFirstMarketService().getPosition().getPositionLong();
        final BigDecimal oPL = getSecondMarketService().getPosition().getPositionLong();
        final BigDecimal oPS = getSecondMarketService().getPosition().getPositionShort();
        final BigDecimal ha = getParams().getHedgeAmount();
        final BigDecimal dc = posDiffService.getPositionsDiffWithHedge();
        final BigDecimal mdc = getParams().getMaxDiffCorr();

        return String.format("b(%s) o(+%s-%s) = %s, ha=%s, dc=%s, mdc=%s",
                Utils.withSign(bP),
                oPL.toPlainString(),
                oPS.toPlainString(),
                posDiff.toPlainString(),
                ha, dc, mdc
        );
    }

    private PlBlocks dynBlockDecriseByAffordable(String deltaRef, BigDecimal blockSize1, BigDecimal blockSize2) {
        BigDecimal b1 = BigDecimal.ZERO;
        BigDecimal b2 = BigDecimal.ZERO;
        BigDecimal OKEX_FACTOR = BigDecimal.valueOf(100);
        if (deltaRef.equals(DELTA1)) {
            // b_sell, o_buy
            final BigDecimal b_sell_lim = firstMarketService.getAffordableContractsForShort().signum() < 0 ? BigDecimal.ZERO : firstMarketService.getAffordableContractsForShort();
            final BigDecimal o_buy_lim = secondMarketService.getAffordableContractsForLong().signum() < 0 ? BigDecimal.ZERO : secondMarketService.getAffordableContractsForLong();
            b1 = blockSize1.compareTo(b_sell_lim) < 0 ? blockSize1 : b_sell_lim;
            b2 = blockSize2.compareTo(o_buy_lim) < 0 ? blockSize2 : o_buy_lim;
        } else if (deltaRef.equals(DELTA2)) {
            // buy p , sell o
            final BigDecimal b_buy_lim = firstMarketService.getAffordableContractsForLong().signum() < 0 ? BigDecimal.ZERO : firstMarketService.getAffordableContractsForLong();
            final BigDecimal o_sell_lim = secondMarketService.getAffordableContractsForShort().signum() < 0 ? BigDecimal.ZERO : secondMarketService.getAffordableContractsForShort();
            b1 = blockSize1.compareTo(b_buy_lim) < 0 ? blockSize1 : b_buy_lim;
            b2 = blockSize2.compareTo(o_sell_lim) < 0 ? blockSize2 : o_sell_lim;
        }

        if (b1.signum() == 0 || b2.signum() == 0) {
            b1 = BigDecimal.ZERO;
            b2 = BigDecimal.ZERO;
        } else if (b1.compareTo(b2.multiply(OKEX_FACTOR)) != 0) {
            b2 = b2.min(b1.divide(OKEX_FACTOR, 0, RoundingMode.HALF_UP));
            b1 = b2.multiply(OKEX_FACTOR);
        }

        return new PlBlocks(b1, b2, PlacingBlocks.Ver.DYNAMIC);
    }

    private boolean checkBalanceBorder1(String deltaRef, BigDecimal blockSize1, BigDecimal blockSize2) {
        boolean affordable = false;
        if (deltaRef.equals(DELTA1)) {
            // sell p, buy o
            if (firstMarketService.isAffordable(Order.OrderType.ASK, blockSize1)
                    && secondMarketService.isAffordable(Order.OrderType.BID, blockSize2)) {
                affordable = true;
            }
        } else if (deltaRef.equals(DELTA2)) {
            // buy p , sell o
            if (firstMarketService.isAffordable(Order.OrderType.BID, blockSize1)
                    && secondMarketService.isAffordable(Order.OrderType.ASK, blockSize2)) {
                affordable = true;
            }
        }

        return affordable;
    }

    private void scheduleRecalculateBorders() {
        if (schdeduleUpdateBorders != null && !schdeduleUpdateBorders.isDisposed()) {
            schdeduleUpdateBorders.dispose();
        }

        startTimeToUpdateBorders = Instant.now();
        schdeduleUpdateBorders = Observable.interval(params.getPeriodSec(), TimeUnit.SECONDS, Schedulers.computation())
                .doOnEach(longNotification -> startTimeToUpdateBorders = Instant.now())
                .doOnError((e) -> logger.error("OnRecalculateBorders", e))
                .retry()
                .subscribe(this::recalculateBordersV1);
    }

    public String getUpdateBordersTimerString() {
        final Duration duration = Duration.between(startTimeToUpdateBorders, Instant.now());
        return String.format("Borders updated %s sec ago. Next (%s) in %s sec",
                duration.getSeconds(),
                updateBordersCounter + 1,
                String.valueOf(params.getPeriodSec() - duration.getSeconds()));
    }

    private void recalculateBordersV1(Long intervalInt) {
        updateBordersCounter = updateBordersCounter + 1;

        final BigDecimal two = new BigDecimal(2);
        final BigDecimal sumDelta = params.getSumDelta();
        if (sumDelta.compareTo(BigDecimal.ZERO) != 0) {
            if (delta1.compareTo(delta2) == 1) {
//            border1 = (abs(delta1) + abs(delta2)) / 2 + sum_delta / 2;
//            border2 = -((abs(delta1) + abs(delta2)) / 2 - sum_delta / 2);
                params.setBorder1(((delta1.abs().add(delta2.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .add(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP)));
                params.setBorder2(((delta1.abs().add(delta2.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .subtract(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP)).negate());
            } else {
//            border1 = -(abs(delta1) + abs(delta2)) / 2 - sum_delta / 2;
//            border2 = abs(delta1) + abs(delta2)) / 2 + sum_delta / 2;
                params.setBorder1(((delta1.abs().add(delta2.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .subtract(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP)).negate());
                params.setBorder2(((delta1.abs().add(delta2.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .add(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP)));
            }
            saveParamsToDb();
        }
    }

    public BigDecimal calcQuAvg() {
        return Utils.calcQuAvg(firstMarketService.getOrderBook(), secondMarketService.getOrderBook());
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
            this.deltaParams = new DeltaParams();
        }
    }

    public void saveParamsToDb() {
        persistenceService.saveGuiParams(params);
    }

    public void resetDeltaParams() {
        deltaParams.setbDeltaMin(delta1);
        deltaParams.setbDeltaMax(delta1);
        deltaParams.setoDeltaMin(delta2);
        deltaParams.setoDeltaMax(delta2);
    }

    private void storeDeltaParams() {
        persistenceService.storeDeltaParams(deltaParams);
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

    public OpenPrices getOpenDiffs() {
        return openDiffs;
    }

    public SignalType getSignalType() {
        return signalType;
    }

    public synchronized void setSignalType(SignalType signalType) {
        this.signalType = signalType != null ? signalType : SignalType.AUTOMATIC;
    }

    public void setPeriodSec(Integer integer) {
        params.setPeriodSec(integer);
        scheduleRecalculateBorders();
    }

    public int getCounter() {
        return params.getCounter1() + params.getCounter2();
    }

    public String getSumBalString() {
        return sumBalString;
    }
}
