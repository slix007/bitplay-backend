package com.bitplay.business.okcoin;

import com.bitplay.business.BusinessService;
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
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.TradeHistoryParamsZero;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
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
                .subscribe(orderBook -> {
                    final List<LimitOrder> bestAsks = Utils.getBestAsks(orderBook.getAsks(), 1);
                    final LimitOrder bestAsk = bestAsks.size() > 0 ? bestAsks.get(0) : null;
                    final List<LimitOrder> bestBids = Utils.getBestBids(orderBook.getBids(), 1);
                    final LimitOrder bestBid = bestBids.size() > 0 ? bestBids.get(0) : null;
                    logger.debug("ask: {}, bid: {}", bestAsk.getLimitPrice(), bestBid.getLimitPrice());
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
        AccountInfo accountInfo = null;
        try {
            accountInfo = exchange.getAccountService().getAccountInfo();
            logger.debug(accountInfo.toString());
        } catch (IOException e) {
            logger.error("AccountInfo error", e);
        }
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

    public String placeMarketOrder(Order.OrderType orderType, BigDecimal amount) {
        String orderId = null;
        try {
            final TradeService tradeService = exchange.getTradeService();
            BigDecimal tradingDigit = null;

            if (orderType.equals(Order.OrderType.BID)) {
                // The price is to total amount you want to buy, and it must be higher than the current price of 0.01 BTC
                tradingDigit = getTotalPriceToBuy(amount);
            } else { // orderType.equals(Order.OrderType.ASK)
                tradingDigit = amount;
            }

//          TODO  Place unclear logic to BitplayOkCoinTradeService.placeMarketOrder()
            final MarketOrder marketOrder = new MarketOrder(orderType,
                    tradingDigit,
                    CURRENCY_PAIR_BTC_USD, new Date());
            orderId = tradeService.placeMarketOrder(marketOrder);

            // TODO save trading history into DB
            tradeLogger.info("{} amount={} with rate={}",
                    orderType.equals(Order.OrderType.BID) ? "BUY" : "SELL",
                    amount.toPlainString(),
                    orderId);
        } catch (Exception e) {
            logger.error("Place market order error", e);
            orderId = e.getMessage();
        }
        return orderId;
    }

    private BigDecimal getTotalPriceToBuy(BigDecimal requiredAmountToBuy) {
        BigDecimal totalPrice = BigDecimal.ZERO;
        int index = 0;
        final LimitOrder limitOrder1 = Utils.getBestAsks(getOrderBook().getAsks(), 1).get(index);
        BigDecimal amountToBuy1 = limitOrder1.getTradableAmount().compareTo(requiredAmountToBuy) == -1
                ? limitOrder1.getTradableAmount()
                : requiredAmountToBuy;

        totalPrice = totalPrice.add(amountToBuy1.multiply(limitOrder1.getLimitPrice()));

        BigDecimal totalAmount = amountToBuy1;
        while (totalAmount.compareTo(requiredAmountToBuy) == -1) {
            index++;
            final LimitOrder lo = Utils.getBestAsks(getOrderBook().getAsks(), index).get(index);
            final BigDecimal toBuyLeft = requiredAmountToBuy.subtract(totalAmount);
            BigDecimal amountToBuy = lo.getTradableAmount().compareTo(toBuyLeft) == -1
                    ? lo.getTradableAmount()
                    : toBuyLeft;
            totalPrice = totalPrice.add(amountToBuy.multiply(lo.getLimitPrice()));
        }

        return totalPrice;
    }

    @Override
    public UserTrades fetchMyTradeHistory() {
//        returnTradeHistory
        UserTrades tradeHistory = null;
        try {
            tradeHistory = exchange.getTradeService().getTradeHistory(TradeHistoryParamsZero.PARAMS_ZERO);
        } catch (Exception e) {
            logger.info("Exception on fetchMyTradeHistory", e);
        }
        return tradeHistory;

    }
}
