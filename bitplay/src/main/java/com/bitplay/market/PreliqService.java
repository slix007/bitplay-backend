package com.bitplay.market;

import static com.bitplay.xchange.dto.Order.OrderType;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.ArbType;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.DelayTimer;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.dto.ThrottledWarn;
import com.bitplay.external.NotifyType;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.DqlState;
import com.bitplay.market.model.LiqInfo;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.model.Pos;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.correction.Preliq;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.fluent.FplayOrder;
import com.bitplay.persistance.domain.settings.Dql;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.utils.Utils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
@Getter
@Slf4j
public class PreliqService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");
    private ThrottledWarn throttledLog = new ThrottledWarn(log, 30);

    private final MarketServicePreliq marketService;

    private final DelayTimer dtPreliq = new DelayTimer();
    private final DelayTimer dtKillpos = new DelayTimer();

    public void checkForPreliq(boolean settlementMode) throws Exception {
        if (settlementMode) {
            resetPreliqState();
            dtPreliq.stop();
            dtKillpos.stop();
            return;
        }
        if (marketService.getPersistenceService().getSettingsRepositoryService().getSettings().getManageType().isManual()) {
            dtPreliq.stop();
            dtKillpos.stop();
            return;
        }

        Instant start = Instant.now();
        try {

            final PersistenceService persistenceService = marketService.getPersistenceService();
            final ArbitrageService arbitrageService = marketService.getArbitrageService();

            if (arbitrageService.isArbStateStopped()) {
                resetPreliqState();
                dtPreliq.stop();
                dtKillpos.stop();
                return;
            }

            final LiqInfo liqInfo = marketService.getLiqInfo();
            final BigDecimal dqlCloseMin = getDqlCloseMin(marketService);
            final BigDecimal dqlOpenMin = getDqlOpenMin();
            final BigDecimal dqlKillPos = getDqlKillPos(marketService);
            final Pos pos = marketService.getPos();
            final BigDecimal posVal = pos.getPositionLong();
            final CorrParams corrParams = persistenceService.fetchCorrParams();
            maxCountNotify(corrParams.getPreliq());
            maxCountNotifyKillpos(corrParams.getKillpos());
            final BigDecimal dqlLevel = marketService.getPersistenceService().getSettingsRepositoryService().getSettings().getDql().getDqlLevel();
            final BigDecimal dqlCurr = liqInfo.getDqlCurr();

            final String dSym = arbitrageService.getBothOkexDsym();
            final DqlState marketDqlState = arbitrageService.getDqlStateService().updateDqlState(dSym, marketService.getArbType(),
                    dqlKillPos, dqlOpenMin, dqlCloseMin, dqlCurr, dqlLevel);

            if (dqlCurr != null && dqlCurr.compareTo(dqlLevel) < 0) {
                throttledLog.info(String.format("dtKillpos.stop() because dqlCurr(%s) < dqlLevel(%s)", dqlCurr, dqlLevel));
                dtPreliq.stop();
                dtKillpos.stop();
                return;
            }

            if (marketDqlState != DqlState.KILLPOS) {
                if (corrParams.getKillpos().tryIncSuccessful(getName())) {
                    persistenceService.saveCorrParams(corrParams);
                }
                dtKillpos.stop();
            }

            boolean normalDql = !marketDqlState.isActiveClose();
            final boolean noSpareAttempts = !corrParams.getPreliq().hasSpareAttempts() && !corrParams.getKillpos().hasSpareAttemptsCurrentOnly();
            if (normalDql
                    || posZeroViolation(pos)
                    || marketService.getLimitsService().outsideLimitsForPreliq(posVal)
                    || noSpareAttempts
            ) {
                if (corrParams.getPreliq().tryIncSuccessful(getName())) {
                    persistenceService.saveCorrParams(corrParams);
                    marketService.stopAllActions("preliq:stopAllActions");
                }
                if (corrParams.getKillpos().tryIncSuccessful(getName())) {
                    persistenceService.saveCorrParams(corrParams);
                }
                resetPreliqState();
                dtPreliq.stop();
                dtKillpos.stop();
            } else {

                if (marketDqlState == DqlState.KILLPOS) {
                    dtKillpos.activate();
                }

                boolean gotActivated = dtPreliq.activate();
                if (gotActivated) {
                    marketService.getArbitrageService().setBusyStackChecker();
                }

                setDqlResetState(marketService, marketDqlState);
                if (arbitrageService.areBothOkex()) {
                    setDqlResetState(getTheOtherMarket(), marketDqlState);
                }

                if (!persistenceService.getSettingsRepositoryService().getSettings().getManageType().isAuto()) {
                    return; // keep timer on, but no actions
                }

                // ex: 2:o_preliq* - the counter of the possible preliq
                final String nameSymbol = marketService.getArbType().s();

                // killpos timer
                final Integer delaySecKillpos = persistenceService.getSettingsRepositoryService().getSettings().getPosAdjustment().getKillposDelaySec();
                long secToReadyKillpos = dtKillpos.secToReadyPrecise(delaySecKillpos);
                if (marketDqlState == DqlState.KILLPOS) {
                    if (secToReadyKillpos > 0) {
                        final String counterForLogs = marketService.getCounterNameNext(getKillposSignalType()) + "*";
                        String msg = String.format("#%s %s_KILLPOS signal mainSet. Waiting delay(sec)=%s", counterForLogs, nameSymbol, secToReadyKillpos);
                        log.info(msg);
                        warningLogger.info(msg);
                        marketService.getTradeLogger().info(msg);
                    }
                }

                // preliq timer
                final Integer delaySec = persistenceService.getSettingsRepositoryService().getSettings().getPosAdjustment().getPreliqDelaySec();
                long secToReady = dtPreliq.secToReadyPrecise(delaySec);
                if (secToReady > 0) {
                    final PreliqParams preliqParams = getPreliqParams(pos, posVal);
                    final String counterForLogs = marketService.getCounterNameNext(preliqParams.getSignalType()) + "*";
                    String msg = String.format("#%s %s_PRE_LIQ signal mainSet. Waiting delay(sec)=%s", counterForLogs, nameSymbol, secToReady);
                    log.info(msg);
                    warningLogger.info(msg);
                    marketService.getTradeLogger().info(msg);
                }

                // do killpos
                if (marketDqlState == DqlState.KILLPOS && secToReadyKillpos <= 0 && corrParams.getKillpos().hasSpareAttemptsCurrentOnly()) {
                    if (doKillPosWithAttempts(persistenceService, liqInfo, pos, corrParams, nameSymbol)) {
                        return;
                    }
                }

                // do preliq
                if (marketDqlState == DqlState.PRELIQ && secToReady <= 0 && corrParams.getPreliq().hasSpareAttempts()) {
                    final PreliqParams preliqParams = getPreliqParams(pos, posVal);
                    boolean preliqBlockEnough = preliqParams != null && preliqParams.getPreliqBlocks() != null
                            && ((getName().equals(BitmexService.NAME) && preliqParams.getPreliqBlocks().getB_block().signum() > 0) ||
                            (getName().equals(OkCoinService.NAME) && preliqParams.getPreliqBlocks().getO_block().signum() > 0));

                    if (!preliqBlockEnough && arbitrageService.areBothOkex()) {
                        final Pos posLeft = getTheOtherMarket().getPos();
                        final PreliqParams preliqParamsLeft = getPreliqParams(posLeft, posLeft.getPositionLong());
                        //noinspection UnnecessaryLocalVariable
                        final boolean preliqBlockEnoughLeft = preliqParamsLeft != null && preliqParamsLeft.getPreliqBlocks() != null
                                && (preliqParamsLeft.getPreliqBlocks().getO_block().signum() > 0);
                        preliqBlockEnough = preliqBlockEnoughLeft;
                    }
                    if (!preliqBlockEnough) {
                        resetPreliqState();
                        dtPreliq.stop();
                        return;
                    }

                    final String counterForLogs = marketService.getCounterNameNext(preliqParams.getSignalType());

                    if (corrParams.getPreliq().tryIncFailed(getName())) { // previous preliq counter
                        persistenceService.saveCorrParams(corrParams);
                        if (arbitrageService.areBothOkex()) {
                            marketService.stopAllActions("preliq:stopAllActions");
                        }
                        if (!corrParams.getPreliq().hasSpareAttempts()) {
                            if (!arbitrageService.areBothOkex()) {
                                marketService.stopAllActions("preliq:stopAllActions");
                            }
                        }
                    }
                    if (corrParams.getPreliq().hasSpareAttempts()) {
                        printPreliqStarting(counterForLogs, nameSymbol, pos, liqInfo, "PRELIQ");
                        corrParams.getPreliq().incTotalCount(getName()); // counterName relates on it
                        persistenceService.saveCorrParams(corrParams);

                        if (arbitrageService.areBothOkex()) {
                            try {
                                final Future<Boolean> theOtherMarketPreliq = ((OkCoinService) marketService.getTheOtherMarket()).preliqLeftAsync();
                                doPreliqOrder(preliqParams);
                                theOtherMarketPreliq.get(30, TimeUnit.SECONDS);
                            } catch (Exception e) {
                                final String msg = String.format("%s %s okex-okex preliq error %s", counterForLogs, getName(), e.toString());
                                log.error(msg, e);
                                warningLogger.error(msg);
                                marketService.getTradeLogger().info(msg);
                            }
                        } else {
                            getTheOtherMarket().stopAllActionsSingleService("preliq:stopAllActions");
                            doPreliqOrder(preliqParams);
                        }

                        log.info("dtPreliq stop after successful preliq");
                        dtPreliq.stop(); //after successful start
                    }

                }
            }
        } finally {
            Instant end = Instant.now();
            Utils.logIfLong(start, end, log, "checkForDecreasePosition");
        }
    }

    public Boolean doPreliqLeft(Pos pos) {
        final LiqInfo liqInfo = marketService.getLiqInfo();
        final String nameSymbol = marketService.getArbType().s();

        final BigDecimal posVal = pos.getPositionLong();

        final PreliqParams preliqParams = getPreliqParams(pos, posVal);
        final boolean preliqBlockEnough = preliqParams != null && preliqParams.getPreliqBlocks() != null
                && (preliqParams.getPreliqBlocks().getO_block().signum() > 0);
        if (!preliqBlockEnough) {
            return false;
        }

        final String counterForLogs = marketService.getCounterName(preliqParams.getSignalType(), false);

        printPreliqStarting(counterForLogs, nameSymbol, pos, liqInfo, "PRELIQ");

        doPreliqOrder(preliqParams);

        return true;
    }

    private boolean doKillPosWithAttempts(PersistenceService persistenceService, LiqInfo liqInfo1, Pos pos1, CorrParams corrParams, String nameSymbol) {
        boolean shouldStopAfter = true;

        int attemptWarnCnt = 0;
        while (true) {
            final Pos pos = marketService.getPos();
            LiqInfo liqInfo = marketService.getLiqInfo();
            if (++attemptWarnCnt % 100 == 0) {
                log.info(attemptWarnCnt + " killposAttempt " + corrParams.getKillpos().toStringKillpos());
                printLogs(marketService.getCounterNameNext(getKillposSignalType()), nameSymbol, pos, liqInfo, "KILLPOS", "WARN_ATTEMPT_" + attemptWarnCnt);
            }

            log.info(attemptWarnCnt + " killposAttempt " + corrParams.getKillpos().toStringKillpos());

            final BigDecimal posVal = marketService.getPosVal();
            if (posVal.signum() == 0) {
                if (corrParams.getKillpos().tryIncSuccessful(getName())) {
                    persistenceService.saveCorrParams(corrParams);
                }
                log.info("dtKillpos stop by pos==0 after successful killpos. " + corrParams.getKillpos().toStringKillpos());
                printLogs(marketService.getCounterNameNext(getKillposSignalType()), nameSymbol, pos, liqInfo, "KILLPOS", "stopByPos==0");
                break;
            } else {
                if (corrParams.getKillpos().tryIncFailed(getName())) { // previous killpos counter
                    persistenceService.saveCorrParams(corrParams);
                }
                if (!corrParams.getKillpos().hasSpareAttemptsCurrentOnly()) {
                    printLogs(marketService.getCounterNameNext(getKillposSignalType()), nameSymbol, pos, liqInfo, "KILLPOS",
                            "exitByNoSpareAttempts:" + corrParams.getKillpos().toStringKillpos());
                    shouldStopAfter = false;
                    break; // no spare attempts
                }

                // Start Attempt here
                final String counterForLogs = marketService.getCounterNameNext(getKillposSignalType());
                corrParams.getKillpos().incTotalCount(getName()); // counterName relates on it
                persistenceService.saveCorrParams(corrParams);

                printPreliqStarting(counterForLogs, nameSymbol, pos, liqInfo, "KILLPOS");

                final boolean isSuccess = marketService.getKillPosService().doKillPos(counterForLogs);

                if (isSuccess) {
                    if (corrParams.getKillpos().tryIncSuccessful(getName())) {
                        persistenceService.saveCorrParams(corrParams);
                    }
                    log.info("dtKillpos stop by pos==0 after successful killpos. " + corrParams.getKillpos().toStringKillpos());
                    break; // success
                }

                //else continue;
                try {
                    //noinspection BusyWait
                    Thread.sleep(200);
                    continue;
                } catch (InterruptedException e) {
                    log.error("interrupted killpos attempt sleep");
                }
            }

            log.info("dtKillpos should never reach " + corrParams.getKillpos().toStringKillpos());
            break;
        }

        if (shouldStopAfter) {
            dtKillpos.stop();
            dtPreliq.stop();
            return true;
        }
        return false;
    }

    private BigDecimal getDqlKillPos(MarketService marketService) {
        final PersistenceService persistenceService = marketService.getPersistenceService();
        final Dql dql = persistenceService.getSettingsRepositoryService().getSettings().getDql();
        return marketService.getArbType() == ArbType.LEFT ? dql.getLeftDqlKillPos() : dql.getRightDqlKillPos();
    }

    private String getName() {
        return marketService.getName();
    }

    public boolean noPreliq() {
        return !dtPreliq.isActive();
    }

    public void setDqlResetState(MarketService marketService, DqlState marketDqlState) {
        final MarketState toSet = marketDqlState == DqlState.PRELIQ ? MarketState.PRELIQ : MarketState.KILLPOS;
        final MarketState prevMarketState = marketService.getMarketState();
        if (prevMarketState != toSet) {
            log.info(String.format("%s set to %s; prev market state is %s", marketService.getName(), toSet, prevMarketState));
            marketService.setMarketState(toSet);
        }
        if (!prevMarketState.isActiveClose()) {
            marketService.getArbitrageService().setBusyStackChecker();
        }
    }

    public void resetPreliqState() {
        resetPreliqStateForMarket(marketService);
        if (marketService.getArbitrageService().areBothOkex()) {
            resetPreliqStateForMarket(getTheOtherMarket());
        }
    }

    public void resetPreliqStateForMarket(MarketService marketService) {
        if (marketService.getMarketState() == MarketState.PRELIQ || marketService.getMarketState() == MarketState.KILLPOS) {
            final FplayOrder stub = new FplayOrder(marketService.getMarketId(), null, "cancelOnPreliq");
            marketService.cancelAllOrders(stub, "After PRELIQ: CancelAllOpenOrders", false, true);

            MarketState toSet = MarketState.READY; // hasOpenOrders() ? MarketState.ARBITRAGE : MarketState.READY;
            log.warn(marketService.getName() + "_PRELIQ: resetPreliqState to " + toSet);
            marketService.setMarketState(toSet);
            marketService.updateDqlState();
        }
    }

    private void maxCountNotify(Preliq preliq) {
        if (preliq.totalCountViolated()) {
            marketService.getSlackNotifications().sendNotify(NotifyType.PRELIQ_MAX_TOTAL,
                    String.format("preliq max total %s reached ", preliq.getMaxTotalCount()));
        }
        if (preliq.maxErrorCountViolated()) {
            marketService.getSlackNotifications().sendNotify(NotifyType.PRELIQ_MAX_ATTEMPT,
                    String.format("preliq max attempts %s reached", preliq.getMaxErrorCount()));
        }
    }

    private void maxCountNotifyKillpos(Preliq killpos) {
        if (killpos.maxErrorCountViolated()) {
            marketService.getSlackNotifications().sendNotify(NotifyType.PRELIQ_MAX_ATTEMPT,
                    String.format("killpos max attempts %s reached", killpos.getMaxErrorCount()));
        }
    }


    private boolean posZeroViolation(Pos pos) {
        boolean isPosZero = pos.getPositionLong().signum() == 0;
        if (isPosZero && marketService.getArbitrageService().areBothOkex()) {
            isPosZero = getTheOtherMarket().getPosVal().signum() == 0;
        }
        return isPosZero;
    }

    private void printLogs(String counterForLogs, String nameSymbol, Pos position, LiqInfo liqInfo, String opName, String startStopStatus) {
        try {
            final String prefix = String.format("#%s %s_%s %s: ", counterForLogs, nameSymbol, opName, startStopStatus);
            final MarketServicePreliq thatMarket = getTheOtherMarket();

            final String thisMarketStr = prefix + getPreliqStartingStr(marketService, marketService.getNameWithType(), position, liqInfo);
            final String thatMarketStr = prefix + getPreliqStartingStr(thatMarket, thatMarket.getNameWithType(), thatMarket.getPos(), thatMarket.getLiqInfo());

            log.info(thisMarketStr);
            log.info(thatMarketStr);
            warningLogger.info(thisMarketStr);
            warningLogger.info(thatMarketStr);
            marketService.getTradeLogger().info(thisMarketStr);
            marketService.getTradeLogger().info(thatMarketStr);
            thatMarket.getTradeLogger().info(thisMarketStr);
            thatMarket.getTradeLogger().info(thatMarketStr);
            marketService.getArbitrageService().printToCurrentDeltaLog(thisMarketStr);
            marketService.getArbitrageService().printToCurrentDeltaLog(thatMarketStr);
        } catch (Exception e) {
            log.error("Error in print" + opName + startStopStatus, e);
            final String err = "Error in print" + opName + startStopStatus + " " + e.toString();
            warningLogger.error(err);
            marketService.getTradeLogger().error(err);
            marketService.getArbitrageService().printToCurrentDeltaLog(err);
        }
    }

    private void printPreliqStarting(String counterForLogs, String nameSymbol, Pos position, LiqInfo liqInfo, String opName) {
        printLogs(counterForLogs, nameSymbol, position, liqInfo, opName, "starting");
    }

    private MarketServicePreliq getTheOtherMarket() {
        return marketService.getArbType() == ArbType.LEFT
                ? marketService.getArbitrageService().getRightMarketService()
                : marketService.getArbitrageService().getLeftMarketService();
    }

    private String getPreliqStartingStr(MarketService mrkSrv, String name, Pos position, LiqInfo liqInfo) {
        final BigDecimal dqlCloseMin = getDqlCloseMin(mrkSrv);
        final BigDecimal dqlKillPos = getDqlKillPos(mrkSrv);
        final String dqlCurrStr = liqInfo != null && liqInfo.getDqlCurr() != null ? liqInfo.getDqlCurr().toPlainString() : "null";
        final String dqlCloseMinStr = dqlCloseMin != null ? dqlCloseMin.toPlainString() : "null";
        final String dqlKillPosStr = dqlKillPos != null ? dqlKillPos.toPlainString() : "null";
        return String.format("%s p(%s-%s)/dql%s/dqlClose%s/dqlKillpos%s",
                name,
                position.getPositionLong().toPlainString(),
                position.getPositionShort().toPlainString(),
                dqlCurrStr, dqlCloseMinStr, dqlKillPosStr);
    }

    private SignalType getKillposSignalType() {
        if (getName().equals(BitmexService.NAME)) {
            return SignalType.B_KILLPOS;
        }
        return SignalType.O_KILLPOS;
    }

    private PreliqParams getPreliqParams(Pos posObj, BigDecimal pos) {
        if (pos.signum() == 0) {
            return new PreliqParams(
                    getName().equals(BitmexService.NAME) ? SignalType.B_PRE_LIQ : SignalType.O_PRE_LIQ,
                    null, null);
        }
        PreliqParams preliqParams = null;
        SignalType signalType = null;
        DeltaName deltaName = null;
        if (getName().equals(BitmexService.NAME)) {
            if (pos.signum() > 0) {
                signalType = SignalType.B_PRE_LIQ;
                deltaName = DeltaName.B_DELTA;
            } else if (pos.signum() < 0) {
                signalType = SignalType.B_PRE_LIQ;
                deltaName = DeltaName.O_DELTA;
            }
        } else {
            if (pos.signum() > 0) {
                signalType = SignalType.O_PRE_LIQ;
                deltaName = DeltaName.O_DELTA;
            } else if (pos.signum() < 0) {
                signalType = SignalType.O_PRE_LIQ;
                deltaName = DeltaName.B_DELTA;
            }
        }
        if (deltaName != null) {
            final PreliqBlocks preliqBlocks = getPreliqBlocks(deltaName, posObj);
            preliqParams = new PreliqParams(signalType, deltaName, preliqBlocks);
        }
        return preliqParams;
    }

    private void doPreliqOrder(PreliqParams preliqParams) {

        final SignalType signalType = preliqParams.getSignalType();
        final DeltaName deltaName = preliqParams.getDeltaName();
        final PreliqBlocks preliqBlocks = preliqParams.getPreliqBlocks();

        // put message in a queue
        final BestQuotes bestQuotes = Utils.createBestQuotes(
                marketService.getArbitrageService().getRightMarketService().getOrderBook(),
                marketService.getArbitrageService().getLeftMarketService().getOrderBook());

        final Long tradeId = marketService.getArbitrageService().getLastTradeId();

        final String counterName = marketService.getCounterName(signalType, tradeId);

        final PlaceOrderArgs placeOrderArgs;
        if (getName().equals(BitmexService.NAME)) {
            final BigDecimal b_block = preliqBlocks.b_block;
            placeOrderArgs = PlaceOrderArgs.builder()
                    .orderType(deltaName == DeltaName.B_DELTA ? OrderType.ASK : OrderType.BID)
                    .amount(b_block)
                    .bestQuotes(bestQuotes)
                    .placingType(PlacingType.TAKER)
                    .signalType(signalType)
                    .attempt(1)
                    .tradeId(tradeId)
                    .counterName(counterName)
                    .preliqQueuedTime(Instant.now())
                    .preliqMarketName(getName())
                    .build();
        } else { // okex
            final BigDecimal o_block = preliqBlocks.o_block;
            placeOrderArgs = PlaceOrderArgs.builder()
                    .orderType(deltaName == DeltaName.B_DELTA ? OrderType.BID : OrderType.ASK)
                    .amount(o_block)
                    .bestQuotes(bestQuotes)
                    .placingType(PlacingType.TAKER)
                    .signalType(signalType)
                    .attempt(1)
                    .tradeId(tradeId)
                    .counterName(counterName)
                    .preliqQueuedTime(Instant.now())
                    .preliqMarketName(getName())
                    .build();
        }
        placeOrderArgs.setPreliqOrder(true);

        try {
            marketService.getTradeLogger().info(String.format("%s %s do preliq", counterName, getName()));

            placePreliqOrder(placeOrderArgs);

        } catch (Exception e) {
            final String msg = String.format("%s %s preliq error %s", counterName, getName(), e.toString());
            log.error(msg, e);
            warningLogger.error(msg);
            marketService.getTradeLogger().info(msg);
        }
    }

    private void placePreliqOrder(PlaceOrderArgs placeOrderArgs) throws InterruptedException {

        final BigDecimal block = placeOrderArgs.getAmount();
        if (block.signum() <= 0) {
            String msg = "WARNING: NO PRELIQ: block=" + block;
            Thread.sleep(1000);
            marketService.getTradeLogger().warn(msg);
            log.warn(msg);
            warningLogger.warn(getName() + " " + msg);
            return;
        }
        marketService.getArbitrageService().getSlackNotifications().sendNotify(NotifyType.PRELIQ, String.format("%s %s a=%scont",
                placeOrderArgs.getSignalType().toString(),
                getName(),
                placeOrderArgs.getAmount()
        ));

        marketService.placeOrder(placeOrderArgs);
    }

    public BigDecimal getDqlCloseMin() {
        return getDqlCloseMin(marketService);
    }

    public BigDecimal getDqlCloseMin(MarketService marketService) {
        ArbType arbType = marketService.getArbType();
        final Dql dql = marketService.getPersistenceService().getSettingsRepositoryService().getSettings().getDql();
        if (arbType == ArbType.LEFT) {
            return dql.getLeftDqlCloseMin();
        }
        return dql.getRightDqlCloseMin();
    }

    public BigDecimal getDqlOpenMin() {
        final Dql dql = marketService.getPersistenceService().getSettingsRepositoryService().getSettings().getDql();
        ArbType arbType = marketService.getArbType();
        if (arbType == ArbType.LEFT) {
            return dql.getLeftDqlOpenMin();
        }
        return dql.getRightDqlOpenMin();
    }

    private PreliqBlocks getPreliqBlocks(DeltaName deltaName, Pos posObj) {
        final CorrParams corrParams = marketService.getPersistenceService().fetchCorrParams();
        BigDecimal l_block = BigDecimal.ZERO;
        BigDecimal r_block = BigDecimal.ZERO;
        if (getName().equals(BitmexService.NAME)) {
            l_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockLeft(marketService.getName()));
            final BigDecimal pos = posObj.getPositionLong();
            if (pos != null && pos.signum() != 0) {
                if (deltaName == DeltaName.B_DELTA && pos.signum() > 0 && pos.compareTo(l_block) < 0) { // btm sell
                    l_block = pos;
                }
                if (deltaName == DeltaName.O_DELTA && pos.signum() < 0 && (pos.abs()).compareTo(l_block) < 0) { // btm buy
                    l_block = pos.abs();
                }
            }
            if (l_block.signum() == 0) {
                log.warn("L_block = 0");
            }
        } else if (getName().equals(OkCoinService.NAME)) {
            r_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockOkex());
            BigDecimal pos = posObj.getPositionLong();
            if (pos != null && pos.signum() != 0 && pos.compareTo(r_block) < 0) {
                if (deltaName == DeltaName.O_DELTA && pos.signum() > 0 && pos.compareTo(r_block) < 0) { // okex sell
                    r_block = pos;
                }
                if (deltaName == DeltaName.B_DELTA && pos.signum() < 0 && (pos.abs()).compareTo(r_block) < 0) { // okex buy
                    r_block = pos.abs();
                }
            }
            if (r_block.signum() == 0) {
                log.warn("R_block = 0");
            }
        }
        return new PreliqBlocks(l_block, r_block);
    }

    @Data
    private static class PreliqBlocks {

        private final BigDecimal b_block;
        private final BigDecimal o_block;

    }

    @Data
    private static class PreliqParams {

        private final SignalType signalType;
        private final DeltaName deltaName;
        private final PreliqBlocks preliqBlocks;
    }

}
