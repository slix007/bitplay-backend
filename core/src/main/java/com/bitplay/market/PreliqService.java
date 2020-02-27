package com.bitplay.market;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.DelayTimer;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.posdiff.DqlStateService;
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
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;

import static org.knowm.xchange.dto.Order.OrderType;

@RequiredArgsConstructor
@Getter
@Slf4j
public class PreliqService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private final MarketServicePreliq marketService;

    private final DelayTimer dtPreliq = new DelayTimer();
    private final DelayTimer dtKillpos = new DelayTimer();

    public void checkForPreliq(boolean settlementMode) {
        if (settlementMode) {
            resetPreliqState();
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
            final BigDecimal dqlCloseMin = getDqlCloseMin();
            final BigDecimal dqlOpenMin = getDqlOpenMin();
            final BigDecimal dqlKillPos = getDqlKillPos();
            final Pos pos = marketService.getPos();
            final BigDecimal posVal = pos.getPositionLong().subtract(pos.getPositionShort());
            final CorrParams corrParams = persistenceService.fetchCorrParams();
            maxCountNotify(corrParams.getPreliq(), "preliq");
            maxCountNotify(corrParams.getKillpos(), "killpos");

            final DqlStateService dqlStateService = arbitrageService.getDqlStateService();
            final DqlState marketDqlState = dqlStateService.updateDqlState(marketService.getArbType(), dqlKillPos, dqlOpenMin, dqlCloseMin, liqInfo.getDqlCurr());

            if (marketDqlState != DqlState.KILLPOS) {
                if (corrParams.getKillpos().tryIncSuccessful(getName())) {
                    persistenceService.saveCorrParams(corrParams);
                }
                dtKillpos.stop();
            }

            boolean normalDql = !marketDqlState.isActiveClose();
            final boolean noSpareAttempts = !corrParams.getPreliq().hasSpareAttempts() && !corrParams.getKillpos().hasSpareTotalAttempts();
            if (normalDql
                    || posZeroViolation(pos)
                    || marketService.getLimitsService().outsideLimitsForPreliq(posVal)
                    || noSpareAttempts
            ) {
                if (corrParams.getPreliq().tryIncSuccessful(getName())) {
                    persistenceService.saveCorrParams(corrParams);
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

                if (!persistenceService.getSettingsRepositoryService().getSettings().getManageType().isAuto()) {
                    return; // keep timer on, but no actions
                }

                // ex: 2:o_preliq* - the counter of the possible preliq
                final String nameSymbol = marketService.getName().substring(0, 1).toUpperCase();

                // killpos timer
                final Integer delaySecKillpos = persistenceService.getSettingsRepositoryService().getSettings().getPosAdjustment().getKillposDelaySec();
                long secToReadyKillpos = dtKillpos.secToReadyPrecise(delaySecKillpos);
                if (marketDqlState == DqlState.KILLPOS) {
                    if (secToReadyKillpos > 0) {
                        final String counterForLogs = marketService.getCounterNameNext(getSignalType()) + "*";
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
                if (marketDqlState == DqlState.KILLPOS && secToReadyKillpos <= 0) {
                    if (corrParams.getKillpos().tryIncFailed(getName())) { // previous preliq counter
                        persistenceService.saveCorrParams(corrParams);
                    }

                    if (corrParams.getKillpos().hasSpareTotalAttempts()) {
                        corrParams.getKillpos().incTotalCount(getName()); // counterName relates on it
                        persistenceService.saveCorrParams(corrParams);

                        final String counterForLogs = marketService.getCounterNameNext(getSignalType());
                        printPreliqStarting(counterForLogs, nameSymbol, pos, liqInfo, "KILLPOS");
                        marketService.getKillPosService().doKillPos(counterForLogs);
                        log.info("dtKillpos stop after successful killpos");
                        dtKillpos.stop();
                        dtPreliq.stop();

                        return;
                    }

                }

                // do preliq
                if (marketDqlState == DqlState.PRELIQ && secToReady <= 0) {
                    final PreliqParams preliqParams = getPreliqParams(pos, posVal);
                    final boolean preliqBlockEnough = preliqParams != null && preliqParams.getPreliqBlocks() != null
                            && ((getName().equals(BitmexService.NAME) && preliqParams.getPreliqBlocks().getB_block().signum() > 0) ||
                            (getName().equals(OkCoinService.NAME) && preliqParams.getPreliqBlocks().getO_block().signum() > 0));
                    if (!preliqBlockEnough) {
                        resetPreliqState();
                        dtPreliq.stop();
                        return;
                    }

                    final String counterForLogs = marketService.getCounterNameNext(preliqParams.getSignalType());

                    if (corrParams.getPreliq().tryIncFailed(getName())) { // previous preliq counter
                        persistenceService.saveCorrParams(corrParams);
                    }
                    if (corrParams.getPreliq().hasSpareAttempts()) {
                        printPreliqStarting(counterForLogs, nameSymbol, pos, liqInfo, "PRELIQ");
                        corrParams.getPreliq().incTotalCount(getName()); // counterName relates on it
                        persistenceService.saveCorrParams(corrParams);
                        doPreliqOrder(preliqParams);
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

    private BigDecimal getDqlKillPos() {
        final PersistenceService persistenceService = marketService.getPersistenceService();
        final Dql dql = persistenceService.getSettingsRepositoryService().getSettings().getDql();
        return getName().equals(BitmexService.NAME) ? dql.getBtmDqlKillPos() : dql.getOkexDqlKillPos();
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
            log.info(String.format("%s set to %s; prev market state is %s", getName(), toSet, prevMarketState));
            marketService.setMarketState(toSet);
        }
        if (!prevMarketState.isActiveClose()) {
            marketService.getArbitrageService().setBusyStackChecker();
        }
    }

    public void resetPreliqState() {
        if (marketService.getMarketState() == MarketState.PRELIQ || marketService.getMarketState() == MarketState.KILLPOS) {
            final FplayOrder stub = new FplayOrder(marketService.getMarketId(), null, "cancelOnPreliq");
            marketService.cancelAllOrders(stub, "After PRELIQ: CancelAllOpenOrders", false, true);

            MarketState toSet = MarketState.READY; // hasOpenOrders() ? MarketState.ARBITRAGE : MarketState.READY;
            log.warn(getName() + "_PRELIQ: resetPreliqState to " + toSet);
            marketService.setMarketState(toSet);
            marketService.updateDqlState();
        }
    }

    private void maxCountNotify(Preliq preliq, String name) {
        if (preliq.totalCountViolated()) {
            marketService.getSlackNotifications().sendNotify(NotifyType.PRELIQ_MAX_TOTAL,
                    String.format("%s max total %s reached ", name, preliq.getMaxTotalCount()));
        }
        if (preliq.maxErrorCountViolated()) {
            marketService.getSlackNotifications().sendNotify(NotifyType.PRELIQ_MAX_ATTEMPT,
                    String.format("%s max attempts %s reached", name, preliq.getMaxErrorCount()));
        }
    }


    private boolean posZeroViolation(Pos pos) {
        if (getName().equals(BitmexService.NAME)) {
            return pos.getPositionLong().signum() == 0; // no preliq
        }
        if (getName().equals(OkCoinService.NAME)) {
            return pos.getPositionLong().signum() == 0 && pos.getPositionShort().signum() == 0; // no preliq
        }
        return false;
    }

    private void printPreliqStarting(String counterForLogs, String nameSymbol, Pos position, LiqInfo liqInfo, String opName) {
        try {
            final String prefix = String.format("#%s %s_%s starting: ", counterForLogs, nameSymbol, opName);
            final MarketServicePreliq thatMarket = getTheOtherMarket();

            final String thisMarketStr = prefix + getPreliqStartingStr(getName(), position, liqInfo);
            final String thatMarketStr = prefix + getPreliqStartingStr(thatMarket.getName(), thatMarket.getPos(), thatMarket.getLiqInfo());

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
            log.error("Error in printPreliqStarting", e);
            final String err = "Error in printPreliqStarting " + e.toString();
            warningLogger.error(err);
            marketService.getTradeLogger().error(err);
            marketService.getArbitrageService().printToCurrentDeltaLog(err);
        }
    }

    private MarketServicePreliq getTheOtherMarket() {
        return getName().equals(BitmexService.NAME)
                ? marketService.getArbitrageService().getRightMarketService()
                : marketService.getArbitrageService().getLeftMarketService();
    }

    private String getPreliqStartingStr(String name, Pos position, LiqInfo liqInfo) {
        final BigDecimal dqlCloseMin = getDqlCloseMin(name);
        final BigDecimal dqlKillPos = getDqlKillPos();
        final String dqlCurrStr = liqInfo != null && liqInfo.getDqlCurr() != null ? liqInfo.getDqlCurr().toPlainString() : "null";
        final String dqlCloseMinStr = dqlCloseMin != null ? dqlCloseMin.toPlainString() : "null";
        final String dqlKillPosStr = dqlKillPos != null ? dqlKillPos.toPlainString() : "null";
        return String.format("%s p(%s-%s)/dql%s/dqlClose%s/dqlKillpos%s",
                name,
                position.getPositionLong().toPlainString(),
                position.getPositionShort().toPlainString(),
                dqlCurrStr, dqlCloseMinStr, dqlKillPosStr);
    }

    private SignalType getSignalType() {
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
        return getDqlCloseMin(getName());
    }

    public BigDecimal getDqlCloseMin(String marketName) {
        final Dql dql = marketService.getPersistenceService().getSettingsRepositoryService().getSettings().getDql();
        if (marketName.equals(BitmexService.NAME)) {
            return dql.getBDQLCloseMin();
        }
        return dql.getODQLCloseMin();
    }

    public BigDecimal getDqlOpenMin() {
        final Dql dql = marketService.getPersistenceService().getSettingsRepositoryService().getSettings().getDql();
        if (getName().equals(BitmexService.NAME)) {
            return dql.getBDQLOpenMin();
        }
        return dql.getODQLOpenMin();
    }

    private PreliqBlocks getPreliqBlocks(DeltaName deltaName, Pos posObj) {
        final CorrParams corrParams = marketService.getPersistenceService().fetchCorrParams();
        BigDecimal b_block = BigDecimal.ZERO;
        BigDecimal o_block = BigDecimal.ZERO;
        if (getName().equals(BitmexService.NAME)) {
            b_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockBitmex());
            final BigDecimal pos = posObj.getPositionLong();
            if (pos != null && pos.signum() != 0) {
                if (deltaName == DeltaName.B_DELTA && pos.signum() > 0 && pos.compareTo(b_block) < 0) { // btm sell
                    b_block = pos;
                }
                if (deltaName == DeltaName.O_DELTA && pos.signum() < 0 && (pos.abs()).compareTo(b_block) < 0) { // btm buy
                    b_block = pos.abs();
                }
            }
            if (b_block.signum() == 0) {
                log.warn("b_block = 0");
            }
        } else if (getName().equals(OkCoinService.NAME)) {
            o_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockOkex());
            final BigDecimal okLong = posObj.getPositionLong();
            final BigDecimal okShort = posObj.getPositionShort();
            BigDecimal okMeaningPos = deltaName == DeltaName.B_DELTA ? okShort : okLong;
            if (okMeaningPos != null && okMeaningPos.signum() != 0 && okMeaningPos.compareTo(o_block) < 0) {
                o_block = okMeaningPos;
            }
            if (o_block.signum() == 0) {
                log.warn("o_block = 0");
            }
        }
        return new PreliqBlocks(b_block, o_block);
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
