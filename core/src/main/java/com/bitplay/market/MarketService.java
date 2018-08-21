package com.bitplay.market;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.events.SignalEvent;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.EventBus;
import com.bitplay.market.model.Affordable;
import com.bitplay.market.model.FullBalance;
import com.bitplay.market.model.LiqInfo;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.domain.LiqParams;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.SysOverloadArgs;
import com.bitplay.utils.Utils;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.AccountInfo;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.ContractIndex;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;

/**
 * Created by Sergey Shurmin on 4/16/17.
 */
public abstract class MarketService extends MarketServiceOpenOrders {

    protected static final int MAX_ATTEMPTS_CANCEL = 90;
    protected static final BigDecimal DQL_WRONG = BigDecimal.valueOf(-100);

    private static final int ORDERBOOK_MAX_SIZE = 20;
    protected BigDecimal bestBid = BigDecimal.ZERO;
    protected BigDecimal bestAsk = BigDecimal.ZERO;
    protected volatile OrderBook orderBook = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
    protected volatile OrderBook orderBookForPrice = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
    protected final static Object orderBookLock = new Object();
    protected final static Object orderBookForPriceLock = new Object();
    protected volatile AccountInfo accountInfo = null;
    protected volatile AccountInfoContracts accountInfoContracts = new AccountInfoContracts();
    protected volatile Position position = new Position(null, null, null, null, "");
    protected volatile Affordable affordable = new Affordable();
    protected volatile ContractIndex contractIndex = new ContractIndex(BigDecimal.ZERO, new Date());
    protected volatile Ticker ticker;
    protected volatile int usdInContract = 0;

    protected final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    // Moving timeout
    private volatile ScheduledFuture<?> scheduledOverloadReset;
    private volatile PlaceOrderArgs placeOrderArgs;
    private volatile MarketState marketState = MarketState.READY;
    private volatile Instant readyTime = Instant.now();

    protected volatile boolean shouldStopPlacing;

    private volatile SpecialFlags specialFlags = SpecialFlags.NONE;
    protected EventBus eventBus = new EventBus();
    protected volatile LiqInfo liqInfo = new LiqInfo();

    Disposable openOrdersMovingSubscription;

    public void init(String key, String secret, ContractType contractType) {
        initEventBus();
        initOpenOrdersMovingSubscription();
        initializeMarket(key, secret, contractType);
    }

    protected abstract void initializeMarket(String key, String secret, ContractType contractType);

    public abstract UserTrades fetchMyTradeHistory();

    public OrderBook getOrderBookForPrice() {
        return getOrderBook();
    }

    public OrderBook getOrderBook() {
        OrderBook orderBook;
        synchronized (orderBookLock) {
            orderBook = getShortOrderBook(this.orderBook);
        }
        return orderBook;
    }

    protected OrderBook getShortOrderBook(OrderBook orderBook) {
        List<LimitOrder> asks = orderBook.getAsks().size() > ORDERBOOK_MAX_SIZE
                ? orderBook.getAsks().stream().limit(ORDERBOOK_MAX_SIZE).collect(Collectors.toList())
                : orderBook.getAsks();
        List<LimitOrder> bids = orderBook.getBids().size() > ORDERBOOK_MAX_SIZE
                ? orderBook.getBids().stream().limit(ORDERBOOK_MAX_SIZE).collect(Collectors.toList())
                : orderBook.getBids();

        return new OrderBook(orderBook.getTimeStamp(),
                new ArrayList<>(asks),
                new ArrayList<>(bids));
    }

    protected OrderBook getFullOrderBook() {
        OrderBook orderBook;
        synchronized (orderBookLock) {
            orderBook = new OrderBook(this.orderBook.getTimeStamp(),
                    new ArrayList<>(this.orderBook.getAsks()),
                    new ArrayList<>(this.orderBook.getBids()));
        }
        return orderBook;
    }

    protected OrderBook getFullOrderBookForPrice() {
        OrderBook orderBook;
        synchronized (orderBookForPriceLock) {
            orderBook = new OrderBook(this.orderBookForPrice.getTimeStamp(),
                    new ArrayList<>(this.orderBookForPrice.getAsks()),
                    new ArrayList<>(this.orderBookForPrice.getBids()));
        }
        return orderBook;
    }

    public abstract String fetchPosition() throws Exception;

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

    public abstract Affordable recalcAffordable();

