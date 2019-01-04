package com.bitplay.market;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.LiqInfo;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.PlacingType;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.utils.Utils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Position;

@Getter
@Slf4j
public abstract class MarketServicePreliq extends MarketService {

    protected volatile MarketState prevPreliqMarketState;

    public final BlockingQueue<PlaceOrderArgs> preliqQueue = new LinkedBlockingQueue<>();

    public boolean noPreliq() {
        return preliqQueue.isEmpty() && !dtPreliq.isActive();
    }

    public void setPreliqState() {
        prevPreliqMarketState = getMarketState();
        setMarketState(MarketState.PRELIQ);
        getArbitrageService().setBusyStackChecker();
        log.info(getName() + "_PRELIQ: save prev market state to " + prevPreliqMarketState);
    }

    public void resetPreliqState(boolean forced) {
        if (forced || !getArbitrageService().isArbStatePreliq()) {
            final MarketState prevState = this.prevPreliqMarketState;
            if (prevState != null) {
                if (prevState == MarketState.PRELIQ) {
                    log.warn(getName() + "_PRELIQ: prevPreliqMarketState is PRELIQ");
                }
                log.info(getName() + "_PRELIQ: revert market state to " + prevState);
                setMarketState(prevState);
                this.prevPreliqMarketState = null;
            }
            if (getMarketState() == MarketState.PRELIQ) {
                log.warn(getName() + "_PRELIQ: resetPreliqState to READY.");
                setMarketState(MarketState.READY);
            }
        }
    }

    public void checkForDecreasePosition() {
        Instant start = Instant.now();

        if (getMarketState().isStopped()) {
            dtPreliq.stop();
            return;
        }
        Position position = getPosition();
        LiqInfo liqInfo = getLiqInfo();

        final BigDecimal dqlCloseMin = getDqlCloseMin();
        final BigDecimal pos = getPos(position);
        final CorrParams corrParams = getPersistenceService().fetchCorrParams();

        final boolean dqlViolated = isDqlViolated(liqInfo, dqlCloseMin);

        if (!dqlViolated) {
            //TODO. Sometimes (DQL==na and pos==0) when it's not true.
            // Sometimes (pos!=0 && DQL==n/a)
            // It means okex-preliq-curr/max errors does not work
            if (corrParams.getPreliq().tryIncSuccessful(getName())) {
                getPersistenceService().saveCorrParams(corrParams);
            }
        }

        if (dqlViolated
                && pos.signum() != 0
                && corrParams.getPreliq().hasSpareAttempts()
                && preliqQueue.isEmpty()
        ) {
            final PreliqParams preliqParams = getPreliqParams(pos);
            if (preliqParams != null && preliqParams.getPreliqBlocks() != null
                    && preliqParams.getPreliqBlocks().getB_block().signum() > 0
                    && preliqParams.getPreliqBlocks().getO_block().signum() > 0) {

                boolean gotActivated = dtPreliq.activate();
                if (gotActivated) {
//                    setPreliqState(); // only current Market!
                    getArbitrageService().setArbStatePreliq();
                }

                final Integer delaySec = getPersistenceService().getSettingsRepositoryService().getSettings().getPosAdjustment().getPreliqDelaySec();
                long secToReady = dtPreliq.secToReadyPrecise(delaySec);

                final String counterForLogs = getCounterNameNext(preliqParams.getSignalType()) + "*"; // ex: 2:o_preliq* - the counter of the possible preliq
                final String nameSymbol = getName().substring(0, 1).toUpperCase();
                if (secToReady > 0) {
                    String msg = String.format("#%s %s_PRE_LIQ signal mainSet. Waiting delay(sec)=%s", counterForLogs, nameSymbol, secToReady);
                    log.info(msg);
                    warningLogger.info(msg);
                    getTradeLogger().info(msg);
                } else {
                    String msg = String.format("#%s %s_PRE_LIQ starting: p(%s-%s)/dql%s/dqlClose%s",
                            counterForLogs,
                            nameSymbol,
                            position.getPositionLong().toPlainString(), position.getPositionShort().toPlainString(),
                            liqInfo.getDqlCurr().toPlainString(), dqlCloseMin.toPlainString());
                    log.info(msg);
                    warningLogger.info(msg);
                    getTradeLogger().info(msg);

                    if (corrParams.getPreliq().tryIncFailed(getName())) {
                        getPersistenceService().saveCorrParams(corrParams);
                    }
                    if (corrParams.getPreliq().hasSpareAttempts()) {
                        putPreliqInQueue(preliqParams);
                    } else {
                        resetPreliqState(false);
                    }

                    dtPreliq.stop(); //after successful start
                }
            } else {
                resetPreliqState(false);
                dtPreliq.stop();
            }
        } else {
            resetPreliqState(false);
            dtPreliq.stop();
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, log, "checkForDecreasePosition");
    }

