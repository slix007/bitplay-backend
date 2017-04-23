package com.bitplay.market;

import com.bitplay.market.model.TradeResponse;
import com.bitplay.utils.Utils;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.OpenOrders;
import org.knowm.xchange.dto.trade.UserTrades;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

/**
 * Created by Sergey Shurmin on 4/16/17.
 */
public abstract class MarketService {

    protected BigDecimal bestBid = BigDecimal.ZERO;
    protected BigDecimal bestAsk = BigDecimal.ZERO;
    protected List<LimitOrder> openOrders = new ArrayList<>();
    protected Subject<BigDecimal> bestAskChangedSubject = PublishSubject.create();
    protected Subject<BigDecimal> bestBidChangedSubject = PublishSubject.create();

    public abstract UserTrades fetchMyTradeHistory();

    public abstract OrderBook getOrderBook();

    public abstract TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amount);

    public BigDecimal getTotalPriceOfAmountToBuy(BigDecimal requiredAmountToBuy) {
        BigDecimal totalPrice = BigDecimal.ZERO;
        int index = 1;
        final LimitOrder limitOrder1 = Utils.getBestAsks(getOrderBook().getAsks(), index).get(index-1);
        BigDecimal totalAmountToBuy = limitOrder1.getTradableAmount().compareTo(requiredAmountToBuy) == -1
                ? limitOrder1.getTradableAmount()
                : requiredAmountToBuy;

        totalPrice = totalPrice.add(totalAmountToBuy.multiply(limitOrder1.getLimitPrice()));

        while (totalAmountToBuy.compareTo(requiredAmountToBuy) == -1) {
            index++;
            final LimitOrder lo = Utils.getBestAsks(getOrderBook().getAsks(), index).get(index-1);
            final BigDecimal toBuyLeft = requiredAmountToBuy.subtract(totalAmountToBuy);
            BigDecimal amountToBuyForItem = lo.getTradableAmount().compareTo(toBuyLeft) == -1
                    ? lo.getTradableAmount()
                    : toBuyLeft;
            totalPrice = totalPrice.add(amountToBuyForItem.multiply(lo.getLimitPrice()));
            totalAmountToBuy = totalAmountToBuy.add(amountToBuyForItem);
        }

        return totalPrice;
    }

    public abstract OpenOrders fetchOpenOrders();

    protected void initOrderBookSubscribers(Logger logger) {
        bestAskChangedSubject.subscribe(bestAsk -> {
            logger.info("BEST ASK WAS CHANGED TO " + bestAsk.toPlainString());
            if (openOrders.size() > 0) {
                openOrders = fetchOpenOrders().getOpenOrders();

                openOrders.stream()
                        .filter(limitOrder -> limitOrder.getType() == Order.OrderType.ASK)
                        .forEach(limitOrder -> {
                            if (limitOrder.getLimitPrice().compareTo(bestAsk) != 0) {
                                moveMakerOrder(limitOrder);
                            }
                        });
            }
        });
        bestBidChangedSubject.subscribe(bestBid -> {
            if (openOrders.size() > 0) {
                openOrders = fetchOpenOrders().getOpenOrders();
                openOrders.stream()
                        .filter(limitOrder -> limitOrder.getType() == Order.OrderType.BID)
                        .forEach(limitOrder -> {
                            if (limitOrder.getLimitPrice().compareTo(bestBid) != 0) {
                                moveMakerOrder(limitOrder);
                            }
                        });
            }
        });
    }

    public abstract void moveMakerOrder(LimitOrder limitOrder);

    protected abstract BigDecimal getMakerStep();

    protected abstract BigDecimal getMakerDelta();

    protected BigDecimal createBestMakerPrice(Order.OrderType orderType, boolean forceUsingStep) {
        BigDecimal thePrice = null;
        BigDecimal makerDelta = getMakerDelta();
        if (makerDelta.compareTo(BigDecimal.ZERO) == 0 || forceUsingStep) {
            makerDelta = getMakerStep();
        }

        if (orderType == Order.OrderType.BID) {
            thePrice = Utils.getBestBids(getOrderBook().getBids(), 1)
                    .get(0)
                    .getLimitPrice();
            thePrice = thePrice.add(makerDelta);
            //2
            final BigDecimal bestAsk = Utils.getBestAsks(getOrderBook().getAsks(), 1).get(0).getLimitPrice();
            if (thePrice.compareTo(bestAsk) == 1 || thePrice.compareTo(bestAsk) == 0) {
                thePrice = bestAsk.subtract(getMakerStep());
            }
        } else if (orderType == Order.OrderType.ASK) {
            thePrice = Utils.getBestAsks(getOrderBook().getAsks(), 1)
                    .get(0)
                    .getLimitPrice();
            thePrice = thePrice.subtract(makerDelta);
            //2
            final BigDecimal bestBid = Utils.getBestBids(getOrderBook().getBids(), 1).get(0).getLimitPrice();
            if (thePrice.compareTo(bestBid) == -1 || thePrice.compareTo(bestBid) == 0) {
                thePrice = bestBid.add(getMakerStep());
            }
        }
        return thePrice;
    }

    protected void moveMakerOrderIfNotFirst(LimitOrder limitOrder) {
        BigDecimal bestPrice;
        if (limitOrder.getType() == Order.OrderType.ASK) {
            bestPrice = Utils.getBestAsks(getOrderBook(), 1).get(0).getLimitPrice();
        } else if (limitOrder.getType() == Order.OrderType.BID) {
            bestPrice = Utils.getBestBids(getOrderBook(), 1).get(0).getLimitPrice();
        } else {
            throw new IllegalArgumentException("Order type is not supported");
        }

        if (limitOrder.getLimitPrice().compareTo(bestPrice) != 0) { // if we need moving
            moveMakerOrder(limitOrder);
        }
    }
}
