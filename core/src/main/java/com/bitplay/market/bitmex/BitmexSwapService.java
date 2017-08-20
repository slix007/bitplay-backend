package com.bitplay.market.bitmex;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.SignalType;
import com.bitplay.market.MarketState;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.persistance.domain.SwapParams;
import com.bitplay.utils.Utils;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.account.Position;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
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
    private SwapParams swapParams;

    private BitmexService bitmexService;
    private ArbitrageService arbitrageService;

    public BitmexSwapService(BitmexService bitmexService, ArbitrageService arbitrageService) {
        this.bitmexService = bitmexService;
        this.arbitrageService = arbitrageService;

        Completable.timer(100, TimeUnit.MILLISECONDS)
                .doOnComplete(this::restartScheduleFunding)
                .subscribe();
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

        final BitmexFunding bitmexFunding = bitmexService.getBitmexFunding();
        final BigDecimal fRate = bitmexFunding.getFundingRate();
        final BigDecimal pos = bitmexService.getPosition().getPositionLong();
        final BigDecimal maxFRate = bitmexService.getArbitrageService().getParams().getFundingRateFee(); //BitmexFunding.MAX_F_RATE;
        bitmexFunding.setUpdatingTime(OffsetDateTime.now());

        if (pos.signum() > 0) {
            if (fRate.signum() > 0 && fRate.compareTo(maxFRate) > 0) {
                bitmexFunding.setSignalType(SignalType.SWAP_CLOSE_LONG);
            } else {
                bitmexFunding.setSignalType(null);
            }
        } else if (pos.signum() < 0) {
            if (fRate.signum() < 0 && fRate.negate().compareTo(maxFRate) > 0) {
                bitmexFunding.setSignalType(SignalType.SWAP_CLOSE_SHORT);
            } else {
                bitmexFunding.setSignalType(null);
            }
        } else {// pos = 0
            bitmexFunding.setSignalType(null);
        }

        final int SWAP_AWAIT_INTERVAL_SEC = 300;
        final int SWAP_INTERVAL_SEC = 2;
        final MarketState marketState = bitmexService.getMarketState();
        switch (marketState) {
            default:
                checkStartSwapAwait(SWAP_AWAIT_INTERVAL_SEC);
                break;

            case SWAP_AWAIT:
                checkStartFunding(SWAP_INTERVAL_SEC);
                break;

            case SWAP:
                checkEndFunding();
                break;
        }
    }

    private void checkStartSwapAwait(int SWAP_AWAIT_INTERVAL) {
        final BitmexFunding bitmexFunding = bitmexService.getBitmexFunding();
        final long nowSec = Instant.now().getEpochSecond();
        final long startAwaitSec = bitmexFunding.getSwapTime().minusSeconds(SWAP_AWAIT_INTERVAL).toEpochSecond();
        final long swapTimeSec = bitmexFunding.getSwapTime().toEpochSecond();

        // ---------AW--(now)-----------SW---RV----->
        if (startAwaitSec < nowSec && nowSec < swapTimeSec) {
            final SignalType signalType = bitmexFunding.getSignalType();
            bitmexService.getArbitrageService().setSignalType(signalType);
            bitmexService.setMarketState(MarketState.SWAP_AWAIT);
        }
    }

    private void checkStartFunding(int SWAP_INTERVAL) {
        final BitmexFunding bitmexFunding = bitmexService.getBitmexFunding();
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
        final BitmexFunding bitmexFunding = bitmexService.getBitmexFunding();
        if (swapTicker > MAX_TICKS_TO_SWAP_REVERT
                && (bitmexFunding.getFixedSwapTime() == null || bitmexFunding.getStartPosition() == null)) {
            logger.warn("Warning: SWAP REVERT " + bitmexFunding.toString());
            tradeLogger.warn("Warning: SWAP REVERT " + bitmexFunding.toString());
            warningLogger.warn("Warning: SWAP REVERT " + bitmexFunding.toString());
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
                }
            }
        }
    }

    private synchronized void endFunding() {
        final BitmexFunding bitmexFunding = bitmexService.getBitmexFunding();
        BigDecimal pos = bitmexFunding.getStartPosition();

        final Order.OrderType orderType = pos.signum() > 0 ? Order.OrderType.BID : Order.OrderType.ASK;
        SignalType signalType = pos.signum() > 0 ? SignalType.SWAP_REVERT_LONG : SignalType.SWAP_REVERT_SHORT;

        final OrderBook orderBook = bitmexService.getOrderBook();
        BigDecimal bestPrice = signalType == SignalType.SWAP_REVERT_LONG
                ? Utils.getBestAsk(orderBook).getLimitPrice()
                : Utils.getBestBid(orderBook).getLimitPrice();
        final String message = String.format("#%s signal to p%s, swap_price=%s1=%s", signalType.getCounterName(), pos.toPlainString(),
                orderType, bestPrice.toPlainString());
        logger.info(message);
        tradeLogger.info(message);

        arbitrageService.setSignalType(signalType);

        final TradeResponse tradeResponse = bitmexService.takerOrder(orderType, pos.abs(), null, signalType);
        if (tradeResponse.getErrorCode() == null) {
            resetSwapState();
        }
    }

    private synchronized void startFunding(int SWAP_INTERVAL) {
        final BitmexFunding bitmexFunding = bitmexService.getBitmexFunding();
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
            if (signalType == null) {
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
                    logger.info(message);
                    tradeLogger.info(message);

                    arbitrageService.setSignalType(SignalType.SWAP_CLOSE_LONG);

                    final TradeResponse tradeResponse = bitmexService.takerOrder(Order.OrderType.ASK, pos, null, SignalType.SWAP_CLOSE_LONG);
                    if (tradeResponse.getErrorCode() == null) {
                        bitmexService.setMarketState(MarketState.SWAP);
                        bitmexFunding.setFixedSwapTime(bitmexFunding.getSwapTime());
                        bitmexFunding.setStartPosition(pos);
                    }

                } else if (signalType == SignalType.SWAP_CLOSE_SHORT) {
                    final BigDecimal bestAskPrice = Utils.getBestAsk(orderBook).getLimitPrice();
                    final String message = String.format("#swap_close_short signal p%s f%s%%, ask[1]=%s", pos.toPlainString(), fRate.toPlainString(),
                            bestAskPrice.toPlainString());
                    logger.info(message);
                    tradeLogger.info(message);

                    arbitrageService.setSignalType(SignalType.SWAP_CLOSE_SHORT);

                    final TradeResponse tradeResponse = bitmexService.takerOrder(Order.OrderType.BID, pos.abs(), null, SignalType.SWAP_CLOSE_SHORT);
                    if (tradeResponse.getErrorCode() == null) {
                        bitmexService.setMarketState(MarketState.SWAP);
                        bitmexFunding.setFixedSwapTime(bitmexFunding.getSwapTime());
                        bitmexFunding.setStartPosition(pos);
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

    private void resetSwapState() {
        bitmexService.setMarketState(MarketState.READY);
        bitmexService.getBitmexFunding().setFixedSwapTime(null);
        bitmexService.getBitmexFunding().setStartPosition(null);
        arbitrageService.getParams().setMaxDiffCorr(maxDiffCorrStored);
    }
}
