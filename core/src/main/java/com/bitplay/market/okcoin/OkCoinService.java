package com.bitplay.market.okcoin;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.MarketService;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.utils.Utils;

import info.bitrich.xchangestream.core.dto.PositionInfo;
import info.bitrich.xchangestream.okex.OkExStreamingExchange;
import info.bitrich.xchangestream.okex.OkExStreamingMarketDataService;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.ContractIndex;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.okcoin.FuturesContract;
import org.knowm.xchange.okcoin.dto.trade.OkCoinPosition;
import org.knowm.xchange.okcoin.dto.trade.OkCoinPositionResult;
import org.knowm.xchange.okcoin.service.OkCoinFuturesTradeService;
import org.knowm.xchange.okcoin.service.OkCoinTradeService;
import org.knowm.xchange.okcoin.service.OkCoinTradeServiceRaw;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import rx.Completable;

/**
 * Created by Sergey Shurmin on 3/21/17.
 */
@Service("okcoin")
public class OkCoinService extends MarketService {

    private static final Logger logger = LoggerFactory.getLogger(OkCoinService.class);
    private static final Logger tradeLogger = LoggerFactory.getLogger("OKCOIN_TRADE_LOG");

    private final static CurrencyPair CURRENCY_PAIR_BTC_USD = new CurrencyPair("BTC", "USD");

    private static final BigDecimal OKCOIN_STEP = new BigDecimal("0.01");
    private final static String NAME = "okcoin";

    ArbitrageService arbitrageService;

    @Autowired
    public void setArbitrageService(ArbitrageService arbitrageService) {
        this.arbitrageService = arbitrageService;
    }

    @Override
    public ArbitrageService getArbitrageService() {
        return arbitrageService;
    }

    private OkExStreamingExchange exchange;

    private Disposable orderBookSubscription;
    private Disposable privateDataSubscription;
    private Disposable accountInfoSubscription;
    private Disposable futureIndexSubscription;
    private Observable<OrderBook> orderBookObservable;
//    private Map<String, Disposable> orderSubscriptions = new HashMap<>();

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
        this.usdInContract = 100;
        exchange = initExchange(key, secret);

