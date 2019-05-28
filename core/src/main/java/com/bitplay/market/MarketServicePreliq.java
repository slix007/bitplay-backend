package com.bitplay.market;

import com.bitplay.arbitrage.dto.BestQuotes;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.external.NotifyType;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.LiqInfo;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.model.Pos;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.utils.Utils;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order.OrderType;

@Getter
@Slf4j
public abstract class MarketServicePreliq extends MarketServicePortions {

    public abstract LimitsService getLimitsService();

    public boolean noPreliq() {
        return !dtPreliq.isActive();
    }

    public void setPreliqState() {
        final MarketState prevMarketState = getMarketState();
        if (prevMarketState != MarketState.PRELIQ) {
            log.info(getName() + "_PRELIQ: prev market state is " + prevMarketState);
            setMarketState(MarketState.PRELIQ);
            getArbitrageService().setBusyStackChecker();
        }
    }

    public void resetPreliqState() {
        if (getMarketState() == MarketState.PRELIQ) {
            cancelAllOrders("After PRELIQ: CancelAllOpenOrders", false);

            MarketState toSet = MarketState.READY; // hasOpenOrders() ? MarketState.ARBITRAGE : MarketState.READY;
            log.warn(getName() + "_PRELIQ: resetPreliqState to " + toSet);
            setMarketState(toSet);
        }
    }

    public void checkForDecreasePosition() {
        Instant start = Instant.now();

        if (getArbitrageService().isArbStateStopped() || getMarketState() == MarketState.FORBIDDEN) {
            dtPreliq.stop();
            return;
        }

        final LiqInfo liqInfo = getLiqInfo();
        final BigDecimal dqlCloseMin = getDqlCloseMin();
        final BigDecimal pos = getPosVal();
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

        final boolean outsideLimits = getLimitsService().outsideLimitsForPreliq(pos);

        if (dqlViolated
                && !outsideLimits
                && pos.signum() != 0
                && corrParams.getPreliq().hasSpareAttempts()
        ) {
            final PreliqParams preliqParams = getPreliqParams(pos);
            if (preliqParams != null && preliqParams.getPreliqBlocks() != null
                    &&
                    ((getName().equals(BitmexService.NAME) && preliqParams.getPreliqBlocks().getB_block().signum() > 0) ||
                            (getName().equals(OkCoinService.NAME) && preliqParams.getPreliqBlocks().getO_block().signum() > 0))
            ) {

                boolean gotActivated = dtPreliq.activate();
                if (gotActivated) {
                    getArbitrageService().setArbStatePreliq(); // do setPreliqState for arbState and both markets
                } else {
                    setPreliqState(); // NOTE: race condition with setMarketState in "finishing placeOrder" and preliq gotActivated
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

                    if (getPersistenceService().getSettingsRepositoryService().getSettings().getManageType().isAuto()) {

                        printPreliqStarting(counterForLogs, nameSymbol);
                        if (corrParams.getPreliq().tryIncFailed(getName())) { // previous preliq counter
                            getPersistenceService().saveCorrParams(corrParams);
                        }
                        if (corrParams.getPreliq().hasSpareAttempts()) {
                            doPreliqOrder(preliqParams);
                        } else {
                            resetPreliqState();
                        }
                        log.info("dtPreliq stop after successful preliq");
                        dtPreliq.stop(); //after successful start

                    } // else stay _ready_

                }
            } else {
                resetPreliqState();
                dtPreliq.stop();
            }
        } else {
            resetPreliqState();
            dtPreliq.stop();
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, log, "checkForDecreasePosition");
    }

