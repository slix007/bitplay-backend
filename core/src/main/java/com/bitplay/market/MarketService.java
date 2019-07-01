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
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.MoveResponse;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.SpecialFlags;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.market.state.TmpStateKeeper;
import com.bitplay.metrics.MetricsDictionary;
import com.bitplay.model.AccountBalance;
import com.bitplay.model.Pos;
import com.bitplay.persistance.domain.LiqParams;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.persistance.domain.settings.SysOverloadArgs;
import com.bitplay.utils.Utils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.AccountInfoContracts;
import org.knowm.xchange.dto.marketdata.ContractIndex;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.UserTrades;
import org.knowm.xchange.service.trade.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Sergey Shurmin on 4/16/17.
 */
public abstract class MarketService extends MarketServiceWithState {

    private final static Logger logger = LoggerFactory.getLogger(MarketService.class);

    protected static final int MAX_ATTEMPTS_CANCEL = 90;

    public static final int ORDERBOOK_MAX_SIZE = 5;
    protected volatile BigDecimal bestBid = BigDecimal.ZERO;
    protected volatile BigDecimal bestAsk = BigDecimal.ZERO;
    protected volatile OrderBook orderBook = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
    protected volatile OrderBook orderBookShort = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
    protected volatile OrderBook orderBookXBTUSD = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
    protected volatile OrderBook orderBookXBTUSDShort = new OrderBook(new Date(), new ArrayList<>(), new ArrayList<>());
    protected final AtomicReference<AccountBalance> account = new AtomicReference<>(AccountBalance.empty());
    protected final AtomicReference<Pos> pos = new AtomicReference<>(new Pos(null, null, null, BigDecimal.ZERO, ""));
    protected final AtomicReference<Pos> posXBTUSD = new AtomicReference<>(new Pos(null, null, null, BigDecimal.ZERO, ""));
    protected final AtomicReference<FullBalance> fullBalanceRef = new AtomicReference<>(new FullBalance(account.get(), pos.get(), null));
    protected volatile Affordable affordable = new Affordable();
    protected final AtomicReference<ContractIndex> contractIndex = new AtomicReference<>(new ContractIndex(BigDecimal.ZERO, new Date()));
    protected final AtomicReference<ContractIndex> btcContractIndex = new AtomicReference<>(new ContractIndex(BigDecimal.ZERO, new Date()));
    protected volatile Ticker ticker;
    protected volatile Ticker ethBtcTicker;
    protected volatile int usdInContract = 0;
    protected final DelayTimer dtPreliq = new DelayTimer();

    protected final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3,
            new ThreadFactoryBuilder().setNameFormat(getName() + "-overload-scheduler-%d").build());
    protected final ExecutorService ooSingleExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory(getName() + "-oo-executor"));
    protected final Scheduler ooSingleScheduler = Schedulers.from(ooSingleExecutor);
    protected final Scheduler indexSingleExecutor = Schedulers.from(Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat(getName() + "-index-executor-%d").build()));
    protected final Scheduler movingExecutor = Schedulers.from(Executors.newFixedThreadPool(5,
            new ThreadFactoryBuilder().setNameFormat(getName() + "-moving-executor-%d").build()));
    protected final Scheduler stateUpdater = Schedulers.from(Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat(getName() + "-state-updater-%d").build()));

    // Moving timeout
    private volatile ScheduledFuture<?> scheduledOverloadReset;
    private volatile PlaceOrderArgs placeOrderArgs;
    private volatile MarketState marketState = MarketState.READY;
    private volatile Instant readyTime = Instant.now();
    private final TmpStateKeeper tmpStateKeeper = new TmpStateKeeper(getName());

    // moving mon
