package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketState;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.PlacingType;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.Counters;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.Settings;

import org.knowm.xchange.dto.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.disposables.Disposable;

/**
 * Created by Sergey Shurmin on 7/15/17.
 */
@Service("pos-diff")
public class PosDiffService {

    private static final Logger logger = LoggerFactory.getLogger(com.bitplay.arbitrage.ArbitrageService.class);
    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");
    private static final Logger signalLogger = LoggerFactory.getLogger("SIGNAL_LOG");
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");
    private final static Logger debugLogger = LoggerFactory.getLogger("DEBUG_LOG");
    private static final long MIN_CORR_TIME_AFTER_READY = 5000;

    private final BigDecimal DIFF_FACTOR = BigDecimal.valueOf(100);

    private Disposable theTimer;

    private boolean immediateCorrectionEnabled = true;

    private volatile boolean calcInProgress = false;

    private boolean hasMDCStarted = false;
    private volatile boolean hasTimerStarted = false;
    private volatile boolean hasGeneralCorrStarted = false;

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private PersistenceService persistenceService;

    private void startTimerToCorrection() {
        if (arbitrageService.getFirstMarketService().getMarketState() == MarketState.STOPPED
                || arbitrageService.getSecondMarketService().getMarketState() == MarketState.STOPPED) {
            return;
        }
        if (!hasTimerStarted) {
            warningLogger.info("Timer for timer-correction has started");
            hasTimerStarted = true;
        }

        final Long periodToCorrection = arbitrageService.getParams().getPeriodToCorrection();
        theTimer = Completable.timer(periodToCorrection, TimeUnit.SECONDS)
                .doOnComplete(() -> {
                    final String infoMsg = "Double check before timer-correction: fetchPosition:";
                    final String pos1 = arbitrageService.getFirstMarketService().fetchPosition();
                    final String pos2 = arbitrageService.getSecondMarketService().fetchPosition();
                    warningLogger.info(infoMsg + "bitmex " + pos1);
                    warningLogger.info(infoMsg + "okex "+ pos2);

                    doCorrectionImmediate(SignalType.CORR_PERIOD);
                })
                .doOnError(e -> {
                    warningLogger.error("Correction on timer failed. " + e.getMessage());
                    logger.error("Correction on timer failed.", e);
                })
                .retry()
                .subscribe();
    }

    private void stopTimerToCorrection() {
        if (theTimer != null) {
            theTimer.dispose();
        }
    }

