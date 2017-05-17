package com.bitplay.market.bitmex;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.market.MarketService;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.utils.Utils;

import info.bitrich.xchangestream.bitmex.BitmexStreamingExchange;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.utils.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import rx.Completable;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Service("bitmex")
public class BitmexService extends MarketService {
    private final static Logger logger = LoggerFactory.getLogger(BitmexService.class);

    private final static String NAME = "bitmex";

    private final static CurrencyPair CURRENCY_PAIR_XBTUSD = new CurrencyPair("XBT", "USD");

    private BitmexStreamingExchange exchange;

    private List<Long> latencyList = new ArrayList<>();

    private Observable<AccountInfo> accountInfoObservable;
    private Disposable accountInfoSubscription;

    private Observable<OrderBook> orderBookObservable;
    private Disposable orderBookSubscription;

    private ArbitrageService arbitrageService;
    @Autowired
    public void setArbitrageService(ArbitrageService arbitrageService) {
        this.arbitrageService = arbitrageService;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected Exchange getExchange() {
        return exchange;
    }

    @Override
    public void initializeMarket(String key, String secret) {
        this.exchange = initExchange(key, secret);

        initWebSocketAndAllSubscribers();

        Completable.timer(1000, TimeUnit.MILLISECONDS)
                .doOnCompleted(this::startAccountInfoListener)
                .subscribe();
    }

    private void initWebSocketAndAllSubscribers() {
        initWebSocketConnection();
        createOrderBookObservable();
        subscribeOnOrderBook();
    }

    private BitmexStreamingExchange initExchange(String key, String secret) {
        ExchangeSpecification spec = new ExchangeSpecification(BitmexStreamingExchange.class);
        spec.setApiKey(key);
        spec.setSecretKey(secret);

        //ExchangeFactory.INSTANCE.createExchange(spec); - class cast exception, because
        // bitmex-* implementations should be moved into libraries.
        return (BitmexStreamingExchange) createExchange(spec);
    }

    private Exchange createExchange(ExchangeSpecification exchangeSpecification) {

        Assert.notNull(exchangeSpecification, "exchangeSpecfication cannot be null");

        logger.debug("Creating exchange from specification");

        String exchangeClassName = exchangeSpecification.getExchangeClassName();

        // Attempt to create an instance of the exchange provider
        try {

            // Attempt to locate the exchange provider on the classpath
            Class exchangeProviderClass = Class.forName(exchangeClassName);

            // Test that the class implements Exchange
            if (Exchange.class.isAssignableFrom(exchangeProviderClass)) {
                // Instantiate through the default constructor
                Exchange exchange = (Exchange) exchangeProviderClass.newInstance();
                exchange.applySpecification(exchangeSpecification);
                return exchange;
            } else {
                throw new ExchangeException("Class '" + exchangeClassName + "' does not implement Exchange");
            }
        } catch (ClassNotFoundException e) {
            throw new ExchangeException("Problem starting exchange provider (class not found)", e);
        } catch (InstantiationException e) {
            throw new ExchangeException("Problem starting exchange provider (instantiation)", e);
        } catch (IllegalAccessException e) {
            throw new ExchangeException("Problem starting exchange provider (illegal access)", e);
        }

        // Cannot be here due to exceptions
    }

    private void initWebSocketConnection() {
        // Connect to the Exchange WebSocket API. Blocking wait for the connection.
        exchange.connect().blockingAwait();

        exchange.authenticate().blockingAwait();

        // Retry on disconnect. (It's disconneced each 5 min)
//        exchange.onDisconnect().doOnComplete(() -> {
//            logger.warn("onClientDisconnect BitmexService");
//            doDisconnect();
//            initWebSocketAndAllSubscribers();
//        }).subscribe();
    }

    private void doDisconnect() {
        exchange.disconnect();
        orderBookSubscription.dispose();
        accountInfoSubscription.dispose();
    }

    private void subscribeOnOrderBook() {
        //TODO subscribe on updates only to increase the speed
        orderBookSubscription = getOrderBookObservable()
                .subscribeOn(Schedulers.computation())
                .subscribe(orderBook -> {
                    final List<LimitOrder> bestAsks = Utils.getBestAsks(orderBook.getAsks(), 1);
                    final LimitOrder bestAsk = bestAsks.size() > 0 ? bestAsks.get(0) : null;
                    final List<LimitOrder> bestBids = Utils.getBestBids(orderBook.getBids(), 1);
                    final LimitOrder bestBid = bestBids.size() > 0 ? bestBids.get(0) : null;
                    logger.debug("ask: {}, bid: {}",
                            bestAsk != null ? bestAsk.getLimitPrice() : null,
                            bestBid != null ? bestBid.getLimitPrice() : null);
                    this.orderBook = orderBook;

//                    orderBookChangedSubject.onNext(orderBook);

                    //TODO subscribe on orderBook
//                    CompletableFuture.runAsync(() -> {
//                        checkOrderBook(orderBook);
//                    });


                }, throwable -> logger.error("ERROR in getting order book: ", throwable));
    }

    private void createOrderBookObservable() {
        orderBookObservable = exchange.getStreamingMarketDataService()
                .getOrderBook(CurrencyPair.BTC_USD, 20)
                .doOnDispose(() -> logger.info("bitmex subscription doOnDispose"))
                .doOnTerminate(() -> logger.info("bitmex subscription doOnTerminate"))
                .share();
    }

    @Override
    public Observable<OrderBook> getOrderBookObservable() {
        return orderBookObservable;
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
    protected Observable<AccountInfo> createAccountInfoObservable() {
        accountInfoObservable =
                exchange.getStreamingAccountService()
                        .getAccountInfoObservable(CurrencyPair.BTC_USD, 20)
                        .doOnDispose(() -> logger.info("bitmex subscription doOnDispose"))
                        .doOnTerminate(() -> logger.info("bitmex subscription doOnTerminate"))
                        .share();
        return accountInfoObservable;
    }

    private void startAccountInfoListener() {
        accountInfoObservable = createAccountInfoObservable();

        accountInfoSubscription = getAccountInfoObservable()
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> logger.error("Account fetch error", throwable))
                .subscribe(accountInfo1 -> {
                    setAccountInfo(accountInfo1);
                    logger.info("Balance XBt={}, Margin={}",
                            this.accountInfo.getWallet().getBalance(Currency.XBT).getAvailable().toPlainString(),
                            this.accountInfo.getWallet().getBalance(new Currency("MARGIN")).getAvailable().toPlainString());
                }, throwable -> {
                    logger.error("Can not fetchAccountInfo", throwable);
                    // schedule it again
                    sleep(5000);
                    startAccountInfoListener();
                });
    }


    public Observable<AccountInfo> getAccountInfoObservable() {
        return accountInfoObservable;
    }

    @PreDestroy
    public void preDestroy() {
        // Unsubscribe from data order book.
        accountInfoSubscription.dispose();

        orderBookSubscription.dispose();
    }

    @Override
    public AccountInfo getAccountInfo() {
        return accountInfo;
    }
}
