package com.bitplay.market;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BestQuotes;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.dto.FullBalance;
import com.bitplay.market.dto.LiqInfo;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.EventBus;
import com.bitplay.market.events.SignalEvent;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.Counters;
import com.bitplay.persistance.domain.LiqParams;
import com.bitplay.persistance.domain.settings.SysOverloadArgs;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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

    protected final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    // Moving timeout
    private volatile ScheduledFuture<?> scheduledOverloadReset;
    private volatile PlaceOrderArgs placeOrderArgs;
    private volatile MarketState marketState = MarketState.READY;
    private volatile Instant readyTime = Instant.now();

    private volatile SpecialFlags specialFlags = SpecialFlags.NONE;
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

    public boolean isReadyForArbitrage() {
        if (getMarketState() == MarketState.STOPPED || isBusy()) {
            return false;
        }

        long openOrdersCount;
        synchronized (openOrdersLock) {

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

            openOrdersCount = openOrders.stream()
                    .filter(limitOrder -> limitOrder.getTradableAmount().compareTo(BigDecimal.ZERO) != 0) // filter as for gui
                    .count();
            if (openOrders.size() != openOrdersCount) {
                logger.warn("OO with zero amount: " + openOrders.stream()
                        .map(LimitOrder::toString)
                        .reduce((s, s2) -> s + "; " + s2));
            }
        } //synchronized (openOrdersLock)

        return openOrdersCount == 0;
    }

    private void initEventBus() {
        eventBus.toObserverable()
                .doOnError(throwable -> logger.error("doOnError handling", throwable))
                .retry()
                .subscribe(btsEvent -> {
                    if (btsEvent == BtsEvent.MARKET_FREE_FROM_UI) {
                        setFree("UI");
                    } else if (btsEvent == BtsEvent.MARKET_FREE) {
                        setFree();
                    } else if (btsEvent == BtsEvent.MARKET_BUSY) {
                        setBusy();
                    }
                }, throwable -> logger.error("On event handling", throwable));
    }

    public void setBusy() {
        if (this.marketState == MarketState.STOPPED) {
            return;
        }
        if (this.marketState != MarketState.SWAP && this.marketState != MarketState.SWAP_AWAIT) {
            if (!isBusy()) {
                getTradeLogger().info("{} {}: busy, {}", getCounterNameNext(), getName(), getPosDiffString());
            }
            this.marketState = MarketState.ARBITRAGE;
        }
    }

    protected void setFree(String... flags) {
        switch (marketState) {
            case SWAP:
            case SWAP_AWAIT:
                // do nothing
                break;
            case WAITING_ARB:
            case MOVING:
            case TAKER_IN_PROGRESS:
            case STOPPED:
                if (flags != null && flags.length > 0 && flags[0].equals("UI")) {
                    logger.info("reset STOPPED from UI");
                    setMarketState(MarketState.READY);
                }
                break;
            case SYSTEM_OVERLOADED:
                if (flags != null && flags.length > 0 && flags[0].equals("UI")) {
                    logger.info("reset SYSTEM_OVERLOADED from UI");
                    resetOverload();
                } else {
                    logger.info("Free attempt when SYSTEM_OVERLOADED");
                }
                break;

            case ARBITRAGE:
//            fetchPosition(); -- deadlock
                setMarketState(MarketState.READY);
                eventBus.send(BtsEvent.MARKET_GOT_FREE);

                iterateOpenOrdersMove();
                break;

            case READY:
                logger.warn("{}: already ready. Iterate OO.", getName());

                iterateOpenOrdersMove(); // TODO we should not have such cases
                break;

            default:
                throw new IllegalStateException("Unhandled market state");
        }

    }

    protected void setOverloaded(final PlaceOrderArgs placeOrderArgs) {
        final MarketState currMarketState = getMarketState();
        if (currMarketState == MarketState.STOPPED) {
            // do nothing
            return;
        }

        final String changeStat = String.format("%s change status from %s to %s",
                getCounterName(),
                currMarketState,
                MarketState.SYSTEM_OVERLOADED);
        getTradeLogger().warn(changeStat);
        warningLogger.warn(changeStat);
        logger.warn(changeStat);

        setMarketState(MarketState.SYSTEM_OVERLOADED);
        this.placeOrderArgs = placeOrderArgs;

        final SysOverloadArgs sysOverloadArgs = getPersistenceService().getSettingsRepositoryService()
                .getSettings().getBitmexSysOverloadArgs();

        scheduledOverloadReset = scheduler.schedule(this::resetOverload, sysOverloadArgs.getOverloadTimeSec(), TimeUnit.SECONDS);
    }

    private void resetOverload() {
        final MarketState currMarketState = getMarketState();
        if (currMarketState == MarketState.STOPPED) {
            // do nothing
            return;

        } else if (currMarketState == MarketState.SYSTEM_OVERLOADED) {

            MarketState marketStateToSet;
            synchronized (openOrdersLock) {
                marketStateToSet = (openOrders.size() > 0 || placeOrderArgs != null) // moving or placing attempt
                        ? MarketState.ARBITRAGE
                        : MarketState.READY;
            }

            final String backWarn = String.format("%s change status from %s to %s",
                    getCounterName(),
                    MarketState.SYSTEM_OVERLOADED,
                    marketStateToSet);
            getTradeLogger().warn(backWarn);
            warningLogger.warn(backWarn);
            logger.warn(backWarn);

            setMarketState(marketStateToSet);

            // Place order if it was placing
            if (placeOrderArgs != null) {
                placeOrder(PlaceOrderArgs.nextPlacingArgs(placeOrderArgs));
                placeOrderArgs = null;
            }
        }
    }

    public String getTimeToReset() {
        String secLeft = "";
        if (scheduledOverloadReset != null && !scheduledOverloadReset.isDone()) {
            secLeft = String.valueOf(scheduledOverloadReset.getDelay(TimeUnit.SECONDS));
        }
        return secLeft;
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

    public boolean isReadyForMoving() {
        return marketState != MarketState.SYSTEM_OVERLOADED && marketState != MarketState.STOPPED;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public void setMarketStateNextCounter(MarketState newState) {
        setMarketState(newState, getCounterNameNext());
    }

    public void setMarketState(MarketState newState) {
        setMarketState(newState, getCounterName());
    }

    public void setMarketState(MarketState newState, String counterName) {
        getTradeLogger().info("{} {} marketState: {} {}", counterName, getName(), newState, getPosDiffString());
        this.marketState = newState;
        this.readyTime = Instant.now();
    }

    public MarketState getMarketState() {
        return marketState;
    }

    public Instant getReadyTime() {
        return readyTime;
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

    public FullBalance calcFullBalance() {
        return getBalanceService().recalcAndGetAccountInfo(accountInfoContracts, position, orderBook);
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

    public abstract TradeResponse placeOrder(final PlaceOrderArgs placeOrderArgs);

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
        List<LimitOrder> limitOrders;
        synchronized (openOrdersLock) {
            limitOrders = openOrders != null ? openOrders : new ArrayList<>();
        }
        return limitOrders;
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
        } // synchronized (openOrdersLock)
        return openOrders;
    }

    public Optional<Order> getOrderInfoAttempts(String orderId, String counterName, String logInfoId) throws InterruptedException {
        Optional<Order> orderInfo = Optional.empty();
        for (int i = 0; i < 20; i++) { // about 11 sec
            long sleepTime = 200;
            if (i > 5) {
                sleepTime = 2000;
            }
            Thread.sleep(sleepTime);

            orderInfo = getOrderInfo(orderId, counterName, i, logInfoId);
            if (orderInfo.isPresent()) {
                break;
            }
        }
        return orderInfo;
    }

    protected Optional<Order> getOrderInfo(String orderId, String counterName, int attemptCount, String logInfoId) {
        final TradeService tradeService = getExchange().getTradeService();
        Order orderInfo = null;
        try {
            final Collection<Order> order = tradeService.getOrder(orderId);
            if (order.isEmpty()) {
                final String message = String.format("%s/%s %s orderId=%s, error: %s",
                        counterName, attemptCount,
                        logInfoId,
                        orderId, "Market did not return info by orderId");
                getTradeLogger().error(message);
            }
            orderInfo = order.iterator().next();

            if (!orderInfo.getStatus().equals(Order.OrderStatus.FILLED)) {
                getTradeLogger().error("{}/{} {} {} status={}, avgPrice={}, orderId={}, type={}, cumAmount={}",
                        counterName, attemptCount,
                        logInfoId,
                        Utils.convertOrderTypeName(orderInfo.getType()),
                        orderInfo.getStatus() != null ? orderInfo.getStatus().toString() : null,
                        orderInfo.getAveragePrice() != null ? orderInfo.getAveragePrice().toPlainString() : null,
                        orderInfo.getId(),
                        orderInfo.getType(),
                        orderInfo.getCumulativeAmount() != null ? orderInfo.getCumulativeAmount().toPlainString() : null);
            }
        } catch (Exception e) {
            final String message = String.format("%s/%s %s orderId=%s, error: %s",
                    counterName, attemptCount,
                    logInfoId,
                    orderId, e.toString());
            getTradeLogger().error(message);
            logger.error(message, e);
        }
        return Optional.ofNullable(orderInfo);
    }

    private void initOpenOrdersMovingSubscription() {
        openOrdersMovingSubscription = getArbitrageService().getSignalEventBus().toObserverable()
                .sample(100, TimeUnit.MILLISECONDS)
                .subscribe(signalEvent -> {
                    try {
                        if (signalEvent == SignalEvent.B_ORDERBOOK_CHANGED
                                || signalEvent == SignalEvent.O_ORDERBOOK_CHANGED) {
                            checkOpenOrdersForMoving();
                        }
                    } catch (Exception e) {
                        logger.error("{} openOrdersMovingSubscription error", getName(), e);
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
            synchronized (openOrdersLock) {
                this.openOrders = orderList;

                response = this.openOrders.stream()
                        .filter(limitOrder -> limitOrder.getId().equals(orderId))
                        .findFirst()
                        .map(limitOrder -> moveMakerOrderIfNotFirst(limitOrder, signalType))
                        .orElseGet(() -> new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "can not find in openOrders list"));
            }
        }
        return response;
    }

    public abstract MoveResponse moveMakerOrder(LimitOrder limitOrder, SignalType signalType, BigDecimal newPrice);

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

        if (specialFlags == SpecialFlags.STOP_MOVING) {
            response = new MoveResponse(MoveResponse.MoveOrderStatus.WAITING_TIMEOUT, "");
        } else if ((limitOrder.getType() == Order.OrderType.ASK && limitOrder.getLimitPrice().compareTo(bestAsk) == 0)
                || (limitOrder.getType() == Order.OrderType.BID && limitOrder.getLimitPrice().compareTo(bestBid) == 0)) {

            response = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_FIRST, "");
        } else {

            BigDecimal bestPrice = createBestMakerPrice(limitOrder.getType());

            if (bestPrice.signum() == 0) {
                response = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "bestPrice is 0");
            } else if (limitOrder.getLimitPrice().compareTo(bestPrice) != 0) { // if we need moving
                logger.info("{} Try to move maker order {} {}, from {} to {}",
                        getName(), limitOrder.getId(), limitOrder.getType(),
                        limitOrder.getLimitPrice(), bestPrice);
                response = moveMakerOrder(limitOrder, signalType, bestPrice);
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
