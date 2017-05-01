package com.bitplay.market.arbitrage;

import com.bitplay.TwoMarketStarter;
import com.bitplay.market.MarketService;
import com.bitplay.market.polonex.PoloniexService;
import com.bitplay.utils.Utils;

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

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Sergey Shurmin on 4/18/17.
 */
@Service
public class ArbitrageService {

    private static final Logger logger = LoggerFactory.getLogger(ArbitrageService.class);
    private static final Logger tradeLogger = LoggerFactory.getLogger("POLONIEX_TRADE_LOG");
    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");

    private TwoMarketStarter twoMarketStarter;

    //TODO rename them to first and second
    private MarketService poloniexService;
    private MarketService okCoinService;

    private BigDecimal delta1 = BigDecimal.ZERO;
    private BigDecimal delta2 = BigDecimal.ZERO;

    private BigDecimal border1 = BigDecimal.ZERO;
    private BigDecimal border2 = BigDecimal.ZERO;
    private BigDecimal makerDelta = BigDecimal.ZERO;
    private Instant previousEmitTime = Instant.now();

    public void init(TwoMarketStarter twoMarketStarter) {
        this.twoMarketStarter = twoMarketStarter;
        this.poloniexService = twoMarketStarter.getFirstMarketService();
        this.okCoinService = twoMarketStarter.getSecondMarketService();
        startArbitrageMonitoring();
    }

    private void startArbitrageMonitoring() {

        observableAccountInfo()
                .subscribeOn(Schedulers.computation())
                .subscribe(bestQuotes -> {
                    // Log not often then 5 sec
                    if (Duration.between(previousEmitTime, Instant.now()).getSeconds() > 5
                            && bestQuotes != null
                            && bestQuotes.getArbitrageEvent() != BestQuotes.ArbitrageEvent.NONE) {

                        previousEmitTime = Instant.now();
                        deltasLogger.info(bestQuotes.toString());
                    }
                });

    }

    private Observable<BestQuotes> observableAccountInfo() {
        final Observable<OrderBook> firstOrderBook = poloniexService.observeOrderBook();
        final Observable<OrderBook> secondOrderBook = okCoinService.observeOrderBook();

        return Observable.combineLatest(
                firstOrderBook
                        .filter(orderBook -> orderBook.getAsks().size() > 0),
                secondOrderBook
                        .filter(orderBook -> orderBook.getAsks().size() > 0),
                this::doComparison);
    }

    public BestQuotes doComparison(OrderBook poloniexOrderBook, OrderBook okCoinOrderBook) {
//        calcDeltas(okCoinOrderBook, poloniexOrderBook);

//        final int sizeOKCoin = okCoinService.getOpenOrders().size();
//        final int sizePoloniex = poloniexService.getOpenOrders().size();

        BestQuotes bestQuotes = null;
        if (poloniexService.getAccountInfo() != null) {
//                && sizeOKCoin == 0
//                && sizePoloniex == 0) {
            bestQuotes = calcAndDoArbitrage(okCoinOrderBook, poloniexOrderBook);
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

        final Wallet walletP = poloniexService.getAccountInfo().getWallet();
        final BigDecimal btcP = walletP.getBalance(Currency.BTC).getAvailable();
        final BigDecimal usdP = walletP.getBalance(PoloniexService.CURRENCY_PAIR_USDT_BTC.counter).getAvailable();
        final Wallet walletO = okCoinService.getAccountInfo().getWallet();
        final BigDecimal btcO = walletO.getBalance(Currency.BTC).getAvailable();
        final BigDecimal usdO = walletO.getBalance(Currency.USD).getAvailable();


        BigDecimal amount = new BigDecimal("0.01");
//            1) если delta1 >= border1, то происходит sell у poloniex и buy у okcoin
        if (border1.compareTo(BigDecimal.ZERO) != 0) {
            if (delta1.compareTo(border1) == 0 || delta1.compareTo(border1) == 1) {
                if (checkBalance("delta1", amount)) {
                    tradeLogger.info(String.format("delta1=%s-%s=%s; b1=%s; btcP=%s; usdP=%s; btcO=%s; usdO=%s",
                            bid1_p.toPlainString(), ask1_o.toPlainString(),
                            delta1.toPlainString(),
                            border1.toPlainString(),
                            btcP, usdP, btcO, usdO));
                    poloniexService.placeMakerOrder(Order.OrderType.ASK, amount, bestQuotes);
                    okCoinService.placeMakerOrder(Order.OrderType.BID, amount, bestQuotes);
                    bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
                } else {
                    bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.ONLY_SIGNAL);
                }
            }
        }
//            2) если delta2 >= border2, то происходит buy у poloniex и sell у okcoin
        if (border2.compareTo(BigDecimal.ZERO) != 0) {
            if (delta2.compareTo(border2) == 0 || delta2.compareTo(border2) == 1) {
                if (checkBalance("delta2", amount)) {
                    tradeLogger.info(String.format("delta2=%s-%s=%s; b2=%s; btcP=%s; usdP=%s; btcO=%s; usdO=%s",
                            bid1_o.toPlainString(), ask1_p.toPlainString(),
                            delta2.toPlainString(),
                            border2.toPlainString(),
                            btcP, usdP, btcO, usdO));
                    poloniexService.placeMakerOrder(Order.OrderType.BID, amount, bestQuotes);
                    okCoinService.placeMakerOrder(Order.OrderType.ASK, amount, bestQuotes);
                    bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
                } else {
                    bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.ONLY_SIGNAL);
                }
            }
        }
        return bestQuotes;
    }

    private boolean checkBalance(String deltaRef, BigDecimal tradableAmount) {
        final Wallet walletP = poloniexService.getAccountInfo().getWallet();
        final BigDecimal btcP = walletP.getBalance(Currency.BTC).getAvailable();
        final BigDecimal usdP = walletP.getBalance(PoloniexService.CURRENCY_PAIR_USDT_BTC.counter).getAvailable();
        final Wallet walletO = okCoinService.getAccountInfo().getWallet();
        final BigDecimal btcO = walletO.getBalance(Currency.BTC).getAvailable();
        final BigDecimal usdO = walletO.getBalance(Currency.USD).getAvailable();

        boolean affordable = false;
        if (deltaRef.equals("delta1")) {
            // sell p, buy o

            // Only poloniex need to check the first item.
            final BigDecimal bestBidP = Utils.getBestBids(poloniexService.getOrderBook().getBids(), 1).get(0).getLimitPrice();

            if ((btcP.compareTo(tradableAmount) == -1 || bestBidP.compareTo(tradableAmount) == -1)
                    || usdO.compareTo(okCoinService.getTotalPriceOfAmountToBuy(tradableAmount)) == -1) {
                affordable = false;
            } else {
                affordable = true;
            }
        } else if (deltaRef.equals("delta2")) {
            // sell o, buy c

            // Only poloniex need to check the first item.
            final BigDecimal bestAskP = Utils.getBestAsks(poloniexService.getOrderBook().getAsks(), 1).get(0).getLimitPrice();

            if (btcO.compareTo(tradableAmount) == -1 || bestAskP.compareTo(tradableAmount) == -1
                    || usdP.compareTo(poloniexService.getTotalPriceOfAmountToBuy(tradableAmount)) == -1) {
                affordable = false;
            } else {
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
}
