package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BordersService;
import com.bitplay.arbitrage.BordersService.Borders;
import com.bitplay.arbitrage.HedgeService;
import com.bitplay.arbitrage.dto.ArbType;
import com.bitplay.arbitrage.dto.DelayTimer;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.dto.SignalTypeEx;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.MarketStaticData;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.bitmex.BitmexUtils;
import com.bitplay.market.model.FullBalance;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.market.okcoin.OkexSettlementService;
import com.bitplay.model.Pos;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.borders.BorderParams.Ver;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.correction.CountedWithExtra;
import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.persistance.domain.settings.PosAdjustment;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.utils.Utils;
import io.reactivex.Completable;
import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

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
    private final DelayTimer dtMdc = new DelayTimer();
    private final DelayTimer dtExtraMdc = new DelayTimer();
    private final DelayTimer dtMdcAdj = new DelayTimer();
    private final DelayTimer dtExtraMdcAdj = new DelayTimer();
    private final DelayTimer dtCorr = new DelayTimer();
    private final DelayTimer dtExtraCorr = new DelayTimer();
    private final DelayTimer dtAdj = new DelayTimer();
    private final DelayTimer dtExtraAdj = new DelayTimer();

    private volatile Long prevTradeId;
    private volatile String prevCounterName;
    private volatile CorrObj prevCorrObj;

    @Autowired
    private BordersService bordersService;

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private HedgeService hedgeService;

    @Autowired
    private SlackNotifications slackNotifications;

    @Autowired
    private TradeService tradeService;
    @Autowired
    private OkexSettlementService okexSettlementService;
    @Autowired
    private NtUsdExecutor ntUsdExecutor;

    public void init() {
        ntUsdExecutor.addScheduledTask(this::calcPosDiffJob, 60, 1, TimeUnit.SECONDS);
        ntUsdExecutor.addScheduledTask(this::checkMDCJob, 60, 1, TimeUnit.SECONDS);
    }

    public DelayTimer getDtMdc() {
        return dtMdc;
    }

    public DelayTimer getDtExtraMdc() {
        return dtExtraMdc;
    }

    public DelayTimer getDtMdcAdj() {
        return dtMdcAdj;
    }

    public DelayTimer getDtExtraMdcAdj() {
        return dtExtraMdcAdj;
    }

    public DelayTimer getDtCorr() {
        return dtCorr;
    }

    public DelayTimer getDtExtraCorr() {
        return dtExtraCorr;
    }

    public DelayTimer getDtAdj() {
        return dtAdj;
    }

    public DelayTimer getDtExtraAdj() {
        return dtExtraAdj;
    }

    public void calcPosDiffJob() {
        if (!hasGeneralCorrStarted) {
            warningLogger.info("General correction has started");
            hasGeneralCorrStarted = true;
        }

        try {
            checkPosDiff();
        } catch (Exception e) {
            warningLogger.error("Check correction: is failed. " + e.getMessage());
            log.error("Check correction: is failed.", e);
        }
    }

    private void countOnStartCorr(final CorrParams corrParams, SignalType signalType) {
        if (signalType.isAdjBtc()) {
            corrParams.getAdj().incTotalCountExtra();
        } else if (signalType.isAdj()) {
            corrParams.getAdj().incTotalCount();
        } else if (signalType.isCorrBtc()) {
            corrParams.getCorr().incTotalCountExtra();
        } else { // signalType.isCorr()
            corrParams.getCorr().incTotalCount();
        }
        persistenceService.saveCorrParams(corrParams);
    }

    private void countFailedOnStartCorr(final CorrParams corrParams, SignalType signalType) {
        boolean justFinished;
        CountedWithExtra obj;
        if (signalType.isAdjBtc()) {
            justFinished = corrParams.getAdj().tryIncFailedExtra();
            obj = corrParams.getAdj();
        } else if (signalType.isAdj()) {
            justFinished = corrParams.getAdj().tryIncFailed();
            obj = corrParams.getAdj();
        } else if (signalType.isCorrBtc()) {
            justFinished = corrParams.getCorr().tryIncFailedExtra();
            obj = corrParams.getCorr();
        } else { // signalType.isCorr()
            justFinished = corrParams.getCorr().tryIncFailed();
            obj = corrParams.getCorr();
        }
        persistenceService.saveCorrParams(corrParams);

        if (justFinished) {
            final Long tradeId = prevTradeId != null ? prevTradeId : arbitrageService.getLastTradeId();
            final String counterName = prevCounterName != null ? prevCounterName : signalType.getCounterName();
            final String attemptsStr = obj != null ? obj.toString() : "";
            final String mainSetStr = arbitrageService.getMainSetStr() + arbitrageService.getxRateLimitString();
            final String extraSetStr = arbitrageService.getExtraSetStr();
            tradeService.info(tradeId, counterName, String.format("#%s fail. %s", counterName, attemptsStr));
            tradeService.info(tradeId, counterName, String.format("#%s fail. %s", counterName, mainSetStr));
            tradeService.info(tradeId, counterName, String.format("#%s fail. %s", counterName, extraSetStr));
            if (prevCorrObj != null && prevCorrObj.marketService != null) {
                prevCorrObj.marketService.getTradeLogger().info(String.format("#%s fail. %s", counterName, attemptsStr));
                prevCorrObj.marketService.getTradeLogger().info(String.format("#%s fail. %s", counterName, mainSetStr));
                prevCorrObj.marketService.getTradeLogger().info(String.format("#%s fail. %s", counterName, extraSetStr));
            }
        }

    }

    private void tryFinishPrevCorr(final CorrParams corrParams) {
        final BigDecimal dcMainSet = getDcMainSet();
        final BigDecimal dcExtraSet = getDcExtraSet();

        boolean justFinished = false;
        CountedWithExtra obj = null;
        if (isPosEqualByMaxAdj(dcMainSet)) {
            if (corrParams.getCorr().tryIncSuccessful()) {
                persistenceService.saveCorrParams(corrParams);
                justFinished = true;
                obj = corrParams.getCorr();
            }
        }
        if (isPosEqualByMaxAdj(dcExtraSet)) {
            if (corrParams.getCorr().tryIncSuccessfulExtra()) {
                persistenceService.saveCorrParams(corrParams);
                justFinished = true;
                obj = corrParams.getCorr();
            }
        }

        if (isPosEqualByMinAdj(dcMainSet)) {
            if (corrParams.getAdj().tryIncSuccessful()) {
                persistenceService.saveCorrParams(corrParams);
                justFinished = true;
                obj = corrParams.getAdj();
            }
        }
        if (isPosEqualByMinAdj(dcExtraSet)) {
            if (corrParams.getAdj().tryIncSuccessfulExtra()) {
                persistenceService.saveCorrParams(corrParams);
                justFinished = true;
                obj = corrParams.getAdj();
            }
        }

        if (justFinished) {
            final Long tradeId = prevTradeId != null ? prevTradeId : arbitrageService.getLastTradeId();
            final String counterName = prevCounterName != null ? prevCounterName : "";
            final String currAttemptsStr = obj != null ? obj.toString() : "";
            final String mainSetStr = arbitrageService.getMainSetStr() + arbitrageService.getxRateLimitString();
            final String extraSetStr = arbitrageService.getExtraSetStr();
            final String attemptStr = String.format("#%s success. %s", counterName, currAttemptsStr);
            final String mainStr = String.format("#%s success. %s", counterName, mainSetStr);
            final String extraStr = String.format("#%s success. %s", counterName, extraSetStr);
            log.info(attemptStr);
            log.info(mainStr);
            log.info(extraStr);
            tradeService.info(tradeId, counterName, attemptStr);
            tradeService.info(tradeId, counterName, mainStr);
            tradeService.info(tradeId, counterName, extraStr);
            if (prevCorrObj != null && prevCorrObj.marketService != null) {
                prevCorrObj.marketService.getTradeLogger().info(attemptStr);
                prevCorrObj.marketService.getTradeLogger().info(mainStr);
                prevCorrObj.marketService.getTradeLogger().info(extraStr);
            }
        }

    }

    private void startTimerToImmediateCorrection() {
        if (marketsStopped()) {
            stopTimerToImmediateCorrection();
            return;
        }
        if (!hasTimerStarted) {
            warningLogger.info("Timer for timer-state-reset has started");
            hasTimerStarted = true;
        }

        final Long periodToCorrection = arbitrageService.getParams().getPeriodToCorrection();
        theTimerToImmediateCorr = Completable.timer(periodToCorrection, TimeUnit.SECONDS)
                .doOnComplete(() -> {
                    final String infoMsg = String.format("Double check before timer-state-reset mainSet. %s. %s. fetchPosition:",
                            arbitrageService.getMainSetStr(), arbitrageService.getxRateLimitString());
                    if (Thread.interrupted()) {
                        return;
                    }
                    final String pos1 = arbitrageService.getLeftMarketService().fetchPosition();
                    if (Thread.interrupted()) {
                        return;
                    }
                    final String pos2 = arbitrageService.getRightMarketService().fetchPosition();
                    warningLogger.info(infoMsg + "left " + pos1);
                    warningLogger.info(infoMsg + "right " + pos2);

                    if (arbitrageService.getLeftMarketService().getContractType().isQuanto()) {
                        final String infoMsgXBTUSD = String.format("Double check before timer-state-reset XBTUSD. %s fetchPosition:",
                                arbitrageService.getExtraSetStr());
                        checkBitmexPosXBTUSD(infoMsgXBTUSD);
                    }

                    if (Thread.interrupted()) {
                        return;
                    }
//                    doCorrectionImmediate(SignalType.CORR_TIMER); - no correction. StopAllActions instead.
                    if (!isPosEqualByMaxAdj(getDcMainSet()) || !isPosEqualByMaxAdj(getDcExtraSet())) {
                        arbitrageService.getLeftMarketService().stopAllActions("stopAllActions");
                        arbitrageService.getRightMarketService().stopAllActions("stopAllActions");
                        arbitrageService.resetArbState("timer-state-reset");
                        slackNotifications.sendNotify(NotifyType.STOP_ALL_ACTIONS_BY_MDC_TIMER, "STOP_ALL_ACTIONS_BY_MDC_TIMER: timer-state-reset");
                    }
                })
                .doOnError(e -> {
                    warningLogger.error("timer-state-reset failed. " + e.getMessage());
                    log.error("timer-state-reset failed.", e);
                })
                .retry()
                .subscribe();
    }

    private void checkBitmexPosXBTUSD(String infoMsg) {
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        if (left.getMarketStaticData() == MarketStaticData.BITMEX) {
            final BitmexService bitmexService = (BitmexService) left;
            bitmexService.posXBTUSDUpdater();
        }
        final Pos pos2 = left.getPositionXBTUSD();
        String msg = infoMsg + "bitmexXBTUSD=" + BitmexUtils.positionToString(pos2);
        warningLogger.info(msg);
        log.info(msg);
    }

    void stopTimerToImmediateCorrection() {
        if (theTimerToImmediateCorr != null) {
            theTimerToImmediateCorr.dispose();
        }
    }

    public void checkMDCJob() {
        arbitrageService.getParams().setLastMDCCheck(new Date());

        if (marketsStopped()) {
            dtMdc.stop();
            dtMdcAdj.stop();
            dtExtraMdc.stop();
            dtExtraMdcAdj.stop();
            return;
        }
        if (!hasMDCStarted) {
            warningLogger.info("MDC has started");
            hasMDCStarted = true;
        }

        try {
            Integer delaySec = settingsRepositoryService.getSettings().getPosAdjustment().getCorrDelaySec();
            Integer adjDelaySec = settingsRepositoryService.getSettings().getPosAdjustment().getPosAdjustmentDelaySec();
            checkMdcMainSet("MDC-mainSet", delaySec, dtMdc, this::isMdcNeededMainSet);
            checkMdcMainSet("MDCADJ-mainSet", adjDelaySec, dtMdcAdj, this::isMdcAdjNeededMainSet);

            if (arbitrageService.isEth()) {
                checkMdcExtraSet("MDC-extraSet", delaySec, dtExtraMdc, this::isMdcNeededExtraSet);
                checkMdcExtraSet("MDCADJ-extraSet", adjDelaySec, dtExtraMdcAdj, this::isMdcAdjNeededExtraSet);
            }

        } catch (Exception e) {
            warningLogger.error("Correction MDC failed. " + e.getMessage());
            log.error("Correction MDC failed.", e);
        }
    }

    private void checkMdcExtraSet(String name, Integer delaySec, DelayTimer dt, BooleanSupplier isNeededFunc) {
        if (isNeededFunc.getAsBoolean()) {
            dt.activate();
            long secToReady = dt.secToReadyPrecise(delaySec);
            if (secToReady > 0) {
                String msg = String.format("%s signal. Waiting delay(sec)=%s", name, secToReady);
                log.info(msg);
                warningLogger.info(msg);
            } else {

                String infoMsgXBTUSD = String.format("Double check before %s. %s fetchPosition:", name, arbitrageService.getExtraSetStr());
                checkBitmexPosXBTUSD(infoMsgXBTUSD);

                if (isNeededFunc.getAsBoolean()) {
                    final BigDecimal maxDiffCorr = arbitrageService.getParams().getMaxDiffCorr();
                    final BigDecimal positionsDiffWithHedge = getDcExtraSet();
                    String msg = String.format("MDC extraSet posWithHedge=%s > mdc=%s", positionsDiffWithHedge, maxDiffCorr);
                    warningLogger.info(msg);
                    arbitrageService.getLeftMarketService().stopAllActions("stopAllActions");
                    arbitrageService.getRightMarketService().stopAllActions("stopAllActions");
                    arbitrageService.resetArbState("MDC extraSet");
                    dt.stop();
                    slackNotifications.sendNotify(NotifyType.STOP_ALL_ACTIONS_BY_MDC_TIMER, "STOP_ALL_ACTIONS_BY_MDC_TIMER:" + msg);
                }
            }
        } else {
            dt.stop();
        }
    }

    private void checkMdcMainSet(String name, Integer delaySec, DelayTimer dt, BooleanSupplier isNeededFunc) throws Exception {
        if (isNeededFunc.getAsBoolean()) {
            dt.activate();
            long secToReady = dt.secToReadyPrecise(delaySec);
            if (secToReady > 0) {
                String msg = String.format("%s signal. Waiting delay(sec)=%s", name, secToReady);
                log.info(msg);
                warningLogger.info(msg);
            } else {
                String infoMsg = String.format("Double check before %s. %s. %s fetchPosition:", name, arbitrageService.getMainSetStr(),
                        arbitrageService.getxRateLimitString());
                final String pos1 = arbitrageService.getLeftMarketService().fetchPosition();
                final String pos2 = arbitrageService.getRightMarketService().fetchPosition();
                warningLogger.info(infoMsg + "left " + pos1);
                warningLogger.info(infoMsg + "right " + pos2);

                if (isNeededFunc.getAsBoolean()) {
                    final BigDecimal maxDiffCorr = arbitrageService.getParams().getMaxDiffCorr();
                    final BigDecimal positionsDiffWithHedge = getDcMainSet();
                    String msg = String.format("%s posWithHedge=%s > mdc=%s", name, positionsDiffWithHedge, maxDiffCorr);
                    warningLogger.info(msg);
                    arbitrageService.getLeftMarketService().stopAllActions("stopAllActions");
                    arbitrageService.getRightMarketService().stopAllActions("stopAllActions");
                    arbitrageService.resetArbState("MDC mainSet");
                    dt.stop();
                    slackNotifications.sendNotify(NotifyType.STOP_ALL_ACTIONS_BY_MDC_TIMER, "STOP_ALL_ACTIONS_BY_MDC_TIMER: " + msg);
                }
            }

        } else {
            dt.stop();
        }
    }

    boolean marketsStopped() {
        return okexSettlementService.isSettlementMode()
                || arbitrageService.isArbStateStopped()
                || arbitrageService.isArbForbidden()
                || arbitrageService.getDqlStateService().isPreliq()
                || !fullBalanceIsValid();
    }

    private boolean marketsReady() {
        return !okexSettlementService.isSettlementMode()
                && arbitrageService.getLeftMarketService().isReadyForArbitrage()
                && arbitrageService.getRightMarketService().isReadyForArbitrage()
                && !arbitrageService.isArbStateStopped()
                && !arbitrageService.getDqlStateService().isPreliq()
                && fullBalanceIsValid();
    }

    @SuppressWarnings("Duplicates")
    private boolean marketsReadyForCorr() {
        final MarketServicePreliq bitmexService = arbitrageService.getLeftMarketService();
        final boolean btmReady = bitmexService.getMarketState() == MarketState.READY;
        final boolean btmSo = bitmexService.getMarketState() == MarketState.SYSTEM_OVERLOADED;  // when SO, then corr on okex
        final boolean btmReadyForCorr = !bitmexService.hasOpenOrders() && (btmReady || btmSo);

        return !okexSettlementService.isSettlementMode()
                && btmReadyForCorr
                && arbitrageService.getRightMarketService().isReadyForArbitrage()
                && !arbitrageService.getDqlStateService().isPreliq()
                && fullBalanceIsValid();
    }

    @SuppressWarnings("Duplicates")
    private boolean marketsReadyForAdj() {
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        final boolean leftReady = left.getMarketState() == MarketState.READY;
        final boolean leftSo = left.getMarketState() == MarketState.SYSTEM_OVERLOADED;
        final boolean leftSoReady = leftSo && adjOnRight();
        final boolean leftReadyForAdj = !left.hasOpenOrders() && (leftReady || leftSoReady);

        return !okexSettlementService.isSettlementMode()
                && leftReadyForAdj
                && arbitrageService.getRightMarketService().isReadyForArbitrage()
                && !arbitrageService.getDqlStateService().isPreliq()
                && fullBalanceIsValid();
    }

    private boolean fullBalanceIsValid() {
        final FullBalance firstFullBalance = arbitrageService.getLeftMarketService().getFullBalance();
        final FullBalance secondFullBalance = arbitrageService.getRightMarketService().getFullBalance();
        return firstFullBalance.isValid() && secondFullBalance.isValid();
    }

    private boolean adjOnRight() {
        final BigDecimal bP = arbitrageService.getLeftMarketService().getPosVal();
        final Pos secondPos = arbitrageService.getRightMarketService().getPos();
        final BigDecimal oPL = secondPos.getPositionLong();
        final BigDecimal oPS = secondPos.getPositionShort();

        final BigDecimal cm = arbitrageService.getCm();
        boolean isEth = arbitrageService.isEth();
        final BigDecimal dc = getDcMainSet().setScale(2, RoundingMode.HALF_UP);
        final CorrObj corrObj = new CorrObj(SignalType.ADJ, oPL, oPS);
        final BigDecimal hedgeAmount = getHedgeAmountMainSet();

        fillCorrObjForAdj(corrObj, hedgeAmount, bP, oPL, oPS, cm, isEth, dc, false);

        if (corrObj.marketService != null && corrObj.marketService.getArbType() == ArbType.RIGHT) {
            return true;
        }

        return false;
    }

    private boolean isMdcNeededMainSet() {
        final BigDecimal maxDiffCorr = arbitrageService.getParams().getMaxDiffCorr();
        final BigDecimal dc = getDcMainSet();
        return (!isPosEqualByMaxAdj(dc) && dc.abs().compareTo(maxDiffCorr) >= 0);
    }

    private boolean isMdcNeededExtraSet() {
        final BigDecimal maxDiffCorr = arbitrageService.getParams().getMaxDiffCorr();
        final BigDecimal dcExtra = getDcExtraSet();
        return (!isPosEqualByMaxAdj(dcExtra) && dcExtra.abs().compareTo(maxDiffCorr) >= 0);
    }

    private boolean isMdcAdjNeededMainSet() {
        final BigDecimal maxDiffCorr = arbitrageService.getParams().getMaxDiffCorr();
        final BigDecimal dc = getDcMainSet();
        return (isAdjViolated(dc) && dc.abs().compareTo(maxDiffCorr) >= 0);
    }

    private boolean isMdcAdjNeededExtraSet() {
        final BigDecimal maxDiffCorr = arbitrageService.getParams().getMaxDiffCorr();
        final BigDecimal dcExtra = getDcExtraSet();
        return (isAdjViolated(dcExtra) && dcExtra.abs().compareTo(maxDiffCorr) >= 0);
    }

    private void checkPosDiff() throws Exception {
        if (!hasGeneralCorrStarted
                || arbitrageService.getLeftMarketService() == null
                || !arbitrageService.getLeftMarketService().isStarted()
                || marketsStopped()
                || settingsRepositoryService.getSettings().getManageType().isManual()
        ) {
            dtCorr.stop();
            dtExtraCorr.stop();
            dtAdj.stop();
            dtExtraAdj.stop();
            return;
        }

        if (!checkInProgress) {
            checkInProgress = true;

            try {
                arbitrageService.getParams().setLastCorrCheck(new Date());

                updateTimerToImmediateCorr();

                final CorrParams corrParams = persistenceService.fetchCorrParams();
                tryFinishPrevCorr(corrParams);

                if (corrStartedOrFailed(corrParams)) {
                    return;
                }

                boolean isQuanto = arbitrageService.getLeftMarketService().getContractType().isQuanto();
                if (isQuanto) {
                    if (corrExtraStartedOrFailed(corrParams)) {
                        return;
                    }
                }

                if (adjStartedOrFailed(corrParams)) {
                    return;
                }

                if (isQuanto) {
                    if (adjExtraStartedOrFailed(corrParams)) {
                        return;
                    }
                }

            } finally {
                checkInProgress = false;
            }
        }
    }

    private boolean adjStartedOrFailed(CorrParams corrParams) throws Exception {

        // if all READY more than X sec
        final BigDecimal dcMainSet = getDcMainSet();
        if (marketsReadyForAdj() && isAdjViolated(dcMainSet) && corrParams.getAdj().hasSpareAttempts()) {

            final Settings settings = settingsRepositoryService.getSettings();
            final PosAdjustment pa = settings.getPosAdjustment();
            final long secToReady = dtAdj.secToReadyPrecise(pa.getPosAdjustmentDelaySec());

            final boolean activated = dtAdj.activate();
            if (activated) {
                final BigDecimal max = pa.getPosAdjustmentMax();
                final BigDecimal min = pa.getPosAdjustmentMin();
                String msg = String.format("Adj signal mainSet. Waiting delay(sec)=%s, nt_usd=%s, posAdjMin/Max=%s/%s", secToReady, dcMainSet, min, max);
                log.info(msg);
                warningLogger.info(msg);
            }

            if (secToReady > 0) {
                // do nothing
            } else {

                String infoMsg = String.format("Double check before adjustment mainSet. %s. %s fetchPosition:",
                        arbitrageService.getMainSetStr(), arbitrageService.getxRateLimitString());
                if (doubleFetchPositionFailed(infoMsg, false)) {
                    return true;
                }

                if (marketsReadyForAdj() && isAdjViolated(getDcMainSet())) {

                    doCorrection(getHedgeAmountMainSet(), SignalType.ADJ);
                    dtAdj.stop();
                    return true; // started

                } else {
                    dtAdj.stop();
                }
            }
        } else {
            dtAdj.stop();
            maxCountNotify(corrParams.getAdj(), "adj");
        }

        return false;
    }

    private boolean doubleFetchPositionFailed(String infoMsg, boolean isExtra) throws Exception {
        if (!isExtra) {
            final String pos1 = arbitrageService.getLeftMarketService().fetchPosition();
            if (Thread.interrupted()) {
                return true;
            }

            final String pos2 = arbitrageService.getRightMarketService().fetchPosition();
            if (Thread.interrupted()) {
                return true;
            }
            warningLogger.info(infoMsg + "left " + pos1 + "; right " + pos2);
        } else {
            checkBitmexPosXBTUSD(infoMsg);
            if (Thread.interrupted()) {
                return true;
            }
        }

        return false;
    }

    private boolean adjExtraStartedOrFailed(CorrParams corrParams) throws Exception {
        // if all READY more than X sec
        final BigDecimal dcExtraSet = getDcExtraSet();
        if (marketsReady() && isAdjViolated(dcExtraSet) && corrParams.getAdj().hasSpareAttempts()) {

            final Settings settings = settingsRepositoryService.getSettings();
            final PosAdjustment pa = settings.getPosAdjustment();
            final long secToReady = dtExtraAdj.secToReadyPrecise(pa.getPosAdjustmentDelaySec());

            final boolean activated = dtExtraAdj.activate();
            if (activated) {
                final BigDecimal max = pa.getPosAdjustmentMax();
                final BigDecimal min = pa.getPosAdjustmentMin();
                String msg = String.format("Adj signal extraSet. Waiting delay(sec)=%s, nt_usd=%s, posAdjMin/Max=%s/%s", secToReady, dcExtraSet, min, max);
                log.info(msg);
                warningLogger.info(msg);
            }

            if (secToReady > 0) {
                // do nothing
            } else {

                String infoMsg = String.format("Double check before adjustment XBTUSD. %s fetchPosition:",
                        arbitrageService.getExtraSetStr());
                if (doubleFetchPositionFailed(infoMsg, true)) {
                    return true;
                }

                // Second check
                if (marketsReady() && isAdjViolated(getDcExtraSet())) {

                    if (settings.getManageType().isAuto()) {
                        doCorrection(getHedgeAmountExtraSet(), SignalType.ADJ_BTC);
                        dtExtraAdj.stop();
                        return true; // started
                    } // else stay _ready_

                } else {
                    dtExtraAdj.stop();
                }
            }
        } else {
            dtExtraAdj.stop();
            maxCountNotify(corrParams.getAdj(), "adj");
        }
        return false;
    }

    private boolean corrStartedOrFailed(CorrParams corrParams) throws Exception {
        final BigDecimal dcMainSet = getDcMainSet();
        if (marketsReadyForCorr() && !isPosEqualByMaxAdj(dcMainSet) && corrParams.getCorr().hasSpareAttempts()) {

            final Settings settings = settingsRepositoryService.getSettings();
            final PosAdjustment pa = settings.getPosAdjustment();
            final long secToReady = dtCorr.secToReadyPrecise(pa.getCorrDelaySec());

            final boolean activated = dtCorr.activate();
            if (activated) {
                final BigDecimal posAdjustmentMax = pa.getPosAdjustmentMax();
                String msg = String.format("Corr signal mainSet. Waiting delay(sec)=%s, nt_usd=%s, posAdjMax=%s", secToReady, dcMainSet, posAdjustmentMax);
                log.info(msg);
                warningLogger.info(msg);
            }

            if (secToReady > 0) {
                // do nothing
            } else {

                String infoMsg = String.format("Double check before correction mainSet. %s. %s. fetchPosition:",
                        arbitrageService.getMainSetStr(), arbitrageService.getxRateLimitString());
                if (doubleFetchPositionFailed(infoMsg, false)) {
                    return true; // failed
                }

                // Second check
                if (marketsReadyForCorr() && !isPosEqualByMaxAdj(getDcMainSet())) {

                    doCorrection(getHedgeAmountMainSet(), SignalType.CORR);
                    dtCorr.stop();
                    return true; // started

                } else {
                    dtCorr.stop();
                }
            }

        } else {
            dtCorr.stop();
            maxCountNotify(corrParams.getCorr(), "corr");
        }
        return false;
    }

    private void maxCountNotify(CountedWithExtra countedWithExtra, String name) {
        if (countedWithExtra.totalCountViolated()) {
            slackNotifications.sendNotify(NotifyType.ADJ_CORR_MAX_TOTAL,
                    String.format("%s max total %s reached ", name, countedWithExtra.getMaxTotalCount()));
        }
        if (countedWithExtra.maxErrorCountViolated()) {
            slackNotifications.sendNotify(NotifyType.ADJ_CORR_MAX_ATTEMPT,
                    String.format("%s max attempts %s reached", name, countedWithExtra.getMaxErrorCount()));
        }
    }

    private boolean corrExtraStartedOrFailed(CorrParams corrParams) throws Exception {
        final BigDecimal dcExtraSet = getDcExtraSet();
        if (marketsReady() && !isPosEqualByMaxAdj(dcExtraSet) && corrParams.getCorr().hasSpareAttempts()) {

            final Settings settings = settingsRepositoryService.getSettings();
            final PosAdjustment pa = settings.getPosAdjustment();
            final long secToReady = dtExtraCorr.secToReadyPrecise(pa.getCorrDelaySec());

            final boolean activated = dtExtraCorr.activate();
            if (activated) {
                final BigDecimal posAdjustmentMax = pa.getPosAdjustmentMax();
                String msg = String.format("Corr signal extraSet. Waiting delay(sec)=%s, nt_usd=%s, posAdjMax=%s", secToReady, dcExtraSet, posAdjustmentMax);
                log.info(msg);
                warningLogger.info(msg);
            }

            if (secToReady > 0) {
                // do nothing
            } else {

                String info = String.format("Double check before correction XBTUSD. %s fetchPosition:",
                        arbitrageService.getExtraSetStr());
                if (doubleFetchPositionFailed(info, true)) {
                    return true; // failed
                }

                // Second check
                if (marketsReady() && !isPosEqualByMaxAdj(getDcExtraSet())) {

                    doCorrection(getHedgeAmountExtraSet(), SignalType.CORR_BTC);
                    dtExtraCorr.stop();
                    return true; // started

                } else {
                    dtExtraCorr.stop();
                }
            }

        } else {
            dtExtraCorr.stop();
            maxCountNotify(corrParams.getCorr(), "corr");
        }
        return false;
    }

    BigDecimal getHedgeAmountMainSet() {
        final BigDecimal hedgeAmount = arbitrageService.isEth()
                ? hedgeService.getHedgeEth()
                : hedgeService.getHedgeBtc();
        if (hedgeAmount == null) {
            warningLogger.error("Hedge amount is null on checkPosDiff");
            throw new RuntimeException("Hedge amount is null on checkPosDiff");
        }
        return hedgeAmount;
    }

    private BigDecimal getHedgeAmountExtraSet() {
        if (!arbitrageService.isEth()) {
            return BigDecimal.ZERO;
        }
        final BigDecimal hedgeAmount = hedgeService.getHedgeBtc();
        if (hedgeAmount == null) {
            warningLogger.error("Hedge amount is null on checkPosDiff");
            throw new RuntimeException("Hedge amount is null on checkPosDiff");
        }
        return hedgeAmount;
    }

