package com.bitplay.market;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.EventBus;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.utils.Utils;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.ContractIndex;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

/**
 * Created by Sergey Shurmin on 4/16/17.
 */
public abstract class MarketService {

    protected final static Logger debugLog = LoggerFactory.getLogger("DEBUG_LOG");
    private final static Logger logger = LoggerFactory.getLogger(MarketService.class);
    protected BigDecimal bestBid = BigDecimal.ZERO;
    protected BigDecimal bestAsk = BigDecimal.ZERO;
    protected List<LimitOrder> openOrders = new CopyOnWriteArrayList<>();
    protected OrderBook orderBook = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
    protected AccountInfo accountInfo = null;
    protected AccountInfoContracts accountInfoContracts = new AccountInfoContracts();
    protected Position position = null;
    protected BigDecimal affordableContractsForShort = BigDecimal.ZERO;
    protected BigDecimal affordableContractsForLong = BigDecimal.ZERO;
    protected ContractIndex contractIndex = new ContractIndex(BigDecimal.ZERO, new Date());
    protected int usdInContract = 0;
    protected Map<String, BestQuotes> orderIdToSignalInfo = new HashMap<>();
    protected MarketState marketState = MarketState.IDLE;
//    protected boolean checkOpenOrdersInProgress = false; - #checkOpenOrdersForMoving() is synchronized instead of it
    protected boolean isBusy = false;
    protected EventBus eventBus = new EventBus();
    private Boolean isReadyForMoving = true;
    private Disposable theTimer;

    public void init(String key, String secret) {
        initEventBus();
        initializeMarket(key, secret);
    }

    protected abstract void initializeMarket(String key, String secret);

    public abstract UserTrades fetchMyTradeHistory();

    public abstract OrderBook getOrderBook();

    public abstract Logger getTradeLogger();

    public abstract String getPositionAsString();

    /*
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
    }*/

    public abstract boolean isAffordable(Order.OrderType orderType, BigDecimal tradableAmount);

