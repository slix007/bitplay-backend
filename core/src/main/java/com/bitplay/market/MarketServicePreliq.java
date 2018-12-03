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
        return preliqQueue.isEmpty();
    }

    public void setPreliqState() {
        prevPreliqMarketState = getMarketState();
        setMarketState(MarketState.PRELIQ);
    }

    public void resetPreliqState() {
        if (prevPreliqMarketState != null) {
            setMarketState(prevPreliqMarketState);
            prevPreliqMarketState = null;
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

        if (isDqlViolated(liqInfo, dqlCloseMin)
                && pos.signum() != 0
                && corrParams.getPreliq().hasSpareAttempts()
                && preliqQueue.isEmpty()
        ) {

            boolean gotActivated = dtPreliq.activate();
            if (gotActivated) {
                getArbitrageService().setArbStatePreliq();
                setPreliqState();
            }

            final Integer delaySec = getPersistenceService().getSettingsRepositoryService().getSettings().getPosAdjustment().getPreliqDelaySec();
            long secToReady = dtPreliq.secToReady(delaySec);
            if (secToReady > 0) {
                String msg = getName() + "_PRE_LIQ signal mainSet. Waiting delay(sec)=" + secToReady;
                log.info(msg);
                warningLogger.info(msg);
                getTradeLogger().info(msg);
            } else {
                final String counterForLogs = getCounterName(); // example: 2:o_preliq
                String msg = String.format("#%s %s_PRE_LIQ starting: p(%s-%s)/dql%s/dqlClose%s",
                        counterForLogs,
                        getName(),
                        position.getPositionLong().toPlainString(), position.getPositionShort().toPlainString(),
                        liqInfo.getDqlCurr().toPlainString(), dqlCloseMin.toPlainString());
                log.info(msg);
                warningLogger.info(msg);
                getTradeLogger().info(msg);
                final BestQuotes bestQuotes = Utils.createBestQuotes(
                        getArbitrageService().getSecondMarketService().getOrderBook(),
                        getArbitrageService().getFirstMarketService().getOrderBook());
                preparePreliq(pos, bestQuotes);
                dtPreliq.stop();
            }
        } else {
            dtPreliq.stop();
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, log, "checkForDecreasePosition");
    }

    private boolean isDqlViolated(LiqInfo liqInfo, BigDecimal dqlCloseMin) {
        return liqInfo.getDqlCurr() != null
                && liqInfo.getDqlCurr().compareTo(BigDecimal.valueOf(-30)) > 0 // workaround when DQL is less zero
                && liqInfo.getDqlCurr().compareTo(dqlCloseMin) < 0;
    }

    private void preparePreliq(BigDecimal pos, BestQuotes bestQuotes) {
        if (getName().equals(BitmexService.NAME)) {
            if (pos.signum() > 0) {
                preparePreliqOnDelta(SignalType.B_PRE_LIQ, DeltaName.B_DELTA, bestQuotes);
            } else if (pos.signum() < 0) {
                preparePreliqOnDelta(SignalType.B_PRE_LIQ, DeltaName.O_DELTA, bestQuotes);
            }
        } else {
            if (pos.signum() > 0) {
                preparePreliqOnDelta(SignalType.O_PRE_LIQ, DeltaName.O_DELTA, bestQuotes);
            } else if (pos.signum() < 0) {
                preparePreliqOnDelta(SignalType.O_PRE_LIQ, DeltaName.B_DELTA, bestQuotes);
            }
        }
    }

    private void preparePreliqOnDelta(SignalType signalType, DeltaName deltaName, BestQuotes bestQuotes) {
        // put message in a queue
        final PreliqBlocks preliqBlocks = getPreliqBlocks(deltaName);
        if (preliqBlocks == null) {
            log.info("No Preliq");
            return;
        }

        final Long tradeId = getArbitrageService().getLastTradeId();

        final BigDecimal b_block = preliqBlocks.b_block;
        final BigDecimal o_block = preliqBlocks.o_block;
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

        incTotalCount();

        getArbitrageService().getFirstMarketService().getPreliqQueue().add(btmArgs);
        getArbitrageService().getSecondMarketService().getPreliqQueue().add(okexArgs);

    }

    private void incTotalCount() {
        CorrParams corrParams = getPersistenceService().fetchCorrParams();
        corrParams.getPreliq().incTotalCount();
        getPersistenceService().saveCorrParams(corrParams);
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

        BigDecimal b_block;
        BigDecimal o_block;
        b_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockBitmex());
        o_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockOkex());

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
        if (okMeaningPos.compareTo(o_block) < 0) {
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
            log.error("WARNING: Preliq was not started. Can not increase position. Details:" + posDetails);
            warningLogger.error("WARNING: Preliq was not started. Can not increase position. Details:" + posDetails);
            return true;
        }
        return false;
    }

    private boolean bitmexDqlViolated(Position bitmexPosition, Position okexPosition) {
        final BigDecimal dqlCloseMin = getPersistenceService().fetchGuiLiqParams().getBDQLCloseMin();
        final LiqInfo btmLiqInfo = getArbitrageService().getFirstMarketService().getLiqInfo();
        if (isDqlViolated(btmLiqInfo, dqlCloseMin)) {
            String posDetails = String.format("Bitmex %s(dql=%s, dql_close_min=%s); Okex %s",
                    bitmexPosition, btmLiqInfo.getDqlCurr(), dqlCloseMin, okexPosition);
            log.error("WARNING: Preliq was not started. Can not increase position. Details:" + posDetails);
            warningLogger.error("WARNING: Preliq was not started. Can not increase position. Details:" + posDetails);
            return true;
        }
        return false;
    }

    @Data
    private static class PreliqBlocks {

        private final BigDecimal b_block;
        private final BigDecimal o_block;

    }


}