        initWebSocketAndAllSubscribers();
    }

    private void initWebSocketAndAllSubscribers() {
        initWebSocketConnection();

        createOrderBookObservable();
        subscribeOnOrderBook();

//        startTradesListener(); // to remove openOrders

        privateDataSubscription = startPrivateDataListener();
        accountInfoSubscription = startAccountInfoSubscription();
        futureIndexSubscription = startFutureIndexListener();

        fetchOpenOrdersWithDelay();

//        fetchAccountInfo();
//        accountInfo = fetchAccountInfoViaWebsocket();
        fetchPosition();
    }

    private void createOrderBookObservable() {
        orderBookObservable = ((OkExStreamingMarketDataService) exchange.getStreamingMarketDataService())
                .getOrderBook(CurrencyPair.BTC_USD,
                        OkExStreamingMarketDataService.Tool.BTC,
                        FuturesContract.ThisWeek,
                        OkExStreamingMarketDataService.Depth.DEPTH_20)
                .doOnDispose(() -> logger.info("okcoin subscription doOnDispose"))
                .doOnTerminate(() -> logger.info("okcoin subscription doOnTerminate"))
                .doOnError(throwable -> logger.error("okcoin onError orderBook", throwable))
                .retryWhen(throwableObservable -> throwableObservable.delay(5, TimeUnit.SECONDS))
                .share();
    }

    private OkExStreamingExchange initExchange(String key, String secret) {
        ExchangeSpecification spec = new ExchangeSpecification(OkExStreamingExchange.class);
        spec.setApiKey(key);
        spec.setSecretKey(secret);

        spec.setExchangeSpecificParametersItem("Use_Intl", true);
        spec.setExchangeSpecificParametersItem("Use_Futures", true);
        spec.setExchangeSpecificParametersItem("Futures_Contract", FuturesContract.ThisWeek);

        return (OkExStreamingExchange) ExchangeFactory.INSTANCE.createExchange(spec);
    }

    private void initWebSocketConnection() {
        // Connect to the Exchange WebSocket API. Blocking wait for the connection.
        exchange.connect().blockingAwait();

        // Retry on disconnect. (It's disconneced each 5 min)
        exchange.onDisconnect().doOnComplete(() -> {
            logger.warn("onClientDisconnect okCoinService");
            initWebSocketAndAllSubscribers();
        }).subscribe();
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
                    this.bestAsk = bestAsk != null ? bestAsk.getLimitPrice() : BigDecimal.ZERO;
                    this.bestBid = bestBid != null ? bestBid.getLimitPrice() : BigDecimal.ZERO;
                    logger.debug("ask: {}, bid: {}", this.bestAsk, this.bestBid);
                    this.orderBook = orderBook;

                    CompletableFuture.runAsync(this::checkOpenOrdersForMoving)
                            .exceptionally(throwable -> {
                                logger.error("OnCheckOpenOrders", throwable);
                                return null;
                            });


                }, throwable -> logger.error("ERROR in getting order book: ", throwable));
    }

    @Override
    public Logger getTradeLogger() {
        return tradeLogger;
    }

    @Override
    public Observable<OrderBook> getOrderBookObservable() {
        return orderBookObservable;
    }

    @PreDestroy
    public void preDestroy() {
        // Unsubscribe from data order book.
        orderBookSubscription.dispose();
//        orderSubscriptions.forEach((s, disposable) -> disposable.dispose());
        privateDataSubscription.dispose();
        accountInfoSubscription.dispose();
        futureIndexSubscription.dispose();

        // Disconnect from exchange (non-blocking)
        exchange.disconnect().subscribe(() -> logger.info("Disconnected from the Exchange"));
    }

    @Override
    protected BigDecimal getMakerPriceStep() {
        return OKCOIN_STEP;
    }

    @Override
    protected BigDecimal getMakerDelta() {
        return arbitrageService.getParams().getMakerDelta();
    }

    public String fetchCurrencies() {
        final List<CurrencyPair> exchangeSymbols = exchange.getExchangeSymbols();
        final String toString = Arrays.toString(exchangeSymbols.toArray());
        logger.info(toString);
        return toString;
    }

    public AccountInfo fetchAccountInfo() {
        try {
            accountInfo = exchange.getAccountService().getAccountInfo(); // only available wallet
        } catch (IOException e) {
            logger.error("AccountInfo error", e);
        }
        return accountInfo;
    }

    public void fetchPosition() {
        try {
            final OkCoinPositionResult positionResult = ((OkCoinTradeServiceRaw) exchange.getTradeService()).getFuturesPosition("btc_usd", FuturesContract.ThisWeek);
            if (positionResult.getPositions().length > 1) {
                logger.warn("More than one positions found");
                tradeLogger.warn("More than one positions found");
            }
            final OkCoinPosition okCoinPosition = positionResult.getPositions()[0];
            positionInfo = new PositionInfo(
                    okCoinPosition.getBuyAmount(),
                    okCoinPosition.getSellAmount(),
                    okCoinPosition.toString()
            );
//            tradeLogger.info("PositionInfo(rest): " + positionInfo);

        } catch (Exception e) {
            tradeLogger.error("FetchPositionError", e);
            logger.error("FetchPositionError", e);
        }
    }

    //    @Scheduled(fixedRate = 1000 * 60 * 15) // The subscription should handle it.
    public AccountInfo fetchAccountInfoViaWebsocket() {
        AccountInfo accountInfo = null;
        try {
            accountInfo = (exchange.getStreamingAccountInfoService().getAccountInfo());
//            mergeAccountInfo(newAccountInfo);
//            logger.info(this.accountInfo.toString());
        } catch (Exception e) {
            logger.error("AccountInfo error", e);
        }
        return accountInfo;
    }