//    private void doCorrectionImmediate(SignalType signalTypeMdcOrMdcbtc) {
//        if (arbitrageService.getFirstMarketService().getMarketState().isStopped()
//                || arbitrageService.getSecondMarketService().getMarketState().isStopped()) {
//            return;
//        }
//
//        final CorrParams corrParams = persistenceService.fetchCorrParams();
//
//        if (corrParams.getCorr().hasSpareAttempts()) {
//            // The double check with 'fetchPosition' should be before this method
//            final BigDecimal bP = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
//            final BigDecimal oPL = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
//            final BigDecimal oPS = arbitrageService.getSecondMarketService().getPosition().getPositionShort();
//            final BigDecimal hedgeAmount = signalTypeMdcOrMdcbtc == SignalType.CORR_MDC
//                    ? getHedgeAmountMainSet() : getHedgeAmountExtraSet();
//
//            doCorrection(bP, oPL, oPS, hedgeAmount, signalTypeMdcOrMdcbtc);
//        }
//    }

    private void doCorrection(final BigDecimal hedgeAmount, SignalType baseSignalType) {
        // NOTE: CorrParams may be changed by UI request -> will create illegal state for CorrParams.
        // change by preliq should not be possible according to business logic.
        final CorrParams corrParams = persistenceService.fetchCorrParams();
        countFailedOnStartCorr(corrParams, baseSignalType);
        if (hasNoSpareAttempts(baseSignalType, corrParams)) {
            return;
        }

        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        boolean isLeftOkex = left.getMarketStaticData() == MarketStaticData.OKEX;
        final BigDecimal leftPosVal = left.getPosVal();
        final MarketServicePreliq right = arbitrageService.getRightMarketService();
        final Pos secondPos = right.getPos();
        final BigDecimal oPL = secondPos.getPositionLong();
        final BigDecimal oPS = secondPos.getPositionShort();
        final BigDecimal rightPosVal = oPL.subtract(oPS);

        if (!left.isStarted() || marketsStopped()) {
            return;
        }
        stopTimerToImmediateCorrection(); // avoid double-correction

        final BigDecimal cm = arbitrageService.getCm();
        boolean isEth = arbitrageService.isEth();

        final BigDecimal dc = (baseSignalType == SignalType.ADJ_BTC || baseSignalType == SignalType.CORR_BTC || baseSignalType == SignalType.CORR_BTC_MDC)
                ? getDcExtraSet().setScale(2, RoundingMode.HALF_UP)
                : getDcMainSet().setScale(2, RoundingMode.HALF_UP);

        // --- Filling corrObj ---> market and amount

        final CorrObj corrObj = new CorrObj(baseSignalType, oPL, oPS);

        // for logs
        Integer maxBtm = null;// = corrParams.getCorr().getMaxVolCorrBitmex(bitmexService.getCm());
        Integer maxOkex = null;// = corrParams.getCorr().getMaxVolCorrOkex();
        String corrName = baseSignalType.getCounterName();

        // init all corrObj.* properties
        final BigDecimal bMax;
        final BigDecimal okMax;
        if (baseSignalType == SignalType.ADJ_BTC) {

            BigDecimal bPXbtUsd = left.getPositionXBTUSD().getPositionLong();
            defineCorrAdjExtraSetByPos(corrObj, bPXbtUsd, dc);
            final CorrParams corrParamsExtra = persistenceService.fetchCorrParams();
            corrParamsExtra.getCorr().setIsEth(false);
            bMax = BigDecimal.valueOf(corrParamsExtra.getCorr().getMaxVolCorrBitmex(isLeftOkex));
            okMax = BigDecimal.valueOf(corrParamsExtra.getCorr().getMaxVolCorrOkex());

        } else if (baseSignalType == SignalType.ADJ) {

            fillCorrObjForAdj(corrObj, hedgeAmount, leftPosVal, oPL, oPS, cm, isEth, dc, true);
            bMax = BigDecimal.valueOf(corrParams.getCorr().getMaxVolCorrBitmex(isLeftOkex));
            okMax = BigDecimal.valueOf(corrParams.getCorr().getMaxVolCorrOkex());

        } else if (baseSignalType == SignalType.CORR_BTC || baseSignalType == SignalType.CORR_BTC_MDC) {

            @SuppressWarnings("Duplicates")
            BigDecimal bPXbtUsd = left.getPositionXBTUSD().getPositionLong();
            defineCorrAdjExtraSetByPos(corrObj, bPXbtUsd, dc);
            final CorrParams corrParamsExtra = persistenceService.fetchCorrParams();
            corrParamsExtra.getCorr().setIsEth(false);
            bMax = BigDecimal.valueOf(corrParamsExtra.getCorr().getMaxVolCorrBitmex(isLeftOkex));
            okMax = BigDecimal.valueOf(corrParamsExtra.getCorr().getMaxVolCorrOkex());

        } else { // corr
            maxBtm = corrParams.getCorr().getMaxVolCorrBitmex(isLeftOkex);
            maxOkex = corrParams.getCorr().getMaxVolCorrOkex();

            if (left.getMarketState() == MarketState.SYSTEM_OVERLOADED) {
                // no check okexAmountIsZero(corrObj, dc, isEth)
                adaptCorrByPosOnBtmSo(corrObj, oPL, oPS, dc, isEth);
            } else {
                adaptCorrAdjByPos(corrObj, leftPosVal, oPL, oPS, hedgeAmount, dc, cm, isEth, !isLeftOkex);
            }
            bMax = BigDecimal.valueOf(corrParams.getCorr().getMaxVolCorrBitmex(isLeftOkex));
            okMax = BigDecimal.valueOf(corrParams.getCorr().getMaxVolCorrOkex());

        } // end corr

        // ------
        assert corrObj.marketService != null;
        trySwitchByDqlOrEBestMin(oPS, cm, isEth, dc, corrObj, corrParams);

        reupdateSignalTypeToIncrease(corrObj, leftPosVal, rightPosVal);
        corrObj.noSwitch = true;
        validateIncreaseByDqlAndAdaptMaxVol(corrObj, dc, cm, isEth, bMax, okMax);

        // use corrObj.*
        final MarketServicePreliq marketService = corrObj.marketService;
        final Order.OrderType orderType = corrObj.orderType;
        final BigDecimal correctAmount = corrObj.correctAmount;
        final SignalType signalType = corrObj.signalType;
        final ContractType contractType = corrObj.contractType;

        // 3. check DQL, correctAmount
        if (corrObj.errorDescription != null) { // DQL violation (open_min or close_min)
            countOnStartCorr(corrParams, signalType); // inc counters
            final String msg = String.format("No %s. %s", baseSignalType, corrObj.errorDescription);
            warningLogger.warn(msg);
            corrObj.marketService.getTradeLogger().warn(msg);
            slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, msg);
        } else if (correctAmount.signum() <= 0) {
            countOnStartCorr(corrParams, signalType); // inc counters
            final String msg = String.format("No %s: amount=%s, maxBtm=%s, maxOk=%s, dc=%s, btmPos=%s, okPos=%s, hedge=%s, signal=%s",
                    corrName,
                    correctAmount,
                    maxBtm, maxOkex, dc,
                    left.getPos().toString(),
                    right.getPos().toString(),
                    hedgeAmount.toPlainString(),
                    signalType
            );
            warningLogger.warn(msg);
            corrObj.marketService.getTradeLogger().warn(msg);
            slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY,
                    String.format("No %s: amount=%s", corrName, correctAmount));
        } else {

            final PlacingType placingType;
            if (!signalType.isAdj()) {
                placingType = PlacingType.TAKER; // correction is only taker
            } else {
                final PosAdjustment posAdjustment = settingsRepositoryService.getSettings().getPosAdjustment();
                placingType = (posAdjustment.getPosAdjustmentPlacingType().isTaker() && marketService.getName().equals(OkCoinService.NAME))
                        ? PlacingType.TAKER
                        : posAdjustment.getPosAdjustmentPlacingType();
            }

            if (outsideLimits(marketService, orderType, placingType, signalType)) {
                // do nothing
            } else {

                arbitrageService.setSignalType(signalType);

                // Market specific params
                final String counterName = marketService.getCounterNameNext(signalType);
                marketService.setBusy(counterName, MarketState.ARBITRAGE);

                final Long tradeId = arbitrageService.getLastTradeId();

                final String soMark = getSoMark(corrObj);
                final SignalTypeEx signalTypeEx = new SignalTypeEx(signalType, soMark);

                countOnStartCorr(corrParams, signalType);

                final String message = String.format("#%s %s %s amount=%s c=%s. ", counterName, placingType, orderType, correctAmount, contractType);
                final String setStr = signalType.getCounterName().contains("btc") ? arbitrageService.getExtraSetStr() : arbitrageService.getMainSetStr();
                tradeService
                        .info(tradeId, counterName, String.format("#%s %s %s", signalTypeEx.getCounterName(), setStr, arbitrageService.getxRateLimitString()));
                tradeService.info(tradeId, counterName, message);
                prevTradeId = tradeId;
                prevCounterName = counterName;
                prevCorrObj = corrObj;

                PlaceOrderArgs placeOrderArgs = PlaceOrderArgs.builder()
                        .orderType(orderType)
                        .amount(correctAmount)
                        .placingType(placingType)
                        .signalType(signalType)
                        .attempt(1)
                        .tradeId(tradeId)
                        .counterName(counterName)
                        .contractType(contractType)
                        .build();
                marketService.getTradeLogger().info(message + placeOrderArgs.toString());
                final TradeResponse tradeResponse = marketService.placeOrder(placeOrderArgs);
                if (signalType.isMainSet() && tradeResponse.errorInsufficientFunds()) {
                    // NO SWITCH after error the market
//                    final String switchMsg = String
//                            .format("%s switch markets. %s INSUFFICIENT_BALANCE.", corrObj.signalType, corrObj.marketService.getNameWithType());
//                    warningLogger.warn(switchMsg);
//                    corrObj.marketService.getTradeLogger().info(switchMsg);
//                    slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, switchMsg);

//                    final MarketServicePreliq theOtherService = corrObj.marketService.getArbType() == ArbType.LEFT
//                            ? right
//                            : left;
//                    final BigDecimal bMax = BigDecimal.valueOf(corrParams.getCorr().getMaxVolCorrBitmex(isLeftOkex));
//                    final BigDecimal okMax = BigDecimal.valueOf(corrParams.getCorr().getMaxVolCorrOkex());
//                    switchMarkets(corrObj, dc, cm, isEth, bMax, okMax, theOtherService);
//                    updateSignalTypeToIncrease(corrObj, leftPosVal, rightPosVal);
//                    PlacingType pl = placingType.isTaker() ? PlacingType.TAKER : placingType;
//                    PlaceOrderArgs theOtherMarketArgs = PlaceOrderArgs.builder()
//                            .orderType(corrObj.orderType)
//                            .amount(corrObj.correctAmount)
//                            .placingType(pl)
//                            .signalType(corrObj.signalType)
//                            .attempt(1)
//                            .tradeId(tradeId)
//                            .counterName(counterName)
//                            .contractType(corrObj.contractType)
//                            .build();
//                    corrObj.marketService.getTradeLogger().info(message + theOtherMarketArgs.toString());
//                    final TradeResponse theOtherResp = corrObj.marketService.placeOrder(theOtherMarketArgs);
//                    if (theOtherResp.errorInsufficientFunds()) {
                        final String msg = String.format("No %s. INSUFFICIENT_BALANCE on both markets.", baseSignalType);
                        warningLogger.warn(msg);
                        corrObj.marketService.getTradeLogger().warn(msg);
                        slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, message);
//                    }
                }
                corrObj.marketService.getArbitrageService().setBusyStackChecker();

                slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, message);
                log.info(message);

            }
        }

    }

    @SuppressWarnings("DuplicatedCode")
    private void trySwitchByDqlOrEBestMin(BigDecimal oPS, BigDecimal cm, boolean isEth, BigDecimal dc, CorrObj corrObj, CorrParams corrParams) {
        boolean isFirstMarket = corrObj.marketService.getArbType() == ArbType.LEFT;
        boolean leftIsBtm = arbitrageService.getLeftMarketService().isBtm();
        final BigDecimal bP = arbitrageService.getLeftMarketService().getPosVal();
        final BigDecimal bitmexUsd;
        if (leftIsBtm) {
            bitmexUsd = isEth
                    ? bP.multiply(BigDecimal.valueOf(10)).divide(cm, 2, RoundingMode.HALF_UP)
                    : bP;
        } else {
            bitmexUsd = isEth
                    ? (bP).multiply(BigDecimal.valueOf(10))
                    : (bP).multiply(BigDecimal.valueOf(100));
        }
        // >>> Corr_increase_pos improvement as Recovery_nt_usd_increase_pos (only button) UPDATE
        StringBuilder exLog = new StringBuilder();
        boolean isSecondMarket = !isFirstMarket;
        boolean increaseOnLeft = isFirstMarket && corrObj.getSignalType().isIncreasePos();
        boolean increaseOnOkex = isSecondMarket && corrObj.getSignalType().isIncreasePos();
        // 1. Сравниваем DQL двух бирж:
        BigDecimal leftDql = arbitrageService.getLeftMarketService().getLiqInfo().getDqlCurr();
        BigDecimal rightDql = arbitrageService.getRightMarketService().getLiqInfo().getDqlCurr();
        exLog.append("leftDql=").append(leftDql).append(",rightDql=").append(rightDql);
        BigDecimal leBest = arbitrageService.getbEbest();
        BigDecimal reBest = arbitrageService.getoEbest();
        String leftEBest = String.format("L_e_best%s_%s", leBest, arbitrageService.getbEbestUsd());
        String rightEBest = String.format(", R_e_best%s_%s", reBest, arbitrageService.getoEbestUsd());
        exLog.append(leftEBest).append(rightEBest);
        if (increaseOnLeft || increaseOnOkex) {
            if (leftDql != null && rightDql != null
                    && leftDql.subtract(rightDql).signum() != 0) {
                // a) У обеих бирж DQL числовые значения (не na), тогда выбираем ту, где DQL выше (это будет биржа A, другая - B).
                isFirstMarket = leftDql.subtract(rightDql).signum() > 0;
            } else {
                // b) Если хотя бы на одной из бирж DQL равен na или DQL числовые и равны, тогда сравниваем e_best_usd бирж. Там, где больше e_best_usd, та биржа A.
                isFirstMarket = leBest.subtract(reBest).signum() >= 0;
            }
        }
        // <<< endOf Corr_increase_pos improvement as Recovery_nt_usd_increase_pos (only button) UPDATE
        // logs
        String exLogStr = exLog.toString();
        if (exLogStr.length() != 0) {
            final String msg = "corrIncreasePosImprovements: " + corrObj.getSignalType() + ": " + exLogStr;
            printTradeLog(msg, corrObj);
        }
        // do the changes
        final boolean alreadyFirst = corrObj.marketService.getArbType() == ArbType.LEFT && isFirstMarket;
        final boolean alreadySecond = corrObj.marketService.getArbType() == ArbType.RIGHT && isSecondMarket;
        if (alreadyFirst || alreadySecond) {
            //do nothing
        } else {
            final MarketServicePreliq left = arbitrageService.getLeftMarketService();
            boolean isLeftOkex = left.getMarketStaticData() == MarketStaticData.OKEX;
            final BigDecimal bMax = BigDecimal.valueOf(corrParams.getCorr().getMaxVolCorrBitmex(isLeftOkex));
            final BigDecimal okMax = BigDecimal.valueOf(corrParams.getCorr().getMaxVolCorrOkex());
            final MarketServicePreliq theOtherService = corrObj.marketService.getArbType() == ArbType.LEFT
                    ? arbitrageService.getRightMarketService()
                    : arbitrageService.getLeftMarketService();
            switchMarkets(corrObj, dc, cm, isEth, bMax, okMax, theOtherService);
        }
    }

    private String getSoMark(CorrObj corrObj) {
        if (corrObj.marketService != null && corrObj.signalType != null) {
            if (corrObj.signalType.isAdj()) {
                if (corrObj.marketService.getName().equals(OkCoinService.NAME)
                        && arbitrageService.getLeftMarketService().getMarketState() == MarketState.SYSTEM_OVERLOADED) {
                    return "_SO";
                }
            } else { // isCorr
                if (corrObj.marketService.getName().equals(OkCoinService.NAME)
                        && corrObj.signalType.isIncreasePos()
                        && arbitrageService.getLeftMarketService().getMarketState() == MarketState.SYSTEM_OVERLOADED) {
                    return "_SO";
                }
            }
        }
        return "";
    }

    private void fillCorrObjForAdj(CorrObj corrObj, BigDecimal hedgeAmount, BigDecimal bP, BigDecimal oPL, BigDecimal oPS, BigDecimal cm, boolean isEth,
            BigDecimal dc, boolean withLogs) {
        final Borders minBorders;
        if (persistenceService.fetchBorders().getActiveVersion() == Ver.V1) {
            final GuiParams guiParams = arbitrageService.getParams();
            minBorders = new Borders(guiParams.getBorder1(), guiParams.getBorder2());
        } else {
            minBorders = bordersService.getMinBordersV2(bP, oPL, oPS);
        }
        if (minBorders != null) {
            adaptAdjByPos(corrObj, bP, oPL, oPS, dc, cm, isEth, minBorders, withLogs);
        } else {
            adaptCorrAdjByPos(corrObj, bP, oPL, oPS, hedgeAmount, dc, cm, isEth, arbitrageService.getLeftMarketService().isBtm());
        }
    }

    private boolean hasNoSpareAttempts(SignalType baseSignalType, CorrParams corrParams) {
        final boolean isAdj = baseSignalType == SignalType.ADJ || baseSignalType == SignalType.ADJ_BTC;
        final boolean isCorr = baseSignalType == SignalType.CORR || baseSignalType == SignalType.CORR_BTC;
        final boolean adjLimit = isAdj && !corrParams.getAdj().hasSpareAttempts();
        final boolean corrLimit = isCorr && !corrParams.getCorr().hasSpareAttempts();
        return adjLimit || corrLimit;
    }

    void validateIncreaseByDqlAndAdaptMaxVol(final CorrObj corrObj, BigDecimal dc, BigDecimal cm, boolean isEth, BigDecimal bMax, BigDecimal okMax) {
        maxVolCorrAdapt(corrObj, bMax, okMax);
        validateIncreasePosByDql(corrObj, dc, cm, isEth, bMax, okMax);
    }

    private void maxVolCorrAdapt(CorrObj corrObj, BigDecimal bMax, BigDecimal okMax) {
        if (corrObj.marketService.getArbType() == ArbType.RIGHT) {
            if (corrObj.correctAmount.compareTo(okMax) > 0) {
                corrObj.correctAmount = okMax;
            }
        } else {
            if (corrObj.correctAmount.compareTo(bMax) > 0) {
                corrObj.correctAmount = bMax;
            }
        }
    }

    private void validateIncreasePosByDql(final CorrObj corrObj, BigDecimal dc, BigDecimal cm, boolean isEth, BigDecimal bMax, BigDecimal okMax) {
        if (corrObj.signalType.isIncreasePos()) {
            if (corrObj.signalType.isMainSet() && !corrObj.signalType.isAdj()) {
                dqlOpenMinAdjust(corrObj, dc, cm, isEth, bMax, okMax);
            }
            dqlCloseMinAdjust(corrObj);
        }
    }

    private void dqlOpenMinAdjust(CorrObj corrObj, BigDecimal dc, BigDecimal cm, boolean isEth, BigDecimal bMax, BigDecimal okMax) {
        final boolean dqlOpenViolated = corrObj.marketService.isDqlOpenViolated();
        if (dqlOpenViolated) {
            if (corrObj.noSwitch) {
                corrObj.correctAmount = BigDecimal.ZERO;
                corrObj.errorDescription = "Try INCREASE_POS when DQL_open_min is violated and noSwitch";
            } else {
                // check if other market isOk
                final MarketServicePreliq theOtherService = corrObj.marketService.getArbType() == ArbType.LEFT
                        ? arbitrageService.getRightMarketService()
                        : arbitrageService.getLeftMarketService();
                boolean theOtherMarketIsViolated = theOtherService.isDqlOpenViolated();
                if (theOtherMarketIsViolated) {
                    corrObj.correctAmount = BigDecimal.ZERO;
                    corrObj.errorDescription = "Try INCREASE_POS when DQL_open_min is violated on both markets.";
                } else {
                    final String switchMsg =
                            String.format("%s switch markets. %s DQL_open_min is violated.", corrObj.signalType, corrObj.marketService.getNameWithType());
                    log.warn(switchMsg);
                    warningLogger.warn(switchMsg);
                    slackNotifications.sendNotify(corrObj.signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, switchMsg);
                    switchMarkets(corrObj, dc, cm, isEth, bMax, okMax, theOtherService);
                }
            }
        }
    }

    /**
     * <p><b>NotNull:</b></p>
     * - corrObj with all properties.
     * <br>
     * <p><b>Possible update:</b></p>
     * - corrObj
     */
    void switchMarkets(CorrObj corrObj, BigDecimal dc, BigDecimal cm, boolean isEth, BigDecimal bMax, BigDecimal okMax,
            MarketServicePreliq theOtherService) {
        corrObj.marketService = theOtherService;
        corrObj.signalType = corrObj.signalType.switchMarket();
        if (theOtherService.getArbType() == ArbType.LEFT) {
            boolean leftIsBtm = theOtherService.isBtm();
            defineCorrectAmountBitmex(corrObj, dc, cm, isEth, leftIsBtm);
        } else {
            // no check okexAmountIsZero(corrObj, dc, isEth)
            defineCorrectAmountOkex(corrObj, dc, isEth);
            defineOkexThroughZero(corrObj);
        }
        maxVolCorrAdapt(corrObj, bMax, okMax);
    }

    /**
     * <p><b>NotNull:</b></p>
     * - corrObj.marketService<br> - corrObj.orderType<br> - corrObj.signalType<br>
     * <br>
     * <p><b>Possible update:</b></p>
     * - corrObj.signalType to increase<br>
     */
    void reupdateSignalTypeToIncrease(CorrObj corrObj, BigDecimal leftPosVal, BigDecimal rightPosVal) {
        if (corrObj.marketService.getArbType() == ArbType.LEFT) {
            if (leftPosVal.signum() == 0
                    || (leftPosVal.signum() > 0 && (corrObj.orderType == OrderType.BID || corrObj.orderType == OrderType.EXIT_ASK))
                    || (leftPosVal.signum() < 0 && (corrObj.orderType == OrderType.ASK || corrObj.orderType == OrderType.EXIT_BID))) { //increase
                corrObj.signalType = corrObj.signalType.toIncrease(true);
            } else {
                corrObj.signalType = corrObj.signalType.toDecrease(true);
            }
        } else { //OKEX
            if (rightPosVal.signum() == 0
                    || (rightPosVal.signum() > 0 && (corrObj.orderType == OrderType.BID || corrObj.orderType == OrderType.EXIT_ASK))
                    || (rightPosVal.signum() < 0 && (corrObj.orderType == OrderType.ASK || corrObj.orderType == OrderType.EXIT_BID))) { //increase
                corrObj.signalType = corrObj.signalType.toIncrease(false);
            } else {
                corrObj.signalType = corrObj.signalType.toDecrease(false);
            }
        }
    }

    private void dqlCloseMinAdjust(CorrObj corrObj) {
        final boolean dqlViolated = corrObj.marketService.isDqlViolated();
        if (dqlViolated) {
            corrObj.correctAmount = BigDecimal.ZERO;
            corrObj.errorDescription = "DQL_close_min is violated";
        }
    }

    public void stopTimer(String type) {
        if (type.equals("adj")) {
            dtAdj.stop();
            dtMdcAdj.stop();
            dtExtraAdj.stop();
            dtExtraMdcAdj.stop();
        } else { //"corr"
            dtCorr.stop();
            dtMdc.stop();
            dtExtraCorr.stop();
            dtExtraMdc.stop();
        }
    }

    /**
     * Corr/adj by 'trying decreasing pos'.
     */
    @SuppressWarnings("Duplicates")
    void adaptCorrAdjByPos(final CorrObj corrObj, final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS, final BigDecimal hedgeAmount,
            final BigDecimal dc, final BigDecimal cm, final boolean isEth, boolean leftIsBtm) {

        final BigDecimal okexUsd = isEth
                ? (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(10))
                : (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(100));

        final BigDecimal bitmexUsd;
        if (leftIsBtm) {
            bitmexUsd = isEth
                    ? bP.multiply(BigDecimal.valueOf(10)).divide(cm, 2, RoundingMode.HALF_UP)
                    : bP;
        } else {
            bitmexUsd = isEth
                    ? (bP).multiply(BigDecimal.valueOf(10))
                    : (bP).multiply(BigDecimal.valueOf(100));
        }

        //noinspection UnnecessaryLocalVariable
        final BigDecimal okEquiv = okexUsd;
        final BigDecimal bEquiv = bitmexUsd.subtract(hedgeAmount);

        if (dc.signum() < 0) {
            corrObj.orderType = Order.OrderType.BID;
            if (bEquiv.compareTo(okEquiv) < 0 || okexAmountIsZero(corrObj, dc, isEth)) {
                // bitmex buy
                defineCorrectAmountBitmex(corrObj, dc, cm, isEth, leftIsBtm);
                corrObj.marketService = arbitrageService.getLeftMarketService();
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
                defineOkexThroughZero(corrObj);

                corrObj.marketService = arbitrageService.getRightMarketService();
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
            if (bEquiv.compareTo(okEquiv) < 0 && !okexAmountIsZero(corrObj, dc, isEth)) {
                // okcoin sell
                defineCorrectAmountOkex(corrObj, dc, isEth);
                defineOkexThroughZero(corrObj);

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
                corrObj.marketService = arbitrageService.getRightMarketService();
            } else {
                // bitmex sell
                defineCorrectAmountBitmex(corrObj, dc, cm, isEth, leftIsBtm);
                corrObj.marketService = arbitrageService.getLeftMarketService();
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

        corrObj.contractType = corrObj.marketService != null ? corrObj.marketService.getContractType() : null;
    }

    /**
     * Corr by 'trying decreasing pos' with handling bitmex==SYSTEM_OVERLOADED.
     */
    @SuppressWarnings("Duplicates")
    private void adaptCorrByPosOnBtmSo(final CorrObj corrObj, final BigDecimal oPL, final BigDecimal oPS, final BigDecimal dc, final boolean isEth) {

        if (dc.signum() < 0) {
            // okcoin buy
            corrObj.orderType = Order.OrderType.BID;
            defineCorrectAmountOkex(corrObj, dc, isEth);
            defineOkexThroughZero(corrObj);

            if ((oPL.subtract(oPS)).signum() >= 0) {
                corrObj.signalType = SignalType.O_CORR_INCREASE_POS;
            } else {
                corrObj.signalType = SignalType.O_CORR;
            }
        } else {
            // okcoin sell
            corrObj.orderType = Order.OrderType.ASK;
            defineCorrectAmountOkex(corrObj, dc, isEth);
            defineOkexThroughZero(corrObj);

            if ((oPL.subtract(oPS)).signum() <= 0) {
                corrObj.signalType = SignalType.O_CORR_INCREASE_POS;
            } else {
                corrObj.signalType = SignalType.O_CORR;
            }
        }

        corrObj.marketService = arbitrageService.getRightMarketService();
        corrObj.contractType = corrObj.marketService.getContractType();
    }

    /**
     * Adj by 'how close to a border'.
     */
    @SuppressWarnings("Duplicates")
    private void adaptAdjByPos(final CorrObj corrObj, final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS,
            final BigDecimal dc, final BigDecimal cm, final boolean isEth, final Borders minBorders, boolean withLogs) {

        assert corrObj.signalType == SignalType.B_ADJ;

        boolean leftIsBtm = arbitrageService.getLeftMarketService().isBtm();
        final OrderBook leftOb = arbitrageService.getLeftMarketService().getOrderBook();
        final OrderBook rightOb = arbitrageService.getRightMarketService().getOrderBook();
        final BigDecimal leftAvg = Utils.calcQuAvg(leftOb, 3);
        final BigDecimal rightAvg = Utils.calcQuAvg(rightOb, 3);

        final Settings settings = settingsRepositoryService.getSettings();
        final PlacingType placingType = settings.getPosAdjustment().getPosAdjustmentPlacingType();
        // com_pts = com / 100 * b_best_sam
        final BigDecimal left_com = (settings.getLeftFee(placingType)).multiply(leftAvg).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
        final BigDecimal right_com = (settings.getOFee(placingType)).multiply(rightAvg).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
        final BigDecimal ntUsd = dc.negate();

        final BigDecimal l_border = minBorders.b_border;
        final BigDecimal r_border = minBorders.o_border;
        final BigDecimal l_delta = arbitrageService.getDelta1();
        final BigDecimal r_delta = arbitrageService.getDelta2();

        String adjName = "";
        if (ntUsd.signum() < 0) {
            //if (nt_usd < 0) {
            //if (l_border - (l_delta - left_com) >= r_border - (r_delta - right_com)
            //adj = R_delta_adj;
            //else
            //adj = L_delta_adj;
            //}
            // L_delta_adj означает что подгонку делаем по l_delta, то есть при nt_usd < 0 adj-сделку sell делаем на bitmex, при nt_usd > 0 adj-сделку buy делаем на okex.
            // R_delta_adj означает что подгонку делаем по r_delta, то есть при nt_usd < 0 adj-сделку sell делаем на okex, при nt_usd > 0 adj-сделку buy делаем на bitmex.
            final boolean btmIsHigher = l_border.subtract(l_delta.subtract(left_com)).compareTo(r_border.subtract(r_delta.subtract(right_com))) >= 0;
            if (btmIsHigher && !okexAmountIsZero(corrObj, dc, isEth)) {
                adjName = "R_delta_adj";
                // okex sell
                corrObj.marketService = arbitrageService.getRightMarketService();
                corrObj.orderType = OrderType.ASK;
                defineCorrectAmountOkex(corrObj, dc, isEth);
                defineOkexThroughZero(corrObj);
                if ((oPL.subtract(oPS)).signum() <= 0) {
                    corrObj.signalType = SignalType.O_ADJ_INCREASE_POS;
                } else {
                    corrObj.signalType = SignalType.O_ADJ;
                }
            } else {
                adjName = "L_delta_adj";
                // bitmex sell
                corrObj.marketService = arbitrageService.getLeftMarketService();
                corrObj.orderType = OrderType.ASK;
                defineCorrectAmountBitmex(corrObj, dc, cm, isEth, leftIsBtm);
                if (bP.signum() <= 0) {
                    corrObj.signalType = SignalType.B_ADJ_INCREASE_POS;
                } else {
                    corrObj.signalType = SignalType.B_ADJ;
                }
            }
        } else if (ntUsd.signum() > 0) { //
            //if (nt_usd > 0) {
            //if (l_border - (l_delta + right_com) >= r_border - (r_delta - left_com)
            //adj = R_delta_adj;
            //else
            //adj = L_delta_adj;
            //}
            // L_delta_adj означает что подгонку делаем по l_delta, то есть при nt_usd < 0 adj-сделку sell делаем на bitmex, при nt_usd > 0 adj-сделку buy делаем на okex.
            // R_delta_adj означает что подгонку делаем по r_delta, то есть при nt_usd < 0 adj-сделку sell делаем на okex, при nt_usd > 0 adj-сделку buy делаем на bitmex.
            final boolean btmIsHigher = l_border.subtract(l_delta.subtract(right_com)).compareTo(r_border.subtract(r_delta.subtract(left_com))) >= 0;
            if (btmIsHigher || okexAmountIsZero(corrObj, dc, isEth)) {
                adjName = "R_delta_adj";
                // bitmex buy
                corrObj.marketService = arbitrageService.getLeftMarketService();
                corrObj.orderType = OrderType.BID;
                defineCorrectAmountBitmex(corrObj, dc, cm, isEth, leftIsBtm);
                if (bP.signum() >= 0) {
                    corrObj.signalType = SignalType.B_ADJ_INCREASE_POS;
                } else {
                    corrObj.signalType = SignalType.B_ADJ;
                }
            } else {
                adjName = "L_delta_adj";
                // okcoin buy
                corrObj.marketService = arbitrageService.getRightMarketService();
                corrObj.orderType = OrderType.BID;
                defineCorrectAmountOkex(corrObj, dc, isEth);
                defineOkexThroughZero(corrObj);
                if ((oPL.subtract(oPS)).signum() >= 0) {
                    corrObj.signalType = SignalType.O_ADJ_INCREASE_POS;
                } else {
                    corrObj.signalType = SignalType.O_ADJ;
                }
            }
        }

        corrObj.contractType = corrObj.marketService != null ? corrObj.marketService.getContractType() : null;
        if (withLogs) {
            printLogsAdjByPos(corrObj, left_com, right_com, ntUsd, l_border, r_border, l_delta, r_delta, adjName);
        }
    }

    private void printLogsAdjByPos(CorrObj corrObj, BigDecimal left_com, BigDecimal right_com, BigDecimal ntUsd, BigDecimal l_border, BigDecimal r_border,
            BigDecimal b_delta, BigDecimal o_delta, String adjName) {
        //для nt_usd < 0:
        //L_delta_adj, l_border - (b_delta + left_com), или
        //R_delta_adj, r_border - (o_delta + right_com), или
        //для nt_usd > 0:
        //L_delta_adj, l_border - (b_delta + right_com), или
        //R_delta_adj, r_border - (o_delta + left_com),
        // Example:
        // L_delta_adj, 2 - (1.2 + (-0.030) = 0,83.
        BigDecimal borderVal = adjName.equals("L_delta_adj") ? l_border : r_border;
        BigDecimal deltaVal = adjName.equals("L_delta_adj") ? b_delta : o_delta;
        BigDecimal comVal = ntUsd.signum() < 0
                ? (adjName.equals("L_delta_adj") ? left_com : right_com)
                : (adjName.equals("L_delta_adj") ? right_com : left_com);
        if (corrObj.marketService != null) {
            final String counterName = corrObj.marketService.getCounterNameNext(corrObj.signalType);
            final String msg = String.format("#%s starting %s, %s - (%s - %s) = %s. %s",
                    counterName,
                    adjName,
                    borderVal,
                    deltaVal,
                    comVal,
                    borderVal.subtract(deltaVal.add(comVal)),
                    corrObj
            );
            printTradeLog(msg, corrObj);
        } else {
            warningLogger.info("adaptAdjByPos failed. " + corrObj);
        }
    }

    private void printTradeLog(String msg, CorrObj corrObj) {
        if (corrObj.marketService != null) {
            final String counterName = corrObj.marketService.getCounterNameNext(corrObj.signalType);
            final Long tradeId = prevTradeId != null ? prevTradeId : arbitrageService.getLastTradeId();
            tradeService.info(tradeId, counterName, msg);
            corrObj.marketService.getTradeLogger().info(msg);
        }
    }

    private void defineCorrAdjExtraSetByPos(final CorrObj corrObj, final BigDecimal bPXbtUsd, final BigDecimal dc) {
        corrObj.contractType = BitmexContractType.XBTUSD_Perpetual;
        corrObj.marketService = arbitrageService.getLeftMarketService();
        corrObj.correctAmount = dc.abs().setScale(0, RoundingMode.DOWN);
        if (dc.signum() < 0) {
            corrObj.orderType = Order.OrderType.BID;
            // bitmex buy
            if (bPXbtUsd.signum() >= 0) {
                setExtraSetIncreatePos(corrObj);
            } else {
                setExtraSetDecreasePos(corrObj);
            }
        } else {
            corrObj.orderType = Order.OrderType.ASK;
            // bitmex sell
            if (bPXbtUsd.signum() <= 0) {
                setExtraSetIncreatePos(corrObj);
            } else {
                setExtraSetDecreasePos(corrObj);
            }
        }
    }

    private void setExtraSetDecreasePos(CorrObj corrObj) {
        if (corrObj.signalType == SignalType.ADJ_BTC) {
            corrObj.signalType = SignalType.B_ADJ_BTC;
        } else if (corrObj.signalType == SignalType.CORR_BTC || corrObj.signalType == SignalType.CORR_BTC_MDC) {
            corrObj.signalType = SignalType.B_CORR_BTC;
        }
    }

    private void setExtraSetIncreatePos(CorrObj corrObj) {
        if (corrObj.signalType == SignalType.ADJ_BTC) {
            corrObj.signalType = SignalType.B_ADJ_BTC_INCREASE_POS;
        } else if (corrObj.signalType == SignalType.CORR_BTC || corrObj.signalType == SignalType.CORR_BTC_MDC) {
            corrObj.signalType = SignalType.B_CORR_BTC_INCREASE_POS;
        }
    }

    void defineCorrectAmountBitmex(CorrObj corrObj, BigDecimal dc, final BigDecimal cm, final boolean isEth, boolean leftIsBtm) {
        if (isEth) {
//            adj/corr_cont_bitmex = abs(dc) / (10 / CM); // если делаем на Bitmex - usd to cont
            BigDecimal btmCm = BigDecimal.valueOf(10).divide(cm, 4, RoundingMode.HALF_UP);
            corrObj.correctAmount = dc.abs().divide(btmCm, 0, RoundingMode.HALF_UP);
        } else {
            final BigDecimal dcCont;
            if (leftIsBtm) {
                dcCont = dc;
            } else { //left okex
                dcCont = dc.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            }
            if (corrObj.signalType.isAdj() || corrObj.signalType.isRecoveryNtUsd()) {
                corrObj.correctAmount = dcCont.abs().setScale(0, RoundingMode.HALF_UP);
            } else {
                corrObj.correctAmount = dcCont.abs().setScale(0, RoundingMode.DOWN);
            }
        }
    }

    private BigDecimal getCorrectAmountOkex(SignalType signalType, BigDecimal dc, boolean isEth) {
        final BigDecimal am;
        if (isEth) {
//            adj/corr_cont_okex = abs(dc) / 10; // если делаем на Okex
            am = dc.abs().divide(BigDecimal.valueOf(10), 0, RoundingMode.HALF_UP);
        } else {
            if (signalType.isAdj() || signalType.isRecoveryNtUsd()) {
                am = dc.abs().divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            } else {
                am = dc.abs().divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
            }
        }
        return am;
    }

    boolean okexAmountIsZero(CorrObj corrObj, BigDecimal dc, boolean isEth) {
        final BigDecimal am = getCorrectAmountOkex(corrObj.signalType, dc, isEth);
        return am.signum() == 0;
    }

    void defineCorrectAmountOkex(CorrObj corrObj, BigDecimal dc, boolean isEth) {
        corrObj.correctAmount = getCorrectAmountOkex(corrObj.signalType, dc, isEth);
    }

    void defineOkexThroughZero(CorrObj corrObj) {
        final OrderType orderType = corrObj.orderType;
        final BigDecimal oPL = corrObj.getOPL();
        final BigDecimal oPS = corrObj.getOPS();
        // okcoin sell
        if (orderType == OrderType.ASK || orderType == OrderType.EXIT_BID) {
            corrObj.okexThroughZero = oPL.signum() > 0 && oPL.subtract(corrObj.correctAmount).signum() < 0;
            if (corrObj.okexThroughZero) { // orderType==CLOSE_BID
                corrObj.correctAmount = oPL;
            }
        }
        // okcoin buy
        if (orderType == OrderType.BID || orderType == OrderType.EXIT_ASK) {
            corrObj.okexThroughZero = oPS.signum() > 0 && oPS.subtract(corrObj.correctAmount).signum() < 0;
            if (corrObj.okexThroughZero) { // orderType==CLOSE_ASK
                corrObj.correctAmount = oPS;
            }
        }
    }

    boolean outsideLimits(MarketServicePreliq marketService, OrderType orderType, PlacingType placingType, SignalType signalType) {
        if (marketService.getLimitsService().outsideLimits(orderType, placingType, signalType)) {
            warningLogger.error("Attempt of correction when outside limits. " + marketService.getLimitsService().getLimitsJson());
            return true;
        }
        return false;
    }

    public boolean checkIsPositionsEqual() {

        ntUsdExecutor.addTask(() -> {
            try {
                checkPosDiff();
            } catch (Exception e) {
                warningLogger.error("Check correction: is failed(check before signal). " + e.getMessage());
                log.error("Check correction: is failed(check before signal).", e);
            }
        });

        return isPosEqual();
    }

    private boolean isPosEqualByMaxAdj(BigDecimal dc) {
        final PosAdjustment pa = settingsRepositoryService.getSettings().getPosAdjustment();
        final BigDecimal posAdjustmentMax = pa.getPosAdjustmentMax();
        return dc.abs().subtract(posAdjustmentMax).signum() <= 0;
    }

    boolean isPosEqualByMinAdj(final BigDecimal dc) {
        final PosAdjustment pa = settingsRepositoryService.getSettings().getPosAdjustment();
        final BigDecimal posAdjustmentMin = pa.getPosAdjustmentMin();
        return dc.abs().subtract(posAdjustmentMin).signum() <= 0;
    }

    private boolean isPosEqual() {
//        final PosAdjustment pa = settingsRepositoryService.getSettings().getPosAdjustment();
//        final BigDecimal posAdjustmentMin = pa.getPosAdjustmentMin();
//
//        return getDc().abs().subtract(posAdjustmentMin).signum() <= 0;
        return isPosEqualByMinAdj(getDcMainSet()) && isPosEqualByMinAdj(getDcExtraSet());
    }

    public boolean isMainSetEqual() {
        return isPosEqualByMinAdj(getDcMainSet());
    }

    public boolean isExtraSetEqual() {
        return isPosEqualByMinAdj(getDcExtraSet());
    }

    private boolean isAdjViolated(BigDecimal dc) {
        final PosAdjustment pa = settingsRepositoryService.getSettings().getPosAdjustment();
        final BigDecimal max = pa.getPosAdjustmentMax();
        final BigDecimal min = pa.getPosAdjustmentMin();
        final BigDecimal dcAbs = dc.abs();
        return dcAbs.subtract(min).signum() > 0 && dcAbs.subtract(max).signum() <= 0;
    }

    BigDecimal getDcMainSet() {
        final BigDecimal hedgeAmountUsd = getHedgeAmountMainSet();
        final boolean isQuanto = arbitrageService.isEth();
        final MarketServicePreliq right = arbitrageService.getRightMarketService();
        final BigDecimal rightUsd = getOkexUsd(isQuanto, right.getPosVal(), right.getSCV());
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        final BigDecimal leftUsd = getLeftUsd(arbitrageService.getCm(), isQuanto, left.getPosVal(), left.isBtm(),
                left.getSCV());
        final BigDecimal bitmexUsdWithHedge = leftUsd.subtract(hedgeAmountUsd);

        return rightUsd.add(bitmexUsdWithHedge);
    }

    BigDecimal getDcMainSet(RecoveryParam rp) {
        final BigDecimal hedgeAmountUsd = getHedgeAmountMainSet();
        final boolean isQuanto = arbitrageService.isEth();
        final MarketServicePreliq right = arbitrageService.getRightMarketService();
        final BigDecimal rightPosVal = rp.isKpRight() ? BigDecimal.ZERO : right.getPosVal();
        final BigDecimal rightUsd = getOkexUsd(isQuanto, rightPosVal, right.getSCV());
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        final BigDecimal leftPosVal = rp.isKpLeft() ? BigDecimal.ZERO : left.getPosVal();
        final BigDecimal leftUsd = getLeftUsd(arbitrageService.getCm(), isQuanto, leftPosVal, left.isBtm(),
                arbitrageService.getLeftMarketService().getSCV());
        final BigDecimal bitmexUsdWithHedge = leftUsd.subtract(hedgeAmountUsd);

        return rightUsd.add(bitmexUsdWithHedge);
    }

    private BigDecimal getDcExtraSet() {
        if (!arbitrageService.isEth() || !arbitrageService.getLeftMarketService().isBtm()) {
            return BigDecimal.ZERO;
        }
        final BigDecimal hedgeAmountUsd = getHedgeAmountExtraSet();
        final BigDecimal bitmexUsd = arbitrageService.getLeftMarketService().getHbPosUsd();
        return bitmexUsd.subtract(hedgeAmountUsd);
    }

    public static BigDecimal getOkexUsd(boolean isQuanto, BigDecimal oP, BigDecimal scv) {
        return oP.multiply(scv);
    }

    public static BigDecimal getLeftUsd(BigDecimal cm, boolean isQuanto, BigDecimal lP, boolean leftIsBitmex, BigDecimal scv) {
        final BigDecimal leftUsd;
        if (leftIsBitmex) {
            leftUsd = isQuanto
                    ? lP.multiply(scv).setScale(2, RoundingMode.HALF_UP)
                    : lP;
        } else {
            leftUsd = getOkexUsd(isQuanto, lP, scv);
        }
        return leftUsd;
    }

    public void setPeriodToCorrection(Long periodToCorrection) {
        arbitrageService.getParams().setPeriodToCorrection(periodToCorrection);
        // restart timer
        stopTimerToImmediateCorrection();
        if (!isPosEqualByMaxAdj(getDcMainSet()) || !isPosEqualByMaxAdj(getDcExtraSet())) {
            startTimerToImmediateCorrection();
        }
    }

    private void updateTimerToImmediateCorr() {
        final boolean dcMainSetViolated = !isPosEqualByMaxAdj(getDcMainSet());
        final boolean dcExtraSetViolated = !isPosEqualByMaxAdj(getDcExtraSet());
        final boolean notOkexSettlementMode = !okexSettlementService.isSettlementMode();
        if (notOkexSettlementMode
                && (dcMainSetViolated || dcExtraSetViolated)) {
            if (theTimerToImmediateCorr == null || theTimerToImmediateCorr.isDisposed()) {
                startTimerToImmediateCorrection();
            }
        } else {
            stopTimerToImmediateCorrection();
        }
    }
}
