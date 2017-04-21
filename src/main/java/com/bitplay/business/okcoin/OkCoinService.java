package com.bitplay.business.okcoin;

import com.bitplay.business.BusinessService;
import com.bitplay.business.model.TradeResponse;
import com.bitplay.utils.Utils;

import info.bitrich.xchangestream.okcoin.OkCoinStreamingExchange;

import org.knowm.xchange.BaseExchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.okcoin.service.OkCoinTradeService;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.annotation.PreDestroy;

import io.reactivex.disposables.Disposable;

/**
 * Created by Sergey Shurmin on 3/21/17.
 */
@Service
public class OkCoinService implements BusinessService {

    private static final Logger logger = LoggerFactory.getLogger(OkCoinService.class);
    private static final Logger tradeLogger = LoggerFactory.getLogger("OKCOIN_TRADE_LOG");

    private static String KEY = "d4566d08-4fef-49ac-8933-e51f8c873795";
    private static String SECRET = "3DB6AD75C7CD78392947A5D4CE8567D2";

    private final static CurrencyPair CURRENCY_PAIR_BTC_USD = new CurrencyPair("BTC", "USD");

    private OkCoinStreamingExchange exchange;

    OrderBook orderBook = null;
    AccountInfo accountInfo = null;

    Disposable orderBookSubscription;

    private OkCoinStreamingExchange initExchange() {
        ExchangeSpecification spec = new ExchangeSpecification(OkCoinStreamingExchange.class);
        spec.setApiKey(KEY);
        spec.setSecretKey(SECRET);

        spec.setExchangeSpecificParametersItem("Use_Intl", true);

        exchange = (OkCoinStreamingExchange) ExchangeFactory.INSTANCE.createExchange(spec);
        String metaDataFileName = ((BaseExchange) exchange).getMetaDataFileName(spec);
        logger.info("OKCOING metaDataFileName=" + metaDataFileName);

        return exchange;
    }

    public OkCoinService() {
        init();
    }

    public void init() {
        exchange = initExchange();

        initWebSocketConnection();
    }

    private void initWebSocketConnection() {
        // Connect to the Exchange WebSocket API. Blocking wait for the connection.
        exchange.connect().blockingAwait();

        subscribeOnOrderBook();

        subscribeOnOthers();

        // Retry on disconnect. (It's disconneced each 5 min)
        exchange.onDisconnect().doOnComplete(() -> {
            logger.warn("onClientDisconnect okCoinService");
            initWebSocketConnection();
        }).subscribe();
    }

    private void subscribeOnOthers() {
        //        exchange.getStreamingMarketDataService().getTicker(CurrencyPair.BTC_USD).subscribe(ticker -> {
//            logger.info("TICKER: {}", ticker);
//        }, throwable -> logger.error("ERROR in getting ticker: ", throwable));

//        exchange.getStreamingMarketDataService().getTrades(CurrencyPair.BTC_USD).subscribe(trade -> {
//            logger.info("TRADE: {}", trade);
//        }, throwable -> logger.error("ERROR in getting trades: ", throwable));
    }

    private void subscribeOnOrderBook() {
        //TODO subscribe on updates only to increase the speed
        orderBookSubscription = exchange.getStreamingMarketDataService()
                .getOrderBook(CurrencyPair.BTC_USD, 20)
                .doOnDispose(() -> logger.info("okcoin subscription doOnDispose"))
                .doOnTerminate(() -> logger.info("okcoin subscription doOnTerminate"))
                .subscribe(orderBook -> {
                    final List<LimitOrder> bestAsks = Utils.getBestAsks(orderBook.getAsks(), 1);
                    final LimitOrder bestAsk = bestAsks.size() > 0 ? bestAsks.get(0) : null;
                    final List<LimitOrder> bestBids = Utils.getBestBids(orderBook.getBids(), 1);
                    final LimitOrder bestBid = bestBids.size() > 0 ? bestBids.get(0) : null;
                    logger.debug("ask: {}, bid: {}",
                            bestAsk != null ? bestAsk.getLimitPrice() : null,
                            bestBid != null ? bestBid.getLimitPrice() : null);
                    this.orderBook = orderBook;
                }, throwable -> logger.error("ERROR in getting order book: ", throwable));
    }

