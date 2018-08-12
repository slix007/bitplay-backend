package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.market.MarketService;
import com.bitplay.market.bitmex.BitmexLimitsService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.market.okcoin.OkexLimitsService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.Completable;
import io.reactivex.disposables.Disposable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.knowm.xchange.dto.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 7/15/17.
 */
@Service("pos-diff")
public class PosDiffService {

    private static final Logger logger = LoggerFactory.getLogger(com.bitplay.arbitrage.ArbitrageService.class);
    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");
    private static final Logger signalLogger = LoggerFactory.getLogger("SIGNAL_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");
    private static final long MIN_CORR_TIME_AFTER_READY_MS = 25000; // 25 sec

    private final BigDecimal DIFF_FACTOR = BigDecimal.valueOf(100);

    private Disposable theTimerToImmidiateCorr;

    private volatile boolean checkInProgress = false;

    private boolean hasMDCStarted = false;
    private volatile boolean hasTimerStarted = false;
    private volatile boolean hasGeneralCorrStarted = false;
    private volatile boolean corrInProgress = false;

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private BitmexLimitsService bitmexLimitsService;
    @Autowired
    private OkexLimitsService okexLimitsService;

    private ScheduledExecutorService posDiffExecutor;

    @PreDestroy
    public void preDestory() {
        posDiffExecutor.shutdown();
    }

    @PostConstruct
    private void init() {
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("pos-diff-thread-%d").build();
        posDiffExecutor = Executors.newSingleThreadScheduledExecutor(namedThreadFactory);
        posDiffExecutor.scheduleWithFixedDelay(this::calcPosDiffJob,
                60, 1, TimeUnit.SECONDS);

        posDiffExecutor.scheduleWithFixedDelay(this::checkMDCJob,
                60, 5, TimeUnit.SECONDS);
    }

    private void calcPosDiffJob() {
        if (!hasGeneralCorrStarted) {
            warningLogger.info("General correction has started");
            hasGeneralCorrStarted = true;
        }

        try {
            checkPosDiff(false);
        } catch (Exception e) {
            warningLogger.error("Check correction: is failed. " + e.getMessage());
            logger.error("Check correction: is failed.", e);
        }
    }

    public void finishCorr(boolean wasOrderSuccess) {
        if (corrInProgress) {
            corrInProgress = false;
            BigDecimal dc = getPositionsDiffWithHedge();

            try {
                boolean isCorrect = false;
                if (wasOrderSuccess) {
                    if (dc.signum() == 0) {
                        isCorrect = true;
                    } else {

                        Thread.sleep(1000);
                        String infoMsg = "First check before finishCorr: fetchPosition:";
                        String pos1 = arbitrageService.getFirstMarketService().fetchPosition();
                        String pos2 = arbitrageService.getSecondMarketService().fetchPosition();
                        logger.info(infoMsg + "bitmex " + pos1);
                        logger.info(infoMsg + "okex " + pos2);

                        dc = getPositionsDiffWithHedge();

                        if (dc.signum() == 0) {
                            isCorrect = true;
                        } else {

                            Thread.sleep(14 * 1000);

                            infoMsg = "Double check before finishCorr: fetchPosition:";
                            pos1 = arbitrageService.getFirstMarketService().fetchPosition();
                            pos2 = arbitrageService.getSecondMarketService().fetchPosition();
                            logger.info(infoMsg + "bitmex " + pos1);
                            logger.info(infoMsg + "okex " + pos2);
                            if (getIsPositionsEqual()) {
                                isCorrect = true;
                            }
                        }
                    }
                }

                if (isCorrect) {
                    // correct++
                    final CorrParams corrParams = persistenceService.fetchCorrParams();
                    corrParams.getCorr().incSuccesses();
                    persistenceService.saveCorrParams(corrParams);
                    deltasLogger.info("Correction succeed. " + corrParams.getCorr().toString());
                } else {
                    // error++
                    final CorrParams corrParams = persistenceService.fetchCorrParams();
                    corrParams.getCorr().incFails();
                    persistenceService.saveCorrParams(corrParams);
                    deltasLogger.info("Correction failed. {}. dc={}", corrParams.getCorr().toString(), dc);
                }

            } catch (Exception e) {
                warningLogger.error("Error on finishCorr: " + e.getMessage());
                logger.error("Error on finishCorr: ", e);

                // error++
                final CorrParams corrParams = persistenceService.fetchCorrParams();
                corrParams.getCorr().incFails();
                persistenceService.saveCorrParams(corrParams);
                deltasLogger.info("Error on finishCorr. {}. dc={}", corrParams.getCorr().toString(), dc);
            }
        }
    }

