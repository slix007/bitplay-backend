package com.bitplay.market;

import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.utils.Utils;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

/**
 * Created by Sergey Shurmin on 4/16/17.
 */
public abstract class MarketService {

    protected BigDecimal bestBid = BigDecimal.ZERO;
    protected BigDecimal bestAsk = BigDecimal.ZERO;
    protected List<LimitOrder> openOrders = new ArrayList<>();
    protected OrderBook orderBook = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
    protected AccountInfo accountInfo = null;

    protected Subject<BigDecimal> bestAskChangedSubject = PublishSubject.create();
    protected Subject<BigDecimal> bestBidChangedSubject = PublishSubject.create();
    protected Subject<OrderBook> orderBookChangedSubject = PublishSubject.create();
    protected Map<String, BestQuotes> orderIdToSignalInfo = new HashMap<>();

    protected MarketState marketState = MarketState.IDLE;

    private final static Logger debugLog = LoggerFactory.getLogger("DEBUG_LOG");
    private final static Logger logger = LoggerFactory.getLogger(MarketService.class);

    public abstract void initializeMarket(String key, String secret);

    public abstract UserTrades fetchMyTradeHistory();

    public abstract OrderBook getOrderBook();

    public boolean isAffordable(Order.OrderType orderType, BigDecimal tradableAmount) {
        boolean isAffordable = false;
        if (accountInfo != null && accountInfo.getWallet() != null) {
            final Wallet wallet = getAccountInfo().getWallet();
            final BigDecimal btcBalance = wallet.getBalance(Currency.BTC).getAvailable();
            final BigDecimal usdBalance = wallet.getBalance(getSecondCurrency()).getAvailable();
            if (orderType.equals(Order.OrderType.BID)) {
                if (usdBalance.compareTo(getTotalPriceOfAmountToBuy(tradableAmount)) != -1) {
                    isAffordable = true;
                }
            }
            if (orderType.equals(Order.OrderType.ASK)) {
                if (btcBalance.compareTo(tradableAmount) != -1) {
                    isAffordable = true;
                }
            }
        }
        return isAffordable;
    }

    /**
     * Create only one observable on initialization.<br>
     * Use also .share() to make it multisubscribers compatible.
     *
     * @return observable that was created before this method.
     */
    public abstract Observable<OrderBook> getOrderBookObservable();

    public abstract String getName();

    public AccountInfo getAccountInfo() {
        return accountInfo;
    }

    public synchronized void setAccountInfo(AccountInfo accountInfo) {
        this.accountInfo = accountInfo;
    }