/*
    private void mergeAccountInfo(AccountInfo newAccountInfo) {
        Balance walletBalance = (this.accountInfo != null && this.accountInfo.getWallet() != null)
                ? this.accountInfo.getWallet().getBalance(OkExAdapters.WALLET_CURRENCY)
                : new Balance(OkExAdapters.WALLET_CURRENCY, BigDecimal.ZERO);

        Balance pLongBalance = (this.accountInfo != null && this.accountInfo.getWallet() != null)
                ? this.accountInfo.getWallet().getBalance(OkExAdapters.POSITION_LONG_CURRENCY)
                : new Balance(OkExAdapters.POSITION_LONG_CURRENCY, BigDecimal.ZERO);
        Balance pShortBalance = (this.accountInfo != null && this.accountInfo.getWallet() != null)
                ? this.accountInfo.getWallet().getBalance(OkExAdapters.POSITION_SHORT_CURRENCY)
                : new Balance(OkExAdapters.POSITION_SHORT_CURRENCY, BigDecimal.ZERO);

        if (newAccountInfo.getWallet().getBalance(OkExAdapters.WALLET_CURRENCY).getTotal().compareTo(BigDecimal.ZERO) != 0) {
            walletBalance = newAccountInfo.getWallet().getBalance(OkExAdapters.WALLET_CURRENCY);
        }
        if (newAccountInfo.getWallet().getBalance(OkExAdapters.POSITION_LONG_CURRENCY).getTotal().compareTo(BigDecimal.ZERO) != 0) {
            pLongBalance = newAccountInfo.getWallet().getBalance(OkExAdapters.POSITION_LONG_CURRENCY);
        }
        if (newAccountInfo.getWallet().getBalance(OkExAdapters.POSITION_SHORT_CURRENCY).getTotal().compareTo(BigDecimal.ZERO) != 0) {
            pShortBalance = newAccountInfo.getWallet().getBalance(OkExAdapters.POSITION_SHORT_CURRENCY);
        }

        final AccountInfo resultAccountInfo = new AccountInfo(new Wallet(walletBalance, pLongBalance, pShortBalance));

        setAccountInfo(resultAccountInfo);
        logger.debug("Balance Wallet={}, Margin={}, Position={},{}",
                walletBalance.getTotal().toPlainString(),
                walletBalance.getFrozen().toPlainString(),
                pLongBalance.getFrozen().toPlainString(),
                pShortBalance.getFrozen().toPlainString());

        String walletRaw = "";
        String positionsRaw = "";
        if (walletBalance instanceof BalanceEx) {
            walletRaw = ((BalanceEx) walletBalance).getRaw();
        }
        if (pLongBalance instanceof BalanceEx) {
            positionsRaw = "Long: " + ((BalanceEx) pLongBalance).getRaw();
        }
        if (pShortBalance instanceof BalanceEx) {
            positionsRaw += "\n";
            positionsRaw += "Short: " + ((BalanceEx) pShortBalance).getRaw();
        }
        tradeLogger.info("AccountInfo:\n"
                + "Wallet: " + walletRaw
                + "\n"
                + positionsRaw
        );
    }*/

    //TODO use subscribing on open orders
    @Scheduled(fixedRate = 1000 * 60 * 15)
    public void fetchOpenOrdersWithDelay() {
        Completable.timer(2000, TimeUnit.MILLISECONDS)
                .doOnError(throwable -> logger.error("onError fetchOOWithDelay", throwable))
                .doOnCompleted(() -> {
                    fetchOpenOrders(); // Synchronous
//                    if (openOrders.size() == 0) {
//                        eventBus.send(BtsEvent.MARKET_FREE);
//                    }
                })
                .retry(3)
                .subscribe();
//        this.fetchOpenOrders();
    }

    /*
        private Disposable startOrderListener(String orderId) {
            return exchange.getStreamingTradingService()
                    .getOpenOrderObservable("btc_usd", orderId)
                    .doOnError(throwable -> logger.error("onOrder onError", throwable))
                    .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                    .subscribeOn(Schedulers.io())
                    .subscribe(updatedOrder -> {
                        logger.info("Order update: " + updatedOrder.toString());
                        this.openOrders = this.openOrders.stream()
                                .map(existingInMemory -> {
                                    // merge if the update of an existingInMemory
                                    LimitOrder order = existingInMemory;
                                    final Optional<LimitOrder> optionalMatch = updatedOrder.getOpenOrders().stream()
                                            .filter(existing -> existingInMemory.getId().equals(existing.getId()))
                                            .findFirst();
                                    if (optionalMatch.isPresent()) {
                                        order.getCumulativeAmount();
                                        order = optionalMatch.get();
                                        logger.info("Order has been updated: " + order.toString());
                                    }
    //                                final List<LimitOrder> optionalOrder = new ArrayList<>();
    //                                if (order.getStatus() != Order.OrderStatus.CANCELED
    //                                        && order.getStatus() != Order.OrderStatus.EXPIRED
    //                                        && order.getStatus() != Order.OrderStatus.FILLED
    //                                        && order.getStatus() != Order.OrderStatus.REJECTED
    //                                        && order.getStatus() != Order.OrderStatus.REPLACED
    //                                        && order.getStatus() != Order.OrderStatus.STOPPED) {
    //                                    optionalOrder.add(order);
    //                                } else {
    //                                    orderSubscriptions.computeIfPresent(orderId, (s, disposable) -> {
    //                                        disposable.dispose();
    //                                        return disposable;
    //                                    });
    //                                    orderSubscriptions.remove(orderId);
    //                                }
                                    if (order.getStatus() == Order.OrderStatus.CANCELED
                                            || order.getStatus() == Order.OrderStatus.EXPIRED
                                            || order.getStatus() == Order.OrderStatus.FILLED
                                            || order.getStatus() == Order.OrderStatus.REJECTED
                                            || order.getStatus() == Order.OrderStatus.REPLACED
                                            || order.getStatus() == Order.OrderStatus.STOPPED) {
                                        orderSubscriptions.computeIfPresent(orderId, (s, disposable) -> {
                                            disposable.dispose();
                                            return disposable;
                                        });
                                        orderSubscriptions.remove(orderId);
                                        logger.info("");
                                    }

                                    return order;
                                }).collect(Collectors.toList());

                    }, throwable -> {
                        logger.error("OO.Exception: ", throwable);
                    });
        }
    */