    private void startTimerToImmidiateCorrection() {
        if (arbitrageService.getFirstMarketService().getMarketState().isStopped()
                || arbitrageService.getSecondMarketService().getMarketState().isStopped()) {
            return;
        }
        if (!hasTimerStarted) {
            warningLogger.info("Timer for timer-state-reset has started");
            hasTimerStarted = true;
        }

        final Long periodToCorrection = arbitrageService.getParams().getPeriodToCorrection();
        theTimerToImmidiateCorr = Completable.timer(periodToCorrection, TimeUnit.SECONDS)
                .doOnComplete(() -> {
                    final String infoMsg = "Double check before timer-state-reset: fetchPosition:";
                    if (Thread.interrupted()) return;
                    final String pos1 = arbitrageService.getFirstMarketService().fetchPosition();
                    if (Thread.interrupted()) return;
                    final String pos2 = arbitrageService.getSecondMarketService().fetchPosition();
                    warningLogger.info(infoMsg + "bitmex " + pos1);
                    warningLogger.info(infoMsg + "okex "+ pos2);

                    if (Thread.interrupted()) return;
//                    doCorrectionImmediate(SignalType.CORR_TIMER); - no correction. StopAllActions instead.
                    if (getPositionsDiffWithHedge().signum() != 0) {
                        arbitrageService.getFirstMarketService().stopAllActions();
                        arbitrageService.getSecondMarketService().stopAllActions();
                    }
                })
                .doOnError(e -> {
                    warningLogger.error("timer-state-reset failed. " + e.getMessage());
                    logger.error("timer-state-reset failed.", e);
                })
                .retry()
                .subscribe();
    }

    private void stopTimerToImmidiateCorrection() {
        if (theTimerToImmidiateCorr != null) {
            theTimerToImmidiateCorr.dispose();
        }
    }

    private void checkMDCJob() {
        arbitrageService.getParams().setLastMDCCheck(new Date());

        if (arbitrageService.getFirstMarketService().getMarketState().isStopped()
                || arbitrageService.getSecondMarketService().getMarketState().isStopped()) {
            return;
        }
        if (!hasMDCStarted) {
            warningLogger.info("MDC has started");
            hasMDCStarted = true;
        }

        try {
            if (isMdcNeeded()) {
                final String infoMsg = "Double check before MDC-correction: fetchPosition:";
                final String pos1 = arbitrageService.getFirstMarketService().fetchPosition();
                final String pos2 = arbitrageService.getSecondMarketService().fetchPosition();
                warningLogger.info(infoMsg + "bitmex " + pos1);
                warningLogger.info(infoMsg + "okex "+ pos2);

                if (isMdcNeeded()) {
                    final BigDecimal maxDiffCorr = arbitrageService.getParams().getMaxDiffCorr();
                    final BigDecimal positionsDiffWithHedge = getPositionsDiffWithHedge();
                    warningLogger.info("MDC posWithHedge={} > mdc={}", positionsDiffWithHedge, maxDiffCorr);

                    doCorrectionImmediate(SignalType.CORR_MDC);
                }
            }
        } catch (Exception e) {
            warningLogger.error("Correction MDC failed. " + e.getMessage());
            logger.error("Correction MDC failed.", e);
        }
    }

    private boolean isMdcNeeded() {
        final BigDecimal maxDiffCorr = arbitrageService.getParams().getMaxDiffCorr();
        final BigDecimal positionsDiffWithHedge = getPositionsDiffWithHedge();
        return positionsDiffWithHedge.signum() != 0
                && positionsDiffWithHedge.abs().compareTo(maxDiffCorr) != -1;
    }

