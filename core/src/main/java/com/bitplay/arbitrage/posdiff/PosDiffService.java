package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BordersService;
import com.bitplay.arbitrage.BordersService.Borders;
import com.bitplay.arbitrage.HedgeService;
import com.bitplay.arbitrage.dto.DelayTimer;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.dto.SignalTypeEx;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.bitmex.BitmexLimitsService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.bitmex.BitmexUtils;
import com.bitplay.market.model.FullBalance;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.market.okcoin.OkexLimitsService;
import com.bitplay.market.okcoin.OkexSettlementService;
import com.bitplay.model.Pos;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.borders.BorderParams.Ver;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.correction.CountedWithExtra;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.persistance.domain.settings.PosAdjustment;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.utils.Utils;
import io.reactivex.Completable;
import io.reactivex.disposables.Disposable;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
    private BitmexLimitsService bitmexLimitsService;

    @Autowired
    private OkexLimitsService okexLimitsService;

    @Autowired
    private BitmexService bitmexService;
    @Autowired
    private OkCoinService okCoinService;

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

    @EventListener(ApplicationReadyEvent.class)
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
            final String mainSetStr = arbitrageService.getMainSetStr();
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
            final String mainSetStr = arbitrageService.getMainSetStr();
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
            return;
        }
        if (!hasTimerStarted) {
            warningLogger.info("Timer for timer-state-reset has started");
            hasTimerStarted = true;
        }

        final Long periodToCorrection = arbitrageService.getParams().getPeriodToCorrection();
        theTimerToImmediateCorr = Completable.timer(periodToCorrection, TimeUnit.SECONDS)
                .doOnComplete(() -> {
                    final String infoMsg = String.format("Double check before timer-state-reset mainSet. %s fetchPosition:",
                            arbitrageService.getMainSetStr());
                    if (Thread.interrupted()) return;
                    final String pos1 = arbitrageService.getFirstMarketService().fetchPosition();
                    if (Thread.interrupted()) return;
                    final String pos2 = arbitrageService.getSecondMarketService().fetchPosition();
                    warningLogger.info(infoMsg + "bitmex " + pos1);
                    warningLogger.info(infoMsg + "okex "+ pos2);

                    if (arbitrageService.getFirstMarketService().getContractType().isEth()) {
                        final String infoMsgXBTUSD = String.format("Double check before timer-state-reset XBTUSD. %s fetchPosition:",
                                arbitrageService.getExtraSetStr());
                        checkBitmexPosXBTUSD(infoMsgXBTUSD);
                    }

                    if (Thread.interrupted()) return;
//                    doCorrectionImmediate(SignalType.CORR_TIMER); - no correction. StopAllActions instead.
                    if (!isPosEqualByMaxAdj(getDcMainSet()) || !isPosEqualByMaxAdj(getDcExtraSet())) {
                        arbitrageService.getFirstMarketService().stopAllActions();
                        arbitrageService.getSecondMarketService().stopAllActions();
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
        final BitmexService bitmexService = (BitmexService) arbitrageService.getFirstMarketService();
        bitmexService.posXBTUSDUpdater();
        final Pos pos2 = bitmexService.getPositionXBTUSD();
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

            if (bitmexService.getContractType().isEth()) {
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
                    arbitrageService.getFirstMarketService().stopAllActions();
                    arbitrageService.getSecondMarketService().stopAllActions();
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
                String infoMsg = String.format("Double check before %s. %s fetchPosition:", name, arbitrageService.getMainSetStr());
                final String pos1 = arbitrageService.getFirstMarketService().fetchPosition();
                final String pos2 = arbitrageService.getSecondMarketService().fetchPosition();
                warningLogger.info(infoMsg + "bitmex " + pos1);
                warningLogger.info(infoMsg + "okex " + pos2);

                if (isNeededFunc.getAsBoolean()) {
                    final BigDecimal maxDiffCorr = arbitrageService.getParams().getMaxDiffCorr();
                    final BigDecimal positionsDiffWithHedge = getDcMainSet();
                    String msg = String.format("%s posWithHedge=%s > mdc=%s", name, positionsDiffWithHedge, maxDiffCorr);
                    warningLogger.info(msg);
                    arbitrageService.getFirstMarketService().stopAllActions();
                    arbitrageService.getSecondMarketService().stopAllActions();
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
                || arbitrageService.getFirstMarketService().getMarketState() == MarketState.FORBIDDEN
                || arbitrageService.getSecondMarketService().getMarketState() == MarketState.FORBIDDEN
                || arbitrageService.isArbStatePreliq()
                || !fullBalanceIsValid();
    }

    private boolean marketsReady() {
        return !okexSettlementService.isSettlementMode()
                && arbitrageService.getFirstMarketService().isReadyForArbitrage()
                && arbitrageService.getSecondMarketService().isReadyForArbitrage()
                && !arbitrageService.isArbStateStopped()
                && !arbitrageService.isArbStatePreliq()
                && fullBalanceIsValid();
    }

    @SuppressWarnings("Duplicates")
    private boolean marketsReadyForCorr() {
        final MarketServicePreliq bitmexService = arbitrageService.getFirstMarketService();
        final boolean btmReady = bitmexService.getMarketState() == MarketState.READY;
        final boolean btmSo = bitmexService.getMarketState() == MarketState.SYSTEM_OVERLOADED;  // when SO, then corr on okex
        final boolean btmReadyForCorr = !bitmexService.hasOpenOrders() && (btmReady || btmSo);

        return !okexSettlementService.isSettlementMode()
                && btmReadyForCorr
                && arbitrageService.getSecondMarketService().isReadyForArbitrage()
                && !arbitrageService.isArbStatePreliq()
                && fullBalanceIsValid();
    }

    @SuppressWarnings("Duplicates")
    private boolean marketsReadyForAdj() {
        final MarketServicePreliq bitmexService = arbitrageService.getFirstMarketService();
        final boolean btmReady = bitmexService.getMarketState() == MarketState.READY;
        final boolean btmSo = bitmexService.getMarketState() == MarketState.SYSTEM_OVERLOADED;
        final boolean btmSoReady = btmSo && adjOnOkex();
        final boolean btmReadyForAdj = !bitmexService.hasOpenOrders() && (btmReady || btmSoReady);

        return !okexSettlementService.isSettlementMode()
                && btmReadyForAdj
                && arbitrageService.getSecondMarketService().isReadyForArbitrage()
                && !arbitrageService.isArbStatePreliq()
                && fullBalanceIsValid();
    }

    private boolean fullBalanceIsValid() {
        final FullBalance firstFullBalance = arbitrageService.getFirstMarketService().getFullBalance();
        final FullBalance secondFullBalance = arbitrageService.getSecondMarketService().getFullBalance();
        return firstFullBalance.isValid() && secondFullBalance.isValid();
    }

    private boolean adjOnOkex() {
        BigDecimal bP = arbitrageService.getFirstMarketService().getPos().getPositionLong();
        final Pos secondPos = arbitrageService.getSecondMarketService().getPos();
        BigDecimal oPL = secondPos.getPositionLong();
        BigDecimal oPS = secondPos.getPositionShort();

        final BigDecimal cm = bitmexService.getCm();
        boolean isEth = bitmexService.getContractType().isEth();
        final BigDecimal dc = getDcMainSet().setScale(2, RoundingMode.HALF_UP);
        final CorrObj corrObj = new CorrObj(SignalType.ADJ);
        final BigDecimal hedgeAmount = getHedgeAmountMainSet();

        fillCorrObjForAdj(corrObj, hedgeAmount, bP, oPL, oPS, cm, isEth, dc, false);

        if (corrObj.marketService != null && corrObj.marketService.getName().equals(OkCoinService.NAME)) {
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
                || arbitrageService.getFirstMarketService() == null
                || !arbitrageService.getFirstMarketService().isStarted()) {
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

                boolean isEth = arbitrageService.getFirstMarketService().getContractType().isEth();
                if (isEth) {
                    if (corrExtraStartedOrFailed(corrParams)) {
                        return;
                    }
                }

                if (adjStartedOrFailed(corrParams)) {
                    return;
                }

                if (isEth) {
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

                String infoMsg = String.format("Double check before adjustment mainSet. %s fetchPosition:",
                        arbitrageService.getMainSetStr());
                if (doubleFetchPositionFailed(infoMsg, false)) {
                    return true;
                }

                if (marketsReadyForAdj() && isAdjViolated(getDcMainSet())) {

                    if (settings.getManageType().isAuto()) {
                        doCorrection(getHedgeAmountMainSet(), SignalType.ADJ);
                        dtAdj.stop();
                        return true; // started
                    } // else stay _ready_

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
            final String pos1 = arbitrageService.getFirstMarketService().fetchPosition();
            if (Thread.interrupted()) {
                return true;
            }

            final String pos2 = arbitrageService.getSecondMarketService().fetchPosition();
            if (Thread.interrupted()) {
                return true;
            }
            warningLogger.info(infoMsg + "bitmex " + pos1 + "; okex " + pos2);
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

                String infoMsg = String.format("Double check before correction mainSet. %s fetchPosition:",
                        arbitrageService.getMainSetStr());
                if (doubleFetchPositionFailed(infoMsg, false)) {
                    return true; // failed
                }

                // Second check
                if (marketsReadyForCorr() && !isPosEqualByMaxAdj(getDcMainSet())) {

                    if (settings.getManageType().isAuto()) {
                        doCorrection(getHedgeAmountMainSet(), SignalType.CORR);
                        dtCorr.stop();
                        return true; // started
                    } // else stay _ready_

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

                    if (settings.getManageType().isAuto()) {
                        doCorrection(getHedgeAmountExtraSet(), SignalType.CORR_BTC);
                        dtExtraCorr.stop();
                        return true; // started
                    } // else stay _ready_

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
        final BigDecimal hedgeAmount = bitmexService.getContractType().isEth()
                ? hedgeService.getHedgeEth()
                : hedgeService.getHedgeBtc();
        if (hedgeAmount == null) {
            warningLogger.error("Hedge amount is null on checkPosDiff");
            throw new RuntimeException("Hedge amount is null on checkPosDiff");
        }
        return hedgeAmount;
    }

    private BigDecimal getHedgeAmountExtraSet() {
        if (!bitmexService.getContractType().isEth()) {
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

        BigDecimal bP = arbitrageService.getFirstMarketService().getPos().getPositionLong();
        final Pos secondPos = arbitrageService.getSecondMarketService().getPos();
        BigDecimal oPL = secondPos.getPositionLong();
        BigDecimal oPS = secondPos.getPositionShort();

        if (!arbitrageService.getFirstMarketService().isStarted() || marketsStopped()) {
            return;
        }
        stopTimerToImmediateCorrection(); // avoid double-correction

        final BigDecimal cm = bitmexService.getCm();
        boolean isEth = bitmexService.getContractType().isEth();

        final BigDecimal dc = (baseSignalType == SignalType.ADJ_BTC || baseSignalType == SignalType.CORR_BTC || baseSignalType == SignalType.CORR_BTC_MDC)
                ? getDcExtraSet().setScale(2, RoundingMode.HALF_UP)
                : getDcMainSet().setScale(2, RoundingMode.HALF_UP);

        final CorrObj corrObj = new CorrObj(baseSignalType);

        // for logs
        Integer maxBtm = null;// = corrParams.getCorr().getMaxVolCorrBitmex(bitmexService.getCm());
        Integer maxOkex = null;// = corrParams.getCorr().getMaxVolCorrOkex();
        String corrName = baseSignalType.getCounterName();

        if (baseSignalType == SignalType.ADJ_BTC) {

            BigDecimal bPXbtUsd = bitmexService.getPositionXBTUSD().getPositionLong();
            adaptCorrAdjExtraSetByPos(corrObj, bPXbtUsd, dc);
            final CorrParams corrParamsExtra = persistenceService.fetchCorrParams();
            corrParamsExtra.getCorr().setIsEth(false);
            adaptCorrAdjByMaxVolCorrAndDql(corrObj, corrParamsExtra, dc, cm, isEth);

        } else if (baseSignalType == SignalType.ADJ) {

            fillCorrObjForAdj(corrObj, hedgeAmount, bP, oPL, oPS, cm, isEth, dc, true);
            adaptCorrAdjByMaxVolCorrAndDql(corrObj, corrParams, dc, cm, isEth);

        } else if (baseSignalType == SignalType.CORR_BTC || baseSignalType == SignalType.CORR_BTC_MDC) {

            @SuppressWarnings("Duplicates")
            BigDecimal bPXbtUsd = bitmexService.getPositionXBTUSD().getPositionLong();
            adaptCorrAdjExtraSetByPos(corrObj, bPXbtUsd, dc);
            final CorrParams corrParamsExtra = persistenceService.fetchCorrParams();
            corrParamsExtra.getCorr().setIsEth(false);
            adaptCorrAdjByMaxVolCorrAndDql(corrObj, corrParamsExtra, dc, cm, isEth);

        } else { // corr
            maxBtm = corrParams.getCorr().getMaxVolCorrBitmex();
            maxOkex = corrParams.getCorr().getMaxVolCorrOkex();

            if (arbitrageService.getFirstMarketService().getMarketState() == MarketState.SYSTEM_OVERLOADED) {
                adaptCorrByPosOnBtmSo(corrObj, oPL, oPS, dc, isEth);
            } else {
                adaptCorrAdjByPos(corrObj, bP, oPL, oPS, hedgeAmount, dc, cm, isEth);
            }
            adaptCorrAdjByMaxVolCorrAndDql(corrObj, corrParams, dc, cm, isEth);

        } // end corr

        defineCorrectSignalType(corrObj, bP, oPL, oPS);

        final MarketService marketService = corrObj.marketService;
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
                    arbitrageService.getFirstMarketService().getPos().toString(),
                    arbitrageService.getSecondMarketService().getPos().toString(),
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
                placingType = (posAdjustment.getPosAdjustmentPlacingType() == PlacingType.TAKER_FOK && marketService.getName().equals(OkCoinService.NAME))
                        ? PlacingType.TAKER
                        : posAdjustment.getPosAdjustmentPlacingType();
            }

            if (outsideLimits(marketService, orderType, placingType, signalType)) {
                // do nothing
            } else {

                arbitrageService.setSignalType(signalType);

                // Market specific params
                final String counterName = marketService.getCounterNameNext(signalType);
                marketService.setBusy(counterName);

                final Long tradeId = arbitrageService.getLastTradeId();

                final String soMark = getSoMark(corrObj);
                final SignalTypeEx signalTypeEx = new SignalTypeEx(signalType, soMark);

                countOnStartCorr(corrParams, signalType);

                final String message = String.format("#%s %s %s amount=%s c=%s. ", counterName, placingType, orderType, correctAmount, contractType);
                final String setStr = signalType.getCounterName().contains("btc") ? arbitrageService.getExtraSetStr() : arbitrageService.getMainSetStr();
                tradeService.info(tradeId, counterName, String.format("#%s %s", signalTypeEx.getCounterName(), setStr));
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
                    // switch the market
                    final String switchMsg = String.format("%s switch markets. %s INSUFFICIENT_BALANCE.", corrObj.signalType, corrObj.marketService.getName());
                    warningLogger.warn(switchMsg);
                    corrObj.marketService.getTradeLogger().info(switchMsg);
                    slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, switchMsg);

                    final MarketServicePreliq theOtherService = corrObj.marketService.getName().equals(BitmexService.NAME) ? okCoinService : bitmexService;
                    switchMarkets(corrObj, dc, cm, isEth, corrParams, theOtherService);
                    defineCorrectSignalType(corrObj, bP, oPL, oPS);
                    PlacingType pl = placingType == PlacingType.TAKER_FOK ? PlacingType.TAKER : placingType;
                    PlaceOrderArgs theOtherMarketArgs = PlaceOrderArgs.builder()
                            .orderType(corrObj.orderType)
                            .amount(corrObj.correctAmount)
                            .placingType(pl)
                            .signalType(corrObj.signalType)
                            .attempt(1)
                            .tradeId(tradeId)
                            .counterName(counterName)
                            .contractType(corrObj.contractType)
                            .build();
                    corrObj.marketService.getTradeLogger().info(message + theOtherMarketArgs.toString());
                    final TradeResponse theOtherResp = corrObj.marketService.placeOrder(theOtherMarketArgs);
                    if (theOtherResp.errorInsufficientFunds()) {
                        final String msg = String.format("No %s. INSUFFICIENT_BALANCE on both markets.", baseSignalType);
                        warningLogger.warn(msg);
                        corrObj.marketService.getTradeLogger().warn(msg);
                        slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, message);
                    }
                }
                corrObj.marketService.getArbitrageService().setBusyStackChecker();

                slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, message);
                log.info(message);

            }
        }

    }

    private String getSoMark(CorrObj corrObj) {
        if (corrObj.marketService != null && corrObj.signalType != null) {
            if (corrObj.signalType.isAdj()) {
                if (corrObj.marketService.getName().equals(OkCoinService.NAME)
                        && arbitrageService.getFirstMarketService().getMarketState() == MarketState.SYSTEM_OVERLOADED) {
                    return "_SO";
                }
            } else { // isCorr
                if (corrObj.marketService.getName().equals(OkCoinService.NAME)
                        && corrObj.signalType.isIncreasePos()
                        && arbitrageService.getFirstMarketService().getMarketState() == MarketState.SYSTEM_OVERLOADED) {
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
            adaptCorrAdjByPos(corrObj, bP, oPL, oPS, hedgeAmount, dc, cm, isEth);
        }
    }

    private boolean hasNoSpareAttempts(SignalType baseSignalType, CorrParams corrParams) {
        final boolean isAdj = baseSignalType == SignalType.ADJ || baseSignalType == SignalType.ADJ_BTC;
        final boolean isCorr = baseSignalType == SignalType.CORR || baseSignalType == SignalType.CORR_BTC;
        final boolean adjLimit = isAdj && !corrParams.getAdj().hasSpareAttempts();
        final boolean corrLimit = isCorr && !corrParams.getCorr().hasSpareAttempts();
        return adjLimit || corrLimit;
    }

    private void adaptCorrAdjByMaxVolCorrAndDql(final CorrObj corrObj, final CorrParams corrParams, BigDecimal dc, BigDecimal cm, boolean isEth) {
        maxVolCorrAdapt(corrObj, corrParams);
        adaptCorrByDql(corrObj, dc, cm, isEth, corrParams);
    }

    private void maxVolCorrAdapt(CorrObj corrObj, CorrParams corrParams) {
        if (corrObj.marketService.getName().equals(OkCoinService.NAME)) {
            BigDecimal okMax = BigDecimal.valueOf(corrParams.getCorr().getMaxVolCorrOkex());
            if (corrObj.correctAmount.compareTo(okMax) > 0) {
                corrObj.correctAmount = okMax;
            }
        } else {
            BigDecimal bMax = BigDecimal.valueOf(corrParams.getCorr().getMaxVolCorrBitmex());
            if (corrObj.correctAmount.compareTo(bMax) > 0) {
                corrObj.correctAmount = bMax;
            }
        }
    }

    private void adaptCorrByDql(final CorrObj corrObj, BigDecimal dc, BigDecimal cm, boolean isEth, CorrParams corrParams) {
        if (corrObj.signalType.isIncreasePos()) {
            if (corrObj.signalType.isMainSet() && !corrObj.signalType.isAdj()) {
                dqlOpenMinAdjust(corrObj, dc, cm, isEth, corrParams);
            }
            dqlCloseMinAdjust(corrObj);
        }
    }

    private void dqlOpenMinAdjust(CorrObj corrObj, BigDecimal dc, BigDecimal cm, boolean isEth, CorrParams corrParams) {
        final boolean dqlOpenViolated = corrObj.marketService.isDqlOpenViolated();
        if (dqlOpenViolated) {
            // check if other market isOk
            final MarketServicePreliq theOtherService = corrObj.marketService.getName().equals(BitmexService.NAME) ? okCoinService : bitmexService;
            boolean theOtherMarketIsViolated = theOtherService.isDqlOpenViolated();
            if (theOtherMarketIsViolated) {
                corrObj.correctAmount = BigDecimal.ZERO;
                corrObj.errorDescription = "Try INCREASE_POS when DQL_open_min is violated on both markets.";
            } else {
                final String switchMsg = String.format("%s switch markets. %s DQL_open_min is violated.", corrObj.signalType, corrObj.marketService.getName());
                warningLogger.warn(switchMsg);
                slackNotifications.sendNotify(corrObj.signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, switchMsg);
                switchMarkets(corrObj, dc, cm, isEth, corrParams, theOtherService);
            }
        }
    }

    private void switchMarkets(CorrObj corrObj, BigDecimal dc, BigDecimal cm, boolean isEth, CorrParams corrParams, MarketServicePreliq theOtherService) {
        corrObj.marketService = theOtherService;
        maxVolCorrAdapt(corrObj, corrParams);
        corrObj.signalType = corrObj.signalType.switchMarket();
        if (theOtherService.getName().equals(BitmexService.NAME)) {
            defineCorrectAmountBitmex(corrObj, dc, cm, isEth);
        } else {
            defineCorrectAmountOkex(corrObj, dc, isEth);
        }
    }

    private void defineCorrectSignalType(CorrObj corrObj, BigDecimal bP, BigDecimal oPL, BigDecimal oPS) {
        if (corrObj.marketService.getName().equals(BitmexService.NAME)) {
            if (bP.signum() == 0
                    || (bP.signum() > 0 && corrObj.orderType == OrderType.BID)
                    || (bP.signum() < 0 && corrObj.orderType == OrderType.ASK)) { //increase
                corrObj.signalType = corrObj.signalType.toIncrease(true);
            } else {
                corrObj.signalType = corrObj.signalType.toDecrease(true);
            }
        } else { //OKEX
            final BigDecimal oPos = oPL.subtract(oPS);
            if (oPos.signum() == 0
                    || (oPos.signum() > 0 && (corrObj.orderType == OrderType.BID || corrObj.orderType == OrderType.EXIT_ASK))
                    || (oPos.signum() < 0 && (corrObj.orderType == OrderType.ASK || corrObj.orderType == OrderType.EXIT_BID))) { //increase
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

    @ToString
    private class CorrObj {

        CorrObj(SignalType signalType) {
            this.signalType = signalType;
        }

        SignalType signalType;
        OrderType orderType;
        BigDecimal correctAmount;
        MarketServicePreliq marketService;
        ContractType contractType;
        String errorDescription;
    }

    /**
     * Corr/adj by 'trying decreasing pos'.
     */
    @SuppressWarnings("Duplicates")
    private void adaptCorrAdjByPos(final CorrObj corrObj, final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS, final BigDecimal hedgeAmount,
            final BigDecimal dc, final BigDecimal cm, final boolean isEth) {

        final BigDecimal okexUsd = isEth
                ? (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(10))
                : (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(100));

        final BigDecimal bitmexUsd = isEth
                ? bP.multiply(BigDecimal.valueOf(10)).divide(cm, 2, RoundingMode.HALF_UP)
                : bP;

        final BigDecimal okEquiv = okexUsd;
        final BigDecimal bEquiv = bitmexUsd.subtract(hedgeAmount);

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
            if (oPS.signum() > 0 && oPS.subtract(corrObj.correctAmount).signum() < 0) { // orderType==CLOSE_ASK
                corrObj.correctAmount = oPS;
            }

            if ((oPL.subtract(oPS)).signum() >= 0) {
                corrObj.signalType = SignalType.O_CORR_INCREASE_POS;
            } else {
                corrObj.signalType = SignalType.O_CORR;
            }
        } else {
            // okcoin sell
            corrObj.orderType = Order.OrderType.ASK;
            defineCorrectAmountOkex(corrObj, dc, isEth);
            if (oPL.signum() > 0 && oPL.subtract(corrObj.correctAmount).signum() < 0) { // orderType==CLOSE_BID
                corrObj.correctAmount = oPL;
            }

            if ((oPL.subtract(oPS)).signum() <= 0) {
                corrObj.signalType = SignalType.O_CORR_INCREASE_POS;
            } else {
                corrObj.signalType = SignalType.O_CORR;
            }
        }

        corrObj.marketService = arbitrageService.getSecondMarketService();
        corrObj.contractType = corrObj.marketService.getContractType();
    }

    /**
     * Adj by 'how close to a border'.
     */
    @SuppressWarnings("Duplicates")
    private void adaptAdjByPos(final CorrObj corrObj, final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS,
            final BigDecimal dc, final BigDecimal cm, final boolean isEth, final Borders minBorders, boolean withLogs) {

        assert corrObj.signalType == SignalType.B_ADJ;

        final OrderBook btmOb = arbitrageService.getFirstMarketService().getOrderBook();
        final OrderBook okOb = arbitrageService.getSecondMarketService().getOrderBook();
        final BigDecimal btmAvg = Utils.calcQuAvg(btmOb, 3);
        final BigDecimal okAvg = Utils.calcQuAvg(okOb, 3);

        final Settings settings = settingsRepositoryService.getSettings();
        final PlacingType placingType = settings.getPosAdjustment().getPosAdjustmentPlacingType();
        // com_pts = com / 100 * b_best_sam
        final BigDecimal b_com = (settings.getBFee(placingType)).multiply(btmAvg).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
        final BigDecimal o_com = (settings.getOFee(placingType)).multiply(okAvg).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
        final BigDecimal ntUsd = dc.negate();

        final BigDecimal b_border = minBorders.b_border;
        final BigDecimal o_border = minBorders.o_border;
        final BigDecimal b_delta = arbitrageService.getDelta1();
        final BigDecimal o_delta = arbitrageService.getDelta2();

        String adjName = "";
        if (ntUsd.signum() < 0) {
            //if (nt_usd < 0) {
            //if (b_border - (b_delta - b_com) >= o_border - (o_delta - o_com)
            //adj = o_delta_adj;
            //else
            //adj = b_delta_adj;
            //}
            // b_delta_adj означает что подгонку делаем по b_delta, то есть при nt_usd < 0 adj-сделку sell делаем на bitmex, при nt_usd > 0 adj-сделку buy делаем на okex.
            // o_delta_adj означает что подгонку делаем по o_delta, то есть при nt_usd < 0 adj-сделку sell делаем на okex, при nt_usd > 0 adj-сделку buy делаем на bitmex.
            if (b_border.subtract(b_delta.subtract(b_com)).compareTo(o_border.subtract(o_delta.subtract(o_com))) >= 0) {
                adjName = "o_delta_adj";
                // okex sell
                corrObj.marketService = arbitrageService.getSecondMarketService();
                corrObj.orderType = OrderType.ASK;
                defineCorrectAmountOkex(corrObj, dc, isEth);
                if (oPL.signum() > 0 && oPL.subtract(corrObj.correctAmount).signum() < 0) { // orderType==CLOSE_BID
                    corrObj.correctAmount = oPL;
                }
                if ((oPL.subtract(oPS)).signum() <= 0) {
                    corrObj.signalType = SignalType.O_ADJ_INCREASE_POS;
                } else {
                    corrObj.signalType = SignalType.O_ADJ;
                }
            } else {
                adjName = "b_delta_adj";
                // bitmex sell
                corrObj.marketService = arbitrageService.getFirstMarketService();
                corrObj.orderType = OrderType.ASK;
                defineCorrectAmountBitmex(corrObj, dc, cm, isEth);
                if (bP.signum() <= 0) {
                    corrObj.signalType = SignalType.B_ADJ_INCREASE_POS;
                } else {
                    corrObj.signalType = SignalType.B_ADJ;
                }
            }
        } else if (ntUsd.signum() > 0) { //
            //if (nt_usd > 0) {
            //if (b_border - (b_delta + o_com) >= o_border - (o_delta - b_com)
            //adj = o_delta_adj;
            //else
            //adj = b_delta_adj;
            //}
            // b_delta_adj означает что подгонку делаем по b_delta, то есть при nt_usd < 0 adj-сделку sell делаем на bitmex, при nt_usd > 0 adj-сделку buy делаем на okex.
            // o_delta_adj означает что подгонку делаем по o_delta, то есть при nt_usd < 0 adj-сделку sell делаем на okex, при nt_usd > 0 adj-сделку buy делаем на bitmex.
            if (b_border.subtract(b_delta.subtract(o_com)).compareTo(o_border.subtract(o_delta.subtract(b_com))) >= 0) {
                adjName = "o_delta_adj";
                // bitmex buy
                corrObj.marketService = arbitrageService.getFirstMarketService();
                corrObj.orderType = OrderType.BID;
                defineCorrectAmountBitmex(corrObj, dc, cm, isEth);
                if (bP.signum() >= 0) {
                    corrObj.signalType = SignalType.B_ADJ_INCREASE_POS;
                } else {
                    corrObj.signalType = SignalType.B_ADJ;
                }
            } else {
                adjName = "b_delta_adj";
                // okcoin buy
                corrObj.marketService = arbitrageService.getSecondMarketService();
                corrObj.orderType = OrderType.BID;
                defineCorrectAmountOkex(corrObj, dc, isEth);
                if (oPS.signum() > 0 && oPS.subtract(corrObj.correctAmount).signum() < 0) { // orderType==CLOSE_ASK
                    corrObj.correctAmount = oPS;
                }
                if ((oPL.subtract(oPS)).signum() >= 0) {
                    corrObj.signalType = SignalType.O_ADJ_INCREASE_POS;
                } else {
                    corrObj.signalType = SignalType.O_ADJ;
                }
            }
        }

        corrObj.contractType = corrObj.marketService != null ? corrObj.marketService.getContractType() : null;
        if (withLogs) {
            printLogsAdjByPos(corrObj, b_com, o_com, ntUsd, b_border, o_border, b_delta, o_delta, adjName);
        }
    }

    private void printLogsAdjByPos(CorrObj corrObj, BigDecimal b_com, BigDecimal o_com, BigDecimal ntUsd, BigDecimal b_border, BigDecimal o_border,
            BigDecimal b_delta, BigDecimal o_delta, String adjName) {
        //для nt_usd < 0:
        //b_delta_adj, b_border - (b_delta + b_com), или
        //o_delta_adj, o_border - (o_delta + o_com), или
        //для nt_usd > 0:
        //b_delta_adj, b_border - (b_delta + o_com), или
        //o_delta_adj, o_border - (o_delta + b_com),
        // Example:
        // b_delta_adj, 2 - (1.2 + (-0.030) = 0,83.
        BigDecimal borderVal = adjName.equals("b_delta_adj") ? b_border : o_border;
        BigDecimal deltaVal = adjName.equals("b_delta_adj") ? b_delta : o_delta;
        BigDecimal comVal = ntUsd.signum() < 0
                ? (adjName.equals("b_delta_adj") ? b_com : o_com)
                : (adjName.equals("b_delta_adj") ? o_com : b_com);
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
            final Long tradeId = prevTradeId != null ? prevTradeId : arbitrageService.getLastTradeId();
            tradeService.info(tradeId, counterName, msg);
            corrObj.marketService.getTradeLogger().info(msg);
        } else {
            warningLogger.info("adaptAdjByPos failed. " + corrObj);
        }
    }

    private void adaptCorrAdjExtraSetByPos(final CorrObj corrObj, final BigDecimal bPXbtUsd, final BigDecimal dc) {
        corrObj.contractType = BitmexService.bitmexContractTypeXBTUSD;
        corrObj.marketService = arbitrageService.getFirstMarketService();
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

    private void defineCorrectAmountBitmex(CorrObj corrObj, BigDecimal dc, final BigDecimal cm, final boolean isEth) {
        if (isEth) {
//            adj/corr_cont_bitmex = abs(dc) / (10 / CM); // если делаем на Bitmex - usd to cont
            BigDecimal btmCm = BigDecimal.valueOf(10).divide(cm, 4, RoundingMode.HALF_UP);
            corrObj.correctAmount = dc.abs().divide(btmCm, 0, RoundingMode.HALF_UP);
        } else {
            if (corrObj.signalType.isAdj()) {
                corrObj.correctAmount = dc.abs().setScale(0, RoundingMode.HALF_UP);
            } else {
                corrObj.correctAmount = dc.abs().setScale(0, RoundingMode.DOWN);
            }
        }
    }

    private void defineCorrectAmountOkex(CorrObj corrObj, BigDecimal dc, boolean isEth) {
        if (isEth) {
//            adj/corr_cont_okex = abs(dc) / 10; // если делаем на Okex
            corrObj.correctAmount = dc.abs().divide(BigDecimal.valueOf(10), 0, RoundingMode.HALF_UP);
        } else {
            if (corrObj.signalType == SignalType.ADJ) {
                corrObj.correctAmount = dc.abs().divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            } else {
                corrObj.correctAmount = dc.abs().divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
            }
        }
    }

    private boolean outsideLimits(MarketService marketService, OrderType orderType, PlacingType placingType, SignalType signalType) {
        if (marketService.getName().equals(BitmexService.NAME) && bitmexLimitsService.outsideLimits()) {
            warningLogger.error("Attempt of correction when outside limits. " + bitmexLimitsService.getLimitsJson());
            return true;
        }
        if (marketService.getName().equals(OkCoinService.NAME) && okexLimitsService.outsideLimits(orderType, placingType, signalType)) {
            warningLogger.error("Attempt of correction when outside limits. " + okexLimitsService.getLimitsJson());
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

    private boolean isPosEqualByMinAdj(final BigDecimal dc) {
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
        final boolean isEth = bitmexService.getContractType().isEth();
        final BigDecimal okexUsd = getOkexUsd(isEth);
        final BigDecimal bitmexUsd = getBitmexUsd(isEth);
        final BigDecimal bitmexUsdWithHedge = bitmexUsd.subtract(hedgeAmountUsd);

        return okexUsd.add(bitmexUsdWithHedge);
    }

    private BigDecimal getDcExtraSet() {
        if (!bitmexService.getContractType().isEth()) {
            return BigDecimal.ZERO;
        }
        final BigDecimal hedgeAmountUsd = getHedgeAmountExtraSet();
        final BigDecimal bitmexUsd = bitmexService.getHbPosUsd();
        return bitmexUsd.subtract(hedgeAmountUsd);
    }

    private BigDecimal getOkexUsd(boolean isEth) {
        final Pos secondServicePosition = arbitrageService.getSecondMarketService().getPos();
        final BigDecimal oPL = secondServicePosition.getPositionLong();
        final BigDecimal oPS = secondServicePosition.getPositionShort();
        if (oPL == null || oPS == null) {
            throw new NotYetInitializedException("Position is not yet defined");
        }
        return isEth
                ? (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(10))
                : (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal getBitmexUsd(boolean isEth) {
        BigDecimal cm = bitmexService.getCm();

        final BigDecimal bP = arbitrageService.getFirstMarketService().getPos().getPositionLong();
        if (bP == null) {
            throw new NotYetInitializedException("Position is not yet defined");
        }
        return isEth
                ? bP.multiply(BigDecimal.valueOf(10)).divide(cm, 2, RoundingMode.HALF_UP)
                : bP;
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
