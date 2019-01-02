package com.bitplay.utils;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import info.bitrich.xchangestream.okex.dto.Tool;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.springframework.data.util.Pair;

/**
 * Created by Sergey Shurmin on 4/4/17.
 */
public class Utils {

    public static String withSign(BigDecimal value) {
        return value.signum() < 0 ? value.toPlainString() : ("+" + value.toPlainString());
    }

    public static LimitOrder getBestBid(OrderBook orderBook) {
        return getBestBids(orderBook, 1).get(0);
    }

    public static LimitOrder getBestAsk(OrderBook orderBook) {
        return getBestAsks(orderBook, 1).get(0);
    }

    public static List<LimitOrder> getBestBids(OrderBook orderBook, int amount) {
        final ArrayList<LimitOrder> filtered = new ArrayList<>();
        final List<LimitOrder> bids = orderBook.getBids();
//        bids.sort((o1, o2) -> o2.getLimitPrice().compareTo(o1.getLimitPrice()));
        filterHalfOrderBook(amount, filtered, bids);
        return filtered;
    }

    public static List<LimitOrder> getBestAsks(OrderBook orderBook, int amount) {
        final ArrayList<LimitOrder> filtered = new ArrayList<>();
        final List<LimitOrder> asks = orderBook.getAsks();
//        asks.sort(Comparator.comparing(LimitOrder::getLimitPrice));
        filterHalfOrderBook(amount, filtered, asks);
        return filtered;
    }

    private static void filterHalfOrderBook(int amount, ArrayList<LimitOrder> filtered, List<LimitOrder> obHalf) {
        synchronized (obHalf) {
            if (obHalf.size() == 0) {
                throw new NotYetInitializedException();
            }
            for (int i = 0; i < amount && i < obHalf.size(); i++) {
                filtered.add(obHalf.get(i));
            }
        }
    }

    public static String convertOrderTypeName(Order.OrderType orderType) {
        String theName = "undefined";
        switch (orderType) {
            case ASK:
                theName = "SELL";
                break;
            case BID:
                theName = "BUY";
                break;
            case EXIT_BID:
                theName = "CLOSE_BUY";
                break;
            case EXIT_ASK:
                theName = "CLOSE_SELL";
                break;
        }
        return theName;
    }

    public static BestQuotes createBestQuotes(OrderBook okCoinOrderBook, OrderBook bitmexOrderBook) {
        BigDecimal ask1_o = BigDecimal.ZERO;
        BigDecimal ask1_p = BigDecimal.ZERO;
        BigDecimal bid1_o = BigDecimal.ZERO;
        BigDecimal bid1_p = BigDecimal.ZERO;
        if (okCoinOrderBook != null && bitmexOrderBook != null) {
            ask1_o = Utils.getBestAsk(okCoinOrderBook).getLimitPrice();
            ask1_p = Utils.getBestAsk(bitmexOrderBook).getLimitPrice();

            bid1_o = Utils.getBestBid(okCoinOrderBook).getLimitPrice();
            bid1_p = Utils.getBestBid(bitmexOrderBook).getLimitPrice();
        }
        return new BestQuotes(ask1_o, ask1_p, bid1_o, bid1_p);
    }

    public static BigDecimal createPriceForTaker(OrderBook orderBook, OrderType orderType, Tool baseTool) {
        BigDecimal thePrice = BigDecimal.ZERO;
        BigDecimal extraPrice = baseTool == Tool.BTC ? BigDecimal.valueOf(50) : BigDecimal.valueOf(5);

        if (orderType == Order.OrderType.ASK
                || orderType == Order.OrderType.EXIT_BID) {

            final List<LimitOrder> bids = orderBook.getBids();
            synchronized (bids) {
                thePrice = bids.get(bids.size() - 1).getLimitPrice().subtract(extraPrice);
            }

        } else if (orderType == Order.OrderType.BID
                || orderType == Order.OrderType.EXIT_ASK) {

            final List<LimitOrder> asks = orderBook.getAsks();
            synchronized (asks) {
                thePrice = asks.get(asks.size() - 1).getLimitPrice().add(extraPrice);
            }
        }

        return thePrice;
    }

    public static BigDecimal createPriceForTaker(OrderType orderType, Ticker ticker, BigDecimal okexFakeTakerDev) {
        //Fake taker price при buy-ордере (open long или close short):
        //FTP = max_price - FTPD, // FTPD = fake taker price dev, usd
        //Fake taker price при sell-ордере (open short или close long):
        //FTP = min_price + FTPD.
        if (ticker == null) {
            throw new NotYetInitializedException();
        }
        final BigDecimal minPrice = ticker.getLow();
        final BigDecimal maxPrice = ticker.getHigh();

        BigDecimal thePrice = BigDecimal.ZERO;
        if (orderType == Order.OrderType.ASK || orderType == Order.OrderType.EXIT_BID) {
            thePrice = minPrice.add(okexFakeTakerDev);
        } else if (orderType == Order.OrderType.BID || orderType == Order.OrderType.EXIT_ASK) {
            thePrice = maxPrice.subtract(okexFakeTakerDev);
        }
        return thePrice;
    }

    public static BigDecimal calcQuAvg(OrderBook orderBook) {
        if (orderBook == null || orderBook.getAsks().size() == 0 || orderBook.getBids().size() == 0) {
            return BigDecimal.ZERO;
        }
//        qu_avg = (b_bid[1] + b_ask[1]) / 2;
        final BigDecimal bB = Utils.getBestBid(orderBook).getLimitPrice();
        final BigDecimal bA = Utils.getBestAsk(orderBook).getLimitPrice();
        return (bB.add(bA)).divide(BigDecimal.valueOf(2), 2, BigDecimal.ROUND_HALF_UP);
    }