    private void checkPosDiff(boolean isSecondCheck) throws Exception {
        if (!hasGeneralCorrStarted) {
            return;
        }

        arbitrageService.getParams().setLastCorrCheck(new Date());

        if (!checkInProgress || isSecondCheck) {
            checkInProgress = true;

            try {
                final BigDecimal bP = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
                final BigDecimal oPL = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
                final BigDecimal oPS = arbitrageService.getSecondMarketService().getPosition().getPositionShort();

                final BigDecimal positionsDiffWithHedge = getPositionsDiffWithHedge();
                if (positionsDiffWithHedge.signum() != 0) {
                    if (theTimerToImmidiateCorr == null || theTimerToImmidiateCorr.isDisposed()) {
                        startTimerToImmidiateCorrection();
                    }
                } else {
                    stopTimerToImmidiateCorrection();
                }

//                writeWarnings(bP, oPL, oPS);
                final CorrParams corrParams = persistenceService.fetchCorrParams();

                if (corrParams.getCorr().hasSpareAttempts()
                        && positionsDiffWithHedge.signum() != 0) {
                    // if all READY more than 15 sec
                    if (arbitrageService.getFirstMarketService().isReadyForArbitrage()
                            && arbitrageService.getSecondMarketService().isReadyForArbitrage()
                            && isReadyByTimeForCorrection(arbitrageService.getFirstMarketService())
                            && isReadyByTimeForCorrection(arbitrageService.getSecondMarketService())) {
                        if (!isSecondCheck) {

                            final String infoMsg = "Double check before correction: fetchPosition:";
                            final String pos1 = arbitrageService.getFirstMarketService().fetchPosition();
                            if (Thread.interrupted()) {
                                return;
                            }
                            final String pos2 = arbitrageService.getSecondMarketService().fetchPosition();
                            if (Thread.interrupted()) {
                                return;
                            }
                            warningLogger.info(infoMsg + "bitmex " + pos1);
                            warningLogger.info(infoMsg + "okex "+ pos2);

                            checkPosDiff(true);

                        } else {
                            final BigDecimal hedgeAmount = getHedgeAmount();
                            doCorrection(bP, oPL, oPS, hedgeAmount, SignalType.CORR);
                        }
                    }
                }

            } finally {
                checkInProgress = false;
            }
        }
    }

    private boolean isReadyByTimeForCorrection(MarketService marketService) {
        final long nowMs = Instant.now().toEpochMilli();
        final long readyMs = marketService.getReadyTime().toEpochMilli();
        return nowMs - readyMs > MIN_CORR_TIME_AFTER_READY_MS;
    }

    private BigDecimal getHedgeAmount() {
        final BigDecimal hedgeAmount = arbitrageService.getParams().getHedgeAmount();
        if (hedgeAmount == null) {
            warningLogger.error("Hedge amount is null on checkPosDiff");
            throw new RuntimeException("Hedge amount is null on checkPosDiff");
        }
        return hedgeAmount;
    }

    private void doCorrectionImmediate(SignalType signalType) {
        if (arbitrageService.getFirstMarketService().getMarketState().isStopped()
                || arbitrageService.getSecondMarketService().getMarketState().isStopped()) {
            return;
        }

        final CorrParams corrParams = persistenceService.fetchCorrParams();

        if (corrParams.getCorr().hasSpareAttempts()) {
            // The double check with 'fetchPosition' should be before this method
            final BigDecimal bP = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
            final BigDecimal oPL = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
            final BigDecimal oPS = arbitrageService.getSecondMarketService().getPosition().getPositionShort();
            final BigDecimal hedgeAmount = getHedgeAmount();

            doCorrection(bP, oPL, oPS, hedgeAmount, signalType);
        }
    }

