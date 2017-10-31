package com.bitplay.market.bitmex;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.MarketState;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.domain.SwapParams;
import com.bitplay.persistance.domain.SwapV2;
import com.bitplay.utils.Utils;

import info.bitrich.xchangestream.bitmex.dto.BitmexContractIndex;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Sergey Shurmin on 8/19/17.
 */
public class BitmexSwapService {

    private final static Logger logger = LoggerFactory.getLogger(BitmexSwapService.class);
    private static final Logger tradeLogger = LoggerFactory.getLogger("BITMEX_TRADE_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");
    private static final int TICK_SEC = 1;
    private static final int MAX_TICKS_TO_SWAP_REVERT = 120; // 2 min
    private Disposable fundingSchedule;
    private volatile long swapTicker = 0L;
    private volatile BigDecimal maxDiffCorrStored;

    private BitmexService bitmexService;
    private ArbitrageService arbitrageService;

    private volatile BitmexFunding bitmexFunding = new BitmexFunding();
    private volatile BitmexSwapOrders bitmexSwapOrders = new BitmexSwapOrders();

    // v2
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private volatile ScheduledFuture<?> scheduledSwapV2Opening;


    public BitmexFunding getBitmexFunding() {
        return bitmexFunding;
    }

    public BitmexSwapService(BitmexService bitmexService, ArbitrageService arbitrageService) {
        this.bitmexService = bitmexService;
        this.arbitrageService = arbitrageService;

        Completable.timer(100, TimeUnit.MILLISECONDS)
                .doOnComplete(this::restartScheduleFunding)
                .subscribe();

        // v2
        ((ScheduledThreadPoolExecutor) scheduler).setRemoveOnCancelPolicy(true);
    }

    private void restartScheduleFunding() {
        if (fundingSchedule != null && !fundingSchedule.isDisposed()) {
            fundingSchedule.dispose();
        }

        fundingSchedule = Observable.interval(TICK_SEC, TimeUnit.SECONDS, Schedulers.computation())
                .doOnError((e) -> logger.error("OnFundingScheduler", e))
                .retry()
                .subscribe(this::fundingRateTicker,
                        throwable -> {
                            logger.error("OnFundingRateTicker", throwable);
                            restartScheduleFunding();
                        },
                        this::restartScheduleFunding);
    }

    void fundingRateTicker(Long tickerCouner) {
        swapTicker = swapTicker + 1;

        SwapParams swapParams = bitmexService.getPersistenceService().fetchSwapParams(bitmexService.getName());

        recalcBitmexFunding(swapParams);

        final int SWAP_AWAIT_INTERVAL_SEC = 300;
        final int SWAP_INTERVAL_SEC = 2;
        final MarketState marketState = bitmexService.getMarketState();
        switch (marketState) {
            default:
                checkStartSwapAwait(SWAP_AWAIT_INTERVAL_SEC, swapParams);
                break;

            case SWAP_AWAIT:
                if (swapParams.getActiveVersion() == SwapParams.Ver.V2) {
                    checkSwapV2();
                } else {
                    checkStartFunding(SWAP_INTERVAL_SEC);
                }
                break;

            case SWAP:
                checkEndFunding();
                break;
        }
    }

    private void checkSwapV2() {
        SwapParams swapParams = bitmexService.getPersistenceService().fetchSwapParams(bitmexService.getName());

        if (scheduledSwapV2Opening == null
                || scheduledSwapV2Opening.isCancelled()
                || scheduledSwapV2Opening.isDone()) {
            resetTimerToSwapV2Opening(swapParams);
        }

        final long msLeft = scheduledSwapV2Opening.getDelay(TimeUnit.MILLISECONDS);
        final BigDecimal secLeft = BigDecimal.valueOf(msLeft).divide(BigDecimal.valueOf(1000), 3, BigDecimal.ROUND_HALF_UP);
        final LocalTime now = LocalTime.now();
        swapParams.getSwapV2().setMsToSwapString(String.format("left:%s sec(updated:%s)", secLeft, now.toString()));

        bitmexService.getPersistenceService().saveSwapParams(swapParams, bitmexService.getName());
    }