    public static BigDecimal calcQuAvg(OrderBook orderBookFirst, OrderBook orderBookSecond) {
        if (orderBookFirst == null || orderBookSecond == null
                || orderBookFirst.getBids().size() == 0 || orderBookSecond.getBids().size() == 0) {
            return BigDecimal.ZERO;
        }
//        qu_avg = (b_bid[1] + b_ask[1] + o_bid[1] + o_ask[1]) / 4;
        final BigDecimal bB = Utils.getBestBid(orderBookFirst).getLimitPrice();
        final BigDecimal bA = Utils.getBestAsk(orderBookFirst).getLimitPrice();
        final BigDecimal oB = Utils.getBestBid(orderBookSecond).getLimitPrice();
        final BigDecimal oA = Utils.getBestAsk(orderBookSecond).getLimitPrice();
        return (bB.add(bA).add(oB).add(oA)).divide(BigDecimal.valueOf(4), 2, BigDecimal.ROUND_HALF_UP);
    }

    public static BigDecimal getAvgPrice(OrderBook orderBook, int bidAmount, int askAmount) {
        if (bidAmount == 0 && askAmount == 0) {
            return BigDecimal.ZERO;
        }

        final Pair<BigDecimal, Integer> bidAvgPrice = bidAmount > 0 ? getAvgPriceForOrders(bidAmount, orderBook.getBids()) : Pair.of(BigDecimal.ZERO, 0);
        final Pair<BigDecimal, Integer> askAvgPrice = askAmount > 0 ? getAvgPriceForOrders(askAmount, orderBook.getAsks()) : Pair.of(BigDecimal.ZERO, 0);

        return ((bidAvgPrice.getFirst().multiply(BigDecimal.valueOf(bidAvgPrice.getSecond())))
                .add(askAvgPrice.getFirst().multiply(BigDecimal.valueOf(askAvgPrice.getSecond()))))
                .divide(BigDecimal.valueOf(bidAvgPrice.getSecond() + askAvgPrice.getSecond()), 2, RoundingMode.HALF_UP);
    }

    private static Pair<BigDecimal, Integer> getAvgPriceForOrders(int askAmount, List<LimitOrder> limitOrderList) {
        synchronized (limitOrderList) {
            // weighted average: sum(priceX*qtyX)/sum(qtyX)
            BigDecimal sumPrices = BigDecimal.ZERO; // sum(priceX*qtyX)
            int sumQty = 0; // sum(qtyX)

            for (int i = 0; sumQty < askAmount && i < limitOrderList.size(); i++) {
                final LimitOrder limitOrder = limitOrderList.get(i);
                final int anAmount = limitOrder.getTradableAmount().intValue();
                final int leftToFill = askAmount - sumQty;
                int qtyX;
                if (leftToFill <= anAmount) {
                    qtyX = leftToFill;
                } else {
                    qtyX = anAmount;
                }

                sumQty += qtyX;
                sumPrices = sumPrices.add(limitOrder.getLimitPrice().multiply(BigDecimal.valueOf(qtyX)));
            }

            final BigDecimal avgPrice = sumPrices.divide(BigDecimal.valueOf(sumQty), 8, RoundingMode.HALF_UP);
            return Pair.of(avgPrice, sumQty);
        }
    }

    public static String getTenAskBid(OrderBook orderBook, String counterName, String description) {
        StringBuilder obBuilder = new StringBuilder();
        obBuilder.append("ask: ");
        final List<LimitOrder> asks = orderBook.getAsks();
        synchronized (asks) {
            for (int i = 0; i < 10 && i < asks.size(); i++) {
                final LimitOrder ask = asks.get(i);
                obBuilder.append(String.format("%d,%s(%s)", i + 1, ask.getLimitPrice(), ask.getTradableAmount()));
            }
        }
        final List<LimitOrder> bids = orderBook.getBids();
        obBuilder.append("bid: ");
        synchronized (bids) {
            for (int i = 0; i < 10 && i < bids.size(); i++) {
                final LimitOrder bid = bids.get(i);
                obBuilder.append(String.format("%d. %s (%s);", i + 1, bid.getLimitPrice(), bid.getTradableAmount()));
            }
        }

        final String message = String.format(
                "#%s %s: %s",
                counterName,
                description,
                obBuilder.toString()
        );
        return message;
    }

    public static boolean orderBookIsFull(OrderBook orderBook) {
        return orderBook != null && orderBook.getBids().size() > 0 && orderBook.getAsks().size() > 0;
    }

    public static void logIfLong(Instant start, Instant end, Logger logger, String methodName) {
        long seconds = Duration.between(start, end).getSeconds();
        if (seconds > 10) {
            logger.warn(methodName + " duration is long(sec): " + seconds);
        }

    }

    public static Instant getLastObTime(OrderBook bitmexOb, OrderBook okexOb) {
        Instant first = bitmexOb.getTimeStamp().toInstant();
        Instant second = okexOb.getTimeStamp().toInstant();
        return first.isAfter(second) ? first : second;
    }

    public static Long lastTradeId(Long tradeId1, Long tradeId2) {
        if (tradeId1 == null) {
            return tradeId2;
        }
        if (tradeId2 == null) {
            return tradeId1;
        }
        return tradeId1 > tradeId2 ? tradeId1 : tradeId2;
    }

}