    @Scheduled(initialDelay = 10*60*1000, fixedDelay = 5000)
    public void checkMaxDiffCorrection() {
        if (arbitrageService.getFirstMarketService().getMarketState() == MarketState.STOPPED
                || arbitrageService.getSecondMarketService().getMarketState() == MarketState.STOPPED) {
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

    @Scheduled(initialDelay = 10*60*1000, fixedDelay = 1000)
    public void calcPosDiffJob() {
        if (!hasGeneralCorrStarted) {
            warningLogger.info("General correction has started");
            hasGeneralCorrStarted = true;
        }

        try {
            calcPosDiff(false);
        } catch (Exception e) {
            warningLogger.error("Correction failed. " + e.getMessage());
            logger.error("Correction failed.", e);
        }
    }

    private void calcPosDiff(boolean isSecondCheck) throws Exception {
        if (!hasGeneralCorrStarted) {
            return;
        }

        if (!calcInProgress || isSecondCheck) {
            calcInProgress = true;

            try {
                final BigDecimal bP = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
                final BigDecimal oPL = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
                final BigDecimal oPS = arbitrageService.getSecondMarketService().getPosition().getPositionShort();

                final BigDecimal positionsDiffWithHedge = getPositionsDiffWithHedge();
                if (positionsDiffWithHedge.signum() != 0) {
                    if (theTimer == null || theTimer.isDisposed()) {
                        startTimerToCorrection();
                    }
                } else {
                    stopTimerToCorrection();
                }

//                writeWarnings(bP, oPL, oPS);

                if (arbitrageService.getParams().getPosCorr().equals("enabled")
                        && positionsDiffWithHedge.signum() != 0) {
                    // 0. check if ready
                    if (arbitrageService.getFirstMarketService().isReadyForArbitrage()
                            && arbitrageService.getSecondMarketService().isReadyForArbitrage()
                            && isReadyByTimeForCorrection(arbitrageService.getFirstMarketService())
                            && isReadyByTimeForCorrection(arbitrageService.getSecondMarketService())) {
                        if (!isSecondCheck) {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                logger.error("Sleep was interrupted");
                            }

                            final String infoMsg = "Double check before correction: fetchPosition:";
                            final String pos1 = arbitrageService.getFirstMarketService().fetchPosition();
                            final String pos2 = arbitrageService.getSecondMarketService().fetchPosition();
                            warningLogger.info(infoMsg + "bitmex " + pos1);
                            warningLogger.info(infoMsg + "okex "+ pos2);

                            calcPosDiff(true);
                        } else {
                            final BigDecimal hedgeAmount = getHedgeAmount();
                            doCorrection(bP, oPL, oPS, hedgeAmount, SignalType.CORR);
                        }
                    }
                }

            } finally {
                calcInProgress = false;
            }
        }
    }

    private boolean isReadyByTimeForCorrection(MarketService marketService) {
        final long nowMs = Instant.now().toEpochMilli();
        final long readyMs = marketService.getReadyTime().toEpochMilli();
        if (nowMs - readyMs > MIN_CORR_TIME_AFTER_READY) {
            return true;
        }
        return false;
    }

    private BigDecimal getHedgeAmount() {
        final BigDecimal hedgeAmount = arbitrageService.getParams().getHedgeAmount();
        if (hedgeAmount == null) {
            warningLogger.error("Hedge amount is null on calcPosDiff");
            throw new RuntimeException("Hedge amount is null on calcPosDiff");
        }
        return hedgeAmount;
    }

    private void doCorrectionImmediate(SignalType signalType) {
        if (arbitrageService.getFirstMarketService().getMarketState() == MarketState.STOPPED
                || arbitrageService.getSecondMarketService().getMarketState() == MarketState.STOPPED) {
            return;
        }

        if (immediateCorrectionEnabled) {
            final BigDecimal bP = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
            final BigDecimal oPL = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
            final BigDecimal oPS = arbitrageService.getSecondMarketService().getPosition().getPositionShort();
            final BigDecimal hedgeAmount = getHedgeAmount();
            if (arbitrageService.getParams().getPosCorr().equals("enabled")) {
                doCorrection(bP, oPL, oPS, hedgeAmount, signalType);
                immediateCorrectionEnabled = false;
            }
        }
    }

    private synchronized void doCorrection(final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS, final BigDecimal hedgeAmount, SignalType signalType) {
        if (arbitrageService.getFirstMarketService().getMarketState() == MarketState.STOPPED
                || arbitrageService.getSecondMarketService().getMarketState() == MarketState.STOPPED) {
            return;
        }

        final BigDecimal positionsDiffWithHedge = getPositionsDiffWithHedge();
        // 1. What we have to correct
        Order.OrderType orderType;
        BigDecimal correctAmount;
        MarketService marketService;
        PlacingType placingType;
        final BigDecimal okEquiv = (oPL.subtract(oPS)).multiply(DIFF_FACTOR);
        final BigDecimal bEquiv = bP.subtract(hedgeAmount);

        final Counters counters = persistenceService.fetchCounters();
        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        final PlacingType okexPlacingType = settings.getOkexPlacingType();
        final PlacingType bitmexPlacingType = settings.getArbScheme() == ArbScheme.TT
                ? PlacingType.TAKER
                : PlacingType.MAKER; // else MT,MT2

        if (positionsDiffWithHedge.signum() < 0) {
            orderType = Order.OrderType.BID;
            if (bEquiv.compareTo(okEquiv) < 0) {
                // bitmex buy
                correctAmount = positionsDiffWithHedge.abs();
                marketService = arbitrageService.getFirstMarketService();
                if (signalType == SignalType.CORR) {
                    signalType = SignalType.B_CORR;
                    counters.incCorrCounter1();
                }
                placingType = bitmexPlacingType;
            } else {
                // okcoin buy
                correctAmount = positionsDiffWithHedge.abs().divide(DIFF_FACTOR, 0, BigDecimal.ROUND_DOWN);
                if (oPS.subtract(correctAmount).signum() < 0) { // orderType==CLOSE_ASK
                    correctAmount = oPS;
                }
                marketService = arbitrageService.getSecondMarketService();
                if (signalType == SignalType.CORR) {
                    signalType = SignalType.O_CORR;
                    counters.incCorrCounter2();
                }
                placingType = okexPlacingType;
            }
        } else {
            orderType = Order.OrderType.ASK;
            if (bEquiv.compareTo(okEquiv) < 0) {
                // okcoin sell
                correctAmount = positionsDiffWithHedge.abs().divide(DIFF_FACTOR, 0, BigDecimal.ROUND_DOWN);
                if (oPL.subtract(correctAmount).signum() < 0) { // orderType==CLOSE_BID
                    correctAmount = oPL;
                }
                marketService = arbitrageService.getSecondMarketService();
                if (signalType == SignalType.CORR) {
                    signalType = SignalType.O_CORR;
                    counters.incCorrCounter2();
                }
                placingType = okexPlacingType;
            } else {
                // bitmex sell
                correctAmount = positionsDiffWithHedge.abs();
                marketService = arbitrageService.getFirstMarketService();
                if (signalType == SignalType.CORR) {
                    signalType = SignalType.B_CORR;
                    counters.incCorrCounter1();
                }
                placingType = bitmexPlacingType;
            }
        }

        persistenceService.saveCounters(counters);

        // 2. check isAffordable
        if (correctAmount.signum() != 0
                && marketService.isAffordable(orderType, correctAmount)) {
//                bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
            arbitrageService.setSignalType(signalType);
            marketService.setBusy();
            // Market specific params
            marketService.placeOrder(new PlaceOrderArgs(orderType, correctAmount, null,
                    placingType, signalType, 1));
        }
    }

    boolean isPositionsEqual() {

        try {
            calcPosDiff(false);
        } catch (Exception e) {
            warningLogger.error("Correction failed(check before signal). " + e.getMessage());
            logger.error("Correction failed(check before signal).", e);
            return false;
        }

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
            throw new IllegalStateException("Position is not yet defined");
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
        stopTimerToCorrection();
        if (getPositionsDiffWithHedge().signum() != 0) {
            startTimerToCorrection();
        }
    }

    public boolean isImmediateCorrectionEnabled() {
        return immediateCorrectionEnabled;
    }

    public void setImmediateCorrectionEnabled(boolean immediateCorrectionEnabled) {
        this.immediateCorrectionEnabled = immediateCorrectionEnabled;
    }
}