    public synchronized void resetTimerToSwapV2Opening(SwapParams swapParams) {
        // cancel
        if (scheduledSwapV2Opening != null && !scheduledSwapV2Opening.isDone()) {
            scheduledSwapV2Opening.cancel(true);
        }

        // ---------AW--(now)-----------SW---RV----->
        final long msAfterSwap = swapParams.getSwapV2().getSwapTimeCorrMs() != null ? swapParams.getSwapV2().getSwapTimeCorrMs().longValue() : 0;
        final long nowMs = Instant.now().toEpochMilli();
        final long swapTimeMs = bitmexFunding.getSwapTime().toInstant().toEpochMilli();
        final long openTimeMs = swapTimeMs + msAfterSwap;
        final long timeToOpen = openTimeMs - nowMs;

        if (timeToOpen > 0) {
            scheduledSwapV2Opening = scheduler.schedule(this::doSwapV2Opening,
                    timeToOpen, TimeUnit.MILLISECONDS);
        }
    }

    public void setCustomSwapTime(String swapTime) {
        SwapParams swapParams = bitmexService.getPersistenceService().fetchSwapParams(bitmexService.getName());
        swapParams.setCustomSwapTime(swapTime);
        bitmexService.getPersistenceService().saveSwapParams(swapParams, bitmexService.getName());

        // reset scheduledSwapV2Opening
        if (swapParams.getActiveVersion() == SwapParams.Ver.V2) {
            resetTimerToSwapV2Opening(swapParams);
        }
    }

    private void doSwapV2Opening() {
        tradeLogger.info("doSwapV2Opening, thread: " + Thread.currentThread().getName());

        SwapParams swapParams = bitmexService.getPersistenceService().fetchSwapParams(bitmexService.getName());
        final SwapV2 swapV2 = swapParams.getSwapV2();
        Order.OrderType orderType = swapV2.getSwapOpenType().equals("Buy") ? Order.OrderType.BID : Order.OrderType.ASK; // Sell, Buy
        final BigDecimal amountInContracts = new BigDecimal(swapV2.getSwapOpenAmount());

        final TradeResponse tradeResponse = bitmexService.takerOrder(orderType, amountInContracts, null, SignalType.SWAP_OPEN);
        if (tradeResponse.getErrorCode() == null) {
            swapParams.getSwapV2().setMsToSwapString("");
            bitmexService.getPersistenceService().saveSwapParams(swapParams, bitmexService.getName());

            printSwapTimeAndAvgPrice(tradeResponse.getOrderId());

            resetSwapState(true);
        }

    }

    private void checkStartSwapAwait(int SWAP_AWAIT_INTERVAL, SwapParams swapParams) {
        if (bitmexFunding == null || bitmexFunding.getSwapTime() == null) {
            return;
        }

        final long nowSec = Instant.now().getEpochSecond();
        final long startAwaitSec = bitmexFunding.getSwapTime().minusSeconds(SWAP_AWAIT_INTERVAL).toEpochSecond();
        final long swapTimeSec = bitmexFunding.getSwapTime().toEpochSecond();

        // ---------AW--(now)-----------SW---RV----->
        if (startAwaitSec < nowSec && nowSec < swapTimeSec) {
            final SignalType signalType = (swapParams.getActiveVersion() != null && swapParams.getActiveVersion() == SwapParams.Ver.V2)
                    ? SignalType.SWAP_OPEN
                    : bitmexFunding.getSignalType();
            bitmexService.getArbitrageService().setSignalType(signalType);
            bitmexService.setMarketState(MarketState.SWAP_AWAIT);
        }
    }

