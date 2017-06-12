package com.bitplay.arbitrage;

import com.bitplay.TwoMarketStarter;
import com.bitplay.market.MarketService;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.utils.Utils;

import org.knowm.xchange.bitmex.BitmexAdapters;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final String DELTA1 = "delta1";
    private static final String DELTA2 = "delta2";

    private BigDecimal amount = new BigDecimal("0.02");

    //TODO rename them to first and second
    private MarketService firstMarketService;
    private MarketService secondMarketService;

    private BigDecimal delta1 = BigDecimal.ZERO;
    private BigDecimal delta2 = BigDecimal.ZERO;

    private BigDecimal border1 = BigDecimal.ZERO;
    private BigDecimal border2 = BigDecimal.ZERO;
    private BigDecimal makerDelta = BigDecimal.ZERO;
    private BigDecimal sumDelta = new BigDecimal(5);
    private BigDecimal buValue = BigDecimal.ZERO;
    private Integer periodSec = 300;
    private BigDecimal cumDelta = BigDecimal.ZERO;
    private BigDecimal cumDeltaMin = BigDecimal.valueOf(10000);
    private BigDecimal cumDeltaMax = BigDecimal.ZERO;
    private BigDecimal cumDeltaFact = BigDecimal.ZERO;
    private BigDecimal cumDeltaFactMin = BigDecimal.valueOf(10000);
    private BigDecimal cumDeltaFactMax = BigDecimal.ZERO;
    private String lastDelta = null;
    private BigDecimal cumDiffFact1 = BigDecimal.ZERO;
    private BigDecimal cumDiffFact1Min = BigDecimal.valueOf(10000);
    private BigDecimal cumDiffFact1Max = BigDecimal.ZERO;
    private BigDecimal cumDiffFact2 = BigDecimal.ZERO;
    private BigDecimal cumDiffFact2Min = BigDecimal.valueOf(10000);
    private BigDecimal cumDiffFact2Max = BigDecimal.ZERO;
    private BigDecimal cumDiffsFactMin = BigDecimal.valueOf(10000);
    private BigDecimal cumDiffsFactMax = BigDecimal.ZERO;
    private BigDecimal diffFactMin = BigDecimal.valueOf(10000);
    private BigDecimal diffFactMax = BigDecimal.ZERO;
    private BigDecimal diffFact1Min = BigDecimal.valueOf(10000);
    private BigDecimal diffFact1Max = BigDecimal.ZERO;
    private BigDecimal diffFact2Min = BigDecimal.valueOf(10000);
    private BigDecimal diffFact2Max = BigDecimal.ZERO;
    private BigDecimal comMin = BigDecimal.valueOf(10000);
    private BigDecimal comMax = BigDecimal.ZERO;
    private BigDecimal com1 = BigDecimal.ZERO;
    private BigDecimal com1Min = BigDecimal.valueOf(10000);
    private BigDecimal com1Max = BigDecimal.ZERO;
    private BigDecimal com2 = BigDecimal.ZERO;
    private BigDecimal com2Min = BigDecimal.valueOf(10000);
    private BigDecimal com2Max = BigDecimal.ZERO;

    private BigDecimal cumCom1 = BigDecimal.ZERO;
    private BigDecimal cumCom2 = BigDecimal.ZERO;

    private int counter1 = 0;
    private int counter2 = 0;

    Disposable schdeduleUpdateBorders;

    private Instant previousEmitTime = Instant.now();

    private Boolean isReadyForTheArbitrage = true;
    private Disposable theTimer;
    private Disposable theCheckBusyTimer;

    private FlagOpenOrder flagOpenOrder = new FlagOpenOrder();
    private OpenPrices openPrices = new OpenPrices();
    private OpenPrices openDiffs = new OpenPrices();

    public FlagOpenOrder getFlagOpenOrder() {
        return flagOpenOrder;
    }

    public OpenPrices getOpenPrices() {
        return openPrices;
    }

    public void init(TwoMarketStarter twoMarketStarter) {
        this.firstMarketService = twoMarketStarter.getFirstMarketService();
        this.secondMarketService = twoMarketStarter.getSecondMarketService();
        startArbitrageMonitoring();
        scheduleRecalculateBorders();
        initArbitrageStateListener();
    }

    private void initArbitrageStateListener() {
        firstMarketService.getEventBus().toObserverable()
                .subscribe(btsEvent -> {
                    if (btsEvent == BtsEvent.MARKET_FREE) {
                        if (!secondMarketService.isArbitrageInProgress()) {
                            writeLogArbitrageIsDone();
                        }
                    }
                }, throwable -> logger.error("On event handling", throwable));
        secondMarketService.getEventBus().toObserverable()
                .subscribe(btsEvent -> {
                    if (btsEvent == BtsEvent.MARKET_FREE) {
                        if (!firstMarketService.isArbitrageInProgress()) {
                            writeLogArbitrageIsDone();
                        }
                    }
                }, throwable -> logger.error("On event handling", throwable));
    }

    private void writeLogArbitrageIsDone() {
        if (openPrices != null && openDiffs != null && lastDelta != null) {
            if (lastDelta.equals(DELTA1)) {
                BigDecimal deltaFact = openPrices.getDelta1Fact();
                cumDeltaFact = cumDeltaFact.add(deltaFact);
                if (cumDeltaFact.compareTo(cumDeltaFactMin) == -1) cumDeltaFactMin = cumDeltaFact;
                if (cumDeltaFact.compareTo(cumDeltaFactMax) == 1) cumDeltaFactMax = cumDeltaFact;

                BigDecimal diffFact = openDiffs.getFirstOpenPrice().add(openDiffs.getSecondOpenPrice());
                if (openDiffs.getFirstOpenPrice().compareTo(diffFact1Min) == -1)
                    diffFact1Min = openDiffs.getFirstOpenPrice();
                if (openDiffs.getFirstOpenPrice().compareTo(diffFact1Max) == 1)
                    diffFact1Max = openDiffs.getFirstOpenPrice();
                if (openDiffs.getSecondOpenPrice().compareTo(diffFact2Min) == -1)
                    diffFact2Min = openDiffs.getSecondOpenPrice();
                if (openDiffs.getSecondOpenPrice().compareTo(diffFact2Max) == 1)
                    diffFact2Max = openDiffs.getSecondOpenPrice();
                if (diffFact.compareTo(diffFactMin) == -1) diffFactMin = diffFact;
                if (diffFact.compareTo(diffFactMax) == 1) diffFactMax = diffFact;

                cumDiffFact1 = cumDiffFact1.add(openDiffs.getFirstOpenPrice());
                if (cumDiffFact1.compareTo(cumDiffFact1Min) == -1) cumDiffFact1Min = cumDiffFact1;
                if (cumDiffFact1.compareTo(cumDiffFact1Max) == 1) cumDiffFact1Max = cumDiffFact1;
                cumDiffFact2 = cumDiffFact2.add(openDiffs.getSecondOpenPrice());
                if (cumDiffFact2.compareTo(cumDiffFact2Min) == -1) cumDiffFact2Min = cumDiffFact2;
                if (cumDiffFact2.compareTo(cumDiffFact2Max) == 1) cumDiffFact2Max = cumDiffFact2;
                BigDecimal cumDiffsFact = cumDiffFact1.add(cumDiffFact2);
                if (cumDiffsFact.compareTo(cumDiffsFactMin) == -1) cumDiffsFactMin = cumDiffsFact;
                if (cumDiffsFact.compareTo(cumDiffsFactMax) == 1) cumDiffsFactMax = cumDiffsFact;

                deltasLogger.info(String.format("#%s delta1_fact=%s-%s=%s; " +
                                "cum_delta_fact=%s/%s/%s; " +
                                "diffFact=%s/%s/%s+%s/%s/%s=%s/%s/%s; " +
                                "cum_diff_fact=%s/%s/%s+%s/%s/%s=%s/%s/%s; position=%s",
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
                        cumDiffsFact, cumDiffsFactMin, cumDiffsFactMax,
                        firstMarketService.getPosition()
                ));
            } else if (lastDelta.equals(DELTA2)) {
                BigDecimal deltaFact = openPrices.getDelta2Fact();
                cumDeltaFact = cumDeltaFact.add(deltaFact);
                if (cumDeltaFact.compareTo(cumDeltaFactMin) == -1) cumDeltaFactMin = cumDeltaFact;
                if (cumDeltaFact.compareTo(cumDeltaFactMax) == 1) cumDeltaFactMax = cumDeltaFact;

                BigDecimal diffFact = openDiffs.getFirstOpenPrice().add(openDiffs.getSecondOpenPrice());
                if (openDiffs.getFirstOpenPrice().compareTo(diffFact1Min) == -1)
                    diffFact1Min = openDiffs.getFirstOpenPrice();
                if (openDiffs.getFirstOpenPrice().compareTo(diffFact1Max) == 1)
                    diffFact1Max = openDiffs.getFirstOpenPrice();
                if (openDiffs.getSecondOpenPrice().compareTo(diffFact2Min) == -1)
                    diffFact2Min = openDiffs.getSecondOpenPrice();
                if (openDiffs.getSecondOpenPrice().compareTo(diffFact2Max) == 1)
                    diffFact2Max = openDiffs.getSecondOpenPrice();
                if (diffFact.compareTo(diffFactMin) == -1) diffFactMin = diffFact;
                if (diffFact.compareTo(diffFactMax) == 1) diffFactMax = diffFact;

                cumDiffFact1 = cumDiffFact1.add(openDiffs.getFirstOpenPrice());
                if (cumDiffFact1.compareTo(cumDiffFact1Min) == -1) cumDiffFact1Min = cumDiffFact1;
                if (cumDiffFact1.compareTo(cumDiffFact1Max) == 1) cumDiffFact1Max = cumDiffFact1;
                cumDiffFact2 = cumDiffFact2.add(openDiffs.getSecondOpenPrice());
                if (cumDiffFact2.compareTo(cumDiffFact2Min) == -1) cumDiffFact2Min = cumDiffFact2;
                if (cumDiffFact2.compareTo(cumDiffFact2Max) == 1) cumDiffFact2Max = cumDiffFact2;
                BigDecimal cumDiffsFact = cumDiffFact1.add(cumDiffFact2);
                if (cumDiffsFact.compareTo(cumDiffsFactMin) == -1) cumDiffsFactMin = cumDiffsFact;
                if (cumDiffsFact.compareTo(cumDiffsFactMax) == 1) cumDiffsFactMax = cumDiffsFact;

                deltasLogger.info(String.format("#%s delta2_fact=%s-%s=%s; " +
                                "cum_delta_fact=%s/%s/%s; " +
                                "diffFact=%s/%s/%s+%s/%s/%s=%s/%s/%s; " +
                                "cum_diff_fact=%s/%s/%s+%s/%s/%s=%s/%s/%s; position=%s",
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
                        cumDiffsFact, cumDiffsFactMin, cumDiffsFactMax,
                        firstMarketService.getPosition()
                ));
            }
        }

        printSumBal();

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
                    if (firstMarketService.isArbitrageInProgress() || secondMarketService.isArbitrageInProgress()) {
                        deltasLogger.warn(String.format("#%s Warning: busy by isArbitrageInProgress for 5 min. first:%s, second:%s",
                                getCounter(),
                                firstMarketService.isArbitrageInProgress(), secondMarketService.isArbitrageInProgress()));
                    } else if (!firstMarketService.isReadyForArbitrage() || !secondMarketService.isReadyForArbitrage()) {
                        deltasLogger.warn(String.format("#%s Warning: busy for 5 min. first:isReady=%s(Orders=%s), second:isReady=%s(Orders=%s)",
                                getCounter(),
                                firstMarketService.isReadyForArbitrage(), firstMarketService.getOpenOrders().size(),
                                secondMarketService.isReadyForArbitrage(), secondMarketService.getOpenOrders().size()));
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
            synchronized (this) {
                final OrderBook firstOrderBook = firstMarketService.getOrderBook();
                final OrderBook secondOrderBook = secondMarketService.getOrderBook();

                if (firstOrderBook != null
                        && secondOrderBook != null
                        && firstMarketService.getAccountInfo() != null
                        && secondMarketService.getAccountInfo() != null) {
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

        final Wallet walletP = firstMarketService.getAccountInfo().getWallet();
        final BigDecimal btcP = walletP.getBalance(Currency.BTC).getAvailable();
        final BigDecimal usdP = walletP.getBalance(firstMarketService.getSecondCurrency()).getAvailable();
        final Wallet walletO = secondMarketService.getAccountInfo().getWallet();
        final BigDecimal btcO = walletO.getBalance(Currency.BTC).getAvailable();
        final BigDecimal usdO = walletO.getBalance(Currency.USD).getAvailable();

//            1) если delta1 >= border1, то происходит sell у poloniex и buy у okcoin
        if (border1.compareTo(BigDecimal.ZERO) != 0) {
            if (delta1.compareTo(border1) == 0 || delta1.compareTo(border1) == 1) {
                if (checkBalance(DELTA1, amount) //) {
                        && firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage()) {

                    writeLogDelta1(ask1_o, bid1_o, bid1_p, btcP, usdP, btcO, usdO);

                    lastDelta = DELTA1;
                    firstMarketService.getEventBus().send(BtsEvent.MARKET_BUSY);
                    secondMarketService.getEventBus().send(BtsEvent.MARKET_BUSY);

                    firstMarketService.placeMakerOrder(Order.OrderType.ASK, amount, bestQuotes, false);
                    secondMarketService.placeMakerOrder(Order.OrderType.BID, amount, bestQuotes, false);
                    bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
                    setTimeoutAfterStartTrading();
                } else {
                    bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.ONLY_SIGNAL);
                }
            }
        }
//            2) если delta2 >= border2, то происходит buy у poloniex и sell у okcoin
        if (border2.compareTo(BigDecimal.ZERO) != 0) {
            if (delta2.compareTo(border2) == 0 || delta2.compareTo(border2) == 1) {
                if (checkBalance(DELTA2, amount) //) {
                        && firstMarketService.isReadyForArbitrage() && secondMarketService.isReadyForArbitrage()) {

                    writeLogDelta2(ask1_o, ask1_p, bid1_o, btcP, usdP, btcO, usdO);

                    lastDelta = DELTA2;
                    firstMarketService.getEventBus().send(BtsEvent.MARKET_BUSY);
                    secondMarketService.getEventBus().send(BtsEvent.MARKET_BUSY);

                    firstMarketService.placeMakerOrder(Order.OrderType.BID, amount, bestQuotes, false);
                    secondMarketService.placeMakerOrder(Order.OrderType.ASK, amount, bestQuotes, false);
                    bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
                    setTimeoutAfterStartTrading();
                } else {
                    bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.ONLY_SIGNAL);
                }
            }
        }
        return bestQuotes;
    }

    private void writeLogDelta1(BigDecimal ask1_o, BigDecimal bid1_o, BigDecimal bid1_p, BigDecimal btcP, BigDecimal usdP, BigDecimal btcO, BigDecimal usdO) {
        deltasLogger.info("------------------------------------------");

        BigDecimal firstWalletBalance = BigDecimal.ZERO;
        if (firstMarketService.getAccountInfo() != null
                && firstMarketService.getAccountInfo().getWallet() != null
                && firstMarketService.getAccountInfo().getWallet().getBalance(BitmexAdapters.WALLET_CURRENCY) != null) {
            firstWalletBalance = firstMarketService.getAccountInfo()
                    .getWallet()
                    .getBalance(BitmexAdapters.WALLET_CURRENCY)
                    .getTotal();
        }

        cumDelta = cumDelta.add(delta1);
        if (cumDelta.compareTo(cumDeltaMin) == -1) cumDeltaMin = cumDelta;
        if (cumDelta.compareTo(cumDeltaMax) == 1) cumDeltaMax = cumDelta;
        deltasLogger.info(String.format("#%s delta1=%s-%s=%s; b1=%s; btcP=%s; usdP=%s; btcO=%s; usdO=%s; w=%s; cum_delta=%s/%s/%s",
                getCounter(),
                bid1_p.toPlainString(), ask1_o.toPlainString(),
                delta1.toPlainString(),
                border1.toPlainString(),
                btcP, usdP, btcO, usdO,
                firstWalletBalance.toPlainString(),
                cumDelta.toPlainString(),
                cumDeltaMin,
                cumDeltaMax
        ));

        com1 = bid1_p.multiply(new BigDecimal("0.075")).divide(new BigDecimal("100"),2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP);
        com2 = ask1_o.multiply(new BigDecimal("0.2")).divide(new BigDecimal("100"),2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP);

        if (com1.compareTo(com1Min) == -1) com1Min = com1;
        if (com1.compareTo(com1Max) == 1) com1Max = com1;
        if (com2.compareTo(com2Min) == -1) com2Min = com2;
        if (com2.compareTo(com2Max) == 1) com2Max = com2;
        BigDecimal com = com1.add(com2);
        if (com.compareTo(comMin) == -1) comMin = com;
        if (com.compareTo(comMax) == 1) comMax = com;
        cumCom1 = cumCom1.add(com1);
        cumCom2 = cumCom2.add(com2);
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

        printSumBal(ask1_o, bid1_o, btcO, usdO, firstWalletBalance);

        counter1++;
        deltasLogger.info(String.format("#%s count=%s+%s=%s", getCounter(), counter1, counter2, counter1 + counter2));
    }

    private void printSumBal() {
        final OrderBook firstOrderBook = secondMarketService.getOrderBook();
        BigDecimal ask1_o = BigDecimal.ZERO;
        BigDecimal bid1_o = BigDecimal.ZERO;
        if (firstOrderBook != null
                && firstOrderBook.getAsks().size() > 1) {
            ask1_o = Utils.getBestAsks(firstOrderBook.getAsks(), 1).get(0).getLimitPrice();
            bid1_o = Utils.getBestBids(firstOrderBook.getBids(), 1).get(0).getLimitPrice();
        }

        BigDecimal firstWalletBalance = BigDecimal.ZERO;
        if (firstMarketService.getAccountInfo() != null
                && firstMarketService.getAccountInfo().getWallet() != null
                && firstMarketService.getAccountInfo().getWallet().getBalance(BitmexAdapters.WALLET_CURRENCY) != null) {
            firstWalletBalance = firstMarketService.getAccountInfo()
                    .getWallet()
                    .getBalance(BitmexAdapters.WALLET_CURRENCY)
                    .getTotal();
        }

        final Wallet walletO = secondMarketService.getAccountInfo().getWallet();
        final BigDecimal btcO = walletO.getBalance(Currency.BTC).getAvailable();
        final BigDecimal usdO = walletO.getBalance(Currency.USD).getAvailable();

        printSumBal(ask1_o, bid1_o, btcO, usdO, firstWalletBalance);
    }

    private void printSumBal(BigDecimal ask1_o, BigDecimal bid1_o, BigDecimal btcO, BigDecimal usdO, BigDecimal firstWalletBalance) {
        //sum_bal = wallet_b + btc_o + usd_o / ask1 , где bu типа double задаем с ui
        BigDecimal sumBal = firstWalletBalance.add(btcO).add(usdO.divide(ask1_o, 8, BigDecimal.ROUND_HALF_UP));
        BigDecimal sumBalUsd = sumBal.multiply(ask1_o).setScale(4, BigDecimal.ROUND_HALF_UP);
        //sum_bal = wallet_b + btc_o + usd_o / bu , где bu типа double задаем с ui
        BigDecimal bu = (buValue.compareTo(BigDecimal.ZERO) == 0) ? ask1_o : buValue;
        BigDecimal sumBal2 = BigDecimal.ZERO;
        BigDecimal sumBalUsd2 = BigDecimal.ZERO;
        if (buValue.compareTo(BigDecimal.ZERO) != 0) {
            sumBal2 = firstWalletBalance.add(btcO).add(usdO.divide(bu, 8, BigDecimal.ROUND_HALF_UP));
            sumBalUsd2 = sumBal2.multiply(bu).setScale(4, BigDecimal.ROUND_HALF_UP);
        }
        BigDecimal sumBalUsd3 = firstWalletBalance.multiply(bu).add(
                btcO.multiply(bid1_o)
        ).add(usdO).setScale(4, BigDecimal.ROUND_HALF_UP);
        BigDecimal sumBalUsd4 = firstWalletBalance.add(btcO).add(usdO.divide(ask1_o, 8, BigDecimal.ROUND_HALF_UP))
                .multiply(bu).setScale(4, BigDecimal.ROUND_HALF_UP);
        deltasLogger.info(String.format("#%s sum_bal=%s+%s+%s/%s (%s)=%sb=%sb=%s$=%s$=%s$=%s$; position=%s",
                getCounter(),
                firstWalletBalance,
                btcO,
                usdO,
                bu,
                ask1_o,
                sumBal,
                sumBal2,
                sumBalUsd,
                sumBalUsd2,
                sumBalUsd3,
                sumBalUsd4,
                firstMarketService.getPosition()
        ));
    }

    private void writeLogDelta2(BigDecimal ask1_o, BigDecimal ask1_p, BigDecimal bid1_o, BigDecimal btcP, BigDecimal usdP, BigDecimal btcO, BigDecimal usdO) {
        deltasLogger.info("------------------------------------------");

        BigDecimal firstWalletBalance = BigDecimal.ZERO;
        if (firstMarketService.getAccountInfo() != null
                && firstMarketService.getAccountInfo().getWallet() != null
                && firstMarketService.getAccountInfo().getWallet().getBalance(BitmexAdapters.WALLET_CURRENCY) != null) {
            firstWalletBalance = firstMarketService.getAccountInfo()
                    .getWallet()
                    .getBalance(BitmexAdapters.WALLET_CURRENCY)
                    .getTotal();
        }

        cumDelta = cumDelta.add(delta2);
        if (cumDelta.compareTo(cumDeltaMin) == -1) cumDeltaMin = cumDelta;
        if (cumDelta.compareTo(cumDeltaMax) == 1) cumDeltaMax = cumDelta;
        deltasLogger.info(String.format("#%s delta2=%s-%s=%s; b2=%s; btcP=%s; usdP=%s; btcO=%s; usdO=%s; w=%s; cum_delta=%s/%s/%s",
                getCounter(),
                bid1_o.toPlainString(), ask1_p.toPlainString(),
                delta2.toPlainString(),
                border2.toPlainString(),
                btcP, usdP, btcO, usdO,
                firstWalletBalance,
                cumDelta.toPlainString(),
                cumDeltaMin,
                cumDeltaMax
        ));

        com1 = ask1_p.multiply(new BigDecimal("0.075")).divide(new BigDecimal("100"),2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP);
        com2 = bid1_o.multiply(new BigDecimal("0.2")).divide(new BigDecimal("100"),2, BigDecimal.ROUND_HALF_UP).setScale(2, BigDecimal.ROUND_HALF_UP);

        if (com1.compareTo(com1Min) == -1) com1Min = com1;
        if (com1.compareTo(com1Max) == 1) com1Max = com1;
        if (com2.compareTo(com2Min) == -1) com2Min = com2;
        if (com2.compareTo(com2Max) == 1) com2Max = com2;
        BigDecimal com = com1.add(com2);
        if (com.compareTo(comMin) == -1) comMin = com;
        if (com.compareTo(comMax) == 1) comMax = com;
        cumCom1 = cumCom1.add(com1);
        cumCom2 = cumCom2.add(com2);
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

        printSumBal(ask1_o, bid1_o, btcO, usdO, firstWalletBalance);

        counter2++;
        deltasLogger.info(String.format("#%s count=%s+%s=%s", getCounter(), counter1, counter2, counter1 + counter2));
    }

    private boolean checkBalance(String deltaRef, BigDecimal tradableAmount) {
        boolean affordable = false;
        if (deltaRef.equals(DELTA1)) {
            // sell p, buy o
            if (firstMarketService.isAffordable(Order.OrderType.ASK, tradableAmount)
                    && secondMarketService.isAffordable(Order.OrderType.BID, tradableAmount)) {
                affordable = true;
            }
        } else if (deltaRef.equals(DELTA2)) {
            // buy p , sell o
            if (firstMarketService.isAffordable(Order.OrderType.BID, tradableAmount)
                    && secondMarketService.isAffordable(Order.OrderType.ASK, tradableAmount)) {
                affordable = true;
            }
        }

        return affordable;
    }

    private void calcDeltas(OrderBook okCoinOrderBook, OrderBook poloniexOrderBook) {
        if (okCoinOrderBook != null && poloniexOrderBook != null
                && okCoinOrderBook.getAsks().size() > 1
                && poloniexOrderBook.getAsks().size() > 1) {
            final BigDecimal ask1_o = Utils.getBestAsks(okCoinOrderBook.getAsks(), 1).get(0).getLimitPrice();
            final BigDecimal ask1_p = Utils.getBestAsks(poloniexOrderBook.getAsks(), 1).get(0).getLimitPrice();

            final BigDecimal bid1_o = Utils.getBestBids(okCoinOrderBook.getBids(), 1).get(0).getLimitPrice();
            final BigDecimal bid1_p = Utils.getBestBids(poloniexOrderBook.getBids(), 1).get(0).getLimitPrice();

            delta1 = bid1_p.subtract(ask1_o);
            delta2 = bid1_o.subtract(ask1_p);
        }
    }

    public void scheduleRecalculateBorders() {
        if (schdeduleUpdateBorders != null && !schdeduleUpdateBorders.isDisposed()) {
            schdeduleUpdateBorders.dispose();
        }

        schdeduleUpdateBorders = Completable.timer(periodSec, TimeUnit.SECONDS, Schedulers.computation())
                .doOnComplete(() -> {
                    recalculateBorders();
                    scheduleRecalculateBorders();
                })
                .subscribe();
    }

    public void recalculateBorders() {
        final BigDecimal two = new BigDecimal(2);
        if (sumDelta.compareTo(BigDecimal.ZERO) != 0) {
            if (delta1.compareTo(delta2) == 1) {
//            border1 = (abs(delta1) + abs(delta2)) / 2 + sum_delta / 2;
//            border2 = -((abs(delta1) + abs(delta2)) / 2 - sum_delta / 2);
                border1 = ((delta1.abs().add(delta2.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .add(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP));
                border2 = ((delta1.abs().add(delta2.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .subtract(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP)).negate();
            } else {
//            border1 = -(abs(delta1) + abs(delta2)) / 2 - sum_delta / 2;
//            border2 = abs(delta1) + abs(delta2)) / 2 + sum_delta / 2;
                border1 = ((delta1.abs().add(delta2.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .subtract(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP)).negate();
                border2 = ((delta1.abs().add(delta2.abs())).divide(two, 2, BigDecimal.ROUND_HALF_UP))
                        .add(sumDelta.divide(two, 2, BigDecimal.ROUND_HALF_UP));
            }
        }

    }

    public BigDecimal getDelta1() {
        return delta1;
    }

    public BigDecimal getDelta2() {
        return delta2;
    }

    public BigDecimal getBorder1() {
        return border1;
    }

    public void setBorder1(BigDecimal border1) {
        this.border1 = border1;
    }

    public BigDecimal getBorder2() {
        return border2;
    }

    public void setBorder2(BigDecimal border2) {
        this.border2 = border2;
    }

    public void setMakerDelta(BigDecimal makerDelta) {
        this.makerDelta = makerDelta;
    }

    public BigDecimal getMakerDelta() {
        return makerDelta;
    }

    public BigDecimal getSumDelta() {
        return sumDelta;
    }

    public void setSumDelta(BigDecimal sumDelta) {
        this.sumDelta = sumDelta;
    }

    public Integer getPeriodSec() {
        return periodSec;
    }

    public void setPeriodSec(Integer periodSec) {
        this.periodSec = periodSec;
        scheduleRecalculateBorders();
    }

    public BigDecimal getBuValue() {
        return buValue;
    }

    public void setBuValue(BigDecimal buValue) {
        this.buValue = buValue;
    }

    public BigDecimal getCumDelta() {
        return cumDelta;
    }

    public void setCumDelta(BigDecimal cumDelta) {
        this.cumDelta = cumDelta;
    }

    public String getLastDelta() {
        return lastDelta;
    }

    public void setLastDelta(String lastDelta) {
        this.lastDelta = lastDelta;
    }

    public OpenPrices getOpenDiffs() {
        return openDiffs;
    }

    public BigDecimal getCumDeltaFact() {
        return cumDeltaFact;
    }

    public void setCumDeltaFact(BigDecimal cumDeltaFact) {
        this.cumDeltaFact = cumDeltaFact;
    }

    public BigDecimal getCumDiffFact1() {
        return cumDiffFact1;
    }

    public void setCumDiffFact1(BigDecimal cumDiffFact1) {
        this.cumDiffFact1 = cumDiffFact1;
    }

    public BigDecimal getCumDiffFact2() {
        return cumDiffFact2;
    }

    public void setCumDiffFact2(BigDecimal cumDiffFact2) {
        this.cumDiffFact2 = cumDiffFact2;
    }

    public BigDecimal getCumCom1() {
        return cumCom1;
    }

    public void setCumCom1(BigDecimal cumCom1) {
        this.cumCom1 = cumCom1;
    }

    public BigDecimal getCumCom2() {
        return cumCom2;
    }

    public void setCumCom2(BigDecimal cumCom2) {
        this.cumCom2 = cumCom2;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public int getCounter() {
        return counter1 + counter2;
    }

    public int getCounter1() {
        return counter1;
    }

    public int getCounter2() {
        return counter2;
    }

    public void setCounter1(int counter1) {
        this.counter1 = counter1;
    }

    public void setCounter2(int counter2) {
        this.counter2 = counter2;
    }
}