    private void printPreliqStarting(String counterForLogs, String nameSymbol) {
        try {
            final String prefix = String.format("#%s %s_PRE_LIQ starting: ", counterForLogs, nameSymbol);
            final MarketServicePreliq thatMarket = getName().equals(BitmexService.NAME)
                    ? getArbitrageService().getSecondMarketService()
                    : getArbitrageService().getFirstMarketService();

            final String thisMarketStr = prefix + getPreliqStartingStr();
            final String thatMarketStr = prefix + thatMarket.getPreliqStartingStr();

            log.info(thisMarketStr);
            log.info(thatMarketStr);
            warningLogger.info(thisMarketStr);
            warningLogger.info(thatMarketStr);
            getTradeLogger().info(thisMarketStr);
            getTradeLogger().info(thatMarketStr);
            thatMarket.getTradeLogger().info(thisMarketStr);
            thatMarket.getTradeLogger().info(thatMarketStr);
            getArbitrageService().printToCurrentDeltaLog(thisMarketStr);
            getArbitrageService().printToCurrentDeltaLog(thatMarketStr);
        } catch (Exception e) {
            log.error("Error in printPreliqStarting", e);
            final String err = "Error in printPreliqStarting " + e.toString();
            warningLogger.error(err);
            getTradeLogger().error(err);
            getArbitrageService().printToCurrentDeltaLog(err);
        }
    }

    private String getPreliqStartingStr() {
        final Pos position = getPos();
        final LiqInfo liqInfo = getLiqInfo();
        final BigDecimal dqlCloseMin = getDqlCloseMin();
        final String dqlCurrStr = liqInfo != null && liqInfo.getDqlCurr() != null ? liqInfo.getDqlCurr().toPlainString() : "null";
        final String dqlCloseMinStr = dqlCloseMin != null ? dqlCloseMin.toPlainString() : "null";
        return String.format("%s p(%s-%s)/dql%s/dqlClose%s",
                getName(),
                position.getPositionLong().toPlainString(),
                position.getPositionShort().toPlainString(),
                dqlCurrStr, dqlCloseMinStr);
    }

    public boolean isDqlViolated() {
        LiqInfo liqInfo = getLiqInfo();
        final BigDecimal dqlCloseMin = getDqlCloseMin();
        return isDqlViolated(liqInfo, dqlCloseMin);
    }

    private boolean isDqlViolated(LiqInfo liqInfo, BigDecimal dqlCloseMin) {
        final BigDecimal dqlLevel = getPersistenceService().getSettingsRepositoryService().getSettings().getDql().getDqlLevel();
        return liqInfo.getDqlCurr() != null
                && liqInfo.getDqlCurr().compareTo(dqlLevel) >= 0 // workaround when DQL is less zero
                && liqInfo.getDqlCurr().compareTo(dqlCloseMin) <= 0;
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
            preliqParams = new PreliqParams(signalType, deltaName, preliqBlocks);
        }
        return preliqParams;
    }

    private void doPreliqOrder(PreliqParams preliqParams) {
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

        final PlaceOrderArgs placeOrderArgs;
        if (getName().equals(BitmexService.NAME)) {
            placeOrderArgs = new PlaceOrderArgs(
                    deltaName == DeltaName.B_DELTA ? OrderType.ASK : OrderType.BID,
                    b_block,
                    bestQuotes,
                    PlacingType.TAKER,
                    signalType,
                    1,
                    tradeId,
                    counterName,
                    null, null, null, Instant.now(), getName());
        } else { // okex
            placeOrderArgs = new PlaceOrderArgs(
                    deltaName == DeltaName.B_DELTA ? OrderType.BID : OrderType.ASK,
                    o_block,
                    bestQuotes,
                    PlacingType.TAKER,
                    signalType,
                    1,
                    tradeId,
                    counterName,
                    null, null, null, Instant.now(), getName());
        }
        placeOrderArgs.setPreliqOrder(true);

        try {
            getTradeLogger().info(String.format("%s %s do preliq", counterName, getName()));

            placePreliqOrder(placeOrderArgs);

        } catch (Exception e) {
            final String msg = String.format("%s %s preliq error %s", counterName, getName(), e.toString());
            log.error(msg, e);
            warningLogger.error(msg);
            getTradeLogger().info(msg);
        }
    }

    private void placePreliqOrder(PlaceOrderArgs placeOrderArgs) throws InterruptedException {

        final BigDecimal block = placeOrderArgs.getAmount();
        if (block.signum() <= 0) {
            String msg = "WARNING: NO PRELIQ: block=" + block;
            Thread.sleep(1000);
            getTradeLogger().warn(msg);
            log.warn(msg);
            warningLogger.warn(getName() + " " + msg);
            return;
        }
        getArbitrageService().getSlackNotifications().sendNotify(NotifyType.PRELIQ, String.format("%s %s a=%scont",
                placeOrderArgs.getSignalType().toString(),
                getName(),
                placeOrderArgs.getAmount()
        ));

        placeOrder(placeOrderArgs);
    }

    private BigDecimal getDqlCloseMin() {
        if (getName().equals(BitmexService.NAME)) {
            return getPersistenceService().fetchGuiLiqParams().getBDQLCloseMin();
        }
        return getPersistenceService().fetchGuiLiqParams().getODQLCloseMin();
    }

    private PreliqBlocks getPreliqBlocks(DeltaName deltaName) {
//        final BigDecimal cm = getPersistenceService().getSettingsRepositoryService().getSettings().getPlacingBlocks().getCm();
        final CorrParams corrParams = getPersistenceService().fetchCorrParams();
//        final Position bitmexPosition = getArbitrageService().getFirstMarketService().getPosition();
        final Pos okexPosition = getArbitrageService().getSecondMarketService().getPos();

        BigDecimal b_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockBitmex());
        BigDecimal o_block = BigDecimal.valueOf(corrParams.getPreliq().getPreliqBlockOkex());