    private void checkStartFunding(int SWAP_INTERVAL) {
        final long nowSec = Instant.now().getEpochSecond();
        final long startSwapSec = bitmexFunding.getSwapTime().minusSeconds(SWAP_INTERVAL).toEpochSecond();
        final long swapTimeSec = bitmexFunding.getSwapTime().toEpochSecond();

        // ---------AW----------SW--(now)--RV----->
        if (startSwapSec < nowSec && nowSec < swapTimeSec) {
            if (bitmexService.getMarketState() == MarketState.SWAP_AWAIT) {
                swapTicker = 0L;
                maxDiffCorrStored = arbitrageService.getParams().getMaxDiffCorr();
                arbitrageService.getParams().setMaxDiffCorr(BigDecimal.valueOf(10000000));
                startFunding(SWAP_INTERVAL);
            }
        } else if (nowSec > swapTimeSec) {
            // we lost the moment
            logger.warn("Warning: lost swap moment");
            tradeLogger.warn("Warning: lost swap moment");
            warningLogger.warn("Warning: lost swap moment");
            resetSwapState();
        }
    }


    private void checkEndFunding() {
        if (swapTicker > MAX_TICKS_TO_SWAP_REVERT
                && (bitmexFunding.getFixedSwapTime() == null || bitmexFunding.getStartPosition() == null)) {
            logger.warn("Warning: SWAP NO_REVERT " + bitmexFunding.toString());
            tradeLogger.warn("Warning: SWAP NO_REVERT " + bitmexFunding.toString());
            warningLogger.warn("Warning: SWAP NO_REVERT " + bitmexFunding.toString());
            bitmexService.setMarketState(MarketState.READY);
            bitmexFunding.setStartPosition(null);
            bitmexFunding.setFixedSwapTime(null);
        } else if (bitmexFunding.getFixedSwapTime() != null && bitmexFunding.getStartPosition() != null) {
            final long fixedSwapTime = bitmexFunding.getFixedSwapTime().toEpochSecond();
            final long nowSeconds = Instant.now().getEpochSecond();

            // ---------AW---------SW----(now)---->
            if (fixedSwapTime <= nowSeconds) {
                if (bitmexService.getMarketState() == MarketState.SWAP) {
                    endFunding();

                    printSwapParams();
                }
            }
        }
    }

    private synchronized void startFunding(int SWAP_INTERVAL) {
        final long startSwapSec = bitmexFunding.getSwapTime().minusSeconds(SWAP_INTERVAL).toEpochSecond();
        final BigDecimal fRate = bitmexFunding.getFundingRate();
        final long seconds = Instant.now().getEpochSecond() - startSwapSec;
        if (Math.abs(seconds) > 2) {
            logger.warn("startFunding at wrong time");
            warningLogger.warn("startFunding at wrong time");
            resetSwapState();
        } else {
            final Position position = bitmexService.getPosition();
            final BigDecimal pos = position.getPositionLong();
            final SignalType signalType = bitmexFunding.getSignalType();
            if (signalType == SignalType.SWAP_NONE) {
                BigDecimal fCost = bitmexService.getFundingCost();
                final String message = String.format("#swap_none p%s fR%s%% fC%sXBT", pos.toPlainString(), fRate.toPlainString(), fCost.toPlainString());
                logger.info(message);
                tradeLogger.info(message);

                resetSwapState();

            } else {
                final OrderBook orderBook = bitmexService.getOrderBook();
                if (signalType == SignalType.SWAP_CLOSE_LONG) {
                    final BigDecimal bestBidPrice = Utils.getBestBid(orderBook).getLimitPrice();
                    final String message = String.format("#swap_close_long signal p%s f%s%%, bid[1]=%s", pos.toPlainString(), fRate.toPlainString(),
                            bestBidPrice.toPlainString());
                    bitmexFunding.setSwapClosePrice(bestBidPrice);
                    logger.info(message);
                    tradeLogger.info(message);

                    arbitrageService.setSignalType(SignalType.SWAP_CLOSE_LONG);

                    final TradeResponse tradeResponse = bitmexService.takerOrder(Order.OrderType.ASK, pos, null, SignalType.SWAP_CLOSE_LONG);
                    bitmexSwapOrders.setSwapCloseOrderId(tradeResponse.getOrderId());
                    if (tradeResponse.getErrorCode() == null) {
                        setStateSwapStarted(position);
                    }

                } else if (signalType == SignalType.SWAP_CLOSE_SHORT) {
                    final BigDecimal bestAskPrice = Utils.getBestAsk(orderBook).getLimitPrice();
                    final String message = String.format("#swap_close_short signal p%s f%s%%, ask[1]=%s", pos.toPlainString(), fRate.toPlainString(),
                            bestAskPrice.toPlainString());
                    bitmexFunding.setSwapClosePrice(bestAskPrice);
                    logger.info(message);
                    tradeLogger.info(message);

                    arbitrageService.setSignalType(SignalType.SWAP_CLOSE_SHORT);

                    final TradeResponse tradeResponse = bitmexService.takerOrder(Order.OrderType.BID, pos.abs(), null, SignalType.SWAP_CLOSE_SHORT);
                    bitmexSwapOrders.setSwapCloseOrderId(tradeResponse.getOrderId());
                    if (tradeResponse.getErrorCode() == null) {
                        setStateSwapStarted(position);
                    }

                } else {
                    final String message = String.format("Warning: wrong signalType on startSwop p%s f%s%% s%s", pos.toPlainString(), fRate.toPlainString(), signalType);
                    logger.warn(message);
                    tradeLogger.warn(message);
                    resetSwapState();
                }
            }

        }
    }