//    protected volatile Instant lastMovingStart = null;


    protected volatile boolean shouldStopPlacing;

    private volatile SpecialFlags specialFlags = SpecialFlags.NONE;
    protected EventBus eventBus = new EventBus();
    protected final LiqInfo liqInfo = new LiqInfo();

    Disposable openOrdersMovingSubscription;

    public void init(String key, String secret, ContractType contractType, Object... exArgs) {
        initEventBus();
        initOpenOrdersMovingSubscription();
        initializeMarket(key, secret, contractType, exArgs);
    }

    protected abstract void initializeMarket(String key, String secret, ContractType contractType, Object... exArgs);

    public abstract UserTrades fetchMyTradeHistory();

    public OrderBook getOrderBookXBTUSD() {
        return getOrderBook();
    }

    public OrderBook getOrderBook() {
        return this.orderBookShort;
    }

    public void fetchOrderBookMain() {
        try {
            final OrderBook orderBook = getExchange().getMarketDataService().getOrderBook(getContractType().getCurrencyPair());
            final OrderBook ob = new OrderBook(new Date(), orderBook.getAsks(), orderBook.getBids());
            this.orderBook = ob;
            this.orderBookShort = ob;
        } catch (IOException e) {
            logger.error("can not fetch orderBook");
        }
    }

    public abstract String fetchPosition() throws Exception;

    public abstract String getPositionAsString();

    public abstract boolean checkLiquidationEdge(Order.OrderType orderType);

    public abstract BalanceService getBalanceService();

    public abstract boolean isAffordable(Order.OrderType orderType, BigDecimal tradableAmount);

    public abstract Affordable recalcAffordable();

    protected Completable recalcAffordableContracts() {
        throw new IllegalArgumentException("not implemented");
    }

    protected Completable recalcLiqInfo() {
        throw new IllegalArgumentException("not implemented");
    }

    protected MetricsDictionary getMetricsDictionary() {
        throw new IllegalArgumentException("not implemented");
    }

    public Sample getRecalcAfterUpdate() {
        throw new IllegalArgumentException("not implemented");
    }

    protected void stateRecalcInStateUpdaterThread() {
        final Completable startSample = Completable.fromAction(() -> {
            getMetricsDictionary().startRecalcAfterUpdate(getName());
        });
        final Completable endSample = Completable.fromAction(() -> {
            getMetricsDictionary().stopRecalcAfterUpdate(getName());
        });

        startSample.subscribeOn(stateUpdater)
                .observeOn(stateUpdater)
                .andThen(recalcAffordableContracts())
                .andThen(recalcLiqInfo())
                .andThen(recalcFullBalance())
                .andThen(endSample)
                .onErrorComplete(throwable -> {
                    logger.error("stateUpdater recalc error", throwable);
                    return true;
                })
                .subscribe();
    }

    public Affordable getAffordable() {
        return affordable;
    }

    public BigDecimal calcBtcInContract() {
        final ContractIndex contractIndex = this.contractIndex.get();
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
        setBusy(getCounterName());
    }

    public void setBusy(String counterName) {
        if (isMarketStopped()) {
            return;
        }
        if (this.marketState != MarketState.SWAP && this.marketState != MarketState.SWAP_AWAIT) {
            if (!isBusy()) {
                getTradeLogger().info(String.format("#%s %s: busy, %s", counterName, getName(), getArbitrageService().getFullPosDiff()));
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
            case FORBIDDEN:
            case PRELIQ:
                if (flags != null && flags.length > 0 && (flags[0].equals("UI") || flags[0].equals("FORCE_RESET"))) {
                    logger.info("reset {} from " + flags[0], marketState);
                    setMarketState(MarketState.READY);
                    eventBus.send(new BtsEventBox(BtsEvent.MARKET_FREE, tradeId)); // end arbitrage trigger s==> already ready.
                }
                break;
            case SYSTEM_OVERLOADED:
                if (flags != null && flags.length > 0 && (flags[0].equals("UI") || flags[0].equals("FORCE_RESET"))) { // only UI and 6 min flag
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

                iterateOpenOrdersMoveAsync();
                break;

            case READY:
                if (flags != null && flags.length > 0 && flags[0].equals("CHECKER")) {
                    // do nothing
                } else {
                    logger.warn("{}: already ready. Iterate OO.", getName());
                }
                eventBus.send(new BtsEventBox(BtsEvent.MARKET_FREE_FOR_ARB, tradeId)); // end arbitrage trigger

                iterateOpenOrdersMoveAsync(); // TODO we should not have such cases
                break;

            default:
                throw new IllegalStateException("Unhandled market state");
        }

    }

    protected void setOverloaded(final PlaceOrderArgs placeOrderArgs) {
        setOverloaded(placeOrderArgs, false);
    }

    protected void setOverloaded(final PlaceOrderArgs placeOrderArgs, boolean withResetWaitingArb) {
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

        if (withResetWaitingArb) {
            ((OkCoinService) getArbitrageService().getSecondMarketService()).resetWaitingArb();
        }

        final SysOverloadArgs sysOverloadArgs = getPersistenceService().getSettingsRepositoryService()
                .getSettings().getBitmexSysOverloadArgs();

        scheduledOverloadReset = scheduler.schedule(this::resetOverloadCycled, sysOverloadArgs.getOverloadTimeMs(), TimeUnit.MILLISECONDS);
    }

    protected abstract void postOverload();

    private void resetOverloadCycled() {
        try {
            resetOverload();

            if (getMarketState() == MarketState.SYSTEM_OVERLOADED) {
                scheduledOverloadReset = scheduler.schedule(this::resetOverloadCycled, 1, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            logger.error("can not resetOverloaded.", e);
        }
    }

    private synchronized void resetOverload() {
        logger.info("resetOverload: starting");
        postOverload();

        final MarketState currMarketState = getMarketState();
        logger.info("resetOverload: currMarketState=" + currMarketState);

        if (isMarketStopped()) {
            logger.info(String.format("resetOverload: skip. Market is stopped. state=%s, ", currMarketState));
            // do nothing
            return;
        }

        if (currMarketState == MarketState.SYSTEM_OVERLOADED) {

            MarketState marketStateToSet;
//            synchronized (openOrdersLock) {
                marketStateToSet = (hasOpenOrders() || placeOrderArgs != null) // moving or placing attempt
                        ? MarketState.ARBITRAGE
                        : MarketState.READY;
//            }

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
                // skip OKEX consistently, before bitemx-Ready
                if (getArbitrageService().getDealPrices().getbPriceFact().getAvg().signum() == 0) {
                    ((OkCoinService)getArbitrageService().getSecondMarketService()).resetWaitingArb();
                } else {
                    ((OkCoinService)getArbitrageService().getSecondMarketService()).resetWaitingArb(Boolean.TRUE);
                }
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
        return getCounterName(signalType, false);
    }

    private String getCounterNameNext() {
        final SignalType signalType = getArbitrageService().getSignalType();
        return getCounterName(signalType, true);
    }

    public String getCounterName(SignalType signalType) {
        return getCounterName(signalType, false);
    }

    public String getCounterNameNext(SignalType signalType) {
        return getCounterName(signalType, true);
    }

    private String getCounterName(SignalType signalType, boolean isNext) {
        String value;
        if (signalType == SignalType.AUTOMATIC) {
            final int counter = getArbitrageService().getCounter();
            value = String.valueOf(isNext ? counter + 1 : counter);
        } else if (signalType.isPreliq()) {
            final CorrParams corrParams = getPersistenceService().fetchCorrParams();
            final Integer counter = corrParams.getPreliq().getTotalCount();
            value = String.format("%s:%s", String.valueOf(isNext ? counter + 1 : counter), signalType.getCounterName());
        } else if (signalType.isAdj()) {
            final CorrParams corrParams = getPersistenceService().fetchCorrParams();
            Integer counter = corrParams.getAdj().getTotalCount();
            value = String.format("%s:%s", String.valueOf(isNext ? counter + 1 : counter), signalType.getCounterName());
        } else if (signalType.isCorr()) {
            final CorrParams corrParams = getPersistenceService().fetchCorrParams();
            Integer counter = corrParams.getCorr().getTotalCount();
            value = String.format("%s:%s", String.valueOf(isNext ? counter + 1 : counter), signalType.getCounterName());
        } else {
            value = signalType.getCounterName();
        }
        return value;
    }

    public boolean isBusy() {
        return marketState != MarketState.READY;
    }

    public boolean isReadyForMoving() {
        return marketState != MarketState.SYSTEM_OVERLOADED && !getArbitrageService().isArbStateStopped() && getMarketState() != MarketState.FORBIDDEN;
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
        if (newState == MarketState.READY) {
            this.readyTime = Instant.now();
            onReadyState(); // may reset WAITING_ARB, iterateOpenOrdersMoveAsync
        }
        this.marketState = newState;
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

    public abstract PosDiffService getPosDiffService();

    public TmpStateKeeper getTmpStateKeeper() {
        return tmpStateKeeper;
    }

    public boolean accountInfoIsReady() {
        return account.get().getWallet().signum() != 0;
    }

    public AccountBalance getAccount() {
        return account.get();
    }

    protected void mergeAccountSafe(AccountInfoContracts newInfo) {
        int iter = 0;
        boolean success = false;
        while (!success) {
            AccountBalance current = this.account.get();
            logger.debug("AccountInfo.Websocket: " + current.toString());
            final AccountBalance updated = mergeAccount(newInfo, current);
            success = this.account.compareAndSet(current, updated);
            if (++iter > 1) {
                logger.warn("merge account iter=" + iter);
            }
        }
    }

    // okex merge account
    protected AccountBalance mergeAccount(AccountInfoContracts newInfo, AccountBalance current) {
        BigDecimal eLast = newInfo.geteLast() != null ? newInfo.geteLast() : current.getELast();
        return new AccountBalance(
                newInfo.getWallet() != null ? newInfo.getWallet() : current.getWallet(),
                newInfo.getAvailable() != null ? newInfo.getAvailable() : current.getAvailable(),
                eLast,
                eLast,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                newInfo.getMargin() != null ? newInfo.getMargin() : current.getMargin(),
                newInfo.getUpl() != null ? newInfo.getUpl() : current.getUpl(),
                newInfo.getRpl() != null ? newInfo.getRpl() : current.getRpl(),
                newInfo.getRiskRate() != null ? newInfo.getRiskRate() : current.getRiskRate()
        );
    }


    public FullBalance getFullBalance() {
        return fullBalanceRef.get();
    }

    protected Completable recalcFullBalance() {
        return Completable.fromAction(() -> {
            final FullBalance newValue = getBalanceService().recalcAndGetAccountInfo(getAccount(), getPos(),
                    this.orderBook, getContractType(), posXBTUSD.get(),
                    this.orderBookXBTUSD);
            this.fullBalanceRef.set(newValue);
        });
    }

    public Pos getPos() {
        return pos.get();
    }

    public void setEmptyPos() {
        pos.set(new Pos(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, ""));
    }

    public BigDecimal getPosVal() {
        final Pos pos = getPos();
        return pos.getPositionLong().subtract(pos.getPositionShort());
    }

    public Pos getPositionXBTUSD() {
        return this.posXBTUSD.get();
    }

    public ContractIndex getContractIndex() {
        return contractIndex.get();
    }

    public Ticker getTicker() {
        return ticker;
    }

    public Ticker getEthBtcTicker() {
        return ethBtcTicker;
    }

    public ContractIndex getBtcContractIndex() {
        return btcContractIndex.get();
    }

    /**
     * use without liqParamsRef.
     */
    public LiqInfo getLiqInfo() {
        return liqInfo;
    }

    protected void storeLiqParams(LiqParams liqParams) {
        getPersistenceService().saveLiqParams(liqParams, getName());
    }

    public void resetLiqInfo() {
        final LiqParams liqParams = getPersistenceService().fetchLiqParams(getName());
        liqParams.setDqlMin(BigDecimal.valueOf(10000));
        liqParams.setDqlMax(BigDecimal.valueOf(-10000));
        liqParams.setDmrlMin(liqInfo.getDmrlCurr() != null ? liqInfo.getDmrlCurr() : BigDecimal.valueOf(10000));
        liqParams.setDmrlMax(liqInfo.getDmrlCurr() != null ? liqInfo.getDmrlCurr() : BigDecimal.valueOf(-10000));

        storeLiqParams(liqParams); // race condition with recalcLiqInfo() => user just have to reset one more time.
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

                updateFplayOrdersToCurrStab(fetchedList, getCurrStub());
            } catch (Exception e) {
                logger.error("GetOpenOrdersError", e);
                throw new IllegalStateException("GetOpenOrdersError", e);
            }
        }
        return getOpenOrders();
    }

    // Only one active task 'freeOoChecker' in ooSingleScheduler.
    private PublishProcessor<Integer> freeOoChecker = PublishProcessor.create();
    private Disposable disposable = Flowable.fromPublisher(freeOoChecker)
            .observeOn(ooSingleScheduler)
            .onBackpressureBuffer(1)
            .onBackpressureDrop()
            .subscribe((i) -> {
                setFreeIfNoOpenOrders("freeOoChecker");
            }, e -> {
                logger.error("freeOoChecker error", e);
                getTradeLogger().warn("freeOoChecker error " + e.getMessage());
            });

    protected void setFreeIfNoOpenOrders(String checkerName) {
        try {
            if (!hasOpenOrders()) {
                getTradeLogger().warn(checkerName);
                logger.warn(checkerName);
                Long lastTradeId = tryFindLastTradeId();
                eventBus.send(new BtsEventBox(BtsEvent.MARKET_FREE, lastTradeId));
            }
        } catch (Exception e) {
            logger.error(checkerName + " error", e);
            getTradeLogger().warn(checkerName + " error " + e.getMessage());
        }
    }

    protected void addCheckOoToFree() {
        freeOoChecker.offer(1);
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
                .observeOn(movingExecutor)
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
            iterateOpenOrdersMoveAsync(startTime);
        }
    }

    abstract protected void iterateOpenOrdersMoveAsync(Object... iterateArgs);

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
            response = orderList.stream()
                    .filter(order -> order.getOrder().getId().equals(orderId))
                    .findFirst()
                    .map(this::moveMakerOrderIfNotFirst)
                    .orElseGet(() -> new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "can not find in openOrders list"));
        }
        return response;
    }

    public abstract MoveResponse moveMakerOrder(FplayOrder fplayOrder, BigDecimal newPrice, Object... reqMovingArgs);

    protected BigDecimal createBestPrice(Order.OrderType orderType, PlacingType placingType, OrderBook orderBook, ContractType contractType) {
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
        } else if (placingType == PlacingType.TAKER || placingType == PlacingType.TAKER_FOK) {
            thePrice = createBestTakerPrice(orderType, orderBook);
        } else { // placingType == null???
            String msg = String.format("%s PlacingType==%s, use MAKER", getName(), placingType);
//            warningLogger.warn(msg);
            logger.warn(msg);
            thePrice = createBestMakerPrice(orderType, orderBook);
        }
        return thePrice.setScale(contractType.getScale(), RoundingMode.HALF_UP);
    }

    @SuppressWarnings("DuplicatedCode")
    protected BigDecimal setScaleUp(BigDecimal in, ContractType contractType) {
        final Integer scale = contractType.getScale();
        final BigDecimal tickSize = contractType.getTickSize();
        if (in.signum() == 0 || in.remainder(tickSize).signum() == 0) {
            return in;
        }
        BigDecimal scaled;
        scaled = in.setScale(scale, RoundingMode.DOWN);
        if (scaled.remainder(tickSize).signum() != 0) {
            final BigDecimal remainder = scaled.abs().remainder(tickSize);
            final BigDecimal tick = tickSize.subtract(remainder);
            scaled = in.signum() > 0
                    ? scaled.add(tick)
                    : scaled.subtract(tick);
        } else {
            scaled = in.signum() > 0
                    ? scaled.add(tickSize)
                    : scaled.subtract(tickSize);
        }
        return scaled;
    }

    @SuppressWarnings("DuplicatedCode")
    protected BigDecimal setScaleDown(BigDecimal in, ContractType contractType) {
        final Integer scale = contractType.getScale();
        final BigDecimal tickSize = contractType.getTickSize();
        if (in.signum() == 0 || in.remainder(tickSize).signum() == 0) {
            return in;
        }
        BigDecimal scaled;
        scaled = in.setScale(scale, RoundingMode.DOWN);
        if (scaled.remainder(tickSize).signum() != 0) {
            final BigDecimal remainder = scaled.abs().remainder(tickSize);
            scaled = scaled.signum() > 0
                    ? scaled.subtract(remainder)
                    : scaled.add(remainder);
        }
        return scaled;
    }

    protected BigDecimal createBestTakerPrice(Order.OrderType orderType, OrderBook orderBook) {
        return createBestHybridPrice(orderType, orderBook);
    }

    protected BigDecimal createBestMakerPrice(Order.OrderType orderType, OrderBook orderBook) {
        BigDecimal thePrice = BigDecimal.ZERO;
        if (orderType == Order.OrderType.BID || orderType == Order.OrderType.EXIT_ASK) {
            thePrice = Utils.getBestBid(orderBook).getLimitPrice();
        } else if (orderType == Order.OrderType.ASK || orderType == Order.OrderType.EXIT_BID) {
            thePrice = Utils.getBestAsk(orderBook).getLimitPrice();
        }
        tryPrintZeroPriceWarning(thePrice);
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
        tryPrintZeroPriceWarning(thePrice);

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
        tryPrintZeroPriceWarning(thePrice);

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
        tryPrintZeroPriceWarning(thePrice);

        return thePrice;
    }

    protected void tryPrintZeroPriceWarning(BigDecimal thePrice) {
        if (thePrice.signum() == 0) {
            getTradeLogger().info("WARNING: PRICE IS 0");
            warningLogger.warn(getName() + " WARNING: PRICE IS 0");
            logger.warn(getName() + " WARNING: PRICE IS 0");
        }
    }

    protected MoveResponse moveMakerOrderIfNotFirst(FplayOrder fplayOrder, Object... reqMovingArgs) {
        MoveResponse response;
        LimitOrder limitOrder = (LimitOrder) fplayOrder.getOrder();
        if (limitOrder.getLimitPrice() == null) {
            final FplayOrder one = getPersistenceService().getOrderRepositoryService().findOne(limitOrder.getId());
            if (one == null) {
                return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "limitPrice is null, id=" + limitOrder.getId());
            } else {
                limitOrder = (LimitOrder) one.getOrder();
                if (limitOrder.getLimitPrice() == null) {
                    return new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "limitPrice is null, id=" + limitOrder.getId());
                }
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

            BigDecimal bestPrice = createBestPrice(limitOrder.getType(), fplayOrder.getPlacingType(), orderBook, contractType);

            if (bestPrice.signum() == 0) {
                response = new MoveResponse(MoveResponse.MoveOrderStatus.EXCEPTION, "bestPrice is 0");

                // do not move from ASK1 to ASK2 ==> trigger on ASK and newPrice < oldPrice
                // do not move from BID1 to BID2 ==> trigger on BID and newPrice > oldPrice
            } else if (fplayOrder.getPlacingType() == PlacingType.TAKER
                    && bestPrice.compareTo(limitOrder.getLimitPrice()) != 0) {
                // move taker
                response = moveMakerOrder(fplayOrder, bestPrice, reqMovingArgs);
            } else if (
                    ((limitOrder.getType() == Order.OrderType.ASK || limitOrder.getType() == Order.OrderType.EXIT_BID)
                            && bestPrice.compareTo(limitOrder.getLimitPrice()) < 0)
                            ||
                            ((limitOrder.getType() == Order.OrderType.BID || limitOrder.getType() == Order.OrderType.EXIT_ASK)
                                    && bestPrice.compareTo(limitOrder.getLimitPrice()) > 0)
            ) {
                // move non-taker
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

    private boolean isMovingStopped() {
        final boolean flagMovingStopped = getPersistenceService().getSettingsRepositoryService().getSettings().flagMovingStopped();
        return flagMovingStopped || getArbitrageService().isArbStatePreliq();
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

    public FplayOrder getCurrStub() {
        Long lastTradeId = tryFindLastTradeId();
        final List<FplayOrder> currOrders = getOpenOrders();
        final Optional<FplayOrder> lastOO = getLastOO(currOrders);
        BestQuotes bestQuotes = null;
        PlacingType placingType = null;
        SignalType signalType = null;
        String counterName;
        if (lastOO.isPresent()) {
            final FplayOrder o = lastOO.get();
            bestQuotes = o.getBestQuotes();
            placingType = o.getPlacingType();
            signalType = o.getSignalType();
            counterName = o.getCounterName();
        } else {
            counterName = gerCurrCounterName(currOrders);
        }

        return new FplayOrder(lastTradeId, counterName, null, bestQuotes, placingType, signalType);
    }

    private Optional<FplayOrder> getLastOO(List<FplayOrder> currOrders) {
        return currOrders.stream()
                .reduce((f1, f2) -> {
                    if (f1.getLimitOrder() != null && f2.getLimitOrder() != null) {
                        if (f1.getLimitOrder().getTimestamp().after(f2.getLimitOrder().getTimestamp())) {
                            return f1;
                        } else {
                            return f2;
                        }
                    }
                    if (f1.isOpen()) {
                        return f1;
                    }
                    if (f2.isOpen()) {
                        return f2;
                    }
                    if (f1.getPlacingType() != null) {
                        return f1;
                    }
                    if (f2.getPlacingType() != null) {
                        return f2;
                    }
                    return f1;
                });
    }

    public String gerCurrCounterName(List<FplayOrder> currOrders) {
        return currOrders.stream()
                .map(FplayOrder::getCounterName)
                .findFirst()
                .orElse(getCounterName());
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
                getOpenOrders().stream()
                        .filter(Objects::nonNull)
                        .filter(FplayOrder::isOpen)
                        .map(FplayOrder::getOrder)
                        .forEach(order -> orderIds.append(order.getId()).append(","));

                String msg = String.format("%s: StopAllActions: CancelAllOpenOrders=%s", getName(), orderIds.toString());
                warningLogger.info(msg);
                getTradeLogger().info(msg);

                cancelAllOrders(null, "StopAllActions: CancelAllOpenOrders", false);
            }
        } catch (Exception e) {
            logger.error("stopAllActions error", e);
        }

        if (getMarketState() != MarketState.READY) {
            setMarketState(MarketState.READY);
        }
    }

    /**
     * @return cancelled order id list
     */
    public List<LimitOrder> cancelAllOrders(FplayOrder stub, String logInfoId, boolean beforePlacing) {
        return new ArrayList<>();
    }

    public boolean isStarted() {
        return true;
    }

    public BigDecimal getHbPosUsd() {
        final Pos positionXBTUSD = this.posXBTUSD.get();
        return positionXBTUSD.getPositionLong() != null
                ? positionXBTUSD.getPositionLong()
                : BigDecimal.ZERO;
    }

    public DelayTimer getDtPreliq() {
        return dtPreliq;
    }

    public TradeResponse closeAllPos() {
        throw new IllegalArgumentException("Not implemented");
    }
}
