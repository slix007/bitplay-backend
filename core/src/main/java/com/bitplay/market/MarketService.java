package com.bitplay.market;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.DelayTimer;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.events.SignalEvent;
import com.bitplay.arbitrage.events.SignalEventEx;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.BtsEventBox;
import com.bitplay.market.events.EventBus;
import com.bitplay.market.model.Affordable;
import com.bitplay.market.model.FullBalance;
import com.bitplay.market.model.LiqInfo;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.domain.LiqParams;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.SysOverloadArgs;
import com.bitplay.utils.Utils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import org.knowm.xchange.dto.Order.OrderType;
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
import org.slf4j.LoggerFactory;

/**
 * Created by Sergey Shurmin on 4/16/17.
 */
public abstract class MarketService extends MarketServiceOpenOrders {

    private final static Logger logger = LoggerFactory.getLogger(MarketService.class);

    protected static final int MAX_ATTEMPTS_CANCEL = 90;

    private static final int ORDERBOOK_MAX_SIZE = 20;
    protected BigDecimal bestBid = BigDecimal.ZERO;
    protected BigDecimal bestAsk = BigDecimal.ZERO;
    protected volatile OrderBook orderBook = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
    protected volatile OrderBook orderBookXBTUSD = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
    protected final Object orderBookLock = new Object();
    protected final Object orderBookForPriceLock = new Object();
    protected volatile AccountInfo accountInfo = null;
    protected volatile AccountInfoContracts accountInfoContracts = new AccountInfoContracts();
    protected volatile Position position = new Position(null, null, null, BigDecimal.ZERO, "");
    protected volatile Position positionXBTUSD = new Position(null, null, null, BigDecimal.ZERO, "");
    protected volatile Affordable affordable = new Affordable();
    protected final Object contractIndexLock = new Object();
    protected volatile ContractIndex contractIndex = new ContractIndex(BigDecimal.ZERO, new Date());
    protected volatile ContractIndex btcContractIndex = new ContractIndex(BigDecimal.ZERO, new Date());
    protected volatile Ticker ticker;
    protected volatile Ticker ethBtcTicker;
    protected volatile int usdInContract = 0;
    protected final DelayTimer dtPreliq = new DelayTimer();

    protected final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3,
            new ThreadFactoryBuilder().setNameFormat(getName() + "-scheduler-%d").build());
    protected final Scheduler obSingleExecutor = Schedulers.from(Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat(getName() + "-ob-executor-%d").build()));
    protected final Scheduler ooSingleExecutor = Schedulers.from(Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat(getName() + "-oo-executor-%d").build()));
    protected final Scheduler posSingleExecutor = Schedulers.from(Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat(getName() + "-pos-executor-%d").build()));
    protected final Scheduler indexSingleExecutor = Schedulers.from(Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat(getName() + "-index-executor-%d").build()));

    // Moving timeout
    private volatile ScheduledFuture<?> scheduledOverloadReset;
    private volatile PlaceOrderArgs placeOrderArgs;
    private volatile MarketState marketState = MarketState.READY;
    private volatile Instant readyTime = Instant.now();

    // moving mon