    private synchronized void doCorrection(final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS, final BigDecimal hedgeAmount, SignalType signalType) {
        if (arbitrageService.getFirstMarketService().getMarketState().isStopped()
                || arbitrageService.getSecondMarketService().getMarketState().isStopped()) {
            return;
        }
        stopTimerToImmidiateCorrection(); // avoid double-correction

        final BigDecimal dc = getPositionsDiffWithHedge();
        // 1. What we have to correct
        Order.OrderType orderType;
        BigDecimal correctAmount;
        MarketService marketService;                                            // Hedge=-300, dc=-100
        final BigDecimal okEquiv = (oPL.subtract(oPS)).multiply(DIFF_FACTOR);   // okexPos   100
        final BigDecimal bEquiv = bP.subtract(hedgeAmount);                     // bitmexPos 100

        if (dc.signum() < 0) {
            orderType = Order.OrderType.BID;
            if (bEquiv.compareTo(okEquiv) < 0) {
                // bitmex buy
                correctAmount = dc.abs();
                marketService = arbitrageService.getFirstMarketService();
                if (signalType == SignalType.CORR) {
                    if (bP.signum() >= 0) {
                        signalType = SignalType.B_CORR_INCREASE_POS;
                    } else {
                        signalType = SignalType.B_CORR;
                    }
                }
            } else {
                // okcoin buy
                correctAmount = dc.abs().divide(DIFF_FACTOR, 0, BigDecimal.ROUND_DOWN);
                if (oPS.subtract(correctAmount).signum() < 0) { // orderType==CLOSE_ASK
                    correctAmount = oPS;
                }
                marketService = arbitrageService.getSecondMarketService();
                if (signalType == SignalType.CORR) {
                    if ((oPL.subtract(oPS)).signum() >= 0) {
                        signalType = SignalType.O_CORR_INCREASE_POS;
                    } else {
                        signalType = SignalType.O_CORR;
                    }
                }
            }
        } else {
            orderType = Order.OrderType.ASK;
            if (bEquiv.compareTo(okEquiv) < 0) {
                // okcoin sell
                correctAmount = dc.abs().divide(DIFF_FACTOR, 0, BigDecimal.ROUND_DOWN);
                if (oPL.subtract(correctAmount).signum() < 0) { // orderType==CLOSE_BID
                    correctAmount = oPL;
                }
                if (signalType == SignalType.CORR) {
                    if ((oPL.subtract(oPS)).signum() <= 0) {
                        signalType = SignalType.O_CORR_INCREASE_POS;
                    } else {
                        signalType = SignalType.O_CORR;
                    }
                }
                marketService = arbitrageService.getSecondMarketService();
            } else {
                // bitmex sell
                correctAmount = dc.abs();
                marketService = arbitrageService.getFirstMarketService();
                if (signalType == SignalType.CORR) {
                    if (bP.signum() <= 0) {
                        signalType = SignalType.B_CORR_INCREASE_POS;
                    } else {
                        signalType = SignalType.B_CORR;
                    }
                }
            }
        }

        // 2. limit by maxVolCorr
        CorrParams corrParams = persistenceService.fetchCorrParams();
        if (marketService.getName().equals(OkCoinService.NAME)) {
            BigDecimal okMax = BigDecimal.valueOf(corrParams.getCorr().getMaxVolCorrOkex());
            if (correctAmount.compareTo(okMax) > 0) {
                correctAmount = okMax;
            }
        } else {
            BigDecimal bMax = BigDecimal.valueOf(corrParams.getCorr().getMaxVolCorrBitmex());
            if (correctAmount.compareTo(bMax) > 0) {
                correctAmount = bMax;
            }
        }

        // 3. check isAffordable
        boolean isAffordable = marketService.isAffordable(orderType, correctAmount);
        if (correctAmount.signum() > 0 && isAffordable) {
//                bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
            arbitrageService.setSignalType(signalType);
            marketService.setBusy();

            corrInProgress = true;

            if (outsideLimits(marketService)) {
                // do nothing
            } else {
                // Market specific params
                String counterName = marketService.getCounterName(signalType);
                marketService.placeOrder(new PlaceOrderArgs(orderType, correctAmount, null,
                        PlacingType.TAKER, signalType, 1, counterName));
            }
        } else {
            Integer maxBtm = corrParams.getCorr().getMaxVolCorrBitmex();
            Integer maxOkex = corrParams.getCorr().getMaxVolCorrOkex();
            warningLogger.warn("No correction: correctAmount={}, isAffordable={}, maxBtm={}, maxOk={}, dc={}, btmPos={}, okPos={}, hedge={}",
                    correctAmount, isAffordable,
                    maxBtm, maxOkex, dc.toPlainString(),
                    arbitrageService.getFirstMarketService().getPosition().toString(),
                    arbitrageService.getSecondMarketService().getPosition().toString(),
                    getHedgeAmount().toPlainString()
            );
        }
    }

