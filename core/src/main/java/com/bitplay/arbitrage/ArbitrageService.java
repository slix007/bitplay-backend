package com.bitplay.arbitrage;

import com.bitplay.TwoMarketStarter;
import com.bitplay.market.MarketService;
import com.bitplay.market.dto.LiqInfo;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.SignalEvent;
import com.bitplay.market.events.SignalEventBus;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.BorderParams;
import com.bitplay.persistance.domain.DeltaParams;
import com.bitplay.persistance.domain.GuiParams;
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
    private PersistenceService persistenceService;
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

    private FlagOpenOrder flagOpenOrder = new FlagOpenOrder();
    private OpenPrices openPrices = new OpenPrices();
    private OpenPrices openDiffs = new OpenPrices();
    private volatile SignalType signalType = SignalType.AUTOMATIC;
    private SignalEventBus signalEventBus = new SignalEventBus();
    private volatile DeltaParams deltaParams = new DeltaParams();

    public FlagOpenOrder getFlagOpenOrder() {
        return flagOpenOrder;
    }

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

                BigDecimal deltaFact = openPrices.getDelta1Fact();
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

                deltasLogger.info(String.format("#%s delta1_fact=%s-%s=%s; " +
                                "cum_delta_fact=%s/%s/%s; " +
                                "diffFact=%s/%s/%s+%s/%s/%s=%s/%s/%s; " +
                                "cum_diff_fact=%s/%s/%s+%s/%s/%s=%s/%s/%s;",
                        getCounter(),
                        openPrices.getFirstOpenPrice().toPlainString(),
                        openPrices.getSecondOpenPrice().toPlainString(),
                        deltaFact.toPlainString(),
                        params.getCumDeltaFact().toPlainString(),
                        params.getCumDeltaFactMin().toPlainString(),
                        params.getCumDeltaFactMax().toPlainString(),
                        openDiffs.getFirstOpenPrice(), params.getDiffFact1Min(), params.getDiffFact1Max(),
                        openDiffs.getSecondOpenPrice(), params.getDiffFact2Min(), params.getDiffFact2Max(),
                        diffFact, params.getDiffFactMin(), params.getDiffFactMax(),
                        params.getCumDiffFact1(), params.getCumDiffFact1Min(), params.getCumDiffFact1Max(),
                        params.getCumDiffFact2(), params.getCumDiffFact2Min(), params.getCumDiffFact2Max(),
                        cumDiffsFact, params.getCumDiffsFactMin(), params.getCumDiffsFactMax()
                ));

                deltasLogger.info(String.format("o_avg_price_long=%s, o_avg_price_short=%s ",
                        getSecondMarketService().getPosition().getPriceAvgLong(),
                        getSecondMarketService().getPosition().getPriceAvgShort()));

                printCumBitmexMCom();

            } else if (params.getLastDelta().equals(DELTA2)) {

                BigDecimal deltaFact = openPrices.getDelta2Fact();
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

                deltasLogger.info(String.format("#%s delta2_fact=%s-%s=%s; " +
                                "cum_delta_fact=%s/%s/%s; " +
                                "diffFact=%s/%s/%s+%s/%s/%s=%s/%s/%s; " +
                                "cum_diff_fact=%s/%s/%s+%s/%s/%s=%s/%s/%s;",
                        getCounter(),
                        openPrices.getSecondOpenPrice().toPlainString(),
                        openPrices.getFirstOpenPrice().toPlainString(),
                        deltaFact.toPlainString(),
                        params.getCumDeltaFact().toPlainString(),
                        params.getCumDeltaFactMin().toPlainString(),
                        params.getCumDeltaFactMax().toPlainString(),
                        openDiffs.getFirstOpenPrice(), params.getDiffFact1Min(), params.getDiffFact1Max(),
                        openDiffs.getSecondOpenPrice(), params.getDiffFact2Min(), params.getDiffFact2Max(),
                        diffFact, params.getDiffFactMin(), params.getDiffFactMax(),
                        params.getCumDiffFact1(), params.getCumDiffFact1Min(), params.getCumDiffFact1Max(),
                        params.getCumDiffFact2(), params.getCumDiffFact2Min(), params.getCumDiffFact2Max(),
                        cumDiffsFact, params.getCumDiffsFactMin(), params.getCumDiffsFactMax()
                ));

                deltasLogger.info(String.format("o_avg_price_long=%s, o_avg_price_short=%s ",
                        getSecondMarketService().getPosition().getPriceAvgLong(),
                        getSecondMarketService().getPosition().getPriceAvgShort()));

                printCumBitmexMCom();

            }
        }

        printSumBal(false);

        saveParamsToDb();
    }

    public MarketService getFirstMarketService() {
        return firstMarketService;
    }

    public MarketService getSecondMarketService() {
        return secondMarketService;
    }

    private void setTimeoutAfterStartTrading() {
//        flagOpenOrder.setFirstReady(false);
//        flagOpenOrder.setSecondReady(false);

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
                    if (firstMarketService.isBusy() || secondMarketService.isBusy()) {
                        final String logString = String.format("#%s Warning: busy by isBusy for 6 min. first:%s(%s), second:%s(%s)",
                                getCounter(),
                                firstMarketService.isBusy(),
                                firstMarketService.getOpenOrders().size(),
                                secondMarketService.isBusy(),
                                secondMarketService.getOpenOrders().size());
                        deltasLogger.warn(logString);
                        warningLogger.warn(logString);


                        if (firstMarketService.isBusy() && firstMarketService.getOpenOrders().size() == 0) {
                            deltasLogger.warn("Warning: Free Bitmex");
                            warningLogger.warn("Warning: Free Bitmex");
                            firstMarketService.getEventBus().send(BtsEvent.MARKET_FREE);
                        }

                        if (secondMarketService.isBusy() && secondMarketService.getOpenOrders().size() == 0) {
                            deltasLogger.warn("Warning: Free Okcoin");
                            warningLogger.warn("Warning: Free Okcoin");
                            secondMarketService.getEventBus().send(BtsEvent.MARKET_FREE);
                        }

                    } else if (!firstMarketService.isReadyForArbitrage() || !secondMarketService.isReadyForArbitrage()) {
                        final String logString = String.format("#%s Warning: busy for 6 min. first:isReady=%s(Orders=%s), second:isReady=%s(Orders=%s)",
                                getCounter(),
                                firstMarketService.isReadyForArbitrage(), firstMarketService.getOpenOrders().size(),
                                secondMarketService.isReadyForArbitrage(), secondMarketService.getOpenOrders().size());
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

        if (isReadyForTheArbitrage) {
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
                    bestQuotes = calcAndDoArbitrage(secondOrderBook, firstOrderBook);
                }
            }
        } else {
            debugLog.info("isReadyForTheArbitrage=false");
        }
        return bestQuotes;
    }

    private BestQuotes calcAndDoArbitrage(OrderBook okCoinOrderBook, OrderBook poloniexOrderBook) {
        // 1. Calc deltas
        final BestQuotes bestQuotes = Utils.createBestQuotes(okCoinOrderBook, poloniexOrderBook);
        if (!bestQuotes.hasEmpty()) {
            if (firstDeltasAfterStart) {
                firstDeltasAfterStart = false;
                warningLogger.info("Started: First delta calculated");
            }

            delta1 = bestQuotes.getBid1_p().subtract(bestQuotes.getAsk1_o());
            delta2 = bestQuotes.getBid1_o().subtract(bestQuotes.getAsk1_p());
            if (delta1.compareTo(deltaParams.getbDeltaMin()) == -1) {
                deltaParams.setbDeltaMin(delta1);
            }
            if (delta1.compareTo(deltaParams.getbDeltaMax()) == 1) {
                deltaParams.setbDeltaMax(delta1);
            }
            if (delta2.compareTo(deltaParams.getoDeltaMin()) == -1) {
                deltaParams.setoDeltaMin(delta2);
            }
            if (delta2.compareTo(deltaParams.getoDeltaMax()) == 1) {
                deltaParams.setoDeltaMax(delta2);
            }

            if (!Thread.interrupted()) {
                storeDeltaParams();
            } else {
                return bestQuotes;
            }
        }

        BigDecimal border1 = params.getBorder1();
        BigDecimal border2 = params.getBorder2();

        final BorderParams borderParams = persistenceService.fetchBorders();
        if (borderParams == null || borderParams.getActiveVersion() == BorderParams.Ver.V1) {
            if (delta1.compareTo(border1) == 0 || delta1.compareTo(border1) == 1) {
                startTradingOnDelta1(SignalType.AUTOMATIC, bestQuotes);
            }

            if (delta2.compareTo(border2) == 0 || delta2.compareTo(border2) == 1) {
                startTradingOnDelta2(SignalType.AUTOMATIC, bestQuotes);
            }
        } else {
            final BigDecimal bP = firstMarketService.getPosition().getPositionLong();
            final BigDecimal oPL = secondMarketService.getPosition().getPositionLong();
            final BigDecimal oPS = secondMarketService.getPosition().getPositionShort();

            final BordersService.TradingSignal tradingSignal = bordersService.checkBorders(delta1, delta2, bP, oPL, oPS);
            if (tradingSignal.tradeType == BordersService.TradeType.DELTA1_B_SELL_O_BUY) {
                startTradingOnDelta1(SignalType.AUTOMATIC, bestQuotes, BigDecimal.valueOf(tradingSignal.bitmexBlock), BigDecimal.valueOf(tradingSignal.okexBlock));
            }

            if (tradingSignal.tradeType == BordersService.TradeType.DELTA2_B_BUY_O_SELL) {
                startTradingOnDelta2(SignalType.AUTOMATIC, bestQuotes, BigDecimal.valueOf(tradingSignal.bitmexBlock), BigDecimal.valueOf(tradingSignal.okexBlock));
            }
        }

        return bestQuotes;
    }

    public void startTradingOnDelta1(SignalType signalType, BestQuotes bestQuotes) {
        final BigDecimal b_block = params.getBlock1();
        final BigDecimal o_block = params.getBlock2();
        startTradingOnDelta1(signalType, bestQuotes, b_block, o_block);
    }

    private void startTradingOnDelta1(SignalType signalType, BestQuotes bestQuotes, final BigDecimal b_block, final BigDecimal o_block) {
        final BigDecimal ask1_o = bestQuotes.getAsk1_o();
        final BigDecimal bid1_p = bestQuotes.getBid1_p();
        if (checkBalance(DELTA1, b_block, o_block) //) {
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

            writeLogDelta1(ask1_o, bid1_p);

            firstMarketService.placeOrderOnSignal(Order.OrderType.ASK, b_block, bestQuotes, signalType);
            secondMarketService.placeOrderOnSignal(Order.OrderType.BID, o_block, bestQuotes, signalType);
            setTimeoutAfterStartTrading();

            saveParamsToDb();
        } else {
            bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.ONLY_SIGNAL);
        }
    }

    public void startTradingOnDelta2(SignalType signalType, BestQuotes bestQuotes) {
        final BigDecimal b_block = params.getBlock1();
        final BigDecimal o_block = params.getBlock2();
        startTradingOnDelta2(signalType, bestQuotes, b_block, o_block);
    }

    private void startTradingOnDelta2(SignalType signalType, BestQuotes bestQuotes, BigDecimal b_block, BigDecimal o_block) {
        final BigDecimal ask1_p = bestQuotes.getAsk1_p();
        final BigDecimal bid1_o = bestQuotes.getBid1_o();

        if (checkBalance(DELTA2, b_block, o_block) //) {
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

            writeLogDelta2(ask1_p, bid1_o);

            firstMarketService.placeOrderOnSignal(Order.OrderType.BID, b_block, bestQuotes, signalType);
            secondMarketService.placeOrderOnSignal(Order.OrderType.ASK, o_block, bestQuotes, signalType);
            setTimeoutAfterStartTrading();

            saveParamsToDb();

        } else {
            bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.ONLY_SIGNAL);
        }
    }

    private void writeLogDelta1(BigDecimal ask1_o, BigDecimal bid1_p) {
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
        deltasLogger.info(String.format("#%s delta1=%s-%s=%s; b1=%s; cum_delta=%s/%s/%s;",
                //usdP=%s; btcO=%s; usdO=%s; w=%s; ",
                getCounter(),
                bid1_p.toPlainString(), ask1_o.toPlainString(),
                delta1.toPlainString(),
                border1.toPlainString(),
                params.getCumDelta().toPlainString(),
                params.getCumDeltaMin().toPlainString(),
                params.getCumDeltaMax().toPlainString()
        ));

        // Count com
        params.setCom1(bid1_p.multiply(FEE_FIRST_MAKER).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP));
        params.setCom2(ask1_o.multiply(FEE_SECOND_TAKER).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP));


        if (signalType == SignalType.AUTOMATIC) {
            printCom();
        }

        printSumBal(false);
    }

    private void writeLogDelta2(BigDecimal ask1_p, BigDecimal bid1_o) {
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
        deltasLogger.info(String.format("#%s delta2=%s-%s=%s; b2=%s; cum_delta=%s/%s/%s;",
                getCounter(),
                bid1_o.toPlainString(), ask1_p.toPlainString(),
                delta2.toPlainString(),
                border2.toPlainString(),
                params.getCumDelta().toPlainString(),
                params.getCumDeltaMin().toPlainString(),
                params.getCumDeltaMax().toPlainString()
        ));

        // Count com
        params.setCom1(ask1_p.multiply(FEE_FIRST_MAKER).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP));
        params.setCom2(bid1_o.multiply(FEE_SECOND_TAKER).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP));

        if (signalType == SignalType.AUTOMATIC) {
            printCom();
        }

        printSumBal(false);
    }

    private void printCom() {
        BigDecimal com1 = params.getCom1();
        BigDecimal com2 = params.getCom2();
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

        deltasLogger.info(String.format("#%s com=%s/%s/%s+%s/%s/%s=%s/%s/%s; cum_com=%s+%s=%s",
                getCounter(),
                com1, params.getCom1Min(), params.getCom1Max(),
                com2, params.getCom2Min(), params.getCom2Max(),
                com, params.getComMin(), params.getComMax(),
                params.getCumCom1(),
                params.getCumCom2(),
                cumCom
        ));
    }

    private void printCumBitmexMCom() {
        // bitmex_m_com = round(open_price_fact * 0.025 / 100; 4),
        BigDecimal bitmexMCom = openPrices.getFirstOpenPrice().multiply(new BigDecimal(0.025))
                .divide(new BigDecimal(100), 4, BigDecimal.ROUND_HALF_UP);
        if (bitmexMCom.compareTo(BigDecimal.ZERO) != 0 && bitmexMCom.compareTo(params.getBitmexMComMin()) == -1) params.setBitmexMComMin(bitmexMCom);
        if (bitmexMCom.compareTo(params.getBitmexMComMax()) == 1) params.setBitmexMComMax(bitmexMCom);

        params.setCumBitmexMCom(params.getCumBitmexMCom().add(bitmexMCom));

        deltasLogger.info(String.format("#%s bitmex_m_com=%s/%s/%s; cum_bitmex_m_com=%s",
                getCounter(),
                bitmexMCom, params.getBitmexMComMin(), params.getBitmexMComMax(),
                params.getCumBitmexMCom()
        ));
    }

    @Scheduled(fixedRate = 1000)
    public void calcSumBalForGui() {
        final AccountInfoContracts firstAccount = firstMarketService.getAccountInfoContracts();
        final AccountInfoContracts secondAccount = secondMarketService.getAccountInfoContracts();
        if (firstAccount != null && secondAccount != null) {
            final BigDecimal bW = firstAccount.getWallet();
            final BigDecimal bE = firstAccount.getEquity();
            final BigDecimal bU = firstAccount.getUpl();
            final BigDecimal bM = firstAccount.getMargin();
            final BigDecimal bA = firstAccount.getAvailable();

            final BigDecimal oW = secondAccount.getWallet();
            final BigDecimal oE = secondAccount.getEquity();
            final BigDecimal oM = secondAccount.getMargin();
            final BigDecimal oU = secondAccount.getUpl();
            final BigDecimal oA = secondAccount.getAvailable();

            final BigDecimal sumW = bW.add(oW).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumE = bE.add(oE).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumUpl = bU.add(oU).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumM = bM.add(oM).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumA = bA.add(oA).setScale(8, BigDecimal.ROUND_HALF_UP);

            final BigDecimal quAvg = calcQuAvg();

            sumBalString = String.format("s_bal=w%s_%s, e%s_%s, u%s_%s, m%s_%s, a%s_%s",
                    sumW.toPlainString(), sumW.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumE.toPlainString(), sumE.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumUpl.toPlainString(), sumUpl.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumM.toPlainString(), sumM.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumA.toPlainString(), sumA.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP));
        }
    }

    public void printSumBal(boolean isGuiButton) {
        String counterName = String.valueOf(getCounter());
        if (isGuiButton) {
            counterName = "button";
        } else if (signalType != SignalType.AUTOMATIC) {
            counterName = signalType.getCounterName();
        }

        final AccountInfoContracts firstAccount = firstMarketService.getAccountInfoContracts();
        final AccountInfoContracts secondAccount = secondMarketService.getAccountInfoContracts();
        if (firstAccount != null && secondAccount != null) {
            final BigDecimal bW = firstAccount.getWallet();
            final BigDecimal bE = firstAccount.getEquity();
            final BigDecimal bU = firstAccount.getUpl();
            final BigDecimal bM = firstAccount.getMargin();
            final BigDecimal bA = firstAccount.getAvailable();
            final BigDecimal bP = firstMarketService.getPosition().getPositionLong();
            final BigDecimal bLv = firstMarketService.getPosition().getLeverage();
            final BigDecimal bAL = firstMarketService.getAffordableContractsForLong();
            final BigDecimal bAS = firstMarketService.getAffordableContractsForShort();
            final BigDecimal quAvg = calcQuAvg();
            final OrderBook bOrderBook = firstMarketService.getOrderBook();
            final BigDecimal bBestAsk = Utils.getBestAsks(bOrderBook, 1).get(0).getLimitPrice();
            final BigDecimal bBestBid = Utils.getBestBids(bOrderBook, 1).get(0).getLimitPrice();
            deltasLogger.info(String.format("#%s b_bal=w%s_%s, e%s_%s, u%s_%s, m%s_%s, a%s_%s, p%s, lv%s, lg%s, st%s, ask[1]%s, bid[1]%s",
                    counterName,
                    bW.toPlainString(), bW.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    bE.toPlainString(), bE.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
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
            final BigDecimal oE = secondAccount.getEquity();
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
            deltasLogger.info(String.format("#%s o_bal=w%s_%s, e%s_%s, u%s_%s, m%s_%s, a%s_%s, p+%s-%s, lv%s, lg%s, st%s, ask[1]%s, bid[1]%s",
                    counterName,
                    oW.toPlainString(), oW.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    oE.toPlainString(), oE.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
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
            final BigDecimal sumE = bE.add(oE).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumUpl = bU.add(oU).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumM = bM.add(oM).setScale(8, BigDecimal.ROUND_HALF_UP);
            final BigDecimal sumA = bA.add(oA).setScale(8, BigDecimal.ROUND_HALF_UP);

            sumBalString = String.format("#%s s_bal=w%s_%s, e%s_%s, u%s_%s, m%s_%s, a%s_%s",
                    counterName,
                    sumW.toPlainString(), sumW.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumE.toPlainString(), sumE.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumUpl.toPlainString(), sumUpl.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumM.toPlainString(), sumM.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumA.toPlainString(), sumA.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP));
            deltasLogger.info(sumBalString);

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
    }

    public String getPosDiffString() {
        final BigDecimal posDiff = posDiffService.getPositionsDiff();
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

    private boolean checkBalance(String deltaRef, BigDecimal blockSize1, BigDecimal blockSize2) {
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
//        qu_avg = (b_bid[1] + b_ask[1] + o_bid[1] + o_ask[1]) / 4;
        final BigDecimal bB = Utils.getBestBid(firstMarketService.getOrderBook()).getLimitPrice();
        final BigDecimal bA = Utils.getBestAsk(firstMarketService.getOrderBook()).getLimitPrice();
        final BigDecimal oB = Utils.getBestBid(secondMarketService.getOrderBook()).getLimitPrice();
        final BigDecimal oA = Utils.getBestAsk(secondMarketService.getOrderBook()).getLimitPrice();
        return (bB.add(bA).add(oB).add(oA)).divide(BigDecimal.valueOf(4), 2, BigDecimal.ROUND_HALF_UP);
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