    public boolean isDqlViolated() {
        LiqInfo liqInfo = getLiqInfo();
        final BigDecimal dqlCloseMin = getDqlCloseMin();
        return isDqlViolated(liqInfo, dqlCloseMin);
    }

    private boolean isDqlViolated(LiqInfo liqInfo, BigDecimal dqlCloseMin) {
        return liqInfo.getDqlCurr() != null
                && liqInfo.getDqlCurr().compareTo(BigDecimal.valueOf(-30)) > 0 // workaround when DQL is less zero
                && liqInfo.getDqlCurr().compareTo(dqlCloseMin) < 0;
    }

    private PreliqParams getPreliqParams(BigDecimal pos) {
        if (pos.signum() == 0) {
            return null;
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
            final PreliqBlocks preliqBlocks = getPreliqBlocks(deltaName);
            if (preliqBlocks != null) {
                preliqParams = new PreliqParams(signalType, deltaName, preliqBlocks);
            } else {
//                log.info("No Preliq: block is null");
            }
        }
        return preliqParams;
    }

    private void putPreliqInQueue(PreliqParams preliqParams) {
        final CorrParams corrParams = getPersistenceService().fetchCorrParams();
        corrParams.getPreliq().incTotalCount(getName()); // counterName relates on it
        getPersistenceService().saveCorrParams(corrParams);

        final SignalType signalType = preliqParams.getSignalType();
        final DeltaName deltaName = preliqParams.getDeltaName();
        final PreliqBlocks preliqBlocks = preliqParams.getPreliqBlocks();
        final BigDecimal b_block = preliqBlocks.b_block;
        final BigDecimal o_block = preliqBlocks.o_block;

        // put message in a queue
        final BestQuotes bestQuotes = Utils.createBestQuotes(
                getArbitrageService().getSecondMarketService().getOrderBook(),
                getArbitrageService().getFirstMarketService().getOrderBook());

        final Long tradeId = getArbitrageService().getLastTradeId();

        final String counterName = getCounterName(signalType);

        final PlaceOrderArgs btmArgs = new PlaceOrderArgs(
                deltaName == DeltaName.B_DELTA ? OrderType.ASK : OrderType.BID,
                b_block,
                bestQuotes,
                PlacingType.TAKER,
                signalType,
                1,
                tradeId,
                counterName,
                null, null, null, Instant.now());
        final PlaceOrderArgs okexArgs = new PlaceOrderArgs(
                deltaName == DeltaName.B_DELTA ? OrderType.BID : OrderType.ASK,
                o_block,
                bestQuotes,
                PlacingType.TAKER,
                signalType,
                1,
                tradeId,
                counterName,
                null, null, null, Instant.now());

        getTradeLogger().info(String.format("%s put preliq orders into the queues(bitmex/okex)", counterName));
        getArbitrageService().getFirstMarketService().getPreliqQueue().add(btmArgs);
        getArbitrageService().getSecondMarketService().getPreliqQueue().add(okexArgs);
    }

    private BigDecimal getPos(Position position) {
//        if (getName().equals(BitmexService.NAME)) {
//            return position.getPositionLong();
//        }
        return position.getPositionLong().subtract(position.getPositionShort());
    }

    private BigDecimal getDqlCloseMin() {
        if (getName().equals(BitmexService.NAME)) {
            return getPersistenceService().fetchGuiLiqParams().getBDQLCloseMin();
        }
        return getPersistenceService().fetchGuiLiqParams().getODQLCloseMin();
    }

    private PreliqBlocks getPreliqBlocks(DeltaName deltaName) {
        final BigDecimal cm = getPersistenceService().getSettingsRepositoryService().getSettings().getPlacingBlocks().getCm();
        final CorrParams corrParams = getPersistenceService().fetchCorrParams();
        final Position bitmexPosition = getArbitrageService().getFirstMarketService().getPosition();
        final Position okexPosition = getArbitrageService().getSecondMarketService().getPosition();

        BigDecimal b_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockBitmex());
        BigDecimal o_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockOkex());

        final BigDecimal btmPos = bitmexPosition.getPositionLong();