/*    private Disposable startTradesListener() { //It doesn't work
        return exchange.getStreamingMarketDataService()
                .getTrades(CurrencyPair.BTC_USD, 20)
                .doOnError(throwable -> logger.error("onTrades", throwable))
                .retryWhen(throwables -> throwables.delay(1, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(trades -> {
                    if (this.openOrders == null) {
                        this.openOrders = new ArrayList<>();
                    }
                    this.openOrders.stream()
                            .filter(limitOrder -> trades.getId().equals(limitOrder.getId()))
                            .forEach(limitOrder -> debugLog.info("Trades: " + trades.toString()));
//                    this.openOrders.removeIf(limitOrder ->
//                            trades.getId().equals(limitOrder.getId()));
                }, throwable -> logger.error("Trades.Exception: ", throwable));
    }
*/
    private Disposable startAccountInfoSubscription() {
        return exchange.getStreamingAccountInfoService()
                .accountInfoObservable()
                .doOnError(throwable -> logger.error("Error on AccountInfo.Websocket observing", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(accountInfo -> {
                    logger.info("AccountInfo.Websocket: " + accountInfo.toString());
                    this.accountInfo = accountInfo;

                }, throwable -> {
                    logger.error("AccountInfo.Websocket.Exception: ", throwable);
                });
    }

    private Disposable startPrivateDataListener() {
        return exchange.getStreamingPrivateDataService()
                .getAllPrivateDataObservable()
                .doOnError(throwable -> logger.error("Error on PrivateData observing", throwable))
                .retryWhen(throwables -> throwables.delay(5, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(privateData -> {
                    logger.debug(privateData.toString());
                    if (privateData.getAccountInfo() != null) {
                        exchange.getStreamingAccountInfoService()
                                .requestAccountInfo();
                    }
                    if (privateData.getPositionInfo() != null) {
                        positionInfo = privateData.getPositionInfo();
                    }
                    if (privateData.getTrades() != null) {
                        updateOpenOrders(privateData.getTrades());
                    }
                }, throwable -> {
                    logger.error("PrivateData.Exception: ", throwable);
                });
    }

    private Disposable startFutureIndexListener() {
        return ((OkExStreamingMarketDataService) exchange.getStreamingMarketDataService())
                .getFutureIndex()
                .doOnError(throwable -> logger.error("Error on FutureIndex observing", throwable))
                .retryWhen(throwables -> throwables.delay(10, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(futureIndex -> {
                    logger.debug(futureIndex.toString());
                    this.contractIndex = new ContractIndex(futureIndex.getIndex(),
                            futureIndex.getTimestamp());
                }, throwable -> {
                    logger.error("FutureIndex.Exception: ", throwable);
                });
    }

    private void updateOpenOrders(List<LimitOrder> trades) {

        // Replace all existing with new info
        this.openOrders = this.openOrders.stream()
                .map(existingInMemory -> {
                    // merge if the update of an existingInMemory
                    LimitOrder order = existingInMemory;
                    final Optional<LimitOrder> optionalMatch = trades.stream()
                            .filter(existing -> existingInMemory.getId().equals(existing.getId()))
                            .findFirst();
                    if (optionalMatch.isPresent()) {
                        order = optionalMatch.get();
                        tradeLogger.info("Order update:id={},status={},amount={},filled={}",
                                order.getId(), order.getStatus(), order.getTradableAmount(),
                                order.getCumulativeAmount());
                    }

                    return order;
                }).collect(Collectors.toList());

        // Add new orders
        List<LimitOrder> newOrders = trades.stream()
                .filter(order -> order.getStatus() != Order.OrderStatus.CANCELED
                        && order.getStatus() != Order.OrderStatus.EXPIRED
                        && order.getStatus() != Order.OrderStatus.FILLED
                        && order.getStatus() != Order.OrderStatus.REJECTED
                        && order.getStatus() != Order.OrderStatus.REPLACED
                        && order.getStatus() != Order.OrderStatus.PENDING_CANCEL
                        && order.getStatus() != Order.OrderStatus.PENDING_REPLACE
                        && order.getStatus() != Order.OrderStatus.STOPPED)
                .filter((LimitOrder limitOrder) -> {
                    final String id = limitOrder.getId();
                    return this.openOrders.stream()
                            .anyMatch(existing -> id.equals(existing.getId()));
                })
                .collect(Collectors.toList());

        debugLog.info("NewOrders: " + Arrays.toString(newOrders.toArray()));
        this.openOrders.addAll(newOrders);
    }

    public OrderBook fetchOrderBook() {
        try {
            orderBook = exchange.getMarketDataService().getOrderBook(CURRENCY_PAIR_BTC_USD);

            bestBid = Utils.getBestBids(getOrderBook(), 1).get(0).getLimitPrice();
            bestAsk = Utils.getBestAsks(getOrderBook(), 1).get(0).getLimitPrice();

            logger.info("Fetched orderBook: {} asks, {} bids. Timestamp {}", orderBook.getAsks().size(), orderBook.getBids().size(),
                    orderBook.getTimeStamp());
        } catch (Exception e) {
            logger.error("fetchOrderBook error", e);
        }
        return orderBook;
    }

    @Override
    public OrderBook getOrderBook() {
        return orderBook;
    }

    public String placeTakerOrder(Order.OrderType orderType, BigDecimal amount) {
        String orderId = "Not implemented yet";
//        try {
//            final TradeService tradeService = exchange.getTradeService();
//            BigDecimal tradingDigit;
//            BigDecimal theBestPrice;
//
//            if (orderType.equals(Order.OrderType.BID)) {
//                // The price is to total amount you want to buy, and it must be higher than the current price of 0.01 BTC
//                tradingDigit = getTotalPriceOfAmountToBuy(amount);
//                theBestPrice = bestAsk;
//            } else { // orderType.equals(Order.OrderType.ASK)
//                tradingDigit = amount;
//                theBestPrice = bestBid;
//            }
//
////          TODO  Place unclear logic to BitplayOkCoinTradeService.placeTakerOrder()
//            final MarketOrder marketOrder = new MarketOrder(orderType,
//                    tradingDigit,
//                    CURRENCY_PAIR_BTC_USD, new Date());
//            orderId = tradeService.placeMarketOrder(marketOrder);
//
////            final Order successfulOrder = fetchOrderInfo(orderId);
//
//            // TODO save trading history into DB
//            tradeLogger.info("taker {} amount={} with theBestPrice={}",
//                    orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
//                    amount.toPlainString(),
//                    theBestPrice);
//
//        } catch (Exception e) {
//            logger.error("Place market order error", e);
//            orderId = e.getMessage();
//        }
        return orderId;
    }

    @Override
    public boolean isAffordable(Order.OrderType orderType, BigDecimal tradableAmount) {
        boolean isAffordable = false;

        if (accountInfo != null && accountInfo.getWallet() != null) {
            final AccountInfo accountInfo = getAccountInfo();
            Wallet wallet = accountInfo.getWallet();
            final Balance balance = wallet.getBalance(Currency.BTC);
            final BigDecimal availableBalance = balance.getAvailable();
            if (availableBalance.signum() > 0) {
                isAffordable = availableBalance.compareTo(tradableAmount) != -1;
            }
        }

        return isAffordable;
    }

    @Override
    public TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes,
                                         SignalType signalType) {
        return placeMakerOrder(orderType, amountInContracts, bestQuotes, false, signalType);
    }

    private BigDecimal convertBtcToContracts(BigDecimal amountInBtc) {

        final Instant timeEdge = Instant.now().minusSeconds(30 * 1000); // 30 sec
        if (contractIndex.getIndexPrice().signum() == 0) {
            tradeLogger.info("ERROR: contractIndex is zero");
            throw new IllegalStateException("ERROR: contractIndex is zero");
        }
        if (contractIndex.getTimestamp().toInstant().isBefore(timeEdge)) {
            tradeLogger.info("WARNING: contractIndex has not been updated more than 30 sec");
        }
        // vol_cont = round(cur_vol_btc * cur_index_price / 100; 0)
        BigDecimal amountInContracts = amountInBtc.multiply(contractIndex.getIndexPrice())
                .divide(BigDecimal.valueOf(100), 0, BigDecimal.ROUND_HALF_UP);

        if (amountInContracts.signum() == 0) {
            amountInContracts = BigDecimal.ONE;
        }
        return amountInContracts;
    }

    private TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes,
                                          boolean isMoving, SignalType signalType) {
        final TradeResponse tradeResponse = new TradeResponse();
        try {
            arbitrageService.setSignalType(signalType);

            BigDecimal thePrice;

            thePrice = createBestMakerPrice(orderType, false)
                    .setScale(2, BigDecimal.ROUND_HALF_UP);

            BigDecimal tradeableAmount = adjustAmount(amount);

            /* TODO update balance right after cancelling before enable the following code.
             for now, we use response error message 'Insuficient coins(fonds)'
            if (!isAffordable(orderType, tradeableAmount)) {
                
                final Wallet wallet = getAccountInfo().getWallet();
                final BigDecimal btcBalance = wallet.getBalance(Currency.BTC).getAvailable();
                final BigDecimal usdBalance = wallet.getBalance(getSecondCurrency()).getAvailable();

                tradeResponse.setErrorMessage(String.format("Not enough money left. Type:%s;amont:%s;bal_btc:%s;bal_usd:%s",
                        orderType.toString(), tradeableAmount.toPlainString(),
                        btcBalance, usdBalance));

            } else
            */
            if (thePrice.compareTo(BigDecimal.ZERO) == 0) {
                tradeResponse.setErrorMessage("The new price is 0 ");
            } else if (tradeableAmount.compareTo(BigDecimal.ZERO) == 0) {

                tradeResponse.setErrorMessage("Not enough amount left. amount=" + tradeableAmount.toPlainString());

            } else {
                // USING REST API
                orderType = adjustOrderType(orderType, tradeableAmount);

                final LimitOrder limitOrder = new LimitOrder(orderType,
                        tradeableAmount, CURRENCY_PAIR_BTC_USD, "123", new Date(),
                        thePrice);

                String orderId = exchange.getTradeService().placeLimitOrder(limitOrder);
                tradeResponse.setOrderId(orderId);

                // USING WEBSOCKET API
//                ContractOrderType contractOrderType;
//                if (orderType == Order.OrderType.BID) {
//                    contractOrderType = ContractOrderType.OPEN_LONG_POSITION_BUY;
//                } else {
//                    contractOrderType = ContractOrderType.OPEN_SHORT_POSITION_SELL;
//                }
//
//                final OkExStreamingTradingService tradingService = (OkExStreamingTradingService) exchange.getStreamingTradingService();
//                String orderId = tradingService.placeContractOrder("btc_usd",
//                        FuturesContract.ThisWeek,
//                        thePrice, tradeableAmount,
//                        contractOrderType);
//                tradeResponse.setOrderId(orderId);

                String diffWithSignal = "";
                if (bestQuotes != null) {
                    final BigDecimal diff1 = bestQuotes.getAsk1_o().subtract(thePrice);
                    final BigDecimal diff2 = thePrice.subtract(bestQuotes.getBid1_o());
                    diffWithSignal = orderType.equals(Order.OrderType.BID)
                            ? String.format("diff1_buy_o = ask_o[1] - order_price_buy_o = %s", diff1.toPlainString()) //"BUY"
                            : String.format("diff2_sell_o = order_price_sell_o - bid_o[1] = %s", diff2.toPlainString()); //"SELL"
                    arbitrageService.getOpenDiffs().setSecondOpenPrice(orderType.equals(Order.OrderType.BID)
                            ? diff1 : diff2);
                }
                tradeLogger.info("#{} {} {} amount={} with quote={} was placed.orderId={}. {}",
                        signalType == SignalType.AUTOMATIC ? arbitrageService.getCounter() : signalType.getCounterName(),
                        isMoving ? "Moved" : "maker",
                        orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                        tradeableAmount.toPlainString(),
                        thePrice,
                        orderId,
                        diffWithSignal);

//                final Disposable orderListener = startOrderListener(orderId);
//                orderSubscriptions.put(orderId, orderListener);
                final LimitOrder limitOrderWithId = new LimitOrder(orderType,
                        tradeableAmount, CURRENCY_PAIR_BTC_USD, orderId, new Date(),
                        thePrice);
                tradeResponse.setLimitOrder(limitOrderWithId);
                if (!isMoving) {
                    // Warning java.util.ConcurrentModificationException with checkOpenOrdersForMoving()
                    // (do not use from iterate over Open orders checkOpenOrdersForMoving())
                    openOrders.add(limitOrderWithId);
                }

                if (signalType == SignalType.AUTOMATIC) {
                    arbitrageService.getOpenPrices().setSecondOpenPrice(thePrice);
                }
                orderIdToSignalInfo.put(orderId, bestQuotes);
            }

        } catch (Exception e) {
            String details = String.format("type=%s,a=%s,bestQuotes=%s,isMove=%s,signalT=%s",
                    orderType, amount, bestQuotes, isMoving, signalType);
            logger.error("Place market order error. Details: " + details, e);
            tradeLogger.info("maker error {}", e.toString() + ". Details: " + details);
            tradeResponse.setOrderId(null);
            tradeResponse.setErrorMessage(e.getMessage());
        }

        fetchOpenOrdersWithDelay();
        return tradeResponse;
    }

    private Order.OrderType adjustOrderType(Order.OrderType orderType, BigDecimal tradeableAmount) {
        BigDecimal pLongBalance = (this.positionInfo != null && this.positionInfo.getPositionLong() != null)
                ? this.positionInfo.getPositionLong()
                : BigDecimal.ZERO;
        BigDecimal pShortBalance = (this.positionInfo != null && this.positionInfo.getPositionShort() != null)
                ? this.positionInfo.getPositionShort()
                : BigDecimal.ZERO;
        Order.OrderType newOrderType = orderType;
        if (orderType == Order.OrderType.BID) { // buy - long
            if (pShortBalance.compareTo(tradeableAmount) != -1) {
                newOrderType = Order.OrderType.EXIT_ASK;
            }
        } else if (orderType == Order.OrderType.ASK) { // sell - short
            if (pLongBalance.compareTo(tradeableAmount) != -1) {
                newOrderType = Order.OrderType.EXIT_BID;
            }
        }
        return newOrderType;
    }

    private BigDecimal adjustAmount(final BigDecimal initialAmount) {
        BigDecimal amount = initialAmount.setScale(3, BigDecimal.ROUND_HALF_UP);
        if (amount.compareTo(OKCOIN_STEP) == -1) {
            amount = amount.setScale(2, BigDecimal.ROUND_HALF_UP);
        }
        if (amount.compareTo(OKCOIN_STEP) == -1) {
            amount = BigDecimal.ZERO;
        }

        if (amount.compareTo(initialAmount) != 0) {
            tradeLogger.info(String.format("Amount change %s -> %s", initialAmount.toPlainString(), amount.toPlainString()));
        }
        return amount;
    }

    private Order fetchOrderInfo(String orderId) {
        Order order = null;
        try {
            //NOT implemented yet
            final Collection<Order> orderCollection = exchange.getTradeService().getOrder(orderId);
            if (!orderCollection.isEmpty()) {
                order = orderCollection.iterator().next();
            }
        } catch (Exception e) {
            logger.error("on fetch order info by id=" + orderId, e);
        }
        return order;
    }

    @Override
    public UserTrades fetchMyTradeHistory() {
//        returnTradeHistory
        UserTrades tradeHistory = null;
        try {
            tradeHistory = exchange.getTradeService()
                    .getTradeHistory(new OkCoinTradeService.OkCoinTradeHistoryParams(
                            10, 1, CURRENCY_PAIR_BTC_USD));
        } catch (Exception e) {
            logger.info("Exception on fetchMyTradeHistory", e);
        }
        return tradeHistory;

    }

    @Override
    public TradeService getTradeService() {
        return exchange.getTradeService();
    }

    @Override
    public MoveResponse moveMakerOrder(LimitOrder limitOrder, SignalType signalType) {
        arbitrageService.setSignalType(signalType);

        // IT doesn't support moving
        // Do cancel ant place
        final OkCoinFuturesTradeService tradeService = (OkCoinFuturesTradeService) exchange.getTradeService();
        MoveResponse response;
        BestQuotes bestQuotes = orderIdToSignalInfo.get(limitOrder.getId());

        int attemptCount = 0;
        Exception lastException = null;
        boolean cancelledSuccessfully = false;
        while (attemptCount < 2) {
            attemptCount++;
            try {
                cancelledSuccessfully = tradeService.cancelOrder(limitOrder.getId());
                if (cancelledSuccessfully) {
                    break;
                }
            } catch (Exception e) {
                lastException = e;
                logger.error("{} attempt on cancel maker order", attemptCount, e);
            }
        }

        if (cancelledSuccessfully) {
            tradeLogger.info("#{} Cancelled {} amount={},quote={},id={},attempt={}",
                    signalType == SignalType.AUTOMATIC ? arbitrageService.getCounter() : signalType.getCounterName(),
                    limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                    limitOrder.getTradableAmount(),
                    limitOrder.getLimitPrice().toPlainString(),
                    limitOrder.getId(),
                    attemptCount);

            // Place order
            TradeResponse tradeResponse = new TradeResponse();
            while (attemptCount < 4) {
                attemptCount++;
                final BigDecimal newAmount = limitOrder.getTradableAmount()
                        .subtract(limitOrder.getCumulativeAmount());
                tradeResponse = placeMakerOrder(limitOrder.getType(),
                        newAmount, bestQuotes, true, signalType);
                if (tradeResponse.getErrorCode() != null && tradeResponse.getErrorCode().startsWith("Insufficient")) {
                    tradeLogger.info("#{} Failed {} amount={},quote={},id={},attempt={}. Error: {}",
                            signalType == SignalType.AUTOMATIC ? arbitrageService.getCounter() : signalType.getCounterName(),
                            limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                            limitOrder.getTradableAmount(),
                            limitOrder.getLimitPrice().toPlainString(),
                            limitOrder.getId(),
                            attemptCount,
                            tradeResponse.getErrorCode());
                }
                if (tradeResponse.getErrorCode() == null) {
                    // tradeResponse.getErrorCode().startsWith("Insufficient")) { // when amount less then affordable.
                    break;
                }
            }
            if (tradeResponse.getOrderId() != null) {
                response = new MoveResponse(MoveResponse.MoveOrderStatus.MOVED_WITH_NEW_ID, tradeResponse.getOrderId(), tradeResponse.getLimitOrder());
            } else {
                final String description = String.format("#%s Moving error. Cancelled amount %s, but %s",
                        signalType == SignalType.AUTOMATIC ? arbitrageService.getCounter() : signalType.getCounterName(),
                        limitOrder.getTradableAmount().toPlainString(), tradeResponse.getErrorCode());
                response = new MoveResponse(MoveResponse.MoveOrderStatus.ONLY_CANCEL, description);
                tradeLogger.info(description);
            }
        } else {
            String logResponse = "";
            if (lastException == null) { // For now we assume that order is filled when no Exceptions
                response = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_CLOSED, "");
                logResponse = "Already closed";
            } else {
                response = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, lastException.getMessage());
                logResponse = "Cancel failed";
            }
            final String logString = String.format("#%s %s %s amount=%s,quote=%s,id=%s,attempt=%s,lastException=%s",
                    signalType == SignalType.AUTOMATIC ? arbitrageService.getCounter() : signalType.getCounterName(),
                    logResponse,
                    limitOrder.getType() == Order.OrderType.BID ? "BUY" : "SELL",
                    limitOrder.getTradableAmount(),
                    limitOrder.getLimitPrice().toPlainString(),
                    limitOrder.getId(),
                    attemptCount,
                    lastException != null ? lastException.getMessage() : null);
            tradeLogger.info(logString);
        }
        return response;
    }

    @Override
    public String getPosition() {
        return null;
    }

}