    public Affordable getAffordable() {
        return affordable;
    }

    public BigDecimal calcBtcInContract() {
        if (contractIndex.getIndexPrice() != null && contractIndex.getIndexPrice().signum() != 0) {
            return BigDecimal.valueOf(usdInContract).divide(contractIndex.getIndexPrice(), 4, BigDecimal.ROUND_HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    public boolean isReadyForArbitrage() {
        if (isBusy()) {
            return false;
        }

        return !hasOpenOrders();
    }

    private void initEventBus() {
        Disposable subscribe = eventBus.toObserverable()
                .doOnError(throwable -> logger.error("doOnError handling", throwable))
                .retry()
                .subscribe(btsEvent -> {
                    if (btsEvent == BtsEvent.MARKET_FREE_FROM_UI) {
                        setFree("UI");
                    } else if (btsEvent == BtsEvent.MARKET_FREE_FROM_CHECKER) {
                        setFree("CHECKER");
                    } else if (btsEvent == BtsEvent.MARKET_FREE) {
                        setFree();
                    }
                }, throwable -> logger.error("On event handling", throwable));
    }

    public void setBusy() {
        if (isMarketStopped()) {
            return;
        }
        if (this.marketState != MarketState.SWAP && this.marketState != MarketState.SWAP_AWAIT) {
            if (!isBusy()) {
                getTradeLogger().info("#{} {}: busy, {}", getCounterNameNext(), getName(), getPosDiffString());
            }
            this.marketState = MarketState.ARBITRAGE;
        }
    }

    protected void setFree(String... flags) {
        switch (marketState) {
            case SWAP:
            case SWAP_AWAIT:
                // do nothing
                if (flags != null && flags.length > 0 && flags[0].equals("UI")) {
                    logger.info("reset {} from UI", marketState);
                    setMarketState(MarketState.READY);
                } else {
                    logger.info("Free attempt when {}", marketState);
                }
                break;
            case WAITING_ARB:
            case MOVING:
            case PLACING_ORDER:
            case STOPPED:
            case FORBIDDEN:
                if (flags != null && flags.length > 0 && flags[0].equals("UI")) {
                    logger.info("reset {} from UI", marketState);
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
                if (getArbitrageService().getSignalType().isCorr()) {
                    warningLogger.info("WARN: finishCorr from unusual place");
                    getPosDiffService().finishCorr(true);
                }

                iterateOpenOrdersMove();
                break;

            case READY:
                if (flags != null && flags.length > 0 && flags[0].equals("CHECKER")) {
                    // do nothing
                } else {
                    logger.warn("{}: already ready. Iterate OO.", getName());
                }

                iterateOpenOrdersMove(); // TODO we should not have such cases
                break;

            default:
                throw new IllegalStateException("Unhandled market state");
        }

    }

    protected void setOverloaded(final PlaceOrderArgs placeOrderArgs) {
        final MarketState currMarketState = getMarketState();
        if (isMarketStopped()) {
            // do nothing
            return;
        }

        final String changeStat = String.format("#%s change status from %s to %s",
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

    protected abstract void postOverload();

    private void resetOverload() {
        postOverload();

        final MarketState currMarketState = getMarketState();
        if (isMarketStopped()) {
            // do nothing
            return;

        } else if (currMarketState == MarketState.SYSTEM_OVERLOADED) {

            MarketState marketStateToSet;
            synchronized (openOrdersLock) {
                marketStateToSet = (hasOpenOrders() || placeOrderArgs != null) // moving or placing attempt
                        ? MarketState.ARBITRAGE
                        : MarketState.READY;
            }

            final String backWarn = String.format("#%s change status from %s to %s",
                    getCounterName(),
                    MarketState.SYSTEM_OVERLOADED,
                    marketStateToSet);
            getTradeLogger().warn(backWarn);
            warningLogger.warn(backWarn);
            logger.warn(backWarn);

            setMarketState(marketStateToSet);

            // Place order if it was placing
            if (placeOrderArgs != null && !shouldStopPlacing) {
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
        String res = "";
        try {
            final BigDecimal posDiff = getPosDiffService().getPositionsDiffSafe();
            final BigDecimal bP = getArbitrageService().getFirstMarketService().getPosition().getPositionLong();
            final BigDecimal oPL = getArbitrageService().getSecondMarketService().getPosition().getPositionLong();
            final BigDecimal oPS = getArbitrageService().getSecondMarketService().getPosition().getPositionShort();
            final BigDecimal ha = getArbitrageService().getParams().getHedgeAmount();
            final BigDecimal dc = getPosDiffService().getPositionsDiffWithHedge();
            final BigDecimal mdc = getArbitrageService().getParams().getMaxDiffCorr();
            res = String.format("b(%s) o(%s-%s) = %s, ha=%s, dc=%s, mdc=%s",
                    Utils.withSign(bP),
                    Utils.withSign(oPL),
                    oPS,
                    posDiff.toPlainString(),
                    ha, dc, mdc
            );
        } catch (Exception e) {
            logger.error("Error in Position.", e);
        }
        return res;
    }

    public String getCounterName() {
        final SignalType signalType = getArbitrageService().getSignalType();
        return getCounterName(getArbitrageService().getCounter(), signalType);
    }

    public String getCounterName(SignalType signalType) {
        return getCounterName(getArbitrageService().getCounter(), signalType);
    }

    protected String getCounterNameNext() {
        final SignalType signalType = getArbitrageService().getSignalType();
        return getCounterName(getArbitrageService().getCounter() + 1, signalType);
    }

    private String getCounterName(final int counter, SignalType signalType) {
        String value;
        if (signalType == SignalType.AUTOMATIC) {
            value = String.valueOf(counter);
        } else if (signalType.isPreliq()) {
            final CorrParams corrParams = getPersistenceService().fetchCorrParams();
            value = String.format("%s:%s", String.valueOf(corrParams.getPreliq().getTotalCount()), signalType.getCounterName());
        } else if (signalType.isCorr()) {
            final CorrParams corrParams = getPersistenceService().fetchCorrParams();
            value = String.format("%s:%s", String.valueOf(corrParams.getCorr().getTotalCount()), signalType.getCounterName());
        } else {
            value = signalType.getCounterName();
        }
        return value;
    }

    public boolean isBusy() {
        return marketState != MarketState.READY;
    }

    public boolean isReadyForMoving() {
        return marketState != MarketState.SYSTEM_OVERLOADED && !marketState.isStopped();
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
        getTradeLogger().info("#{} {} marketState: {} {}", counterName, getName(), newState, getPosDiffString());
        this.marketState = newState;
        if (newState == MarketState.READY) {
            this.readyTime = Instant.now();
            onReadyState();
        }
    }

    public MarketState getMarketState() {
        return marketState;
    }

    public abstract boolean isMarketStopped();

    public Instant getReadyTime() {
        return readyTime;
    }

    public abstract String getName();

    public String getFuturesContractName() {
        return "";
    }

    public abstract ArbitrageService getArbitrageService();

    public abstract PosDiffService getPosDiffService();

    public AccountInfo getAccountInfo() {
        return accountInfo;
    }

    public void setAccountInfo(AccountInfo accountInfo) {
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

    public Ticker getTicker() {
        return ticker;
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
        liqInfo.getLiqParams().setDqlMin(BigDecimal.valueOf(10000));
        liqInfo.getLiqParams().setDqlMax(BigDecimal.valueOf(-10000));
        liqInfo.getLiqParams().setDmrlMin(liqInfo.getDmrlCurr() != null ? liqInfo.getDmrlCurr() : BigDecimal.valueOf(10000));
        liqInfo.getLiqParams().setDmrlMax(liqInfo.getDmrlCurr() != null ? liqInfo.getDmrlCurr() : BigDecimal.valueOf(-10000));

        storeLiqParams();
    }

    public abstract TradeResponse placeOrderOnSignal(Order.OrderType orderType, BigDecimal amountInContracts, BestQuotes bestQuotes, SignalType signalType);

    public abstract TradeResponse placeOrder(final PlaceOrderArgs placeOrderArgs);

    public BigDecimal getTotalPriceOfAmountToBuy(BigDecimal requiredAmountToBuy) {
        BigDecimal totalPrice = BigDecimal.ZERO;
        int index = 1;
        final LimitOrder limitOrder1 = Utils.getBestAsks(getOrderBook(), index).get(index - 1);
        BigDecimal totalAmountToBuy = limitOrder1.getTradableAmount().compareTo(requiredAmountToBuy) == -1
                ? limitOrder1.getTradableAmount()
                : requiredAmountToBuy;

        totalPrice = totalPrice.add(totalAmountToBuy.multiply(limitOrder1.getLimitPrice()));

        while (totalAmountToBuy.compareTo(requiredAmountToBuy) == -1) {
            index++;
            final LimitOrder lo = Utils.getBestAsks(getOrderBook(), index).get(index - 1);
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

    /**
     * Refresh all openOrders
     *
     * @return list of open orders.
     */
    protected List<FplayOrder> fetchOpenOrders() {
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

                    synchronized (openOrdersLock) {
                        this.openOrders = fetchedList.stream()
                                .map(limitOrder ->
                                        this.openOrders.stream()
                                                .filter(ord -> ord.getOrderId().equals(limitOrder.getId()))
                                                .findAny()
                                                .map(fOrd -> new FplayOrder(fOrd.getCounterName(), limitOrder, fOrd.getBestQuotes(), fOrd.getPlacingType(),
                                                        fOrd.getSignalType()))
                                                .orElseGet(() -> new FplayOrder(getCounterName(), (limitOrder), null, null, null)))
                                .collect(Collectors.toList());
                    }

                    // updateOpenOrders(fetchedList); - Don't use incremental update

                } catch (Exception e) {
                    logger.error("GetOpenOrdersError", e);
                    throw new IllegalStateException("GetOpenOrdersError", e);
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
                sleepTime = 500 * i;
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
        return getOrderInfo(orderId, counterName, attemptCount, logInfoId, getTradeLogger());
    }

    protected Optional<Order> getOrderInfo(String orderId, String counterName, int attemptCount, String logInfoId, Logger customLogger) {
        final String[] orderIds = {orderId};
        final Collection<Order> orderInfos = getOrderInfos(orderIds, counterName, attemptCount, logInfoId, customLogger);
        return orderInfos.isEmpty() ? Optional.empty() : Optional.ofNullable(orderInfos.iterator().next());
    }

    protected Collection<Order> getOrderInfos(String[] orderIds, String counterName, int attemptCount, String logInfoId, Logger customLogger) {
        final TradeService tradeService = getExchange().getTradeService();
        Collection<Order> orders = new ArrayList<>();
        try {
            orders = tradeService.getOrder(orderIds);
            if (orders.isEmpty()) {
                final String message = String.format("#%s/%s %s orderIds=%s, error: %s",
                        counterName, attemptCount,
                        logInfoId,
                        Arrays.toString(orderIds), "Market did not return info by orderIds");
                customLogger.error(message);
            } else {
                for (Order orderInfo : orders) {
                    orderInfo = orders.iterator().next();
                    if (!orderInfo.getStatus().equals(Order.OrderStatus.FILLED)) {
                        customLogger.info("#{}/{} {} {} status={}, avgPrice={}, orderId={}, type={}, cumAmount={}",
                                counterName, attemptCount,
                                logInfoId,
                                Utils.convertOrderTypeName(orderInfo.getType()),
                                orderInfo.getStatus() != null ? orderInfo.getStatus().toString() : null,
                                orderInfo.getAveragePrice() != null ? orderInfo.getAveragePrice().toPlainString() : null,
                                orderInfo.getId(),
                                orderInfo.getType(),
                                orderInfo.getCumulativeAmount() != null ? orderInfo.getCumulativeAmount().toPlainString() : null);
                    }
                }
            }
        } catch (Exception e) {
            final String message = String.format("#%s/%s %s orderIds=%s, error: %s",
                    counterName, attemptCount,
                    logInfoId,
                    Arrays.toString(orderIds), e.toString());
            customLogger.error(message);
            logger.error(message, e);
        }
        return orders;
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
                    } catch (NotYetInitializedException e) {
                        // do nothing
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

    abstract protected void onReadyState();

    public MoveResponse moveMakerOrderFromGui(String orderId) {
        MoveResponse response;

        List<FplayOrder> orderList = null;
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
                        .filter(order -> order.getOrder().getId().equals(orderId))
                        .findFirst()
                        .map(this::moveMakerOrderIfNotFirst)
                        .orElseGet(() -> new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "can not find in openOrders list"));
            }
        }
        return response;
    }

    public abstract MoveResponse moveMakerOrder(FplayOrder fplayOrder, BigDecimal newPrice);

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
            warningLogger.warn(getName() + " WARNING: PRICE IS 0");
        }

        return thePrice;
    }

    protected BigDecimal createBestHybridPrice(Order.OrderType orderType) {
        BigDecimal thePrice = BigDecimal.ZERO;
        if (orderType == Order.OrderType.BID
                || orderType == Order.OrderType.EXIT_ASK) {
            thePrice = Utils.getBestAsk(getOrderBook()).getLimitPrice();
        } else if (orderType == Order.OrderType.ASK
                || orderType == Order.OrderType.EXIT_BID) {
            thePrice = Utils.getBestBid(getOrderBook()).getLimitPrice();
        }
        if (thePrice.signum() == 0) {
            getTradeLogger().info("WARNING: PRICE IS 0");
        }

        return thePrice;
    }

    protected MoveResponse moveMakerOrderIfNotFirst(FplayOrder fplayOrder) {
        MoveResponse response;
        LimitOrder limitOrder = (LimitOrder) fplayOrder.getOrder();
        if (limitOrder.getLimitPrice() == null) {
            final FplayOrder one = getPersistenceService().getOrderRepositoryService().findOne(limitOrder.getId());
            if (one != null) {
                final LimitOrder fromDb = (LimitOrder) one.getOrder();
                if (fromDb.getLimitPrice() == null) {
                    return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "limitPrice is null");
                }
                limitOrder = fromDb;
            }
        }

        if (specialFlags == SpecialFlags.STOP_MOVING) {
            response = new MoveResponse(MoveResponse.MoveOrderStatus.WAITING_TIMEOUT, "");
        } else if ((limitOrder.getType() == Order.OrderType.ASK && limitOrder.getLimitPrice().compareTo(bestAsk) == 0)
                || (limitOrder.getType() == Order.OrderType.BID && limitOrder.getLimitPrice().compareTo(bestBid) == 0)) {

            response = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_FIRST, "");
        } else {

            BigDecimal bestPrice = createBestMakerPrice(limitOrder.getType());

            if (bestPrice.signum() == 0) {
                response = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "bestPrice is 0");

                // do not move from ASK1 to ASK2 ==> trigger on ASK and newPrice < oldPrice
                // do not move from BID1 to BID2 ==> trigger on BID and newPrice > oldPrice
            } else if (
                    ((limitOrder.getType() == Order.OrderType.ASK || limitOrder.getType() == Order.OrderType.EXIT_BID)
                            && bestPrice.compareTo(limitOrder.getLimitPrice()) < 0)
                            ||
                            ((limitOrder.getType() == Order.OrderType.BID || limitOrder.getType() == Order.OrderType.EXIT_ASK)
                                    && bestPrice.compareTo(limitOrder.getLimitPrice()) > 0)
                    ) {
//            } else if (limitOrder.getLimitPrice().compareTo(bestPrice) != 0) { // if we need moving
//                debugLog.info("{} Try to move maker order {} {}, from {} to {}",
//                        getName(), limitOrder.getId(), limitOrder.getType(),
//                        limitOrder.getLimitPrice(), bestPrice);
                response = moveMakerOrder(fplayOrder, bestPrice);
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

                if (noSleep) {
                    sleep(10);
                } else {
                    sleep(2000);
                }
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

    public void stopAllActions() {
        shouldStopPlacing = true;

        if (!hasOpenOrders()) {
            String msg = String.format("%s: StopAllActions", getName());
            warningLogger.info(msg);
            getTradeLogger().info(msg);
        } else {
            StringBuilder orderIds = new StringBuilder();
            synchronized (openOrdersLock) {
                openOrders.stream()
                        .map(FplayOrder::getOrder)
                        .filter(order -> order.getStatus() == Order.OrderStatus.NEW
                                || order.getStatus() == Order.OrderStatus.PARTIALLY_FILLED
                                || order.getStatus() == Order.OrderStatus.PENDING_NEW
                                || order.getStatus() == Order.OrderStatus.PENDING_CANCEL
                                || order.getStatus() == Order.OrderStatus.PENDING_REPLACE
                        ).forEach(order -> orderIds.append(order.getId()).append(","));
            }

            String msg = String.format("%s: StopAllActions: CancelAllOpenOrders=%s", getName(), orderIds.toString());
            warningLogger.info(msg);
            getTradeLogger().info(msg);

            cancelAllOrders("StopAllActions: CancelAllOpenOrders");
        }

        if (getMarketState() != MarketState.READY) {
            setMarketState(MarketState.READY);
        }
    }

    public boolean cancelAllOrders(String logInfoId) {
        return false;
    }
}