    private boolean outsideLimits(MarketService marketService) {
        if (marketService.getName().equals(BitmexService.NAME) && bitmexLimitsService.outsideLimits()) {
            warningLogger.error("Attempt of correction when outside limits. " + bitmexLimitsService.getLimitsJson());
            return true;
        }
        if (marketService.getName().equals(OkCoinService.NAME) && okexLimitsService.outsideLimits()) {
            warningLogger.error("Attempt of correction when outside limits. " + okexLimitsService.getLimitsJson());
            return true;
        }
        return false;
    }

    boolean isPositionsEqual() {

        posDiffExecutor.execute(() -> {
            try {
                checkPosDiff(false);
            } catch (Exception e) {
                warningLogger.error("Check correction: is failed(check before signal). " + e.getMessage());
                logger.error("Check correction: is failed(check before signal).", e);
            }
        });

        return getPositionsDiffWithHedge().signum() == 0;
    }

    public boolean getIsPositionsEqual() {
        return getPositionsDiffWithHedge().signum() == 0;
    }

    public BigDecimal getPositionsDiffSafe() {
        BigDecimal bP = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
        BigDecimal oPL = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
        BigDecimal oPS = arbitrageService.getSecondMarketService().getPosition().getPositionShort();
        bP = bP == null ? BigDecimal.ZERO : bP;
        oPL = oPL == null ? BigDecimal.ZERO : oPL;
        oPS = oPS == null ? BigDecimal.ZERO : oPS;

        final BigDecimal okExPosEquivalent = (oPL.subtract(oPS)).multiply(DIFF_FACTOR);
        return okExPosEquivalent.add(bP);
    }

    public BigDecimal getPositionsDiff() {
        final BigDecimal bP = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
        final BigDecimal oPL = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
        final BigDecimal oPS = arbitrageService.getSecondMarketService().getPosition().getPositionShort();
        if (bP == null || oPL == null || oPS == null) {
            throw new NotYetInitializedException("Position is not yet defined");
        }

        final BigDecimal okExPosEquivalent = (oPL.subtract(oPS)).multiply(DIFF_FACTOR);
        return okExPosEquivalent.add(bP);
    }

    public BigDecimal getPositionsDiffWithHedge() {
        final BigDecimal hedgeAmount = getHedgeAmount();
        BigDecimal positionsDiff = getPositionsDiff();
        return positionsDiff.subtract(hedgeAmount);
    }

    private void writeWarnings(final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS) {
//        if (positionsDiffWithHedge.signum() != 0) {
//            final String posString = String.format("b_pos=%s, o_pos=%s-%s", Utils.withSign(bP), Utils.withSign(oPL), oPS.toPlainString());
//            warningLogger.error("Error: {}", posString);
//            deltasLogger.error("Error: {}", posString);
//        }

//        if (oPL.signum() != 0 && oPS.signum() != 0) {
//            final String posString = String.format("b_pos=%s, o_pos=%s-%s", Utils.withSign(bP), Utils.withSign(oPL), oPS.toPlainString());
//            warningLogger.error("Warning: {}", posString);
//        }
    }

    public void setPeriodToCorrection(Long periodToCorrection) {
        arbitrageService.getParams().setPeriodToCorrection(periodToCorrection);
        // restart timer
        stopTimerToImmidiateCorrection();
        if (getPositionsDiffWithHedge().signum() != 0) {
            startTimerToImmidiateCorrection();
        }
    }

}