    private void setStateSwapStarted(Position position) {
        final BigDecimal pos = position.getPositionLong();
        bitmexService.setMarketState(MarketState.SWAP);
        bitmexFunding.setFixedSwapTime(bitmexFunding.getSwapTime());
        bitmexFunding.setStartPosition(pos);
        final BigDecimal fundingCost = calcFundingCost(position, bitmexFunding.getFundingRate());
        bitmexFunding.setFundingCost(fundingCost);
    }

    private synchronized void endFunding() {
        BigDecimal pos = bitmexFunding.getStartPosition();

        final Order.OrderType orderType = pos.signum() > 0 ? Order.OrderType.BID : Order.OrderType.ASK;
        SignalType signalType = pos.signum() > 0 ? SignalType.SWAP_REVERT_LONG : SignalType.SWAP_REVERT_SHORT;

        final OrderBook orderBook = bitmexService.getOrderBook();
        BigDecimal bestPrice = signalType == SignalType.SWAP_REVERT_LONG
                ? Utils.getBestAsk(orderBook).getLimitPrice()
                : Utils.getBestBid(orderBook).getLimitPrice();
        final String message = String.format("#%s signal to p%s, swap_price=%s=%s",
                signalType.getCounterName(),
                pos.toPlainString(),
                orderType == Order.OrderType.BID ? "ask[1]" : "bid[1]",
                bestPrice.toPlainString());
        bitmexFunding.setSwapOpenPrice(bestPrice);
        logger.info(message);
        tradeLogger.info(message);

        arbitrageService.setSignalType(signalType);

        final TradeResponse tradeResponse = bitmexService.takerOrder(orderType, pos.abs(), null, signalType);
        bitmexSwapOrders.setSwapOpenOrderId(tradeResponse.getOrderId());

        if (tradeResponse.getErrorCode() == null) {
            resetSwapState();
        }
    }

    private void resetSwapState() {
        resetSwapState(false);
    }

    private void resetSwapState(boolean isVer2) {
        bitmexService.setMarketState(MarketState.READY);
        bitmexFunding.setFixedSwapTime(null);
        if (!isVer2 && maxDiffCorrStored != null) {
            // delay  for mdc
            Completable.timer(2, TimeUnit.SECONDS)
                    .subscribe(() -> arbitrageService.getParams().setMaxDiffCorr(maxDiffCorrStored));
        }
    }

