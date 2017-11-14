package com.bitplay.market;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.dto.LiqInfo;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.EventBus;
import com.bitplay.market.events.SignalEvent;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.Counters;
import com.bitplay.persistance.domain.LiqParams;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

/**
 * Created by Sergey Shurmin on 4/16/17.
 */
public abstract class MarketService {

    protected final static Logger debugLog = LoggerFactory.getLogger("DEBUG_LOG");
    private final static Logger logger = LoggerFactory.getLogger(MarketService.class);
    protected static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    protected BigDecimal bestBid = BigDecimal.ZERO;
    protected BigDecimal bestAsk = BigDecimal.ZERO;
    protected final Object openOrdersLock = new Object();
    protected volatile List<LimitOrder> openOrders = new ArrayList<>();
    protected volatile OrderBook orderBook = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
    protected volatile AccountInfo accountInfo = null;
    protected volatile AccountInfoContracts accountInfoContracts = new AccountInfoContracts();
    protected volatile Position position = new Position(null, null, null, null, "");
    protected volatile BigDecimal affordableContractsForShort = BigDecimal.ZERO;
    protected volatile BigDecimal affordableContractsForLong = BigDecimal.ZERO;
    protected volatile ContractIndex contractIndex = new ContractIndex(BigDecimal.ZERO, new Date());
    protected volatile int usdInContract = 0;
    protected Map<String, BestQuotes> orderIdToSignalInfo = new HashMap<>();
    protected volatile SpecialFlags specialFlags = SpecialFlags.NONE;
//    protected boolean checkOpenOrdersInProgress = false; - #checkOpenOrdersForMoving() is synchronized instead of it
//    protected volatile Boolean isBusy = false;
    protected volatile MarketState marketState = MarketState.READY;
    protected EventBus eventBus = new EventBus();
    protected volatile LiqInfo liqInfo = new LiqInfo();

    Disposable openOrdersMovingSubscription;

    public void init(String key, String secret) {
        initEventBus();
        initOpenOrdersMovingSubscription();
        initializeMarket(key, secret);
    }

    protected abstract void initializeMarket(String key, String secret);

    public abstract UserTrades fetchMyTradeHistory();

    public OrderBook getOrderBook() {
        return this.orderBook;
    }

    public abstract Logger getTradeLogger();

    public abstract void fetchPosition() throws Exception;

    public abstract String getPositionAsString();

    public abstract boolean checkLiquidationEdge(Order.OrderType orderType);

    public abstract BalanceService getBalanceService();

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

    public boolean isReadyForNewOrder() {
        return true;
    }

    public boolean isReadyForArbitrage() {
        if (openOrders.stream().anyMatch(Objects::isNull)) {
            final String warnMsg = "WARNING: OO has null element";
            getTradeLogger().error(warnMsg);
            logger.error(warnMsg);
        }
        openOrders.stream()
                .filter(Objects::nonNull)
                .filter(limitOrder -> limitOrder.getTradableAmount() == null)
                .forEach(limitOrder -> {
                    final String warnMsg = "WARNING: OO amount is null. " + limitOrder.toString();
                    getTradeLogger().error(warnMsg);
                    logger.error(warnMsg);
                });
        openOrders.removeIf(Objects::isNull);
        openOrders.removeIf(limitOrder -> limitOrder.getTradableAmount() == null);

        final long openOrdersCount = openOrders.stream()
                .filter(limitOrder -> limitOrder.getTradableAmount().compareTo(BigDecimal.ZERO) != 0) // filter as for gui
                .count();
        if (openOrders.size() != openOrdersCount) {
            logger.warn("OO with zero amount: " + openOrders.stream()
                    .map(LimitOrder::toString)
                    .reduce((s, s2) -> s + "; " + s2));
        }
        return (openOrdersCount == 0 && !isBusy());
    }

    private void initEventBus() {
        eventBus.toObserverable()
                .doOnError(throwable -> logger.error("doOnError handling", throwable))
                .retry()
                .subscribe(btsEvent -> {
                    if (btsEvent == BtsEvent.MARKET_FREE) {
                        setFree();
                    } else if (btsEvent == BtsEvent.MARKET_BUSY) {
                        setBusy();
                    }
                }, throwable -> logger.error("On event handling", throwable));
    }

    public void setBusy() {
        if (this.marketState != MarketState.SWAP && this.marketState != MarketState.SWAP_AWAIT) {
            if (!isBusy()) {
                getTradeLogger().info("{} {}: busy, {}", getCounterNameNext(), getName(), getPosDiffString());
            }
            this.marketState = MarketState.ARBITRAGE;
        }
    }

