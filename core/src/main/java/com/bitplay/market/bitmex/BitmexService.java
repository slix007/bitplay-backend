package com.bitplay.market.bitmex;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.market.MarketService;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitmex.BitmexExchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.PreDestroy;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.swagger.client.ApiException;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Service("bitmex")
public class BitmexService extends MarketService {
    private final static Logger logger = LoggerFactory.getLogger(BitmexService.class);

    private final static String NAME = "bitmex";

    private final static CurrencyPair CURRENCY_PAIR_XBTUSD = new CurrencyPair("XBT", "USD");

    private Exchange exchange;

    private List<Long> latencyList = new ArrayList<>();

    Disposable accountInfoSubscription;

    ArbitrageService arbitrageService;

    @Autowired
    public void setArbitrageService(ArbitrageService arbitrageService) {
        this.arbitrageService = arbitrageService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void initializeMarket(String key, String secret) {
        initExchange(key, secret);

//        startAccountInfoListener();
    }

    private void initExchange(String key, String secret) {
        ExchangeSpecification spec = new ExchangeSpecification(BitmexExchange.class);
        spec.setApiKey(key);
        spec.setSecretKey(secret);
        this.exchange = ExchangeFactory.INSTANCE.createExchange(BitmexExchange.class.getName());
    }

    @Override
    public UserTrades fetchMyTradeHistory() {
        return null;
    }

    @Override
    public OrderBook getOrderBook() {
        return orderBook;
    }

    @Override
    public TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes) {
        return null;
    }

    @Override
    public TradeService getTradeService() {
        return null;
    }

    @Override
    public MoveResponse moveMakerOrder(LimitOrder limitOrder) {
        return null;
    }

    @Override
    protected BigDecimal getMakerStep() {
        return null;
    }

    @Override
    protected BigDecimal getMakerDelta() {
        return null;
    }

    @Override
    public Observable<OrderBook> observeOrderBook() {
        return Observable.create(observableOnSubscribe -> {
            while (!observableOnSubscribe.isDisposed()) {
                int sleepTime = 1000;

                try {
                    final OrderBook orderBook = fetchOrderBook();
                    if (orderBook != null) {
                        observableOnSubscribe.onNext(orderBook);
                    }
                } catch (Exception e) {
                    if (e.getCause() != null
                            && e.getCause().getMessage().startsWith("Rate limit exceeded")) {
                        sleepTime = 10000;
                    } else {
                        sleepTime = 1000;
                    }

                    observableOnSubscribe.onError(e);
                }
                sleep(sleepTime);
            }
        });
    }


    private void startAccountInfoListener() {
        accountInfoSubscription = observableAccountInfo()
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> logger.error("Account fetch error", throwable))
                .subscribe(accountInfo1 -> {
                    setAccountInfo(accountInfo1);
                    logger.info("Balance BTC={}, USD={}",
                            accountInfo.getWallet().getBalance(Currency.XBT).getAvailable().toPlainString(),
                            accountInfo.getWallet().getBalance(Currency.USD).getAvailable().toPlainString());
                }, throwable -> {
                    logger.error("Can not fetchAccountInfo", throwable);
                    // schedule it again
                    sleep(5000);
                    startAccountInfoListener();
                });
    }


    public Observable<AccountInfo> observableAccountInfo() {
        return Observable.create(observableOnSubscribe -> {
            while (!observableOnSubscribe.isDisposed()) {
                boolean noSleep = false;
                try {
                    accountInfo = exchange.getAccountService().getAccountInfo();
                    observableOnSubscribe.onNext(accountInfo);
                } catch (ExchangeException e) {
                    if (e.getMessage().startsWith("Nonce must be greater than")) {
                        noSleep = true;
                        logger.warn(e.getMessage());
                    } else {
                        observableOnSubscribe.onError(e);
                    }
                }

                if (noSleep) sleep(10);
                else sleep(2000);
            }
        });
    }

    public OrderBook fetchOrderBook() throws IOException, ExchangeException {

        final long startFetch = System.nanoTime();
        orderBook = exchange.getMarketDataService().getOrderBook(CURRENCY_PAIR_XBTUSD, 5);
        final long endFetch = System.nanoTime();

        latencyList.add(endFetch - startFetch);
        if (latencyList.size() > 100) {
            logger.debug("Average get orderBook(5) time is {} ms",
                    latencyList
                            .stream()
                            .mapToDouble(a -> a)
                            .average().orElse(0) / 1000 / 1000);
            latencyList.clear();
        }

        logger.debug("Fetched orderBook: {} asks, {} bids. Timestamp {}", orderBook.getAsks().size(), orderBook.getBids().size(),
                orderBook.getTimeStamp());

        CompletableFuture.runAsync(() -> {
            checkOrderBook(orderBook);
        });

        return orderBook;
    }

    @PreDestroy
    public void preDestroy() {
        // Unsubscribe from data order book.
        accountInfoSubscription.dispose();
    }

    @Override
    public AccountInfo getAccountInfo() {
        return new AccountInfo(new Wallet(new Balance(Currency.XBT, BigDecimal.ZERO), new Balance(Currency.USD, BigDecimal.ZERO)));
    }
}