//    protected volatile Instant lastMovingStart = null;


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

    public OrderBook getOrderBookXBTUSD() {
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
            orderBook = new OrderBook(this.orderBookXBTUSD.getTimeStamp(),
                    new ArrayList<>(this.orderBookXBTUSD.getAsks()),
                    new ArrayList<>(this.orderBookXBTUSD.getBids()));
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

    public boolean isReadyForArbitrageWithOOFetch() {
        fetchOpenOrders();

        if (isBusy()) {
            return false;
        }
        return !hasOpenOrders();
    }

    private void initEventBus() {
        Disposable subscribe = eventBus.toObserverable()
                .doOnError(throwable -> logger.error("doOnError handling", throwable))
                .retry()
                .subscribe(btsEventBox -> {
                    BtsEvent btsEvent = btsEventBox.getBtsEvent();
                    if (btsEvent == BtsEvent.MARKET_FREE_FROM_UI) {
                        setFree(btsEventBox.getTradeId(), "UI");
                    } else if (btsEvent == BtsEvent.MARKET_FREE_FROM_CHECKER) {
                        setFree(btsEventBox.getTradeId(), "CHECKER");
                    } else if (btsEvent == BtsEvent.MARKET_FREE_FORCE_RESET) {
                        setFree(btsEventBox.getTradeId(), "FORCE_RESET");
                    } else if (btsEvent == BtsEvent.MARKET_FREE) {
                        setFree(btsEventBox.getTradeId());
                    } else if (btsEvent == BtsEvent.MARKET_FREE_FOR_ARB) {
                        // do not repeat. the event is for ArbitrageService
                    }
                }, throwable -> logger.error("On event handling", throwable));
    }

    public void setBusy() {
        if (isMarketStopped()) {
            return;
        }
        if (this.marketState != MarketState.SWAP && this.marketState != MarketState.SWAP_AWAIT) {
            if (!isBusy()) {
                getTradeLogger().info(String.format("#%s %s: busy, %s", getCounterNameNext(), getName(), getArbitrageService().getFullPosDiff()));
            }
            this.marketState = MarketState.ARBITRAGE;
        }
    }

    protected void setFree(Long tradeId, String... flags) {
        logger.info(String.format("setFree(%s, %s, %s), curr marketState=%s", tradeId,
                flags != null && flags.length > 0 ? flags[0] : null,
                flags != null && flags.length > 1 ? flags[1] : null,
                marketState));

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
            case WAITING_ARB: // openOrderSubscr can setFree, when it's not needed
            case PLACING_ORDER: // openOrderSubscr can setFree, when it's not needed
            case MOVING:
            case STOPPED:
            case FORBIDDEN:
            case PRELIQ:
                if (flags != null && flags.length > 0 && (flags[0].equals("UI") || flags[0].equals("FORCE_RESET"))) {
                    logger.info("reset {} from " + flags[0], marketState);
                    setMarketState(MarketState.READY);
                    eventBus.send(new BtsEventBox(BtsEvent.MARKET_FREE, tradeId)); // end arbitrage trigger s==> already ready.
                    if (getArbitrageService().getSignalType().isCorr()) {
                        getPosDiffService().finishCorr(tradeId);
                    }
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
                eventBus.send(new BtsEventBox(BtsEvent.MARKET_FREE, tradeId)); // end arbitrage trigger
                if (getArbitrageService().getSignalType().isCorr()) {
                    getPosDiffService().finishCorr(tradeId);
                }

                iterateOpenOrdersMove();
                break;

            case READY:
                if (flags != null && flags.length > 0 && flags[0].equals("CHECKER")) {
                    // do nothing
                } else {
                    logger.warn("{}: already ready. Iterate OO.", getName());
                }
                eventBus.send(new BtsEventBox(BtsEvent.MARKET_FREE_FOR_ARB, tradeId)); // end arbitrage trigger

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

        final String counterForLogs = getCounterName();
        final String changeStat = String.format("#%s change status from %s to %s",
                counterForLogs,
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

            final String counterForLogs = getCounterName();
            final String backWarn = String.format("#%s change status from %s to %s",
                    counterForLogs,
                    MarketState.SYSTEM_OVERLOADED,
                    marketStateToSet);
            getTradeLogger().warn(backWarn);
            warningLogger.warn(backWarn);
            logger.warn(backWarn);

            if (marketStateToSet == MarketState.READY && getName().equals(BitmexService.NAME)
                    && getArbitrageService().getSecondMarketService().getMarketState() == MarketState.WAITING_ARB) {
                final Long tradeId = getArbitrageService().getTradeId();
                getArbitrageService().getSecondMarketService().setFree(tradeId); // skip OKEX consistently, before bitemx-Ready
            }

            setMarketState(marketStateToSet);

            if (marketStateToSet == MarketState.READY) {
                final Long tradeId = getArbitrageService().getTradeId();
                getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE, tradeId));// to fix ARBITRAGE:IN_PROGRESS
            }

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
        } else if (signalType.isAdj()) {
            final CorrParams corrParams = getPersistenceService().fetchCorrParams();
            value = String.format("%s:%s", String.valueOf(corrParams.getAdj().getTotalCount()), signalType.getCounterName());
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
        final String counterForLogs = getCounterName();
        setMarketState(newState, counterForLogs);
    }

    public void setMarketState(MarketState newState, String counterName) {
        final String msg = String.format("#%s %s marketState: %s %s", counterName, getName(), newState, getArbitrageService().getFullPosDiff());
        getTradeLogger().info(msg);
        logger.info(msg);
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
        return getBalanceService().recalcAndGetAccountInfo(accountInfoContracts, position, orderBook, getContractType(),
                positionXBTUSD, orderBookXBTUSD);
    }

    public Position getPosition() {
        return position;
    }

    public Position getPositionXBTUSD() {
        return positionXBTUSD;
    }

    public ContractIndex getContractIndex() {
        return contractIndex;
    }

    public Ticker getTicker() {
        return ticker;
    }

    public Ticker getEthBtcTicker() {
        return ethBtcTicker;
    }

    public ContractIndex getBtcContractIndex() {
        return btcContractIndex;
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

                final String currCounterName = getCounterName();
                final Long lastTradeId = getArbitrageService().getTradeId();
                //TODO fill placingType, signalType. Use saved fplayTrade-starting-args
//                final PlacingType defaultPlacingType = getArbitrageService().getFplayTrade() != null ?
//                        getArbitrageService().getFplayTrade().get

                synchronized (openOrdersLock) {
                    this.openOrders = fetchedList.stream()
                            .map(limitOrder ->
                                    this.openOrders.stream()
                                            .filter(ord -> ord.getOrderId().equals(limitOrder.getId()))
                                            .findAny()
                                            .map(fOrd -> new FplayOrder(fOrd.getTradeId(), fOrd.getCounterName(),
                                                    limitOrder, fOrd.getBestQuotes(), fOrd.getPlacingType(),
                                                    fOrd.getSignalType()))
                                            //TODO fill placingType, signalType
                                            .orElseGet(() -> new FplayOrder(lastTradeId, currCounterName,
                                                    (limitOrder), null, null, null)))
                            .collect(Collectors.toList());
                }

                // updateOpenOrders(fetchedList); - Don't use incremental update

            } catch (Exception e) {
                logger.error("GetOpenOrdersError", e);
                throw new IllegalStateException("GetOpenOrdersError", e);
            }
        }
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

    protected Optional<Order> getOrderInfo(String orderId, String counterForLogs, int attemptCount, String logInfoId) {
        return getOrderInfo(orderId, counterForLogs, attemptCount, logInfoId, getTradeLogger());
    }

    protected Optional<Order> getOrderInfo(String orderId, String counterForLogs, int attemptCount, String logInfoId, LogService customLogger) {
        final String[] orderIds = {orderId};
        final Collection<Order> orderInfos = getOrderInfos(orderIds, counterForLogs, attemptCount, logInfoId, customLogger);
        return orderInfos.isEmpty() ? Optional.empty() : Optional.ofNullable(orderInfos.iterator().next());
    }

    protected Collection<Order> getOrderInfos(String[] orderIds, String counterForLogs, int attemptCount, String logInfoId, LogService customLogger) {
        final TradeService tradeService = getExchange().getTradeService();
        Collection<Order> orders = new ArrayList<>();
        try {
            orders = tradeService.getOrder(orderIds);
            if (orders.isEmpty()) {
                final String message = String.format("#%s/%s %s orderIds=%s, error: %s",
                        counterForLogs, attemptCount,
                        logInfoId,
                        Arrays.toString(orderIds), "Market did not return info by orderIds");
                customLogger.error(message);
                logger.error(message);
            } else {
                for (Order orderInfo : orders) {
                    orderInfo = orders.iterator().next();
                    if (!orderInfo.getStatus().equals(Order.OrderStatus.FILLED)) {
                        String errorMsg = String.format("#%s/%s %s %s status=%s, avgPrice=%s, orderId=%s, type=%s, cumAmount=%s",
                                counterForLogs, attemptCount,
                                logInfoId,
                                Utils.convertOrderTypeName(orderInfo.getType()),
                                orderInfo.getStatus() != null ? orderInfo.getStatus().toString() : null,
                                orderInfo.getAveragePrice() != null ? orderInfo.getAveragePrice().toPlainString() : null,
                                orderInfo.getId(),
                                orderInfo.getType(),
                                orderInfo.getCumulativeAmount() != null ? orderInfo.getCumulativeAmount().toPlainString() : null);
                        customLogger.info(errorMsg);
                        logger.info(errorMsg);
                    }
                }
            }
        } catch (Exception e) {
            final String message = String.format("#%s/%s %s orderIds=%s, error: %s",
                    counterForLogs, attemptCount,
                    logInfoId,
                    Arrays.toString(orderIds), e.toString());
            customLogger.error(message);
            logger.error(message, e);
        }
        return orders;
    }

    private void initOpenOrdersMovingSubscription() {
        openOrdersMovingSubscription = getArbitrageService().getSignalEventBus().toObserverable()
                .subscribe(eventQuant -> {
                    try {
                        SignalEvent signalEvent = eventQuant instanceof SignalEventEx
                                ? ((SignalEventEx) eventQuant).getSignalEvent()
                                : (SignalEvent) eventQuant;

                        if ((signalEvent == SignalEvent.B_ORDERBOOK_CHANGED && getName().equals(BitmexService.NAME))
                                || (signalEvent == SignalEvent.O_ORDERBOOK_CHANGED && getName().equals(OkCoinService.NAME))) {
                            checkOpenOrdersForMoving(eventQuant.startTime());
                        }
                    } catch (NotYetInitializedException e) {
                        // do nothing
                    } catch (Exception e) {
                        logger.error("{} openOrdersMovingSubscription error", getName(), e);
                    }
                }, throwable -> logger.error("{} openOrdersMovingSubscription error", getName(), throwable));
    }

    protected void checkOpenOrdersForMoving(Instant startTime) {
//        debugLog.info(getName() + ":checkOpenOrdersForMoving");
        if (!isMovingStopped()) {
            iterateOpenOrdersMove(startTime);
        }
    }

    abstract protected void iterateOpenOrdersMove(Object... iterateArgs);

    abstract protected void onReadyState();

    abstract public ContractType getContractType();

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

    public abstract MoveResponse moveMakerOrder(FplayOrder fplayOrder, BigDecimal newPrice, Object... reqMovingArgs);

    protected BigDecimal createNonTakerPrice(Order.OrderType orderType, PlacingType placingType, OrderBook orderBook, ContractType contractType) {
        BigDecimal tickSize = contractType.getTickSize();
        BigDecimal thePrice;
        if (placingType == PlacingType.MAKER) {
            thePrice = createBestMakerPrice(orderType, orderBook);
        } else if (placingType == PlacingType.MAKER_TICK) {
            thePrice = createBestMakerTickPrice(orderType, tickSize, orderBook);
        } else if (placingType == PlacingType.HYBRID_TICK) {
            thePrice = createBestHybridTickPrice(orderType, tickSize, orderBook);
        } else if (placingType == PlacingType.HYBRID) {
            thePrice = createBestHybridPrice(orderType, orderBook);
        } else { // placingType == null???
            String msg = String.format("%s PlacingType==%s, use MAKER", getName(), placingType);
//            warningLogger.warn(msg);
            logger.warn(msg);
            thePrice = createBestMakerPrice(orderType, orderBook);
        }
        return thePrice.setScale(contractType.getScale(), RoundingMode.HALF_UP);
    }

    protected BigDecimal createBestMakerPrice(Order.OrderType orderType, OrderBook orderBook) {
        BigDecimal thePrice = BigDecimal.ZERO;
        if (orderType == Order.OrderType.BID || orderType == Order.OrderType.EXIT_ASK) {
            thePrice = Utils.getBestBid(orderBook).getLimitPrice();
        } else if (orderType == Order.OrderType.ASK || orderType == Order.OrderType.EXIT_BID) {
            thePrice = Utils.getBestAsk(orderBook).getLimitPrice();
        }
        if (thePrice.signum() == 0) {
            getTradeLogger().info("WARNING: PRICE IS 0");
            warningLogger.warn(getName() + " WARNING: PRICE IS 0");
            logger.warn(getName() + " WARNING: PRICE IS 0");
        }
        return thePrice;
    }

    private BigDecimal createBestMakerTickPrice(OrderType orderType, BigDecimal tickSize, OrderBook orderBook) {
        BigDecimal thePrice = BigDecimal.ZERO;
        if (orderType == Order.OrderType.BID || orderType == Order.OrderType.EXIT_ASK) {
            BigDecimal ask = Utils.getBestAsk(orderBook).getLimitPrice();
            thePrice = ask.subtract(tickSize);
        } else if (orderType == Order.OrderType.ASK || orderType == Order.OrderType.EXIT_BID) {
            BigDecimal bid = Utils.getBestBid(orderBook).getLimitPrice();
            thePrice = bid.add(tickSize);
        }
        if (thePrice.signum() == 0) {
            getTradeLogger().info("WARNING: PRICE IS 0");
            warningLogger.warn(getName() + " WARNING: PRICE IS 0");
            logger.warn(getName() + " WARNING: PRICE IS 0");
        }

        return thePrice;
    }

    private BigDecimal createBestHybridTickPrice(Order.OrderType orderType, BigDecimal tickSize, OrderBook orderBook) {
        BigDecimal thePrice = BigDecimal.ZERO;
        BigDecimal bid = Utils.getBestBid(orderBook).getLimitPrice();
        BigDecimal ask = Utils.getBestAsk(orderBook).getLimitPrice();
        if (orderType == Order.OrderType.BID || orderType == Order.OrderType.EXIT_ASK) {
            BigDecimal askTick = ask.subtract(tickSize);
            thePrice = askTick.compareTo(bid) > 0 ? askTick : ask;
        } else if (orderType == Order.OrderType.ASK || orderType == Order.OrderType.EXIT_BID) {
            BigDecimal bidTick = bid.add(tickSize);
            thePrice = bidTick.compareTo(ask) < 0 ? bidTick : bid;
        }
        if (thePrice.signum() == 0) {
            getTradeLogger().info("WARNING: PRICE IS 0");
            logger.warn(getName() + " WARNING: PRICE IS 0");
        }

        return thePrice;
    }

    private BigDecimal createBestHybridPrice(Order.OrderType orderType, OrderBook orderBook) {
        BigDecimal thePrice = BigDecimal.ZERO;
        if (orderType == Order.OrderType.BID
                || orderType == Order.OrderType.EXIT_ASK) {
            thePrice = Utils.getBestAsk(orderBook).getLimitPrice();
        } else if (orderType == Order.OrderType.ASK
                || orderType == Order.OrderType.EXIT_BID) {
            thePrice = Utils.getBestBid(orderBook).getLimitPrice();
        }
        if (thePrice.signum() == 0) {
            getTradeLogger().info("WARNING: PRICE IS 0");
            warningLogger.warn(getName() + " WARNING: PRICE IS 0");
            logger.warn(getName() + " WARNING: PRICE IS 0");
        }

        return thePrice;
    }

    protected MoveResponse moveMakerOrderIfNotFirst(FplayOrder fplayOrder, Object... reqMovingArgs) {
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

        if (isMovingStopped()) {
            response = new MoveResponse(MoveResponse.MoveOrderStatus.WAITING_TIMEOUT, "");
        } else if ((limitOrder.getType() == Order.OrderType.ASK && limitOrder.getLimitPrice().compareTo(bestAsk) == 0)
                || (limitOrder.getType() == Order.OrderType.BID && limitOrder.getLimitPrice().compareTo(bestBid) == 0)) {

            response = new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_FIRST, "");
        } else {
            OrderBook orderBook = getOrderBook();
            ContractType contractType = getContractType();
            if (getName().equals(BitmexService.NAME) && contractType.isEth()) {
                if (limitOrder.getCurrencyPair() == null) {
                    return new MoveResponse(MoveResponse.MoveOrderStatus.ALREADY_FIRST,
                            "can not move when CurrencyPair is null");
                }
                if (limitOrder.getCurrencyPair().base.getCurrencyCode().equals("XBT")) {
                    orderBook = getOrderBookXBTUSD();
                    contractType = BitmexService.bitmexContractTypeXBTUSD;
                }
            }

            BigDecimal bestPrice = createNonTakerPrice(limitOrder.getType(), fplayOrder.getPlacingType(), orderBook, contractType);

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
                response = moveMakerOrder(fplayOrder, bestPrice, reqMovingArgs);
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

    private boolean isMovingStopped() {
        return specialFlags == SpecialFlags.STOP_MOVING || getArbitrageService().isArbStatePreliq();
    }

    public boolean getMovingStop() {
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

        try {
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
        } catch (Exception e) {
            logger.error("stopAllActions error", e);
        }

        if (getMarketState() != MarketState.READY) {
            setMarketState(MarketState.READY);
        }
    }

    public boolean cancelAllOrders(String logInfoId) {
        return false;
    }

    public boolean isStarted() {
        return true;
    }

    public BigDecimal getHbPosUsd() {
        return positionXBTUSD != null && positionXBTUSD.getPositionLong() != null
                ? positionXBTUSD.getPositionLong()
                : BigDecimal.ZERO;
    }

    public DelayTimer getDtPreliq() {
        return dtPreliq;
    }
}