    protected void setFree() {
        if (this.marketState != MarketState.SWAP && this.marketState != MarketState.SWAP_AWAIT) {
            if (isBusy()) {
//            fetchPosition(); -- deadlock
                marketState = MarketState.READY;
                getTradeLogger().info("{} {}: ready, {}", getCounterName(), getName(), getPosDiffString());
                eventBus.send(BtsEvent.MARKET_GOT_FREE);
            } else {
                logger.info("{}: already ready", getName());
            }
            if (openOrders.size() > 0) {
                getTradeLogger().info("{}: try to move openOrders, lock={}", getName(),
                        Thread.holdsLock(openOrdersLock));
                iterateOpenOrdersMove();
            }
        }
    }

    private String getPosDiffString() {
        final BigDecimal posDiff = getPosDiffService().getPositionsDiff();
        final BigDecimal bP = getArbitrageService().getFirstMarketService().getPosition().getPositionLong();
        final BigDecimal oPL = getArbitrageService().getSecondMarketService().getPosition().getPositionLong();
        final BigDecimal oPS = getArbitrageService().getSecondMarketService().getPosition().getPositionShort();
        final BigDecimal ha = getArbitrageService().getParams().getHedgeAmount();
        final BigDecimal dc = getPosDiffService().getPositionsDiffWithHedge();
        final BigDecimal mdc = getArbitrageService().getParams().getMaxDiffCorr();
        return String.format("b(%s) o(%s-%s) = %s, ha=%s, dc=%s, mdc=%s",
                Utils.withSign(bP),
                Utils.withSign(oPL),
                oPS,
                posDiff.toPlainString(),
                ha, dc, mdc
        );
    }

    protected String getCounterName() {
        return getCounterName(getArbitrageService().getCounter());
    }
    protected String getCounterNameNext() {
        return getCounterName(getArbitrageService().getCounter() + 1);
    }
    private String getCounterName(final int counter) {
        final SignalType signalType = getArbitrageService().getSignalType();
        String value;
        if (signalType == SignalType.AUTOMATIC) {
            value = String.valueOf(counter);
        } else if (signalType == SignalType.B_PRE_LIQ || signalType == SignalType.O_PRE_LIQ) {
            value = String.format("%s:%s", String.valueOf(counter), signalType.getCounterName());
        } else if (signalType == SignalType.B_CORR) {
            final Counters counters = getPersistenceService().fetchCounters();
            value = String.format("%s:%s", String.valueOf(counters.getCorrCounter1()), signalType.getCounterName());
        } else if (signalType == SignalType.O_CORR) {
            final Counters counters = getPersistenceService().fetchCounters();
            value = String.format("%s:%s", String.valueOf(counters.getCorrCounter2()), signalType.getCounterName());
        } else {
            value = signalType.getCounterName();
        }
        return "#" + value;
    }

