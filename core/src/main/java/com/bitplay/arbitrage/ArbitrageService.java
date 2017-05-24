package com.bitplay.arbitrage;

import com.bitplay.TwoMarketStarter;
import com.bitplay.market.MarketService;
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
    private BigDecimal cumDelta = new BigDecimal(0);

    Disposable schdeduleUpdateBorders;

    private Instant previousEmitTime = Instant.now();

    private Boolean isReadyForTheArbitrage = true;
    private Disposable theTimer;

    private FlagOpenOrder flagOpenOrder = new FlagOpenOrder();

    public FlagOpenOrder getFlagOpenOrder() {
        return flagOpenOrder;
    }

    public void init(TwoMarketStarter twoMarketStarter) {
        this.firstMarketService = twoMarketStarter.getFirstMarketService();
        this.secondMarketService = twoMarketStarter.getSecondMarketService();
        startArbitrageMonitoring();
        scheduleRecalculateBorders();
    }

    private void setTimeoutAfterStartTrading() {
        flagOpenOrder.setFirstReady(false);
//        flagOpenOrder.setSecondReady(false);

        isReadyForTheArbitrage = false;
        if (theTimer != null) {
            theTimer.dispose();
        }
        theTimer = Completable.timer(5, TimeUnit.SECONDS)
                .doOnComplete(() -> isReadyForTheArbitrage = true)
                .subscribe();
    }

    private void startArbitrageMonitoring() {

        observableOrderBooks()
                .subscribeOn(Schedulers.computation())
                .observeOn(Schedulers.computation())
                .doOnError(throwable -> logger.error("doOnError On combine orderBooks", throwable))
                .subscribe(bestQuotes -> {
                    // Log not often then 5 sec
                    if (Duration.between(previousEmitTime, Instant.now()).getSeconds() > 5
                            && bestQuotes != null
                            && bestQuotes.getArbitrageEvent() != BestQuotes.ArbitrageEvent.NONE) {

                        previousEmitTime = Instant.now();
//                        deltasLogger.info(bestQuotes.toString());
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

        if (isReadyForTheArbitrage && flagOpenOrder.isReady()) {
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


        BigDecimal amount = new BigDecimal("0.02");
//            1) если delta1 >= border1, то происходит sell у poloniex и buy у okcoin
        if (border1.compareTo(BigDecimal.ZERO) != 0) {
            if (delta1.compareTo(border1) == 0 || delta1.compareTo(border1) == 1) {
                if (checkBalance("delta1", amount)) {
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
                    deltasLogger.info(String.format("delta1=%s-%s=%s; b1=%s; btcP=%s; usdP=%s; btcO=%s; usdO=%s; w=%s; cum_delta=%s",
                            bid1_p.toPlainString(), ask1_o.toPlainString(),
                            delta1.toPlainString(),
                            border1.toPlainString(),
                            btcP, usdP, btcO, usdO,
                            firstWalletBalance.toPlainString(),
                            cumDelta.toPlainString()
                    ));

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
                    deltasLogger.info(String.format("sum_bal=%s+%s+%s/%s = %sb = %sb = %s$ = %s$",
                            firstWalletBalance,
                            btcO,
                            usdO,
                            bu,
                            sumBal,
                            sumBal2,
                            sumBalUsd,
                            sumBalUsd2
                    ));

                    firstMarketService.placeMakerOrder(Order.OrderType.ASK, amount, bestQuotes);
                    secondMarketService.placeMakerOrder(Order.OrderType.BID, amount, bestQuotes);
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
                if (checkBalance("delta2", amount)) {
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
                    deltasLogger.info(String.format("delta2=%s-%s=%s; b2=%s; btcP=%s; usdP=%s; btcO=%s; usdO=%s; w=%s; cum_delta=%s",
                            bid1_o.toPlainString(), ask1_p.toPlainString(),
                            delta2.toPlainString(),
                            border2.toPlainString(),
                            btcP, usdP, btcO, usdO,
                            firstWalletBalance,
                            cumDelta.toPlainString()
                    ));

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
                    deltasLogger.info(String.format("sum_bal=%s+%s+%s/%s = %sb = %sb = %s$ = %s$",
                            firstWalletBalance,
                            btcO,
                            usdO,
                            bu,
                            sumBal,
                            sumBal2,
                            sumBalUsd,
                            sumBalUsd2
                    ));

                    firstMarketService.placeMakerOrder(Order.OrderType.BID, amount, bestQuotes);
                    secondMarketService.placeMakerOrder(Order.OrderType.ASK, amount, bestQuotes);
                    bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
                    setTimeoutAfterStartTrading();
                } else {
                    bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.ONLY_SIGNAL);
                }
            }
        }
        return bestQuotes;
    }

    private boolean checkBalance(String deltaRef, BigDecimal tradableAmount) {
        boolean affordable = false;
        if (deltaRef.equals("delta1")) {
            // sell p, buy o
            if (firstMarketService.isAffordable(Order.OrderType.ASK, tradableAmount)
                    && secondMarketService.isAffordable(Order.OrderType.BID, tradableAmount)) {
                affordable = true;
            }
        } else if (deltaRef.equals("delta2")) {
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
}
