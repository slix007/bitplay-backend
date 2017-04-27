package com.bitplay.market;

import com.bitplay.market.arbitrage.BestQuotes;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.utils.Utils;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    protected Subject<OrderBook> orderBookChangedSubject = PublishSubject.create();
    protected Map<String, BestQuotes> orderIdToSignalInfo = new HashMap<>();

    private final static Logger debugLog = LoggerFactory.getLogger("DEBUG_LOG");

    public abstract UserTrades fetchMyTradeHistory();

    public abstract OrderBook getOrderBook();

    public abstract TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes);

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

    public abstract TradeService getTradeService();

    public List<LimitOrder> getOpenOrders() {
        return openOrders != null ? openOrders : new ArrayList<>();
    }

    @Scheduled(fixedRate = 2000)
    public List<LimitOrder> fetchOpenOrders() {
        Exception lastException = null;
            try {
                openOrders = getTradeService().getOpenOrders(null)
                        .getOpenOrders();
            } catch (Exception e) {
                lastException = e;
            }
        if (openOrders == null) {
            debugLog.error("GetOpenOrdersError", lastException);
            throw new IllegalStateException("GetOpenOrdersError", lastException);
        } else {
            orderIdToSignalInfo.entrySet()
                    .removeIf(entry -> openOrders.stream()
                            .noneMatch(l -> l.getId().equals(entry.getKey())));
        }

        return openOrders;
    }

    protected void checkOrderBook(OrderBook orderBook) {
        final BigDecimal bestAsk = Utils.getBestAsks(orderBook, 1).get(0).getLimitPrice();
        final BigDecimal bestBid = Utils.getBestBids(orderBook, 1).get(0).getLimitPrice();

        if (this.bestAsk.compareTo(bestAsk) != 0 || this.bestBid.compareTo(bestBid) != 0) {
            this.bestAsk = bestAsk;
            this.bestBid = bestBid;
//            bestAskChangedSubject.onNext(bestAsk);
//            bestBidChangedSubject.onNext(bestBid);
//            fetchOpenOrders().forEach(this::moveMakerOrderIfNotFirst);
            moveOpenOrdersOrDelete();
        }
    }

    private void moveOpenOrdersOrDelete() {
        //TODO poloniex can be changed for half price. So then only 2 step of changing
        openOrders.removeIf(openOrder -> {
            boolean isNeedToDelete;
            try {
                final MoveResponse response = moveMakerOrderIfNotFirst(openOrder);
                isNeedToDelete = response.getMoveOrderStatus().equals(MoveResponse.MoveOrderStatus.ALREADY_FIRST)
                        || response.getMoveOrderStatus().equals(MoveResponse.MoveOrderStatus.ALREADY_CLOSED);
            } catch (Exception e) {
                e.printStackTrace();
                isNeedToDelete = true;
            }

            if (isNeedToDelete) {
                CompletableFuture.runAsync(this::fetchOpenOrders);
            }

            return isNeedToDelete;
        });
    }