    public boolean isBusy() {
        return marketState != MarketState.READY;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public void setMarketState(MarketState newState) {
        getTradeLogger().info("{} {} marketState: {} {}", getCounterNameNext(), getName(), newState, getPosDiffString());
        this.marketState = newState;
    }

    public MarketState getMarketState() {
        return marketState;
    }

    public abstract String getName();

    public abstract ArbitrageService getArbitrageService();

    public abstract PosDiffService getPosDiffService();

    public abstract PersistenceService getPersistenceService();

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

    public LiqInfo getLiqInfo() {
        return liqInfo;
    }

    protected void loadLiqParams() {
        LiqParams liqParams = getPersistenceService().fetchLiqParams(getName());
        if (liqParams == null) {
            liqParams = new LiqParams();
        }
        liqInfo.setLiqParams(liqParams);
    }

    protected void storeLiqParams() {
        getPersistenceService().saveLiqParams(liqInfo.getLiqParams(), getName());
    }

    public void resetLiqInfo() {
        liqInfo.getLiqParams().setDqlMin(liqInfo.getDqlCurr() != null ? liqInfo.getDqlCurr() : BigDecimal.valueOf(-10000));
        liqInfo.getLiqParams().setDqlMax(liqInfo.getDqlCurr() != null ? liqInfo.getDqlCurr() : BigDecimal.valueOf(10000));
        liqInfo.getLiqParams().setDmrlMin(liqInfo.getDmrlCurr() != null ? liqInfo.getDmrlCurr() : BigDecimal.valueOf(-10000));
        liqInfo.getLiqParams().setDmrlMax(liqInfo.getDmrlCurr() != null ? liqInfo.getDmrlCurr() : BigDecimal.valueOf(10000));

        storeLiqParams();
    }

    public abstract TradeResponse placeOrderOnSignal(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes, SignalType signalType);

    public BigDecimal getTotalPriceOfAmountToBuy(BigDecimal requiredAmountToBuy) {
        BigDecimal totalPrice = BigDecimal.ZERO;
        int index = 1;
        final LimitOrder limitOrder1 = Utils.getBestAsks(getOrderBook(), index).get(index-1);
        BigDecimal totalAmountToBuy = limitOrder1.getTradableAmount().compareTo(requiredAmountToBuy) == -1
                ? limitOrder1.getTradableAmount()
                : requiredAmountToBuy;

        totalPrice = totalPrice.add(totalAmountToBuy.multiply(limitOrder1.getLimitPrice()));

        while (totalAmountToBuy.compareTo(requiredAmountToBuy) == -1) {
            index++;
            final LimitOrder lo = Utils.getBestAsks(getOrderBook(), index).get(index-1);
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
        synchronized (openOrdersLock) {

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

                if (orderIdToSignalInfo.size() > 100) {
                    logger.warn("orderIdToSignalInfo over 100");
                    final Map<String, BestQuotes> newMap = new HashMap<>();
                    openOrders.stream()
                            .map(LimitOrder::getId)
                            .filter(id -> orderIdToSignalInfo.containsKey(id))
                            .forEach(id -> newMap.put(id, orderIdToSignalInfo.get(id)));
                    orderIdToSignalInfo = newMap;
                }

            }
        }
        return openOrders;
    }

    public Optional<Order> getOrderInfoAttempts(String orderId, String counterName, String logInfoId) throws InterruptedException, IOException {
        final TradeService tradeService = getExchange().getTradeService();
        Order orderInfo = null;
        for (int i = 0; i < 20; i++) { // about 11 sec
            try {
                // 2. check status of the order
                long sleepTime = 200;
                if (i > 5) {
                    sleepTime = 2000;
                }
                Thread.sleep(sleepTime);
                final Collection<Order> order = tradeService.getOrder(orderId);
                if (order.isEmpty()) {
                    final String message = String.format("%s/%s %s orderId=%s, error: %s",
                            counterName, i,
                            logInfoId,
                            orderId, "Market did not return info by orderId");
                    getTradeLogger().error(message);
                    continue;
                }
                orderInfo = order.iterator().next();

                if (orderInfo.getStatus().equals(Order.OrderStatus.FILLED)) {
                    break;
                }

                getTradeLogger().error("{}/{} {} {} status={}, avgPrice={}, orderId={}, type={}, cumAmount={}",
                        counterName, i,
                        logInfoId,
                        Utils.convertOrderTypeName(orderInfo.getType()),
                        orderInfo.getStatus().toString(),
                        orderInfo.getAveragePrice().toPlainString(),
                        orderInfo.getId(),
                        orderInfo.getType(),
                        orderInfo.getCumulativeAmount().toPlainString());
            } catch (Exception e) {
                final String message = String.format("%s/%s %s orderId=%s, error: %s",
                        counterName, i,
                        logInfoId,
                        orderId, e.toString());
                getTradeLogger().error(message);
                logger.error(message, e);
            }
        }
        return Optional.ofNullable(orderInfo);
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

    private void initOpenOrdersMovingSubscription() {
        openOrdersMovingSubscription = getArbitrageService().getSignalEventBus().toObserverable()
                .sample(100, TimeUnit.MILLISECONDS)
                .subscribe(signalEvent -> {
                    if (signalEvent == SignalEvent.B_ORDERBOOK_CHANGED
                            || signalEvent == SignalEvent.O_ORDERBOOK_CHANGED) {
                        checkOpenOrdersForMoving();
                    }
                }, throwable -> logger.error("{} openOrdersMovingSubscription error", getName(), throwable));
    }

    protected void checkOpenOrdersForMoving() {
//        debugLog.info(getName() + ":checkOpenOrdersForMoving");
        if (specialFlags != SpecialFlags.STOP_MOVING) {
            iterateOpenOrdersMove();
        }
    }

    abstract protected void iterateOpenOrdersMove();

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

    protected BigDecimal createBestMakerPrice(Order.OrderType orderType) {
        BigDecimal thePrice = BigDecimal.ZERO;
        if (orderType == Order.OrderType.BID
                || orderType == Order.OrderType.EXIT_ASK) {
            thePrice = Utils.getBestBid(getOrderBook()).getLimitPrice();
        } else if (orderType == Order.OrderType.ASK
                || orderType == Order.OrderType.EXIT_BID) {
            thePrice = Utils.getBestAsk(getOrderBook()).getLimitPrice();
        }
        if (thePrice.signum() == 0) {
            getTradeLogger().info("WARNING: PRICE IS 0");
        }

        return thePrice;
    }

    protected MoveResponse moveMakerOrderIfNotFirst(LimitOrder limitOrder, SignalType signalType) {
        MoveResponse response;
        BigDecimal bestPrice;

        if (specialFlags == SpecialFlags.STOP_MOVING) {
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
        return specialFlags == SpecialFlags.STOP_MOVING;
    }

    public void setMovingStop(boolean shouldStopMoving) {
        if (shouldStopMoving) {
            specialFlags = SpecialFlags.STOP_MOVING;
        } else {
            specialFlags = SpecialFlags.NONE;
        }
    }

    public BigDecimal getAffordableContractsForShort() {
        return affordableContractsForShort;
    }

    public BigDecimal getAffordableContractsForLong() {
        return affordableContractsForLong;
    }
}