    @PreDestroy
    public void preDestroy() {
        // Unsubscribe from data order book.
        orderBookSubscription.dispose();

        // Disconnect from exchange (non-blocking)
        exchange.disconnect().subscribe(() -> logger.info("Disconnected from the Exchange"));
    }

    public String fetchCurrencies() {
        final List<CurrencyPair> exchangeSymbols = exchange.getExchangeSymbols();
        final String toString = Arrays.toString(exchangeSymbols.toArray());
        logger.info(toString);
        return toString;
    }

    public AccountInfo fetchAccountInfo() {
        try {
            accountInfo = exchange.getAccountService().getAccountInfo();
            logger.debug(accountInfo.toString());
        } catch (IOException e) {
            logger.error("AccountInfo error", e);
        }
        return accountInfo;
    }

    public AccountInfo getAccountInfo() {
        return accountInfo;
    }

    public OrderBook fetchOrderBook() {
        try {
            orderBook = exchange.getMarketDataService().getOrderBook(CURRENCY_PAIR_BTC_USD);
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
        String orderId = null;
        try {
            final TradeService tradeService = exchange.getTradeService();
            BigDecimal tradingDigit = null;
            BigDecimal theBestPrice = BigDecimal.ZERO;

            if (orderType.equals(Order.OrderType.BID)) {
                // The price is to total amount you want to buy, and it must be higher than the current price of 0.01 BTC
                tradingDigit = getTotalPriceOfAmountToBuy(amount);
                theBestPrice = Utils.getBestAsks(orderBook.getAsks(), 1).get(0).getLimitPrice();
            } else { // orderType.equals(Order.OrderType.ASK)
                tradingDigit = amount;
                theBestPrice = Utils.getBestBids(orderBook.getBids(), 1).get(0).getLimitPrice();
            }

//          TODO  Place unclear logic to BitplayOkCoinTradeService.placeTakerOrder()
            final MarketOrder marketOrder = new MarketOrder(orderType,
                    tradingDigit,
                    CURRENCY_PAIR_BTC_USD, new Date());
            orderId = tradeService.placeMarketOrder(marketOrder);

//            final Order successfulOrder = fetchOrderInfo(orderId);

            // TODO save trading history into DB
            tradeLogger.info("taker {} amount={} with theBestPrice={}",
                    orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                    amount.toPlainString(),
                    theBestPrice);

            fetchAccountInfo();
        } catch (Exception e) {
            logger.error("Place market order error", e);
            orderId = e.getMessage();
        }
        return orderId;
    }

    public TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amount) {
        final TradeResponse tradeResponse = new TradeResponse();
        try {
            final TradeService tradeService = exchange.getTradeService();
            BigDecimal thePrice;

            if (orderType.equals(Order.OrderType.BID)) {
                // The price is to total amount you want to buy, and it must be higher than the current price of 0.01 BTC
                thePrice = Utils.getBestAsks(orderBook.getAsks(), 1).get(0).getLimitPrice();
                thePrice = thePrice.subtract(MAKER_QUOTE_DELTA);
            } else { // orderType.equals(Order.OrderType.ASK)
                thePrice = Utils.getBestBids(orderBook.getBids(), 1).get(0).getLimitPrice();
                thePrice = thePrice.add(MAKER_QUOTE_DELTA);
            }

//          TODO  Place unclear logic to BitplayOkCoinTradeService.placeTakerOrder()
            final LimitOrder limitOrder = new LimitOrder(orderType,
                    amount, CURRENCY_PAIR_BTC_USD, "123", new Date(),
                    thePrice);

            String orderId = tradeService.placeLimitOrder(limitOrder);
            tradeResponse.setOrderId(orderId);

//            final Order successfulOrder = fetchOrderInfo(orderId);

            // TODO save trading history into DB
            tradeLogger.info("maker {} amount={} with quote={} was placed",
                    orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                    amount.toPlainString(),
                    thePrice);

            fetchAccountInfo();
        } catch (Exception e) {
            logger.error("Place market order error", e);
            tradeLogger.info("maker error {}", e.toString());
            tradeResponse.setOrderId(e.getMessage());
            tradeResponse.setErrorMessage(e.getMessage());
        }
        return tradeResponse;
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
}
