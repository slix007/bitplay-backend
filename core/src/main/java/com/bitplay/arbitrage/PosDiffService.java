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
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.fluent.TradeStatus;
import com.bitplay.persistance.domain.settings.PosAdjustment;
import com.bitplay.persistance.repository.FplayTradeRepository;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.Completable;
import io.reactivex.disposables.Disposable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 7/15/17.
 */
@Service("pos-diff")
@Slf4j
public class PosDiffService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private Disposable theTimerToImmediateCorr;

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
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private BitmexLimitsService bitmexLimitsService;

    @Autowired
    private OkexLimitsService okexLimitsService;

    @Autowired
    private BitmexService bitmexService;
    @Autowired
    private OkCoinService okCoinService;

    @Autowired
    private TradeService tradeService;
    @Autowired
    private FplayTradeRepository fplayTradeRepository;

    private ScheduledExecutorService posDiffExecutor;

    @PreDestroy
    public void preDestory() {
        posDiffExecutor.shutdown();
    }

    @PostConstruct
    private void init() {
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("pos-diff-thread-%d").build();
        posDiffExecutor = Executors.newScheduledThreadPool(3, namedThreadFactory);
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
            log.error("Check correction: is failed.", e);
        }
    }

    public void finishCorr(Long tradeId, boolean wasOrderSuccess) {
        if (corrInProgress) {
            corrInProgress = false;
            final SignalType signalType = arbitrageService.getSignalType();
            boolean isAdj = signalType.isAdj();

            try {
                boolean isCorrect = false;
                if (wasOrderSuccess) {
                    if ((isAdj && isPosEqual()) || (!isAdj && isPosEqualByMaxAdj())) {
                        isCorrect = true;
                    } else {

                        Thread.sleep(1000);
                        String infoMsg = "First check before finishCorr: fetchPosition:";
                        String pos1 = arbitrageService.getFirstMarketService().fetchPosition();
                        String pos2 = arbitrageService.getSecondMarketService().fetchPosition();
                        log.info(infoMsg + "bitmex " + pos1);
                        log.info(infoMsg + "okex " + pos2);

                        if ((isAdj && isPosEqual()) || (!isAdj && isPosEqualByMaxAdj())) {
                            isCorrect = true;
                        } else {

                            Thread.sleep(14 * 1000);

                            infoMsg = String.format("Double check before finish %s: fetchPosition:", signalType);
                            pos1 = arbitrageService.getFirstMarketService().fetchPosition();
                            pos2 = arbitrageService.getSecondMarketService().fetchPosition();
                            log.info(infoMsg + "bitmex " + pos1);
                            log.info(infoMsg + "okex " + pos2);
                            if ((isAdj && isPosEqual()) || (!isAdj && isPosEqualByMaxAdj())) {
                                isCorrect = true;
                            }
                        }
                    }
                }

                final CorrParams corrParams = persistenceService.fetchCorrParams();
                if (isAdj) {
                    if (isCorrect) {
                        // correct++
                        corrParams.getAdj().incSuccesses();
                        Long lastTradeId = arbitrageService.printToCurrentDeltaLog("Adj succeed. " + corrParams.getAdj().toString());
                        tradeService.setEndStatus(lastTradeId, TradeStatus.COMPLETED);
                    } else {
                        // error++
                        corrParams.getAdj().incFails();
                        Long lastTradeId = arbitrageService.printToCurrentDeltaLog(String.format("Adj failed. %s. dc=%s",
                                corrParams.getCorr().toString(), getDc()));
                        tradeService.setEndStatus(lastTradeId, TradeStatus.INTERRUPTED);
                    }
                } else {
                    if (isCorrect) {
                        // correct++
                        corrParams.getCorr().incSuccesses();
                        Long lastTradeId = arbitrageService.printToCurrentDeltaLog("Correction succeed. " + corrParams.getCorr().toString());
                        tradeService.setEndStatus(lastTradeId, TradeStatus.COMPLETED);

                    } else {
                        // error++
                        corrParams.getCorr().incFails();
                        Long lastTradeId = arbitrageService.printToCurrentDeltaLog(String.format("Correction failed. %s. dc=%s",
                                corrParams.getCorr().toString(), getDc()));
                        tradeService.setEndStatus(lastTradeId, TradeStatus.INTERRUPTED);

                    }
                }
                persistenceService.saveCorrParams(corrParams);

            } catch (Exception e) {
                final String msg = String.format("Error on finish %s: %s", signalType, e.getMessage());
                warningLogger.error(msg);
                log.error(msg, e);

                // error++
                final CorrParams corrParams = persistenceService.fetchCorrParams();
                if (isAdj) {
                    corrParams.getAdj().incFails();
                } else {
                    corrParams.getCorr().incFails();
                }
                persistenceService.saveCorrParams(corrParams);
                Long lastTradeId = arbitrageService.printToCurrentDeltaLog(String.format("Error on finish %s. %s. dc=%s",
                        signalType,
                        isAdj ? corrParams.getAdj().toString() : corrParams.getCorr().toString(),
                        getDc()));
                tradeService.setEndStatus(lastTradeId, TradeStatus.INTERRUPTED);
            }
        }
    }

    private void startTimerToImmediateCorrection() {
        if (arbitrageService.getFirstMarketService().getMarketState().isStopped()
                || arbitrageService.getSecondMarketService().getMarketState().isStopped()) {
            return;
        }
        if (!hasTimerStarted) {
            warningLogger.info("Timer for timer-state-reset has started");
            hasTimerStarted = true;
        }

        final Long periodToCorrection = arbitrageService.getParams().getPeriodToCorrection();
        theTimerToImmediateCorr = Completable.timer(periodToCorrection, TimeUnit.SECONDS)
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
                    if (!isPosEqualByMaxAdj()) {
                        arbitrageService.getFirstMarketService().stopAllActions();
                        arbitrageService.getSecondMarketService().stopAllActions();
                    }
                })
                .doOnError(e -> {
                    warningLogger.error("timer-state-reset failed. " + e.getMessage());
                    log.error("timer-state-reset failed.", e);
                })
                .retry()
                .subscribe();
    }

    private void stopTimerToImmediateCorrection() {
        if (theTimerToImmediateCorr != null) {
            theTimerToImmediateCorr.dispose();
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
                    final BigDecimal positionsDiffWithHedge = getDc();
                    warningLogger.info("MDC posWithHedge={} > mdc={}", positionsDiffWithHedge, maxDiffCorr);

                    doCorrectionImmediate(SignalType.CORR_MDC);
                }
            }
        } catch (Exception e) {
            warningLogger.error("Correction MDC failed. " + e.getMessage());
            log.error("Correction MDC failed.", e);
        }
    }

    private boolean isMdcNeeded() {
        final BigDecimal maxDiffCorr = arbitrageService.getParams().getMaxDiffCorr();
        final BigDecimal dc = getDc();
        return !isPosEqualByMaxAdj()
                && dc.abs().compareTo(maxDiffCorr) >= 0;
    }

    private void checkPosDiff(boolean isSecondCheck) throws Exception {
        if (!hasGeneralCorrStarted || !arbitrageService.getFirstMarketService().isStarted()) {
            return;
        }

        arbitrageService.getParams().setLastCorrCheck(new Date());

        if (!checkInProgress || isSecondCheck) {
            checkInProgress = true;

            try {
                final BigDecimal bP = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
                final BigDecimal oPL = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
                final BigDecimal oPS = arbitrageService.getSecondMarketService().getPosition().getPositionShort();

                if (!isPosEqualByMaxAdj()) {
                    if (theTimerToImmediateCorr == null || theTimerToImmediateCorr.isDisposed()) {
                        startTimerToImmediateCorrection();
                    }
                } else {
                    stopTimerToImmediateCorrection();
                }

                final CorrParams corrParams = persistenceService.fetchCorrParams();

                boolean isEth = arbitrageService.getFirstMarketService().getContractType().isEth();
                if (isEth) {
                    if (checkPosDiffForAdj(isSecondCheck, bP, oPL, oPS, corrParams)) {
                        return;
                    }
                }

                if (checkPosDiffForCorr(isSecondCheck, bP, oPL, oPS, corrParams)) {
                    return;
                }

            } finally {
                checkInProgress = false;
            }
        }
    }

    private boolean checkPosDiffForAdj(boolean isSecondCheck, BigDecimal bP, BigDecimal oPL, BigDecimal oPS, CorrParams corrParams) throws Exception {
        if (corrParams.getAdj().hasSpareAttempts() && isAdjViolated()) {

            final Integer delaySec = settingsRepositoryService.getSettings().getPosAdjustment().getPosAdjustmentDelaySec();
            // if all READY more than X sec
            if (arbitrageService.getFirstMarketService().isReadyForArbitrage()
                    && arbitrageService.getSecondMarketService().isReadyForArbitrage()
                    && isReadyByTime(arbitrageService.getFirstMarketService(), delaySec)
                    && isReadyByTime(arbitrageService.getSecondMarketService(), delaySec)) {
                if (!isSecondCheck) {

                    final String infoMsg = "Double check before adjustment: fetchPosition:";
                    final String pos1 = arbitrageService.getFirstMarketService().fetchPosition();
                    if (Thread.interrupted()) {
                        return true;
                    }
                    final String pos2 = arbitrageService.getSecondMarketService().fetchPosition();
                    if (Thread.interrupted()) {
                        return true;
                    }
                    warningLogger.info(infoMsg + "bitmex " + pos1);
                    warningLogger.info(infoMsg + "okex " + pos2);

                    checkPosDiff(true);
                    return true;

                } else {
                    final BigDecimal hedgeAmount = getHedgeAmount();
                    doCorrection(bP, oPL, oPS, hedgeAmount, SignalType.ADJ);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkPosDiffForCorr(boolean isSecondCheck, BigDecimal bP, BigDecimal oPL, BigDecimal oPS, CorrParams corrParams) throws Exception {
        if (corrParams.getCorr().hasSpareAttempts() && !isPosEqualByMaxAdj()) {
            final Integer delaySec = settingsRepositoryService.getSettings().getPosAdjustment().getCorrDelaySec();
            // if all READY more than 15 sec
            if (arbitrageService.getFirstMarketService().isReadyForArbitrage()
                    && arbitrageService.getSecondMarketService().isReadyForArbitrage()
                    && isReadyByTime(arbitrageService.getFirstMarketService(), delaySec)
                    && isReadyByTime(arbitrageService.getSecondMarketService(), delaySec)) {
                if (!isSecondCheck) {

                    final String infoMsg = "Double check before correction: fetchPosition:";
                    final String pos1 = arbitrageService.getFirstMarketService().fetchPosition();
                    if (Thread.interrupted()) {
                        return true;
                    }
                    final String pos2 = arbitrageService.getSecondMarketService().fetchPosition();
                    if (Thread.interrupted()) {
                        return true;
                    }
                    warningLogger.info(infoMsg + "bitmex " + pos1);
                    warningLogger.info(infoMsg + "okex " + pos2);

                    checkPosDiff(true);
                    return true;

                } else {
                    final BigDecimal hedgeAmount = getHedgeAmount();
                    doCorrection(bP, oPL, oPS, hedgeAmount, SignalType.CORR);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isReadyByTime(MarketService marketService, Integer delaySec) {
        final long nowMs = Instant.now().toEpochMilli();
        final long readyMs = marketService.getReadyTime().toEpochMilli();
        return nowMs - readyMs > delaySec * 1000;
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

    private synchronized void doCorrection(final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS, final BigDecimal hedgeAmount, SignalType baseSignalType) {
        if (!arbitrageService.getFirstMarketService().isStarted()
                || arbitrageService.getFirstMarketService().getMarketState().isStopped()
                || arbitrageService.getSecondMarketService().getMarketState().isStopped()) {
            return;
        }
        stopTimerToImmediateCorrection(); // avoid double-correction

        final CorrParams corrParams = persistenceService.fetchCorrParams();
        final BigDecimal cm = bitmexService.getCm();
        boolean isEth = bitmexService.getContractType().isEth();

        final BigDecimal dc = getDc().setScale(2, RoundingMode.HALF_UP);

        if (baseSignalType.isAdj()) {

            // 1. What we have to correct
            final CorrObj corrObj = new CorrObj(baseSignalType);
//            adaptAdjByPos(corrObj, dc);
            adaptCorrByPos(corrObj, bP, oPL, oPS, hedgeAmount, dc, cm, isEth);

            // 2. limit by maxVolCorr - no limits for Adj
//            adaptCorrByMaxVolCorr(corrObj, corrParams);

            final MarketService marketService = corrObj.marketService;
            final Order.OrderType orderType = corrObj.orderType;
            final BigDecimal adjAmount = corrObj.correctAmount;

            // 3. check isAffordable
            boolean isAffordable = marketService.isAffordable(orderType, adjAmount);
            if (adjAmount.signum() > 0 && isAffordable) {
//                bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
                arbitrageService.setSignalType(corrObj.signalType);
                marketService.setBusy();

                corrInProgress = true;

                if (outsideLimits(marketService)) {
                    // do nothing
                } else {
                    final PosAdjustment posAdjustment = settingsRepositoryService.getSettings().getPosAdjustment();
                    final PlacingType placingType = posAdjustment.getPosAdjustmentPlacingType();
                    // Market specific params
                    final String counterName = marketService.getCounterName(corrObj.signalType);
                    final Long tradeId = arbitrageService.getLastTradeId();
                    marketService.placeOrder(new PlaceOrderArgs(orderType, adjAmount, null,
                            placingType, corrObj.signalType, 1, tradeId, counterName));
                }
            } else {
                final Integer maxBtm = corrParams.getCorr().getMaxVolCorrBitmex(bitmexService.getCm());
                final Integer maxOkex = corrParams.getCorr().getMaxVolCorrOkex();
                warningLogger.warn("No adjustment: adjAmount={}, isAffordable={}, maxBtm={}, maxOk={}, dc={}, btmPos={}, okPos={}, hedge={}",
                        adjAmount, isAffordable,
                        maxBtm, maxOkex, dc.toPlainString(),
                        arbitrageService.getFirstMarketService().getPosition().toString(),
                        arbitrageService.getSecondMarketService().getPosition().toString(),
                        getHedgeAmount().toPlainString()
                );
            }

        } else { // corr

            // 1. What we have to correct
            final CorrObj corrObj = new CorrObj(baseSignalType);
            adaptCorrByPos(corrObj, bP, oPL, oPS, hedgeAmount, dc, cm, isEth);

            // 2. limit by maxVolCorr
            adaptCorrByMaxVolCorr(corrObj, corrParams);

            final MarketService marketService = corrObj.marketService;
            final Order.OrderType orderType = corrObj.orderType;
            final BigDecimal correctAmount = corrObj.correctAmount;

            // 3. check isAffordable
            boolean isAffordable = marketService.isAffordable(orderType, correctAmount);
            if (correctAmount.signum() > 0 && isAffordable) {
//                bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
                arbitrageService.setSignalType(corrObj.signalType);
                marketService.setBusy();

                corrInProgress = true;

                if (outsideLimits(marketService)) {
                    // do nothing
                } else {
                    // Market specific params
                    final String counterName = marketService.getCounterName(corrObj.signalType);
                    final Long tradeId = arbitrageService.getLastTradeId();
                    marketService.placeOrder(new PlaceOrderArgs(orderType, correctAmount, null,
                            PlacingType.TAKER, corrObj.signalType, 1, tradeId, counterName));
                }
            } else {
                Integer maxBtm = corrParams.getCorr().getMaxVolCorrBitmex(bitmexService.getCm());
                Integer maxOkex = corrParams.getCorr().getMaxVolCorrOkex();
                warningLogger.warn("No correction: correctAmount={}, isAffordable={}, maxBtm={}, maxOk={}, dc={}, btmPos={}, okPos={}, hedge={}",
                        correctAmount, isAffordable,
                        maxBtm, maxOkex, dc.toPlainString(),
                        arbitrageService.getFirstMarketService().getPosition().toString(),
                        arbitrageService.getSecondMarketService().getPosition().toString(),
                        getHedgeAmount().toPlainString()
                );
            }

        } // end corr
    }

    private void adaptCorrByMaxVolCorr(final CorrObj corrObj, final CorrParams corrParams) {
        if (corrObj.marketService.getName().equals(OkCoinService.NAME)) {
            BigDecimal okMax = BigDecimal.valueOf(corrParams.getCorr().getMaxVolCorrOkex());
            if (corrObj.correctAmount.compareTo(okMax) > 0) {
                corrObj.correctAmount = okMax;
            }
        } else {
            BigDecimal bMax = BigDecimal.valueOf(corrParams.getCorr().getMaxVolCorrBitmex(bitmexService.getCm()));
            if (corrObj.correctAmount.compareTo(bMax) > 0) {
                corrObj.correctAmount = bMax;
            }
        }
    }

    private class CorrObj {

        CorrObj(SignalType signalType) {
            this.signalType = signalType;
        }

        SignalType signalType;
        OrderType orderType;
        BigDecimal correctAmount;
        MarketService marketService;
    }

    private void adaptCorrByPos(final CorrObj corrObj, final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS, final BigDecimal hedgeAmount,
            final BigDecimal dc, final BigDecimal cm, final boolean isEth) {

        final BigDecimal okexUsd = isEth
                ? (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(10))
                : (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(100));

        final BigDecimal bitmexUsd = isEth
                ? bP.multiply(BigDecimal.valueOf(10)).divide(cm, 2, RoundingMode.HALF_UP)
                : bP;

        final BigDecimal okEquiv = okexUsd;
        final BigDecimal bEquiv = bitmexUsd.subtract(hedgeAmount).add(bitmexService.getHbPosUsd());

        if (dc.signum() < 0) {
            corrObj.orderType = Order.OrderType.BID;
            if (bEquiv.compareTo(okEquiv) < 0) {
                // bitmex buy
                defineCorrectAmountBitmex(corrObj, dc, cm, isEth);
                corrObj.marketService = arbitrageService.getFirstMarketService();
                if (corrObj.signalType == SignalType.CORR) {
                    if (bP.signum() >= 0) {
                        corrObj.signalType = SignalType.B_CORR_INCREASE_POS;
                    } else {
                        corrObj.signalType = SignalType.B_CORR;
                    }
                }
                if (corrObj.signalType == SignalType.ADJ) {
                    if (bP.signum() >= 0) {
                        corrObj.signalType = SignalType.B_ADJ_INCREASE_POS;
                    } else {
                        corrObj.signalType = SignalType.B_ADJ;
                    }
                }
            } else {
                // okcoin buy
                defineCorrectAmountOkex(corrObj, dc, isEth);
                if (oPS.signum() > 0 && oPS.subtract(corrObj.correctAmount).signum() < 0) { // orderType==CLOSE_ASK
                    corrObj.correctAmount = oPS;
                }

                corrObj.marketService = arbitrageService.getSecondMarketService();
                if (corrObj.signalType == SignalType.CORR) {
                    if ((oPL.subtract(oPS)).signum() >= 0) {
                        corrObj.signalType = SignalType.O_CORR_INCREASE_POS;
                    } else {
                        corrObj.signalType = SignalType.O_CORR;
                    }
                }
                if (corrObj.signalType == SignalType.B_ADJ) {
                    if ((oPL.subtract(oPS)).signum() >= 0) {
                        corrObj.signalType = SignalType.O_ADJ_INCREASE_POS;
                    } else {
                        corrObj.signalType = SignalType.O_ADJ;
                    }
                }
            }
        } else {
            corrObj.orderType = Order.OrderType.ASK;
            if (bEquiv.compareTo(okEquiv) < 0) {
                // okcoin sell
                defineCorrectAmountOkex(corrObj, dc, isEth);
                if (oPL.signum() > 0 && oPL.subtract(corrObj.correctAmount).signum() < 0) { // orderType==CLOSE_BID
                    corrObj.correctAmount = oPL;
                }

                if (corrObj.signalType == SignalType.CORR) {
                    if ((oPL.subtract(oPS)).signum() <= 0) {
                        corrObj.signalType = SignalType.O_CORR_INCREASE_POS;
                    } else {
                        corrObj.signalType = SignalType.O_CORR;
                    }
                }
                if (corrObj.signalType == SignalType.ADJ) {
                    if ((oPL.subtract(oPS)).signum() <= 0) {
                        corrObj.signalType = SignalType.O_ADJ_INCREASE_POS;
                    } else {
                        corrObj.signalType = SignalType.O_ADJ;
                    }
                }
                corrObj.marketService = arbitrageService.getSecondMarketService();
            } else {
                // bitmex sell
                defineCorrectAmountBitmex(corrObj, dc, cm, isEth);
                corrObj.marketService = arbitrageService.getFirstMarketService();
                if (corrObj.signalType == SignalType.CORR) {
                    if (bP.signum() <= 0) {
                        corrObj.signalType = SignalType.B_CORR_INCREASE_POS;
                    } else {
                        corrObj.signalType = SignalType.B_CORR;
                    }
                }
                if (corrObj.signalType == SignalType.ADJ) {
                    if (bP.signum() <= 0) {
                        corrObj.signalType = SignalType.B_ADJ_INCREASE_POS;
                    } else {
                        corrObj.signalType = SignalType.B_ADJ;
                    }
                }
            }
        }
    }

    private void defineCorrectAmountBitmex(CorrObj corrObj, BigDecimal dc, final BigDecimal cm, final boolean isEth) {
        if (isEth) {
//            adj/corr_cont_bitmex = abs(dc) / (10 / CM); // если делаем на Bitmex - usd to cont
            BigDecimal btmCm = BigDecimal.valueOf(10).divide(cm, 4, RoundingMode.HALF_UP);
            corrObj.correctAmount = dc.abs().divide(btmCm, 0, RoundingMode.HALF_UP);
        } else {
            corrObj.correctAmount = dc.abs().setScale(0, RoundingMode.DOWN);
        }
    }

    private void defineCorrectAmountOkex(CorrObj corrObj, BigDecimal dc, boolean isEth) {
        if (isEth) {
//            adj/corr_cont_okex = abs(dc) / 10; // если делаем на Okex
            corrObj.correctAmount = dc.abs().divide(BigDecimal.valueOf(10), 0, RoundingMode.HALF_UP);
        } else {
            corrObj.correctAmount = dc.abs().divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
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

    boolean checkIsPositionsEqual() {

        posDiffExecutor.execute(() -> {
            try {
                checkPosDiff(false);
            } catch (Exception e) {
                warningLogger.error("Check correction: is failed(check before signal). " + e.getMessage());
                log.error("Check correction: is failed(check before signal).", e);
            }
        });

        return isPosEqual();
    }

    public boolean isPosEqualByMaxAdj() {
        final PosAdjustment pa = settingsRepositoryService.getSettings().getPosAdjustment();
        final BigDecimal posAdjustmentMax = pa.getPosAdjustmentMax();

        return getDc().abs().subtract(posAdjustmentMax).signum() <= 0;
    }

    public boolean isPosEqual() {
        final PosAdjustment pa = settingsRepositoryService.getSettings().getPosAdjustment();
        final BigDecimal posAdjustmentMin = pa.getPosAdjustmentMin();

        return getDc().abs().subtract(posAdjustmentMin).signum() <= 0;
    }

    public boolean isAdjViolated() {
        final PosAdjustment pa = settingsRepositoryService.getSettings().getPosAdjustment();
        final BigDecimal max = pa.getPosAdjustmentMax();
        final BigDecimal min = pa.getPosAdjustmentMin();

        BigDecimal dcAbs = getDc().abs();
        return dcAbs.subtract(min).signum() > 0 && dcAbs.subtract(max).signum() <= 0;
    }

    /**
     * Positions diff with hedge in USD.
     */
    public BigDecimal getDc() {
        final BigDecimal hedgeAmountUsd = getHedgeAmount();
        BigDecimal cm = bitmexService.getCm();

        final BigDecimal bP = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
        final BigDecimal oPL = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
        final BigDecimal oPS = arbitrageService.getSecondMarketService().getPosition().getPositionShort();
        if (bP == null || oPL == null || oPS == null) {
            throw new NotYetInitializedException("Position is not yet defined");
        }

        boolean isEth = bitmexService.getContractType().isEth();
        final BigDecimal okexUsd = isEth
                ? (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(10))
                : (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(100));

        final BigDecimal bitmexUsd = isEth
                ? bP.multiply(BigDecimal.valueOf(10)).divide(cm, 2, RoundingMode.HALF_UP)
                : bP;

        final BigDecimal bitmexUsdWithHedge = bitmexUsd.subtract(hedgeAmountUsd).add(bitmexService.getHbPosUsd());

        return okexUsd.add(bitmexUsdWithHedge);
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
        stopTimerToImmediateCorrection();
        if (!isPosEqualByMaxAdj()) {
            startTimerToImmediateCorrection();
        }
    }

}