    private void recalcBitmexFunding(SwapParams swapParams) {
        if (!(bitmexService.getContractIndex() instanceof BitmexContractIndex)) {
            return;
        }

        // 1. use latest data from market
        final BitmexContractIndex contractIndex = (BitmexContractIndex) bitmexService.getContractIndex();
        final BigDecimal fundingRate = contractIndex.getFundingRate();
        this.bitmexFunding.setFundingRate(fundingRate);

        OffsetDateTime swapTime = contractIndex.getSwapTime();
        // For swap testing
        if (swapParams.getCustomSwapTime() != null && swapParams.getCustomSwapTime().length() > 0) {
            swapTime = OffsetDateTime.parse(
                    swapParams.getCustomSwapTime(),
                    //"2017-08-10T13:45:00Z",
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        this.bitmexFunding.setSwapTime(swapTime);

        // 2. recalc all temporary fileds
        final BigDecimal fRate = bitmexFunding.getFundingRate();
        final BigDecimal pos = bitmexService.getPosition().getPositionLong();
        final BigDecimal maxFRate = bitmexService.getArbitrageService().getParams().getFundingRateFee(); //BitmexFunding.MAX_F_RATE;
        bitmexFunding.setUpdatingTime(OffsetDateTime.now());

        if (pos.signum() > 0) {
            if (fRate.signum() > 0 && fRate.compareTo(maxFRate) > 0) {
                bitmexFunding.setSignalType(SignalType.SWAP_CLOSE_LONG);
            } else {
                bitmexFunding.setSignalType(SignalType.SWAP_NONE);
            }
        } else if (pos.signum() < 0) {
            if (fRate.signum() < 0 && fRate.negate().compareTo(maxFRate) > 0) {
                bitmexFunding.setSignalType(SignalType.SWAP_CLOSE_SHORT);
            } else {
                bitmexFunding.setSignalType(SignalType.SWAP_NONE);
            }
        } else {// pos = 0
            bitmexFunding.setSignalType(SignalType.SWAP_NONE);
        }
    }

    BigDecimal calcFundingCost(final Position position, final BigDecimal fRate) {
        if (position.getMarkValue() == null || position.getPositionLong() == null) {
            return BigDecimal.ZERO;
        }

        // (fundingCost = abs(markValue) * signum(currentQty) * fundingRate)
        return position.getMarkValue().abs().multiply(BigDecimal.valueOf(position.getPositionLong().signum())).multiply(fRate)
                .divide(BigDecimal.valueOf(10000000000L), 8, BigDecimal.ROUND_HALF_UP); //to XBT
    }

    private void printSwapParams() {
        // 1. calc current
        final BigDecimal pos = bitmexFunding.getStartPosition();
        final BigDecimal fundingCost = bitmexFunding.getFundingCost();
        final BigDecimal swapClosePrice = getSwapAvgPrice(bitmexSwapOrders.getSwapCloseOrderId());
        final BigDecimal swapOpenPrice = getSwapAvgPrice(bitmexSwapOrders.getSwapOpenOrderId());

//        fee = abs(pos) / swap_close_price * 0,075 / 100 + abs(pos) / swap_open_price * 0,075 / 100)
        BigDecimal fee = ((pos.abs()
                .divide(swapClosePrice, 16, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(0.00075))) // it's 0,075 / 100
                .add(pos.abs()
                        .divide(swapOpenPrice, 16, BigDecimal.ROUND_HALF_UP)
                        .multiply(BigDecimal.valueOf(0.00075)) // it's 0,075 / 100
                )).setScale(8, BigDecimal.ROUND_HALF_UP);

        BigDecimal spl; // (swap_profit_losses)
        if (pos.signum() == 0) {
            spl = BigDecimal.ZERO;
        } else if (pos.signum() < 0) {
            spl = ((BigDecimal.ONE.divide(swapClosePrice, 16, BigDecimal.ROUND_HALF_UP))
                    .subtract(BigDecimal.ONE.divide(swapOpenPrice, 16, BigDecimal.ROUND_HALF_UP))).multiply(pos.abs())
                    .setScale(8, BigDecimal.ROUND_HALF_UP);
        } else {
            spl = ((BigDecimal.ONE.divide(swapOpenPrice, 16, BigDecimal.ROUND_HALF_UP))
                    .subtract(BigDecimal.ONE.divide(swapClosePrice, 16, BigDecimal.ROUND_HALF_UP))).multiply(pos.abs())
                    .setScale(8, BigDecimal.ROUND_HALF_UP);
        }

        BigDecimal swapProfit = spl.subtract(fee);
        BigDecimal swapDiff = swapProfit.add(fundingCost);

        //2. calc cumulative
        SwapParams swapParams = bitmexService.getPersistenceService().fetchSwapParams(bitmexService.getName());
        swapParams.setCumFundingRate(swapParams.getCumFundingRate().add(bitmexFunding.getFundingRate()));
        swapParams.setCumFundingCost(swapParams.getCumFundingCost().add(fundingCost));
        swapParams.setCumSwapProfit(swapParams.getCumSwapProfit().add(swapProfit));
        swapParams.setCumFee(swapParams.getCumFee().add(fee));
        swapParams.setCumSpl(swapParams.getCumSpl().add(spl));
        swapParams.setCumSwapDiff(swapParams.getCumSwapDiff().add(swapDiff));
        bitmexService.getPersistenceService().saveSwapParams(swapParams, bitmexService.getName());

        final String message = String.format(
                "#%s p%s, swap_close_price=%s, swap_open_price=%s, fR%s%%, fC%sXBT, swap_profit=%s," +
                        "fee=%s, spl=%s, swapDiff=%s, cumFR%s, cumFC%s, cum_swap_profit=%s, cum_fee=%s, cum_spl=%s, cum_swap_diff=%s",
                arbitrageService.getSignalType().getCounterName(),
                pos.toPlainString(),
                swapClosePrice.toPlainString(),
                swapOpenPrice.toPlainString(),
                bitmexFunding.getFundingRate().toPlainString(),
                fundingCost.toPlainString(),
                swapProfit.toPlainString(),
                fee.toPlainString(),
                spl.toPlainString(),
                swapDiff.toPlainString(),
                swapParams.getCumFundingRate().toPlainString(),
                swapParams.getCumFundingCost().toPlainString(),
                swapParams.getCumSwapProfit().toPlainString(),
                swapParams.getCumFee().toPlainString(),
                swapParams.getCumSpl().toPlainString(),
                swapParams.getCumSwapDiff().toPlainString()
        );
        logger.info(message);
        tradeLogger.info(message);
    }

    private BigDecimal getSwapAvgPrice(String orderId) {
        BigDecimal avgPrice = BigDecimal.ZERO;

        final Optional<Order> orderInfoAttempts;
        try {
            final String counterName = arbitrageService.getSignalType().getCounterName();

            orderInfoAttempts = bitmexService.getOrderInfoAttempts(orderId,
                    counterName, "SwapService:Status:");
            Order orderInfo = orderInfoAttempts.get();
            avgPrice = orderInfo.getAveragePrice();

            logger.info("OrderInfo:" + orderInfo.toString());
        } catch (Exception e) {
            logger.error("Error on getting order details after swap", e);
        }

        return avgPrice;
    }

    private BigDecimal printSwapTimeAndAvgPrice(String orderId) {
        BigDecimal avgPrice = BigDecimal.ZERO;

        final Optional<Order> orderInfoAttempts;
        try {
            final String counterName = arbitrageService.getSignalType().getCounterName();

            orderInfoAttempts = bitmexService.getOrderInfoAttempts(orderId,
                    counterName, "SwapService:Status:");
            Order orderInfo = orderInfoAttempts.get();
            avgPrice = orderInfo.getAveragePrice();
            final Date timestamp = orderInfo.getTimestamp();
            final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");


            tradeLogger.info(String.format("#%s SwapOrderInfo: time=%s, avgPrice=%s", counterName, sdf.format(timestamp), avgPrice.toPlainString()));
            tradeLogger.info(String.format("#%s SwapOrderInfo: %s", counterName, orderInfo.toString()));
            logger.info("SwapOrderInfo:" + orderInfo.toString());
        } catch (Exception e) {
            logger.error("Error on getting order details after swap", e);
        }

        return avgPrice;
    }
}