//        final BigDecimal btmPos = bitmexPosition.getPositionLong();
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
//            b_block = o_block.multiply(cm).setScale(0, RoundingMode.HALF_UP); // don't change b_block, because markets are not linked in preliq now
        }

        // Check the other market. - don't need it, because markets are not linked in preliq now
        // increasing position only when DQL is ok
//        if (deltaName == DeltaName.B_DELTA) {
//            // bitmex sell, okex buy
//            if (getName().equals(OkCoinService.NAME) && btmPos.signum() < 0 && bitmexDqlViolated(bitmexPosition, okexPosition)) {
//                return null;
//            }
//            if (getName().equals(BitmexService.NAME) && okLong.subtract(okShort).signum() > 0 && okexDqlViolated(bitmexPosition, okexPosition)) {
//                return null;
//            }
//        } else {
//            // bitmex buy, okex sell
//            if (getName().equals(OkCoinService.NAME) && btmPos.signum() > 0 && bitmexDqlViolated(bitmexPosition, okexPosition)) {
//                return null;
//            }
//            if (getName().equals(BitmexService.NAME) && okLong.subtract(okShort).signum() < 0 && okexDqlViolated(bitmexPosition, okexPosition)) {
//                return null;
//            }
//        }

        return new PreliqBlocks(b_block, o_block);
    }

//    private boolean okexDqlViolated(Position bitmexPosition, Position okexPosition) {
//        final BigDecimal dqlCloseMin = getPersistenceService().fetchGuiLiqParams().getODQLCloseMin();
//        final LiqInfo okexLiqInfo = getArbitrageService().getSecondMarketService().getLiqInfo();
//        if (isDqlViolated(okexLiqInfo, dqlCloseMin)) {
//            String posDetails = String.format("Bitmex %s; Okex %s(dql=%s, dql_close_min=%s)",
//                    bitmexPosition, okexPosition, okexLiqInfo.getDqlCurr(), dqlCloseMin);
//            final String msg = getName() + " WARNING: Preliq was not started. Can not increase position. Details:" + posDetails;
//            log.error(msg);
//            warningLogger.error(msg);
//            return true;
//        }
//        return false;
//    }
//
//    private boolean bitmexDqlViolated(Position bitmexPosition, Position okexPosition) {
//        final BigDecimal dqlCloseMin = getPersistenceService().fetchGuiLiqParams().getBDQLCloseMin();
//        final LiqInfo btmLiqInfo = getArbitrageService().getFirstMarketService().getLiqInfo();
//        if (isDqlViolated(btmLiqInfo, dqlCloseMin)) {
//            String posDetails = String
//                    .format("Bitmex %s(dql=%s, dql_close_min=%s); Okex %s", bitmexPosition, btmLiqInfo.getDqlCurr(), dqlCloseMin, okexPosition);
//            final String msg = getName() + " WARNING: Preliq was not started. Can not increase position. Details:" + posDetails;
//            log.error(msg);
//            warningLogger.error(msg);
//            return true;
//        }
//        return false;
//    }

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
