package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DelayTimer;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.dto.ThrottledWarn;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.bitmex.BitmexLimitsService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.bitmex.BitmexUtils;
import com.bitplay.market.model.FullBalance;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.market.okcoin.OkexLimitsService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.PosAdjustment;
import com.bitplay.persistance.repository.FplayTradeRepository;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.Completable;
import io.reactivex.disposables.Disposable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Position;
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
    private final ThrottledWarn dqlWarn = new ThrottledWarn();
    private final ThrottledWarn corrAdjWarn = new ThrottledWarn();

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
    private FplayTradeRepository fplayTradeRepository;

    private ScheduledExecutorService mdcExecutor;
    private ScheduledExecutorService calcPosDiffExecutor;

    @PreDestroy
    public void preDestory() {
        mdcExecutor.shutdown();
        calcPosDiffExecutor.shutdown();
    }

    @PostConstruct
    private void init() {
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("mdc-thread-%d").build();
        mdcExecutor = Executors.newSingleThreadScheduledExecutor(namedThreadFactory);
        calcPosDiffExecutor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setNameFormat("pos-diff-thread-%d").build()
        );
        calcPosDiffExecutor.scheduleWithFixedDelay(this::calcPosDiffJob,
                60, 1, TimeUnit.SECONDS);

        mdcExecutor.scheduleWithFixedDelay(this::checkMDCJob,
                60, 1, TimeUnit.SECONDS);
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

    private void calcPosDiffJob() {
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
        if (signalType.isAdjBtc()) {
            corrParams.getAdj().tryIncFailedExtra();
        } else if (signalType.isAdj()) {
            corrParams.getAdj().tryIncFailed();
        } else if (signalType.isCorrBtc()) {
            corrParams.getCorr().tryIncFailedExtra();
        } else { // signalType.isCorr()
            corrParams.getCorr().tryIncFailed();
        }
        persistenceService.saveCorrParams(corrParams);
    }

    private void tryFinishPrevCorr(final CorrParams corrParams) {
        final BigDecimal dcMainSet = getDcMainSet();
        final BigDecimal dcExtraSet = getDcExtraSet();

        if (isPosEqualByMaxAdj(dcMainSet)) {
            if (corrParams.getCorr().tryIncSuccessful()) {
                persistenceService.saveCorrParams(corrParams);
            }
        }
        if (isPosEqualByMaxAdj(dcExtraSet)) {
            if (corrParams.getCorr().tryIncSuccessfulExtra()) {
                persistenceService.saveCorrParams(corrParams);
            }
        }

        if (isPosEqualByMinAdj(dcMainSet)) {
            if (corrParams.getAdj().tryIncSuccessful()) {
                persistenceService.saveCorrParams(corrParams);
            }
        }
        if (isPosEqualByMinAdj(dcExtraSet)) {
            if (corrParams.getAdj().tryIncSuccessfulExtra()) {
                persistenceService.saveCorrParams(corrParams);
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
                        arbitrageService.releaseArbInProgress("", "timer-state-reset");
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
        final Position pos2 = bitmexService.getPositionXBTUSD();
        String msg = infoMsg + "bitmexXBTUSD=" + BitmexUtils.positionToString(pos2);
        warningLogger.info(msg);
        log.info(msg);
    }

    private void stopTimerToImmediateCorrection() {
        if (theTimerToImmediateCorr != null) {
            theTimerToImmediateCorr.dispose();
        }
    }

    private void checkMDCJob() {
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
            long secToReady = dt.secToReadyPresice(delaySec);
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
                    arbitrageService.releaseArbInProgress("", "MDC extraSet");
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
            long secToReady = dt.secToReadyPresice(delaySec);
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
                    arbitrageService.releaseArbInProgress("", "MDC mainSet");
                    dt.stop();
                    slackNotifications.sendNotify(NotifyType.STOP_ALL_ACTIONS_BY_MDC_TIMER, "STOP_ALL_ACTIONS_BY_MDC_TIMER: " + msg);
                }
            }

        } else {
            dt.stop();
        }
    }

    private boolean marketsStopped() {
        final FullBalance firstFullBalance = arbitrageService.getFirstMarketService().calcFullBalance();
        final FullBalance secondFullBalance = arbitrageService.getSecondMarketService().calcFullBalance();

        return arbitrageService.getFirstMarketService().getMarketState().isStopped()
                || arbitrageService.getSecondMarketService().getMarketState().isStopped()
                || arbitrageService.isArbStatePreliq()
                || firstFullBalance.getAccountInfoContracts() == null
                || firstFullBalance.getAccountInfoContracts().geteBest() == null
                || firstFullBalance.getAccountInfoContracts().geteBest().signum() <= 0
                || secondFullBalance.getAccountInfoContracts() == null
                || secondFullBalance.getAccountInfoContracts().geteBest() == null
                || secondFullBalance.getAccountInfoContracts().geteBest().signum() <= 0;
    }

    private boolean marketsReady() {
        final FullBalance firstFullBalance = arbitrageService.getFirstMarketService().calcFullBalance();
        final FullBalance secondFullBalance = arbitrageService.getSecondMarketService().calcFullBalance();

        return arbitrageService.getFirstMarketService().isReadyForArbitrage()
                && arbitrageService.getSecondMarketService().isReadyForArbitrage()
                && !arbitrageService.isArbStatePreliq()
                && firstFullBalance.getAccountInfoContracts() != null
                && firstFullBalance.getAccountInfoContracts().geteBest() != null
                && firstFullBalance.getAccountInfoContracts().geteBest().signum() > 0
                && secondFullBalance.getAccountInfoContracts() != null
                && secondFullBalance.getAccountInfoContracts().geteBest() != null
                && secondFullBalance.getAccountInfoContracts().geteBest().signum() > 0;
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
        if (!hasGeneralCorrStarted || !arbitrageService.getFirstMarketService().isStarted()) {
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
        if (marketsReady() && isAdjViolated(dcMainSet) && corrParams.getAdj().hasSpareAttempts()) {

            final PosAdjustment pa = settingsRepositoryService.getSettings().getPosAdjustment();
            final long secToReady = dtAdj.secToReadyPresice(pa.getPosAdjustmentDelaySec());

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

                if (marketsReady() && isAdjViolated(getDcMainSet())) {

                    doCorrection(getHedgeAmountMainSet(), SignalType.ADJ);
                    dtAdj.stop();

                    return true; // started
                } else {
                    dtAdj.stop();
                }
            }
        } else {
            dtAdj.stop();
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

            final PosAdjustment pa = settingsRepositoryService.getSettings().getPosAdjustment();
            final long secToReady = dtExtraAdj.secToReadyPresice(pa.getPosAdjustmentDelaySec());

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

                    doCorrection(getHedgeAmountExtraSet(), SignalType.ADJ_BTC);
                    dtExtraAdj.stop();

                    return true; // started
                } else {
                    dtExtraAdj.stop();
                }
            }
        } else {
            dtExtraAdj.stop();
        }
        return false;
    }

    private boolean corrStartedOrFailed(CorrParams corrParams) throws Exception {
        final BigDecimal dcMainSet = getDcMainSet();
        if (marketsReady() && !isPosEqualByMaxAdj(dcMainSet) && corrParams.getCorr().hasSpareAttempts()) {

            final PosAdjustment pa = settingsRepositoryService.getSettings().getPosAdjustment();
            final long secToReady = dtCorr.secToReadyPresice(pa.getCorrDelaySec());

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
                if (marketsReady() && !isPosEqualByMaxAdj(getDcMainSet())) {
                    doCorrection(getHedgeAmountMainSet(), SignalType.CORR);
                    dtCorr.stop();

                    return true; // started
                } else {
                    dtCorr.stop();
                }
            }

        } else {
            dtCorr.stop();
        }
        return false;
    }

    private boolean corrExtraStartedOrFailed(CorrParams corrParams) throws Exception {
        final BigDecimal dcExtraSet = getDcExtraSet();
        if (marketsReady() && !isPosEqualByMaxAdj(dcExtraSet) && corrParams.getCorr().hasSpareAttempts()) {

            final PosAdjustment pa = settingsRepositoryService.getSettings().getPosAdjustment();
            final long secToReady = dtExtraCorr.secToReadyPresice(pa.getCorrDelaySec());

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
        }
        return false;
    }

    private BigDecimal getHedgeAmountMainSet() {
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

    private synchronized void doCorrection(final BigDecimal hedgeAmount, SignalType baseSignalType) {

        final CorrParams corrParams = persistenceService.fetchCorrParams();
        countFailedOnStartCorr(corrParams, baseSignalType);
        if (hasNoSpareAttempts(baseSignalType, corrParams)) {
            return;
        }

        BigDecimal bP = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
        BigDecimal oPL = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
        BigDecimal oPS = arbitrageService.getSecondMarketService().getPosition().getPositionShort();

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
            adaptExtraSetAdjByPos(corrObj, bPXbtUsd, dc);
            final CorrParams corrParamsExtra = persistenceService.fetchCorrParams();
            corrParamsExtra.getCorr().setIsEth(false);
            adaptCorrByMaxVolCorrAndDql(corrObj, corrParamsExtra);

        } else if (baseSignalType == SignalType.ADJ) {

            adaptCorrByPos(corrObj, bP, oPL, oPS, hedgeAmount, dc, cm, isEth);
            adaptCorrByMaxVolCorrAndDql(corrObj, corrParams);

        } else if (baseSignalType == SignalType.CORR_BTC || baseSignalType == SignalType.CORR_BTC_MDC) {

            @SuppressWarnings("Duplicates")
            BigDecimal bPXbtUsd = bitmexService.getPositionXBTUSD().getPositionLong();
            adaptExtraSetAdjByPos(corrObj, bPXbtUsd, dc);
            final CorrParams corrParamsExtra = persistenceService.fetchCorrParams();
            corrParamsExtra.getCorr().setIsEth(false);
            adaptCorrByMaxVolCorrAndDql(corrObj, corrParamsExtra);

        } else { // corr
            maxBtm = corrParams.getCorr().getMaxVolCorrBitmex();
            maxOkex = corrParams.getCorr().getMaxVolCorrOkex();

            adaptCorrByPos(corrObj, bP, oPL, oPS, hedgeAmount, dc, cm, isEth);
            adaptCorrByMaxVolCorrAndDql(corrObj, corrParams);

        } // end corr

        final MarketService marketService = corrObj.marketService;
        final Order.OrderType orderType = corrObj.orderType;
        final BigDecimal correctAmount = corrObj.correctAmount;
        final SignalType signalType = corrObj.signalType;
        final ContractType contractType = corrObj.contractType;

        // 3. check isAffordable
        boolean isAffordable = marketService.isAffordable(orderType, correctAmount);
        if (correctAmount.signum() > 0 && isAffordable) {

            corrAdjWarn.reset();

//                bestQuotes.setArbitrageEvent(BestQuotes.ArbitrageEvent.TRADE_STARTED);
            arbitrageService.setSignalType(signalType);
            marketService.setBusy();

            if (outsideLimits(marketService)) {
                // do nothing
            } else {
                final PlacingType placingType;
                if (!signalType.isAdj()) {
                    placingType = PlacingType.TAKER; // correction is only taker
                } else {
                    final PosAdjustment posAdjustment = settingsRepositoryService.getSettings().getPosAdjustment();
                    placingType = posAdjustment.getPosAdjustmentPlacingType();
                }

                // Market specific params
                final String counterName = marketService.getCounterName(signalType);
                final Long tradeId = arbitrageService.getLastTradeId();

                String message = String.format("%s %s %s %s amount=%s c=%s", signalType, counterName, placingType, orderType, correctAmount, contractType);
                slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, message);

                countOnStartCorr(corrParams, signalType);

                marketService.placeOrder(new PlaceOrderArgs(orderType, correctAmount, null,
                        placingType, signalType, 1, tradeId, counterName, null, contractType));
            }
        } else {
            if (corrAdjWarn.isReadyToSend()) {
                warningLogger.warn("No {}: amount={}, isAffordable={}, maxBtm={}, maxOk={}, dc={}, btmPos={}, okPos={}, hedge={}, signal={}",
                        corrName,
                        correctAmount, isAffordable,
                        maxBtm, maxOkex, dc,
                        arbitrageService.getFirstMarketService().getPosition().toString(),
                        arbitrageService.getSecondMarketService().getPosition().toString(),
                        hedgeAmount.toPlainString(),
                        signalType
                );
            }
        }

    }

    private boolean hasNoSpareAttempts(SignalType baseSignalType, CorrParams corrParams) {
        final boolean isAdj = baseSignalType == SignalType.ADJ || baseSignalType == SignalType.ADJ_BTC;
        final boolean isCorr = baseSignalType == SignalType.CORR || baseSignalType == SignalType.CORR_BTC;
        final boolean adjLimit = isAdj && !corrParams.getAdj().hasSpareAttempts();
        final boolean corrLimit = isCorr && !corrParams.getCorr().hasSpareAttempts();
        return adjLimit || corrLimit;
    }

    private void adaptCorrByMaxVolCorrAndDql(final CorrObj corrObj, final CorrParams corrParams) {
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
        adaptCorrByDql(corrObj);
    }

    private void adaptCorrByDql(final CorrObj corrObj) {
        if (corrObj.signalType.isIncreasePos()) {
            final boolean dqlViolated = corrObj.marketService.isDqlViolated();
            if (dqlViolated) {
                corrObj.correctAmount = BigDecimal.ZERO;
                if (dqlWarn.isReadyToSend()) {
                    warningLogger.warn("No %s. DQL is violated", corrObj.signalType);
                }
            } else {
                dqlWarn.reset();
            }
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

    private class CorrObj {

        CorrObj(SignalType signalType) {
            this.signalType = signalType;
        }

        SignalType signalType;
        OrderType orderType;
        BigDecimal correctAmount;
        MarketServicePreliq marketService;
        ContractType contractType;
    }

    private void adaptCorrByPos(final CorrObj corrObj, final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS, final BigDecimal hedgeAmount,
            final BigDecimal dc, final BigDecimal cm, final boolean isEth) {

        corrObj.contractType = null;
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
    }

    private void adaptExtraSetAdjByPos(final CorrObj corrObj, final BigDecimal bPXbtUsd, final BigDecimal dc) {
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
            if (corrObj.signalType == SignalType.ADJ) {
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

        calcPosDiffExecutor.execute(() -> {
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

    public boolean isPosEqualByMinAdj(final BigDecimal dc) {
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

    private BigDecimal getDcMainSet() {
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
        final BigDecimal oPL = arbitrageService.getSecondMarketService().getPosition().getPositionLong();
        final BigDecimal oPS = arbitrageService.getSecondMarketService().getPosition().getPositionShort();
        if (oPL == null || oPS == null) {
            throw new NotYetInitializedException("Position is not yet defined");
        }
        return isEth
                ? (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(10))
                : (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal getBitmexUsd(boolean isEth) {
        BigDecimal cm = bitmexService.getCm();

        final BigDecimal bP = arbitrageService.getFirstMarketService().getPosition().getPositionLong();
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
        if (!isPosEqualByMaxAdj(getDcMainSet()) || !isPosEqualByMaxAdj(getDcExtraSet())) {
            if (theTimerToImmediateCorr == null || theTimerToImmediateCorr.isDisposed()) {
                startTimerToImmediateCorrection();
            }
        } else {
            stopTimerToImmediateCorrection();
        }
    }
}