/*
    protected void initOrderBookSubscribers(Logger logger) {
        orderBookChangedSubject.subscribe(orderBook -> {
            final BigDecimal bestAsk = Utils.getBestAsks(orderBook, 1).get(0).getLimitPrice();
            if (this.bestAsk.compareTo(bestAsk) != 0) {
                this.bestAsk = bestAsk;
                bestAskChangedSubject.onNext(bestAsk);
            }
            final BigDecimal bestBid = Utils.getBestBids(orderBook, 1).get(0).getLimitPrice();
            if (this.bestBid.compareTo(bestBid) != 0) {
                this.bestBid = bestBid;
                bestBidChangedSubject.onNext(bestBid);
            }
        });
        bestAskChangedSubject.subscribe(bestAsk -> {
//            debugLog.info("BEST ASK WAS CHANGED TO " + bestAsk.toPlainString());
            if (openOrders.size() > 0) {
//                logger.info("HAS OPENORDER ON ASK CHANGING" + bestAsk.toPlainString());
//                final OpenOrders currentOpenOrders = fetchOpenOrders();
//                this.openOrders = currentOpenOrders != null
//                        ? currentOpenOrders.getOpenOrders()
//                        : new ArrayList<>();

                this.openOrders.stream()
                        .filter(limitOrder -> limitOrder.getType() == Order.OrderType.ASK)
                        .forEach(limitOrder -> {
                            if (limitOrder.getLimitPrice().compareTo(bestAsk) != 0) {
                                logger.info("MOVE OPENORDER {} {}. From {} when best is {}",
                                        limitOrder.getType(), limitOrder.getTradableAmount(),
                                        limitOrder.getLimitPrice().toPlainString(),
                                        bestAsk.toPlainString());
                                moveMakerOrder(limitOrder);
                            }
                        });
            }
        });
        bestBidChangedSubject.subscribe(bestBid -> {
//            debugLog.info("BEST BID WAS CHANGED TO " + bestBid.toPlainString());
            if (openOrders.size() > 0) {
                logger.info("HAS OPENORDER ON ASK CHANGING" + bestBid.toPlainString());
//                final OpenOrders currentOpenOrders = fetchOpenOrders();
//                this.openOrders = currentOpenOrders != null
//                        ? currentOpenOrders.getOpenOrders()
//                        : new ArrayList<>();

                openOrders.stream()
                        .filter(limitOrder -> limitOrder.getType() == Order.OrderType.BID)
                        .forEach(limitOrder -> {
                            if (limitOrder.getLimitPrice().compareTo(bestBid) != 0) {
                                logger.info("MOVE OPENORDER {} {}. From {} when best is {}",
                                        limitOrder.getType(), limitOrder.getTradableAmount(),
                                        limitOrder.getLimitPrice().toPlainString(),
                                        bestBid.toPlainString());
                                moveMakerOrder(limitOrder);
                            }
                        });
            }
        });
    }*/

    public MoveResponse moveMakerOrderFromGui(String orderId) {
        MoveResponse response;

        List<LimitOrder> orderList = null;
        try {
            orderList = fetchOpenOrders();
        } catch (Exception e) {
//            response = new MoveResponse(false, "can not fetch openOrders list");
        }
        if (orderList == null) {
            response = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "can not fetch openOrders list");
        } else {
            this.openOrders = orderList;

            response = this.openOrders.stream()
                    .filter(limitOrder -> limitOrder.getId().equals(orderId))
                    .findFirst()
                    .map(this::moveMakerOrderIfNotFirst)
                    .orElseGet(() -> new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "can not find in openOrders list"));
        }
        return response;
    }

    public abstract MoveResponse moveMakerOrder(LimitOrder limitOrder);

    protected abstract BigDecimal getMakerStep();

    protected abstract BigDecimal getMakerDelta();

    protected BigDecimal createBestMakerPrice(Order.OrderType orderType, boolean forceUsingStep) {
        BigDecimal thePrice = null;
        BigDecimal makerDelta = getMakerDelta();
        if (makerDelta.compareTo(BigDecimal.ZERO) == 0 || forceUsingStep) {
            makerDelta = getMakerStep();
        }

        final BigDecimal bestBid = Utils.getBestBids(getOrderBook().getBids(), 1).get(0).getLimitPrice();
        final BigDecimal bestAsk = Utils.getBestAsks(getOrderBook().getAsks(), 1).get(0).getLimitPrice();

        if (orderType == Order.OrderType.BID) {
            // 1 use the best price if it is ours.
            if (openOrders.stream()
                    .anyMatch(limitOrder -> limitOrder.getLimitPrice().compareTo(bestBid) == 0)) {
                thePrice = bestBid;
            } else {
                // 2 the best with delta
                thePrice = bestBid.add(makerDelta);
                // 3 check if we are already on the edge
                if (thePrice.compareTo(bestAsk) == 1 || thePrice.compareTo(bestAsk) == 0) {
                    thePrice = bestAsk.subtract(getMakerStep());
                }
            }
        } else if (orderType == Order.OrderType.ASK) {
            // 1 use the best price if it is ours.
            if (openOrders.stream()
                    .anyMatch(limitOrder -> limitOrder.getLimitPrice().compareTo(bestAsk) == 0)) {
                thePrice = bestAsk;
            } else {
                // 2 the best with delta
                thePrice = bestAsk.subtract(makerDelta);
                // 3 check if we are already on the edge
                if (thePrice.compareTo(bestBid) == -1 || thePrice.compareTo(bestBid) == 0) {
                    thePrice = bestBid.add(getMakerStep());
                }
            }
        }
        return thePrice;
    }

    protected MoveResponse moveMakerOrderIfNotFirst(LimitOrder limitOrder) {
        MoveResponse response;
        BigDecimal bestPrice;

        BigDecimal bestAsk = Utils.getBestAsks(getOrderBook(), 1).get(0).getLimitPrice();
        BigDecimal bestBid = Utils.getBestBids(getOrderBook(), 1).get(0).getLimitPrice();

        if (limitOrder.getType() == Order.OrderType.ASK) {
            bestPrice = bestAsk;
        } else if (limitOrder.getType() == Order.OrderType.BID) {
            bestPrice = bestBid;
        } else {
            throw new IllegalArgumentException("Order type is not supported");
        }

        if (limitOrder.getLimitPrice().compareTo(bestPrice) != 0) { // if we need moving
            response = moveMakerOrder(limitOrder);
        } else {
            response = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_FIRST, "");
        }
        return response;
    }
}
