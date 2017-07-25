package com.bitplay.arbitrage;

import com.bitplay.market.MarketService;
import com.bitplay.utils.Utils;

import org.knowm.xchange.dto.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private final BigDecimal DIFF_FACTOR = BigDecimal.valueOf(100);

    private Disposable theTimer;

    private boolean immediateCorrectionEnabled = true;

    @Autowired
    private ArbitrageService arbitrageService;

    private void startTimerToCorrection() {
        final Long periodToCorrection = arbitrageService.getParams().getPeriodToCorrection();
        theTimer = Completable.timer(periodToCorrection, TimeUnit.SECONDS)
                .doOnComplete(this::doCorrectionImmediate)
                .doOnError(throwable -> logger.error("timer period to correction", throwable))
                .retry()
                .subscribe();
    }

    private void stopTimerToCorrection() {
        if (theTimer != null) {
            theTimer.dispose();
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void checkMaxDiffCorrection() {
        final BigDecimal maxDiffCorr = arbitrageService.getParams().getMaxDiffCorr();
        final BigDecimal positionsDiffWithHedge = getPositionsDiffWithHedge();
        if (positionsDiffWithHedge.signum() != 0
                && positionsDiffWithHedge.abs().compareTo(maxDiffCorr) != -1) {
            doCorrectionImmediate();
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void calcPosDiffJob() {
        calcPosDiff(false);
    }

    public void calcPosDiff(boolean isSecondCheck) {
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

        writeWarnings(bP, oPL, oPS);

        if (arbitrageService.getParams().getPosCorr().equals("enabled")
                && positionsDiffWithHedge.signum() != 0) {
            // 0. check if ready
            if (arbitrageService.getFirstMarketService().isReadyForArbitrage() && arbitrageService.getSecondMarketService().isReadyForArbitrage()) {
                if (!isSecondCheck) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        logger.error("Sleep was interrupted");
                    }
                    arbitrageService.getFirstMarketService().fetchPosition();
                    arbitrageService.getSecondMarketService().fetchPosition();

                    calcPosDiff(true);
                } else {
                    final BigDecimal hedgeAmount = getHedgeAmount();
                    doCorrection(bP, oPL, oPS, hedgeAmount);
                }
            }
        }
    }

    private BigDecimal getHedgeAmount() {
        final BigDecimal hedgeAmount = arbitrageService.getParams().getHedgeAmount();
        if (hedgeAmount == null) {
            warningLogger.error("Hedge amount is null on calcPosDiff");
            throw new RuntimeException("Hedge amount is null on calcPosDiff");
        }
        return hedgeAmount;
    }

    private void doCorrectionImmediate() {
        if (immediateCorrectionEnabled) {
            final BigDecimal bP = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
            final BigDecimal oPL = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
            final BigDecimal oPS = arbitrageService.getSecondMarketService().getPosition().getPositionShort();
            final BigDecimal hedgeAmount = getHedgeAmount();
            if (arbitrageService.getParams().getPosCorr().equals("enabled")) {
                doCorrection(bP, oPL, oPS, hedgeAmount);
                immediateCorrectionEnabled = false;
            }
        }
    }

    private synchronized void doCorrection(final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS, final BigDecimal hedgeAmount) {
        final BigDecimal positionsDiffWithHedge = getPositionsDiffWithHedge();
        // 1. What we have to correct
        Order.OrderType orderType;
        BigDecimal correctAmount;
        MarketService marketService;
        final BigDecimal okEquiv = (oPL.subtract(oPS)).multiply(DIFF_FACTOR);
        final BigDecimal bEquiv = bP.subtract(hedgeAmount);
        if (positionsDiffWithHedge.signum() < 0) {
            orderType = Order.OrderType.BID;
            if (bEquiv.compareTo(okEquiv) < 0) {
                // bitmex buy
                correctAmount = positionsDiffWithHedge.abs();
                marketService = arbitrageService.getFirstMarketService();
            } else {
                // okcoin buy
                correctAmount = positionsDiffWithHedge.abs().divide(DIFF_FACTOR, 0, BigDecimal.ROUND_DOWN);
                if (oPS.subtract(correctAmount).signum() < 0) { // orderType==CLOSE_ASK
                    correctAmount = oPS;
                }
                marketService = arbitrageService.getSecondMarketService();
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
            } else {
                // bitmex sell
                correctAmount = positionsDiffWithHedge.abs();
                marketService = arbitrageService.getFirstMarketService();
            }
        }

        // 2. check isAffordable
        if (correctAmount.signum() != 0
                && marketService.isAffordable(orderType, correctAmount)) {
//                bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
            arbitrageService.setSignalType(SignalType.CORRECTION);
            marketService.setBusy();
            // Market specific params
            marketService.placeOrderOnSignal(orderType, correctAmount, null, SignalType.CORRECTION);
        }
    }

    boolean isPositionsEqual() {

        calcPosDiff(false);

        return getPositionsDiffWithHedge().signum() == 0;
    }

    public boolean getIsPositionsEqual() {
        return getPositionsDiffWithHedge().signum() == 0;
    }

    public BigDecimal getPositionsDiff() {
        final BigDecimal bP = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
        final BigDecimal oPL = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
        final BigDecimal oPS = arbitrageService.getSecondMarketService().getPosition().getPositionShort();

        final BigDecimal okExPosEquivalent = (oPL.subtract(oPS)).multiply(DIFF_FACTOR);
        BigDecimal positionsDiff = okExPosEquivalent.add(bP);
        return positionsDiff;
    }

    public BigDecimal getPositionsDiffWithHedge() {
        final BigDecimal hedgeAmount = getHedgeAmount();
        BigDecimal positionsDiff = getPositionsDiff();
        BigDecimal positionsDiffWithHedge = positionsDiff.subtract(hedgeAmount);
        return positionsDiffWithHedge;
    }

    private void writeWarnings(final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS) {
//        if (positionsDiffWithHedge.signum() != 0) {
//            final String posString = String.format("b_pos=%s, o_pos=%s-%s", Utils.withSign(bP), Utils.withSign(oPL), oPS.toPlainString());
//            warningLogger.error("Error: {}", posString);
//            deltasLogger.error("Error: {}", posString);
//        }

        if (oPL.signum() != 0 && oPS.signum() != 0) {
            final String posString = String.format("b_pos=%s, o_pos=%s-%s", Utils.withSign(bP), Utils.withSign(oPL), oPS.toPlainString());
            warningLogger.error("Warning: {}", posString);
        }
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