    public abstract TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amount, BestQuotes bestQuotes, boolean fromGui);

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

    protected List<LimitOrder> fetchOpenOrders() {
        Exception lastException = null;
            try {
                openOrders = getTradeService().getOpenOrders(null)
                        .getOpenOrders();
            } catch (Exception e) {
                lastException = e;
            }
        if (openOrders == null) {
            logger.error("GetOpenOrdersError", lastException);
            throw new IllegalStateException("GetOpenOrdersError", lastException);
        } else {
            if (orderIdToSignalInfo.size() > 100000) {
                orderIdToSignalInfo.clear();
            }

//            orderIdToSignalInfo.entrySet()
//                    .removeIf(entry -> openOrders.stream()
//                            .noneMatch(l -> l.getId().equals(entry.getKey())));
//            logger.info(String.format("OpenOrders.size=%s, bestQuotesSize=%s",
//                    openOrders.size(),
//                    orderIdToSignalInfo.size()));
        }

        return openOrders;
    }

    protected void checkOpenOrdersForMoving() {
        if (isReadyForMoving && marketState != MarketState.STOP_MOVING) {
            iterateOpenOrdersMove();
        }
    }

    protected void iterateOpenOrdersMove() {
        //TODO poloniex can be changed for half price. So then only 2 step of changing
        openOrders.removeIf(openOrder -> {
            boolean isNeedToDelete;
            try {
                final MoveResponse response = moveMakerOrderIfNotFirst(openOrder, false);
                isNeedToDelete = response.getMoveOrderStatus().equals(MoveResponse.MoveOrderStatus.ALREADY_CLOSED);
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
                    .map(limitOrder -> moveMakerOrderIfNotFirst(limitOrder, true))
                    .orElseGet(() -> new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "can not find in openOrders list"));
        }
        return response;
    }

    public abstract MoveResponse moveMakerOrder(LimitOrder limitOrder, boolean fromGui);

    protected abstract BigDecimal getMakerPriceStep();

    protected abstract BigDecimal getMakerDelta();

    protected BigDecimal createBestMakerPrice(Order.OrderType orderType, boolean forceUsingStep) {
//        final BigDecimal thePrice = createBetterPrice(orderType, forceUsingStep);
        BigDecimal thePrice = BigDecimal.ZERO;
        if (orderType == Order.OrderType.BID) {
            thePrice = Utils.getBestBids(getOrderBook().getBids(), 1).get(0).getLimitPrice();
        } else if (orderType == Order.OrderType.ASK) {
            thePrice = Utils.getBestAsks(getOrderBook().getAsks(), 1).get(0).getLimitPrice();
        }
        return thePrice;
    }

    private Boolean isReadyForMoving = true;
    private Disposable theTimer;

    private void setTimeoutAfterStartMoving() {
        isReadyForMoving = false;
        if (theTimer != null) {
            theTimer.dispose();
        }
        theTimer = Completable.timer(2, TimeUnit.SECONDS)
                .doOnComplete(() -> isReadyForMoving = true)
                .subscribe();
    }

    protected MoveResponse moveMakerOrderIfNotFirst(LimitOrder limitOrder, boolean fromGui) {
        MoveResponse response;
        BigDecimal bestPrice;

        if (!isReadyForMoving || marketState == MarketState.STOP_MOVING) {
            response = new MoveResponse(MoveResponse.MoveOrderStatus.WAITING_TIMEOUT, "");
        } else {


            BigDecimal bestAsk = Utils.getBestAsks(getOrderBook(), 1).get(0).getLimitPrice();
            BigDecimal bestBid = Utils.getBestBids(getOrderBook(), 1).get(0).getLimitPrice();

            if (limitOrder.getType() == Order.OrderType.ASK) {
                bestPrice = bestAsk;
            } else if (limitOrder.getType() == Order.OrderType.BID) {
                bestPrice = bestBid;
            } else {
                throw new IllegalArgumentException("Order type is not supported" + limitOrder.getType());
            }

            if (limitOrder.getLimitPrice().compareTo(bestPrice) != 0) { // if we need moving
                logger.info("{} Try to move maker order {} {}, from {} to {}",
                        getName(), limitOrder.getId(), limitOrder.getType(),
                        limitOrder.getLimitPrice(), bestPrice);
                response = moveMakerOrder(limitOrder, fromGui);
                setTimeoutAfterStartMoving();
            } else {
                response = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_FIRST, "");
            }
        }
        return response;
    }

    protected void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            logger.error("Error on sleep", e);
        }
    }

    public Currency getSecondCurrency() {
        return Currency.USD;
    }

    protected abstract Exchange getExchange();

    protected Observable<AccountInfo> createAccountInfoObservable() {
        return Observable.<AccountInfo>create(observableOnSubscribe -> {
            while (!observableOnSubscribe.isDisposed()) {
                boolean noSleep = false;
                try {
                    accountInfo = getExchange().getAccountService().getAccountInfo();
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
        }).share();
    }

    public boolean isMovingStop() {
        return marketState == MarketState.STOP_MOVING;
    }

    public void setMovingStop(boolean shouldStopMoving) {
        if (shouldStopMoving) {
            marketState = MarketState.STOP_MOVING;
        } else {
            marketState = MarketState.IDLE;
        }
    }

}