    public BigDecimal calcBtcInContract() {
        if (contractIndex.getIndexPrice() != null && contractIndex.getIndexPrice().signum() != 0) {
            return BigDecimal.valueOf(usdInContract).divide(contractIndex.getIndexPrice(), 4, BigDecimal.ROUND_HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    public boolean isReadyForArbitrage() {
        final long openOrdersCount = openOrders.stream()
                .filter(limitOrder -> limitOrder.getTradableAmount().compareTo(BigDecimal.ZERO) != 0) // filter as for gui
                .count();
        if (openOrders.size() != openOrdersCount) {
            logger.warn("OO with zero amount: " + openOrders.stream()
                    .map(LimitOrder::toString)
                    .reduce((s, s2) -> s + "; " + s2));
        }
        return (openOrdersCount == 0 && !isBusy);
    }

    private void initEventBus() {
        eventBus.toObserverable()
                .doOnError(throwable -> logger.error("doOnError handling", throwable))
                .retry()
                .subscribe(btsEvent -> {
                    if (btsEvent == BtsEvent.MARKET_FREE) {
                        isBusy = false;
                        getTradeLogger().info("{}: {}", getName(), isBusy ? "busy" : "ready");
                    } else if (btsEvent == BtsEvent.MARKET_BUSY) {
                        isBusy = true;
                        getTradeLogger().info("{}: {}", getName(), isBusy ? "busy" : "ready");
                    }
                }, throwable -> logger.error("On event handling", throwable));
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public boolean isArbitrageInProgress() {
        return isBusy;
    }

    /**
     * Create only one observable on initialization.<br>
     * Use also .share() to make it multisubscribers compatible.
     *
     * @return observable that was created before this method.
     */
    public abstract Observable<OrderBook> getOrderBookObservable();

    public abstract String getName();

    public abstract ArbitrageService getArbitrageService();

    public AccountInfo getAccountInfo() {
        return accountInfo;
    }

    public synchronized void setAccountInfo(AccountInfo accountInfo) {
        this.accountInfo = accountInfo;
    }

    public AccountInfoContracts getAccountInfoContracts() {
        return accountInfoContracts;
    }

    public Position getPosition() {
        return position;
    }

    public ContractIndex getContractIndex() {
        return contractIndex;
    }

    public abstract TradeResponse placeMakerOrder(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes, SignalType signalType);

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

    /**
     * Add new openOrders.<br>
     * Do not remove old. They will be checked in moveMakerOrder()
     *
     * @return list of open orders.
     */
    protected List<LimitOrder> fetchOpenOrders() {
        if (getExchange() != null && getTradeService() != null) {
            try {
                final List<LimitOrder> fetchedList = getTradeService().getOpenOrders(null)
                        .getOpenOrders();
                if (fetchedList == null) {
                    logger.error("GetOpenOrdersError");
                    throw new IllegalStateException("GetOpenOrdersError");
                }

                if (fetchedList.size() > 1) {
                    getTradeLogger().warn("Warning: openOrders count " + fetchedList.size());
                }

                final List<LimitOrder> allNew = fetchedList.stream()
                        .filter(fetched -> this.openOrders.stream()
                                .noneMatch(o -> o.getId().equals(fetched.getId())))
                        .collect(Collectors.toList());

                this.openOrders.addAll(allNew);

            } catch (Exception e) {
                logger.error("GetOpenOrdersError", e);
                throw new IllegalStateException("GetOpenOrdersError", e);
            }

            if (orderIdToSignalInfo.size() > 100000) {
                logger.warn("orderIdToSignalInfo over 100000");
                final Map<String, BestQuotes> newMap = new HashMap<>();
                openOrders.stream()
                        .map(LimitOrder::getId)
                        .filter(id -> orderIdToSignalInfo.containsKey(id))
                        .forEach(id -> newMap.put(id, orderIdToSignalInfo.get(id)));
                orderIdToSignalInfo = newMap;
            }

        }
        return openOrders;
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

    protected synchronized void checkOpenOrdersForMoving() {
//        debugLog.info(getName() + ":checkOpenOrdersForMoving");
        if (isReadyForMoving && marketState != MarketState.STOP_MOVING) {
            iterateOpenOrdersMove();
        }
    }

    protected void iterateOpenOrdersMove() {
        boolean haveToFetch = false;
        boolean freeTheMarket = false;
        List<String> toRemove = new ArrayList<>();
        List<LimitOrder> toAdd = new ArrayList<>();
        try {
            for (LimitOrder openOrder : openOrders) {
                if (openOrder.getType() != null) {
                    final SignalType signalType = getArbitrageService().getSignalType();
                    final MoveResponse response = moveMakerOrderIfNotFirst(openOrder, signalType);

                    if (response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.ALREADY_CLOSED
                            || response.getMoveOrderStatus() == MoveResponse.MoveOrderStatus.ONLY_CANCEL) {
                        freeTheMarket = true;
                        toRemove.add(openOrder.getId());
                        haveToFetch = true;
                        logger.info(getName() + response.getDescription());
                    }

                    if (response.getMoveOrderStatus().equals(MoveResponse.MoveOrderStatus.MOVED_WITH_NEW_ID)) {
                        toRemove.add(openOrder.getId());
                        haveToFetch = true;
                        if (response.getNewOrder() != null) {
                            toAdd.add(response.getNewOrder());
                        }
                    }
                }
            }

            openOrders.removeIf(o -> toRemove.contains(o.getId()));
            toRemove.forEach(s -> orderIdToSignalInfo.remove(s));
            openOrders.addAll(toAdd);

            if (freeTheMarket && openOrders.size() > 0) {
                logger.warn("Warning: get ALREADY_CLOSED, but there are still open orders");
            }

            if (freeTheMarket && openOrders.size() == 0) {
                eventBus.send(BtsEvent.MARKET_FREE);
            }
        } catch (Exception e) {
            logger.error("On moving", e);
            final List<LimitOrder> orderList = fetchOpenOrders();
            if (orderList.size() == 0) {
                eventBus.send(BtsEvent.MARKET_FREE);
            }
        }

        if (haveToFetch) {
            fetchOpenOrders();
        }
    }

    public MoveResponse moveMakerOrderFromGui(String orderId, SignalType signalType) {
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
                    .map(limitOrder -> moveMakerOrderIfNotFirst(limitOrder, signalType))
                    .orElseGet(() -> new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "can not find in openOrders list"));
        }
        return response;
    }

    public abstract MoveResponse moveMakerOrder(LimitOrder limitOrder, SignalType signalType);

    protected abstract BigDecimal getMakerPriceStep();

    protected abstract BigDecimal getMakerDelta();

    protected BigDecimal createBestMakerPrice(Order.OrderType orderType, boolean forceUsingStep) {
        BigDecimal thePrice = BigDecimal.ZERO;
        if (orderType == Order.OrderType.BID
                || orderType == Order.OrderType.EXIT_ASK) {
            thePrice = Utils.getBestBids(getOrderBook().getBids(), 1).get(0).getLimitPrice();
        } else if (orderType == Order.OrderType.ASK
                || orderType == Order.OrderType.EXIT_BID) {
            thePrice = Utils.getBestAsks(getOrderBook().getAsks(), 1).get(0).getLimitPrice();
        }
        return thePrice;
    }

    private void setTimeoutAfterStartMoving() {
        isReadyForMoving = false;
        if (theTimer != null) {
            theTimer.dispose();
        }
        theTimer = Completable.timer(1, TimeUnit.SECONDS)
                .doOnComplete(() -> isReadyForMoving = true)
                .subscribe();
    }

    protected MoveResponse moveMakerOrderIfNotFirst(LimitOrder limitOrder, SignalType signalType) {
        MoveResponse response;
        BigDecimal bestPrice;

        if (!isReadyForMoving || marketState == MarketState.STOP_MOVING) {
            response = new MoveResponse(MoveResponse.MoveOrderStatus.WAITING_TIMEOUT, "");
        } else if ((limitOrder.getType() == Order.OrderType.ASK && limitOrder.getLimitPrice().compareTo(bestAsk) == 0)
                || (limitOrder.getType() == Order.OrderType.BID && limitOrder.getLimitPrice().compareTo(bestBid) == 0)) {

            response = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_FIRST, "");
        } else {

            if (limitOrder.getType() == Order.OrderType.ASK
                    || limitOrder.getType() == Order.OrderType.EXIT_BID) {
                bestPrice = bestAsk;
            } else if (limitOrder.getType() == Order.OrderType.BID
                    || limitOrder.getType() == Order.OrderType.EXIT_ASK) {
                bestPrice = bestBid;
            } else {
                throw new IllegalArgumentException("Order type is not supported" + limitOrder.getType());
            }

            if (limitOrder.getLimitPrice().compareTo(bestPrice) != 0) { // if we need moving
                logger.info("{} Try to move maker order {} {}, from {} to {}",
                        getName(), limitOrder.getId(), limitOrder.getType(),
                        limitOrder.getLimitPrice(), bestPrice);
                response = moveMakerOrder(limitOrder, signalType);
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

    public BigDecimal getAffordableContractsShort() {
        return affordableContractsForShort;
    }

    public BigDecimal getAffordableContractsForLong() {
        return affordableContractsForLong;
    }
}