//            if (btmPos.signum() == 0) {
//                String posDetails = String.format("Bitmex %s; Okex %s", firstMarketService.getPosition(), secondMarketService.getPosition());
//                logger.error("WARNING: Preliq was not started, because Bitmex pos=0. Details:" + posDetails);
//                warningLogger.error("WARNING: Preliq was not started, because Bitmex pos=0. Details:" + posDetails);
//                forbidden = true;
//                return this;
//            }

        // decrease by bitmex 0
//        if ((deltaName == DeltaName.B_DELTA && btmPos.signum() > 0 && btmPos.compareTo(b_block) < 0)
//                || (deltaName == DeltaName.O_DELTA && btmPos.signum() < 0 && btmPos.abs().compareTo(b_block) < 0)) {
//              // TODO use multiplicity (cont=>usd=>okexCont=>btmCont)
//            b_block = btmPos.abs(); // we should use PlacingBlocks.toBitmexCont(BigDecimal.valueOf(preliqBlockUsd), isEth, cm).intValue();
//            o_block = b_block.divide(cm, 0, RoundingMode.HALF_UP);
//        }
        final BigDecimal okLong = okexPosition.getPositionLong();
        final BigDecimal okShort = okexPosition.getPositionShort();
//            if (okLong.signum() == 0 && okShort.signum() == 0) {
//                String posDetails = String.format("Bitmex %s; Okex %s", firstMarketService.getPosition(), secondMarketService.getPosition());
//                logger.error("WARNING: Preliq was not started, because Okex pos=0. Details:" + posDetails);
//                warningLogger.error("WARNING: Preliq was not started, because Okex pos=0. Details:" + posDetails);
//                forbidden = true;
//                return this;
//            }
        BigDecimal okMeaningPos = deltaName == DeltaName.B_DELTA ? okShort : okLong;
        if (okMeaningPos.signum() != 0 && okMeaningPos.compareTo(o_block) < 0) {
            o_block = okMeaningPos;
            b_block = o_block.multiply(cm).setScale(0, RoundingMode.HALF_UP);
        }

        // increasing position only when DQL is ok
        if (deltaName == DeltaName.B_DELTA) {
            // bitmex sell, okex buy
            if (getName().equals(OkCoinService.NAME) && btmPos.signum() < 0 && bitmexDqlViolated(bitmexPosition, okexPosition)) {
                return null;
            }
            if (getName().equals(BitmexService.NAME) && okLong.subtract(okShort).signum() > 0 && okexDqlViolated(bitmexPosition, okexPosition)) {
                return null;
            }
        } else {
            // bitmex buy, okex sell
            if (getName().equals(OkCoinService.NAME) && btmPos.signum() > 0 && bitmexDqlViolated(bitmexPosition, okexPosition)) {
                return null;
            }
            if (getName().equals(BitmexService.NAME) && okLong.subtract(okShort).signum() < 0 && okexDqlViolated(bitmexPosition, okexPosition)) {
                return null;
            }
        }

        return new PreliqBlocks(b_block, o_block);
    }

    private boolean okexDqlViolated(Position bitmexPosition, Position okexPosition) {
        final BigDecimal dqlCloseMin = getPersistenceService().fetchGuiLiqParams().getODQLCloseMin();
        final LiqInfo okexLiqInfo = getArbitrageService().getSecondMarketService().getLiqInfo();
        if (isDqlViolated(okexLiqInfo, dqlCloseMin)) {
            String posDetails = String.format("Bitmex %s; Okex %s(dql=%s, dql_close_min=%s)",
                    bitmexPosition, okexPosition, okexLiqInfo.getDqlCurr(), dqlCloseMin);
            final String msg = getName() + " WARNING: Preliq was not started. Can not increase position. Details:" + posDetails;
            log.error(msg);
            warningLogger.error(msg);
            return true;
        }
        return false;
    }

    private boolean bitmexDqlViolated(Position bitmexPosition, Position okexPosition) {
        final BigDecimal dqlCloseMin = getPersistenceService().fetchGuiLiqParams().getBDQLCloseMin();
        final LiqInfo btmLiqInfo = getArbitrageService().getFirstMarketService().getLiqInfo();
        if (isDqlViolated(btmLiqInfo, dqlCloseMin)) {
            String posDetails = String
                    .format("Bitmex %s(dql=%s, dql_close_min=%s); Okex %s", bitmexPosition, btmLiqInfo.getDqlCurr(), dqlCloseMin, okexPosition);
            final String msg = getName() + " WARNING: Preliq was not started. Can not increase position. Details:" + posDetails;
            log.error(msg);
            warningLogger.error(msg);
            return true;
        }
        return false;
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
