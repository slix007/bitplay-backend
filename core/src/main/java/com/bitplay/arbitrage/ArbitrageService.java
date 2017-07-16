package com.bitplay.arbitrage;

import com.bitplay.TwoMarketStarter;
import com.bitplay.market.MarketService;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.persistance.PersistenceService;
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

    private static final String DELTA1 = "delta1";
    private static final String DELTA2 = "delta2";
    private static final Object calcLock = new Object();

    @Autowired
    PersistenceService persistenceService;
    Disposable schdeduleUpdateBorders;
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
    private SignalType signalType = SignalType.AUTOMATIC;

    public FlagOpenOrder getFlagOpenOrder() {
        return flagOpenOrder;
    }

    public OpenPrices getOpenPrices() {
        return openPrices;
    }

    public void init(TwoMarketStarter twoMarketStarter) {
        this.firstMarketService = twoMarketStarter.getFirstMarketService();
        this.secondMarketService = twoMarketStarter.getSecondMarketService();
        this.posDiffService = twoMarketStarter.getPosDiffService();
        startArbitrageMonitoring();
        scheduleRecalculateBorders();
        initArbitrageStateListener();
        loadParamsFromDb();
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

                BigDecimal cumDeltaFact = params.getCumDeltaFact();
                BigDecimal cumDeltaFactMin = params.getCumDeltaFactMin();
                BigDecimal cumDeltaFactMax = params.getCumDeltaFactMax();

                params.setCumDeltaFact(cumDeltaFact.add(deltaFact));
                if (cumDeltaFact.compareTo(cumDeltaFactMin) == -1) params.setCumDeltaFactMin(cumDeltaFact);
                if (cumDeltaFact.compareTo(cumDeltaFactMax) == 1) params.setCumDeltaFactMax(cumDeltaFact);

                BigDecimal diffFact1Min = params.getDiffFact1Min();
                BigDecimal diffFact1Max = params.getDiffFact1Max();
                BigDecimal diffFact2Min = params.getDiffFact2Min();
                BigDecimal diffFact2Max = params.getDiffFact2Max();


                BigDecimal diffFact = openDiffs.getFirstOpenPrice().add(openDiffs.getSecondOpenPrice());
                if (openDiffs.getFirstOpenPrice().compareTo(diffFact1Min) == -1) params.setDiffFact1Min(openDiffs.getFirstOpenPrice());
                if (openDiffs.getFirstOpenPrice().compareTo(diffFact1Max) == 1) params.setDiffFact1Max(openDiffs.getFirstOpenPrice());
                if (openDiffs.getSecondOpenPrice().compareTo(diffFact2Min) == -1) params.setDiffFact2Min(openDiffs.getSecondOpenPrice());
                if (openDiffs.getSecondOpenPrice().compareTo(diffFact2Max) == 1) params.setDiffFact2Max(openDiffs.getSecondOpenPrice());
                BigDecimal diffFactMin = params.getDiffFactMin();
                BigDecimal diffFactMax = params.getDiffFactMax();
                if (diffFact.compareTo(diffFactMin) == -1) params.setDiffFactMin(diffFact);
                if (diffFact.compareTo(diffFactMax) == 1) params.setDiffFactMax(diffFact);

                BigDecimal cumDiffFact1 = params.getCumDiffFact1();
                BigDecimal cumDiffFact1Min = params.getCumDiffFact1Min();
                BigDecimal cumDiffFact1Max = params.getCumDiffFact1Max();
                BigDecimal cumDiffFact2 = params.getCumDiffFact2();
                BigDecimal cumDiffFact2Min = params.getCumDiffFact2Min();
                BigDecimal cumDiffFact2Max = params.getCumDiffFact2Max();
                BigDecimal cumDiffsFactMin = params.getCumDiffsFactMin();
                BigDecimal cumDiffsFactMax = params.getCumDiffsFactMax();

                params.setCumDiffFact1(cumDiffFact1.add(openDiffs.getFirstOpenPrice()));
                if (cumDiffFact1.compareTo(cumDiffFact1Min) == -1) params.setCumDiffFact1Min(cumDiffFact1);
                if (cumDiffFact1.compareTo(cumDiffFact1Max) == 1) params.setCumDiffFact1Max(cumDiffFact1);
                params.setCumDiffFact2(cumDiffFact2.add(openDiffs.getSecondOpenPrice()));
                if (cumDiffFact2.compareTo(cumDiffFact2Min) == -1) params.setCumDiffFact2Min(cumDiffFact2);
                if (cumDiffFact2.compareTo(cumDiffFact2Max) == 1) params.setCumDiffFact2Max(cumDiffFact2);
                BigDecimal cumDiffsFact = cumDiffFact1.add(cumDiffFact2);
                if (cumDiffsFact.compareTo(cumDiffsFactMin) == -1) params.setCumDiffsFactMin(cumDiffsFact);
                if (cumDiffsFact.compareTo(cumDiffsFactMax) == 1) params.setCumDiffsFactMax(cumDiffsFact);

                deltasLogger.info(String.format("#%s delta1_fact=%s-%s=%s; " +
                                "cum_delta_fact=%s/%s/%s; " +
                                "diffFact=%s/%s/%s+%s/%s/%s=%s/%s/%s; " +
                                "cum_diff_fact=%s/%s/%s+%s/%s/%s=%s/%s/%s;",// position=%s",
                        getCounter(),
                        openPrices.getFirstOpenPrice().toPlainString(),
                        openPrices.getSecondOpenPrice().toPlainString(),
                        deltaFact.toPlainString(),
                        cumDeltaFact.toPlainString(),
                        cumDeltaFactMin.toPlainString(),
                        cumDeltaFactMax.toPlainString(),
                        openDiffs.getFirstOpenPrice(), diffFact1Min, diffFact1Max,
                        openDiffs.getSecondOpenPrice(), diffFact2Min, diffFact2Max,
                        diffFact, diffFactMin, diffFactMax,
                        cumDiffFact1, cumDiffFact1Min, cumDiffFact1Max,
                        cumDiffFact2, cumDiffFact2Min, cumDiffFact2Max,
                        cumDiffsFact, cumDiffsFactMin, cumDiffsFactMax
//                        firstMarketService.getPositionAsString()
                ));

                printCumBitmexMCom();

            } else if (params.getLastDelta().equals(DELTA2)) {
                BigDecimal deltaFact = openPrices.getDelta2Fact();
                BigDecimal cumDeltaFact = params.getCumDeltaFact();
                BigDecimal cumDeltaFactMin = params.getCumDeltaFactMin();
                BigDecimal cumDeltaFactMax = params.getCumDeltaFactMax();
                params.setCumDeltaFact(cumDeltaFact.add(deltaFact));
                if (cumDeltaFact.compareTo(cumDeltaFactMin) == -1) params.setCumDeltaFactMin(cumDeltaFact);
                if (cumDeltaFact.compareTo(cumDeltaFactMax) == 1) params.setCumDeltaFactMax(cumDeltaFact);

                BigDecimal diffFact1Min = params.getDiffFact1Min();
                BigDecimal diffFact1Max = params.getDiffFact1Max();
                BigDecimal diffFact2Min = params.getDiffFact2Min();
                BigDecimal diffFact2Max = params.getDiffFact2Max();
                BigDecimal diffFact = openDiffs.getFirstOpenPrice().add(openDiffs.getSecondOpenPrice());
                if (openDiffs.getFirstOpenPrice().compareTo(diffFact1Min) == -1) params.setDiffFact1Min(openDiffs.getFirstOpenPrice());
                if (openDiffs.getFirstOpenPrice().compareTo(diffFact1Max) == 1) params.setDiffFact1Max(openDiffs.getFirstOpenPrice());
                if (openDiffs.getSecondOpenPrice().compareTo(diffFact2Min) == -1) params.setDiffFact2Min(openDiffs.getSecondOpenPrice());
                if (openDiffs.getSecondOpenPrice().compareTo(diffFact2Max) == 1) params.setDiffFact2Max(openDiffs.getSecondOpenPrice());
                BigDecimal diffFactMin = params.getDiffFactMin();
                BigDecimal diffFactMax = params.getDiffFactMax();
                if (diffFact.compareTo(diffFactMin) == -1) params.setDiffFactMin(diffFact);
                if (diffFact.compareTo(diffFactMax) == 1) params.setDiffFactMax(diffFact);

                BigDecimal cumDiffFact1 = params.getCumDiffFact1();
                BigDecimal cumDiffFact1Min = params.getCumDiffFact1Min();
                BigDecimal cumDiffFact1Max = params.getCumDiffFact1Max();
                BigDecimal cumDiffFact2 = params.getCumDiffFact2();
                BigDecimal cumDiffFact2Min = params.getCumDiffFact2Min();
                BigDecimal cumDiffFact2Max = params.getCumDiffFact2Max();
                BigDecimal cumDiffsFactMin = params.getCumDiffsFactMin();
                BigDecimal cumDiffsFactMax = params.getCumDiffsFactMax();
                params.setCumDiffFact1(cumDiffFact1.add(openDiffs.getFirstOpenPrice()));
                if (cumDiffFact1.compareTo(cumDiffFact1Min) == -1) params.setCumDiffFact1Min(cumDiffFact1);
                if (cumDiffFact1.compareTo(cumDiffFact1Max) == 1) params.setCumDiffFact1Max(cumDiffFact1);
                params.setCumDiffFact2(cumDiffFact2.add(openDiffs.getSecondOpenPrice()));
                if (cumDiffFact2.compareTo(cumDiffFact2Min) == -1) params.setCumDiffFact2Min(cumDiffFact2);
                if (cumDiffFact2.compareTo(cumDiffFact2Max) == 1) params.setCumDiffFact2Max(cumDiffFact2);
                BigDecimal cumDiffsFact = cumDiffFact1.add(cumDiffFact2);
                if (cumDiffsFact.compareTo(cumDiffsFactMin) == -1) params.setCumDiffsFactMin(cumDiffsFact);
                if (cumDiffsFact.compareTo(cumDiffsFactMax) == 1) params.setCumDiffsFactMax(cumDiffsFact);

                deltasLogger.info(String.format("#%s delta2_fact=%s-%s=%s; " +
                                "cum_delta_fact=%s/%s/%s; " +
                                "diffFact=%s/%s/%s+%s/%s/%s=%s/%s/%s; " +
                                "cum_diff_fact=%s/%s/%s+%s/%s/%s=%s/%s/%s;",// position=%s",
                        getCounter(),
                        openPrices.getSecondOpenPrice().toPlainString(),
                        openPrices.getFirstOpenPrice().toPlainString(),
                        deltaFact.toPlainString(),
                        cumDeltaFact.toPlainString(),
                        cumDeltaFactMin.toPlainString(),
                        cumDeltaFactMax.toPlainString(),
                        openDiffs.getFirstOpenPrice(), diffFact1Min, diffFact1Max,
                        openDiffs.getSecondOpenPrice(), diffFact2Min, diffFact2Max,
                        diffFact, diffFactMin, diffFactMax,
                        cumDiffFact1, cumDiffFact1Min, cumDiffFact1Max,
                        cumDiffFact2, cumDiffFact2Min, cumDiffFact2Max,
                        cumDiffsFact, cumDiffsFactMin, cumDiffsFactMax
//                        firstMarketService.getPositionAsString()
                ));

                printCumBitmexMCom();

            }
        }

        printSumBal(false);

        printVolFact();

        saveParamsToDb();
    }

    private void printVolFact() {
        //vol_fact = pos_after - pos_befor
        final BigDecimal posAfter = new BigDecimal(firstMarketService.getPositionAsString());
        final BigDecimal posBefore = params.getPosBefore();
        final BigDecimal volFact = posAfter.subtract(posBefore);
        // vol_diff = vol_plan - vol_fact
        final BigDecimal volPlan = params.getVolPlan();
        final BigDecimal volDiff = volPlan.subtract(volFact);

        final String warningString = volDiff.compareTo(BigDecimal.ZERO) == 1 ? "WARNING vol_diff > 0" : "";
        final String logString = String.format("#%s %s vol_fact=%s-%s=%s; vol_diff=%s-%s=%s",
                getCounter(),
                warningString,
                posAfter, posBefore, volFact,
                volPlan, volFact, volDiff);
        if (volDiff.compareTo(BigDecimal.ZERO) == 1) {
            warningLogger.error("deltas: " + logString);
        }
        deltasLogger.info(logString);
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
        theTimer = Completable.timer(5, TimeUnit.SECONDS)
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

        theCheckBusyTimer = Completable.timer(5, TimeUnit.MINUTES, Schedulers.computation())
                .doOnComplete(() -> {
                    if (firstMarketService.isBusy() || secondMarketService.isBusy()) {
                        final String logString = String.format("#%s Warning: busy by isBusy for 5 min. first:%s(%s), second:%s(%s)",
                                getCounter(),
                                firstMarketService.isBusy(),
                                firstMarketService.getOpenOrders().size(),
                                secondMarketService.isBusy(),
                                secondMarketService.getOpenOrders().size());
                        deltasLogger.warn(logString);
                        warningLogger.warn(logString);


                        if (firstMarketService.isBusy() && firstMarketService.getOpenOrders().size() == 0) {
                            deltasLogger.warn("Warning: Free Okcoin");
                            warningLogger.warn("Warning: Free Okcoin");
                            firstMarketService.getEventBus().send(BtsEvent.MARKET_FREE);
                        }

                        if (secondMarketService.isBusy() && secondMarketService.getOpenOrders().size() == 0) {
                            deltasLogger.warn("Warning: Free Bitmex");
                            warningLogger.warn("Warning: Free Bitmex");
                            secondMarketService.getEventBus().send(BtsEvent.MARKET_FREE);
                        }

                    } else if (!firstMarketService.isReadyForArbitrage() || !secondMarketService.isReadyForArbitrage()) {
                        final String logString = String.format("#%s Warning: busy for 5 min. first:isReady=%s(Orders=%s), second:isReady=%s(Orders=%s)",
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

    private void startArbitrageMonitoring() {

        observableOrderBooks()
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .doOnError(throwable -> logger.error("doOnError On combine orderBooks", throwable))
                .retryWhen(throwables -> throwables.delay(1, TimeUnit.SECONDS))
                .subscribe(bestQuotes -> {
                    // Log not often then 5 sec
                    if (Duration.between(previousEmitTime, Instant.now()).getSeconds() > 5
                            && bestQuotes != null
                            && bestQuotes.getArbitrageEvent() != BestQuotes.ArbitrageEvent.NONE) {

                        previousEmitTime = Instant.now();
                        signalLogger.info(bestQuotes.toString());
                    }
                }, throwable -> {
                    logger.error("On combine orderBooks", throwable);
                    startArbitrageMonitoring();
                });

    }

    private Observable<BestQuotes> observableOrderBooks() {
        final Observable<OrderBook> firstOrderBook = firstMarketService.getOrderBookObservable();
        final Observable<OrderBook> secondOrderBook = secondMarketService.getOrderBookObservable();

        // Observable.combineLatest - doesn't work while observable isn't completed
        return Observable
                .concat(firstOrderBook, secondOrderBook)
                .map(orderBook -> doComparison());
    }

    private BestQuotes doComparison() {
        BestQuotes bestQuotes = new BestQuotes(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        if (isReadyForTheArbitrage) {
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
        }
        return bestQuotes;
    }

    private BestQuotes calcAndDoArbitrage(OrderBook okCoinOrderBook, OrderBook poloniexOrderBook) {
        // 1. Calc deltas
        BigDecimal ask1_o = BigDecimal.ZERO;
        BigDecimal ask1_p = BigDecimal.ZERO;
        BigDecimal bid1_o = BigDecimal.ZERO;
        BigDecimal bid1_p = BigDecimal.ZERO;
        if (okCoinOrderBook != null && poloniexOrderBook != null
                && okCoinOrderBook.getAsks().size() > 1
                && poloniexOrderBook.getAsks().size() > 1) {
            ask1_o = Utils.getBestAsks(okCoinOrderBook.getAsks(), 1).get(0).getLimitPrice();
            ask1_p = Utils.getBestAsks(poloniexOrderBook.getAsks(), 1).get(0).getLimitPrice();

            bid1_o = Utils.getBestBids(okCoinOrderBook.getBids(), 1).get(0).getLimitPrice();
            bid1_p = Utils.getBestBids(poloniexOrderBook.getBids(), 1).get(0).getLimitPrice();

            delta1 = bid1_p.subtract(ask1_o);
            delta2 = bid1_o.subtract(ask1_p);
        }
        final BestQuotes bestQuotes = new BestQuotes(ask1_o, ask1_p, bid1_o, bid1_p);

        final BigDecimal btcP = firstMarketService.getAccountInfoContracts().getAvailable();

        BigDecimal border1 = params.getBorder1();
        BigDecimal border2 = params.getBorder2();

//            1) если delta1 >= border1, то происходит sell у poloniex и buy у okcoin
        if (border1.compareTo(BigDecimal.ZERO) != 0) {
            if (delta1.compareTo(border1) == 0 || delta1.compareTo(border1) == 1) {
                if (checkBalance(DELTA1, params.getBlock1(), params.getBlock2()) //) {
                        && firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage()
                        && posDiffService.isPositionsEqual()) {
                    bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
                    setSignalType(SignalType.AUTOMATIC);
                    firstMarketService.setBusy();
                    secondMarketService.setBusy();
//                    firstMarketService.getEventBus().send(BtsEvent.MARKET_BUSY);
//                    secondMarketService.getEventBus().send(BtsEvent.MARKET_BUSY);
                    params.setLastDelta(DELTA1);
                    // Market specific params
                    params.setPosBefore(new BigDecimal(firstMarketService.getPositionAsString()));
                    params.setVolPlan(params.getBlock1()); // buy

                    writeLogDelta1(ask1_o, bid1_o, bid1_p, btcP, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

                    firstMarketService.placeOrderOnSignal(Order.OrderType.ASK, params.getBlock1(), bestQuotes, SignalType.AUTOMATIC);
                    secondMarketService.placeOrderOnSignal(Order.OrderType.BID, params.getBlock2(), bestQuotes, SignalType.AUTOMATIC);
                    setTimeoutAfterStartTrading();

                    saveParamsToDb();

                } else {
                    bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.ONLY_SIGNAL);
                }
            }
        }
//            2) если delta2 >= border2, то происходит buy у poloniex и sell у okcoin
        if (border2.compareTo(BigDecimal.ZERO) != 0) {
            if (delta2.compareTo(border2) == 0 || delta2.compareTo(border2) == 1) {
                if (checkBalance(DELTA2, params.getBlock1(), params.getBlock2()) //) {
                        && firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage()
                        && posDiffService.isPositionsEqual()) {

                    bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
                    setSignalType(SignalType.AUTOMATIC);
                    firstMarketService.getEventBus().send(BtsEvent.MARKET_BUSY);
                    secondMarketService.getEventBus().send(BtsEvent.MARKET_BUSY);
                    params.setLastDelta(DELTA2);
                    // Market specific params
                    params.setPosBefore(new BigDecimal(firstMarketService.getPositionAsString()));
                    params.setVolPlan(params.getBlock1().negate());//sell

                    writeLogDelta2(ask1_o, ask1_p, bid1_o, btcP);

                    firstMarketService.placeOrderOnSignal(Order.OrderType.BID, params.getBlock1(), bestQuotes, SignalType.AUTOMATIC);
                    secondMarketService.placeOrderOnSignal(Order.OrderType.ASK, params.getBlock2(), bestQuotes, SignalType.AUTOMATIC);
                    setTimeoutAfterStartTrading();

                    saveParamsToDb();

                } else {
                    bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.ONLY_SIGNAL);
                }
            }
        }
        return bestQuotes;
    }

    public String getPositionsString() {
        final BigDecimal bP = getFirstMarketService().getPosition().getPositionLong();

        final BigDecimal oPL = getSecondMarketService().getPosition().getPositionLong();
        final BigDecimal oPS = getSecondMarketService().getPosition().getPositionShort();
        return String.format("b_pos=%s, o_pos=%s-%s", Utils.withSign(bP), Utils.withSign(oPL), oPS.toPlainString());
    }

    private void writeLogDelta1(BigDecimal ask1_o, BigDecimal bid1_o, BigDecimal bid1_p, BigDecimal btcP, BigDecimal usdP, BigDecimal btcO, BigDecimal usdO) {
        deltasLogger.info("------------------------------------------");

        BigDecimal cumDelta = params.getCumDelta();
        BigDecimal cumDeltaMin = params.getCumDeltaMin();
        BigDecimal cumDeltaMax = params.getCumDeltaMax();
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

        params.setCumDelta(cumDelta.add(delta1));
        if (cumDelta.compareTo(cumDeltaMin) == -1) params.setCumDeltaMin(cumDelta);
        if (cumDelta.compareTo(cumDeltaMax) == 1) params.setCumDeltaMax(cumDelta);
        deltasLogger.info(String.format("#%s delta1=%s-%s=%s; b1=%s; btcP=%s;",
                //usdP=%s; btcO=%s; usdO=%s; w=%s; cum_delta=%s/%s/%s",
                getCounter(),
                bid1_p.toPlainString(), ask1_o.toPlainString(),
                delta1.toPlainString(),
                border1.toPlainString(),
                btcP
//                usdP, btcO, usdO,
//                firstWalletBalance.toPlainString(),
//                cumDelta.toPlainString(),
//                cumDeltaMin,
//                cumDeltaMax
        ));

        // Count com
        params.setCom1(bid1_p.multiply(new BigDecimal("0.075")).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP));
        params.setCom2(ask1_o.multiply(new BigDecimal("0.2")).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP));


        if (signalType == SignalType.AUTOMATIC) {
            printCom();
        }

        printSumBal(false);
    }

    private void printCom() {
        BigDecimal comMin = params.getComMin();
        BigDecimal comMax = params.getComMax();

        BigDecimal com1 = params.getCom1();
        BigDecimal com1Min = params.getCom1Min();
        BigDecimal com1Max = params.getCom1Max();
        BigDecimal com2 = params.getCom2();
        BigDecimal com2Min = params.getCom2Min();
        BigDecimal com2Max = params.getCom2Max();
        BigDecimal cumCom1 = params.getCumCom1();
        BigDecimal cumCom2 = params.getCumCom2();

        if (com1.compareTo(com1Min) == -1) params.setCom1Min(com1);
        if (com1.compareTo(com1Max) == 1) params.setCom1Max(com1);
        if (com2.compareTo(com2Min) == -1) params.setCom2Min(com2);
        if (com2.compareTo(com2Max) == 1) params.setCom2Max(com2);
        BigDecimal com = com1.add(com2);
        if (com.compareTo(comMin) == -1) params.setComMin(com);
        if (com.compareTo(comMax) == 1) params.setComMax(com);
        params.setCumCom1(cumCom1.add(com1));
        params.setCumCom2(cumCom2.add(com2));
        BigDecimal cumCom = cumCom1.add(cumCom2);

        deltasLogger.info(String.format("#%s com=%s/%s/%s+%s/%s/%s=%s/%s/%s; cum_com=%s+%s=%s",
                getCounter(),
                com1, com1Min, com1Max,
                com2, com2Min, com2Max,
                com, comMin, comMax,
                cumCom1,
                cumCom2,
                cumCom
        ));
    }

    private void printCumBitmexMCom() {
        BigDecimal bitmexMComMin = params.getBitmexMComMin();
        BigDecimal bitmexMComMax = params.getBitmexMComMax();
        BigDecimal cumBitmexMCom = params.getCumBitmexMCom();
        // bitmex_m_com = round(open_price_fact * 0.025 / 100; 4),
        BigDecimal bitmexMCom = openPrices.getFirstOpenPrice().multiply(new BigDecimal(0.025)).divide(new BigDecimal(100), 4, BigDecimal.ROUND_HALF_UP);
        if (bitmexMCom.compareTo(BigDecimal.ZERO) != 0 && bitmexMCom.compareTo(bitmexMComMin) == -1) params.setBitmexMComMin(bitmexMCom);
        if (bitmexMCom.compareTo(bitmexMComMax) == 1) params.setBitmexMComMax(bitmexMCom);

        params.setCumBitmexMCom(cumBitmexMCom.add(bitmexMCom));

        deltasLogger.info(String.format("#%s bitmex_m_com=%s/%s/%s; cum_bitmex_m_com=%s",
                getCounter(),
                bitmexMCom, bitmexMComMin, bitmexMComMax,
                cumBitmexMCom
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
            deltasLogger.info(String.format("#%s o_bal=w%s_%s, e%s_%s, u%s_%s, m%s_%s, a%s_%s, p%s%s, lv%s, lg%s, st%s, ask[1]%s, bid[1]%s",
                    counterName,
                    oW.toPlainString(), oW.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    oE.toPlainString(), oE.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    oU.toPlainString(), oU.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    oM.toPlainString(), oM.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    oA.toPlainString(), oA.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    Utils.withSign(oPL), Utils.withSign(oPS),
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

            sumBalString = String.format("s_bal=w%s_%s, e%s_%s, u%s_%s, m%s_%s, a%s_%s",
                    sumW.toPlainString(), sumW.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumE.toPlainString(), sumE.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumUpl.toPlainString(), sumUpl.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumM.toPlainString(), sumM.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP),
                    sumA.toPlainString(), sumA.multiply(quAvg).setScale(2, BigDecimal.ROUND_HALF_UP));
        }
    }

    private void writeLogDelta2(BigDecimal ask1_o, BigDecimal ask1_p, BigDecimal bid1_o, BigDecimal btcP) {
        deltasLogger.info("------------------------------------------");

        BigDecimal cumDelta = params.getCumDelta();
        BigDecimal cumDeltaMin = params.getCumDeltaMin();
        BigDecimal cumDeltaMax = params.getCumDeltaMax();
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

        params.setCumDelta(cumDelta.add(delta2));
        if (cumDelta.compareTo(cumDeltaMin) == -1) params.setCumDeltaMin(cumDelta);
        if (cumDelta.compareTo(cumDeltaMax) == 1) params.setCumDeltaMax(cumDelta);
        deltasLogger.info(String.format("#%s delta2=%s-%s=%s; b2=%s; btcP=%s;",// usdP=%s; btcO=%s; usdO=%s; w=%s; cum_delta=%s/%s/%s",
                getCounter(),
                bid1_o.toPlainString(), ask1_p.toPlainString(),
                delta2.toPlainString(),
                border2.toPlainString(),
                btcP
//                , usdP, btcO, usdO,
//                firstWalletBalance,
//                cumDelta.toPlainString(),
//                cumDeltaMin,
//                cumDeltaMax
        ));

        // Count com
        params.setCom1(ask1_p.multiply(new BigDecimal("0.075")).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP));
        params.setCom2(bid1_o.multiply(new BigDecimal("0.2")).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP));

        if (signalType == SignalType.AUTOMATIC) {
            printCom();
        }

        printSumBal(false);
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

    public void scheduleRecalculateBorders() {
        if (schdeduleUpdateBorders != null && !schdeduleUpdateBorders.isDisposed()) {
            schdeduleUpdateBorders.dispose();
        }

        schdeduleUpdateBorders = Completable.timer(params.getPeriodSec(), TimeUnit.SECONDS, Schedulers.computation())
                .doOnComplete(() -> {
                    recalculateBorders();
                    scheduleRecalculateBorders();
                })
                .subscribe();
    }

    public void recalculateBorders() {
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
        final GuiParams deltas = persistenceService.fetchDeltas();
        if (deltas != null) {
            params = deltas;
        } else {
            params = new GuiParams();
        }
    }

    public void saveParamsToDb() {
        persistenceService.saveDeltas(params);
    }

    public GuiParams getParams() {
        return params;
    }

    public void setParams(GuiParams params) {
        this.params = params;
    }

    public OpenPrices getOpenDiffs() {
        return openDiffs;
    }

    public SignalType getSignalType() {
        return signalType;
    }

    public synchronized void setSignalType(SignalType signalType) {
        this.signalType = signalType;
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
