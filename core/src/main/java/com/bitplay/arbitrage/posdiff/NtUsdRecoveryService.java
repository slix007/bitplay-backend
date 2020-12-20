package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.ArbType;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.dto.SignalTypeEx;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.model.Pos;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.PlacingType;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.Future;

import static org.knowm.xchange.dto.Order.OrderType;

@Service
@RequiredArgsConstructor
@Slf4j
public class NtUsdRecoveryService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private final NtUsdExecutor ntUsdExecutor;
    private final PosDiffService posDiffService;
    private final PersistenceService persistenceService;
    private final ArbitrageService arbitrageService;
    private final TradeService tradeService;

    static class RecoveryParam {

        String predefinedMarketNameWithType;

        public RecoveryParam(String marketWithTypeToRecovery) {
            this.predefinedMarketNameWithType = marketWithTypeToRecovery;
        }

        boolean isAuto() {
            return predefinedMarketNameWithType != null;
        }

    }

    public Future<String> tryRecoveryByButton() {
        return ntUsdExecutor.runTask(() -> {
            try {
                final RecoveryResult recoveryResult = doRecovery(new RecoveryParam(null));
                return recoveryResult.details;
            } catch (Exception e) {
                log.error("recovery_nt_usd is failed.", e);
                final String msg = "recovery_nt_usd is failed." + e.getMessage();
                warningLogger.error(msg);
                return msg;
            }
        });
    }

    public Future<String> tryRecoveryAfterKillPos(MarketServicePreliq marketService) {
        return ntUsdExecutor.runTask(() -> {
            try {
                final String marketWithTypeToRecovery = marketService.getArbType() == ArbType.LEFT
                        ? arbitrageService.getRightMarketService().getNameWithType()
                        : arbitrageService.getLeftMarketService().getNameWithType();

                boolean amount0 = true;
                boolean okexThroughZero = true;
                int attempt = 0;
                String resDetails = "RecoveryNtUsdAfterKillposResult: ";
                while ((amount0 || okexThroughZero) && attempt < 5) {
                    if (++attempt > 1) {
                        Thread.sleep(500);
                    }
                    RecoveryResult r1 = doRecovery(new RecoveryParam(marketWithTypeToRecovery));
                    amount0 = r1.amount0;
                    okexThroughZero = r1.okexThroughZero;

                    resDetails += "a" + attempt + ": " + r1 + "; ";
                }

                return resDetails;
            } catch (Exception e) {
                log.error("recovery_nt_usd is failed.", e);
                final String msg = "recovery_nt_usd is failed." + e.getMessage();
                warningLogger.error(msg);
                return msg;
            }
        });
    }

    @Data
    static class RecoveryResult {

        private final String details;
        private final Boolean okexThroughZero;
        private final Boolean amount0;
    }

    @SuppressWarnings("Duplicates")
    private RecoveryResult doRecovery(RecoveryParam rp) {
        posDiffService.stopTimerToImmediateCorrection(); // avoid double-correction

        arbitrageService.getLeftMarketService().stopAllActions("recovery-nt-usd:stopAllActions");
        arbitrageService.getRightMarketService().stopAllActions("recovery-nt-usd:stopAllActions");
        arbitrageService.resetArbState("recovery-nt-usd");

        BigDecimal dc = posDiffService.getDcMainSet().setScale(2, RoundingMode.HALF_UP);

        // TODO
        BigDecimal cm = arbitrageService.getCm();
        boolean leftIsBtm = arbitrageService.getLeftMarketService().isBtm();
        boolean leftOkex = !leftIsBtm;
        final boolean isEth = arbitrageService.isEth();
        final CorrParams corrParams = persistenceService.fetchCorrParams();
        final int maxBlockUsd = rp.isAuto() ? Integer.MAX_VALUE : corrParams.getRecoveryNtUsd().getMaxBlockUsd();
        BigDecimal maxBtm = PlacingBlocks.toBitmexContPure(BigDecimal.valueOf(maxBlockUsd), isEth, cm, leftOkex);
        BigDecimal maxOk = PlacingBlocks.toOkexCont(BigDecimal.valueOf(maxBlockUsd), isEth);

        final BigDecimal leftPosVal = arbitrageService.getLeftMarketService().getPosVal();
        final Pos secondPos = arbitrageService.getRightMarketService().getPos();
        final BigDecimal oPL = secondPos.getPositionLong();
        final BigDecimal oPS = secondPos.getPositionShort();
        final BigDecimal rightPosVal = oPL.subtract(oPS);

        final CorrObj corrObj = new CorrObj(SignalType.RECOVERY_NTUSD, oPL, oPS);
        corrObj.noSwitch = rp.isAuto();

        final BigDecimal hedgeAmount = posDiffService.getHedgeAmountMainSet();

        final boolean btmSo = arbitrageService.getLeftMarketService().getMarketState() == MarketState.SYSTEM_OVERLOADED;
        adaptCorrAdjByPosAndDqlAndEBest(corrObj, leftPosVal, oPL, oPS, hedgeAmount, dc, cm, isEth, btmSo, rp.predefinedMarketNameWithType);
        if (rp.isAuto() && !corrObj.marketService.getNameWithType().equals(rp.predefinedMarketNameWithType)) {
            log.warn("adaptCorrAdjByPos: predefinedMarketNameWithType=" + rp.predefinedMarketNameWithType + " and marketService are not match");
            warningLogger.warn("adaptCorrAdjByPos: predefinedMarketNameWithType=" + rp.predefinedMarketNameWithType + " and marketService are not match");
        }

        posDiffService.adaptCorrAdjByMaxVolCorrAndDql(corrObj, maxBtm, maxOk, dc, cm, isEth);

        posDiffService.defineSignalTypeToIncrease(corrObj, leftPosVal, rightPosVal);

        final MarketServicePreliq marketService = corrObj.marketService;
        final OrderType orderType = corrObj.orderType;
        final BigDecimal correctAmount = corrObj.correctAmount;
        final SignalType signalType = corrObj.signalType;
        final ContractType contractType = corrObj.contractType;

        String corrName = "recovery_nt_usd";
        String corrNameWithMarket = corrName + " on " + marketService.getNameWithType();

        String resultMsg = "";
        if (corrObj.errorDescription != null) { // DQL violation (open_min or close_min)
            resultMsg = String.format("No %s. %s.", corrNameWithMarket, corrObj.errorDescription);
            warningLogger.warn(resultMsg);
            marketService.getTradeLogger().warn(resultMsg);
            log.warn(resultMsg);
        } else if (correctAmount.signum() <= 0) {
            resultMsg = String.format("No %s: amount=%s, maxBtm=%s, maxOk=%s, dc=%s, btmPos=%s, okPos=%s, hedge=%s, signal=%s",
                    corrNameWithMarket,
                    correctAmount,
                    maxBtm, maxOk, dc,
                    arbitrageService.getLeftMarketService().getPos().toString(),
                    arbitrageService.getRightMarketService().getPos().toString(),
                    hedgeAmount.toPlainString(),
                    signalType
            );
            warningLogger.warn(resultMsg);
            marketService.getTradeLogger().warn(resultMsg);
            log.warn(resultMsg);
        } else {
            final PlacingType placingType = PlacingType.TAKER;
            final String counterName = signalType.getCounterName();

            final Long tradeId = arbitrageService.getLastTradeId();

            final String soMark = (marketService.getName().equals(OkCoinService.NAME)
                    && corrObj.signalType.isIncreasePos()
                    && arbitrageService.getLeftMarketService().getMarketState() == MarketState.SYSTEM_OVERLOADED)
                    ? "_SO" : "";
            final SignalTypeEx signalTypeEx = new SignalTypeEx(signalType, soMark);

            final String message = String.format("#%s %s %s amount=%s c=%s. recovery_nt_usd maxBlock=%s ",
                    counterName, placingType, orderType, correctAmount, contractType, maxBlockUsd);

            final boolean outsideLimits = checkOutsideLimits(corrNameWithMarket, dc, maxBtm, maxOk, corrObj, hedgeAmount,
                    marketService, orderType, correctAmount, signalType, placingType);
            if (outsideLimits) {
                if (rp.isAuto()) {
                    resultMsg = "outsideLimits. No switchMarkets because predefinedMarket=" + rp.predefinedMarketNameWithType;
                    warningLogger.warn(resultMsg);
                    marketService.getTradeLogger().warn(resultMsg);
                    log.warn(resultMsg);
                } else {
                    resultMsg = switchMarkets(resultMsg, corrName, dc, cm, isEth, maxBtm, maxOk, leftPosVal, rightPosVal, corrObj,
                            placingType, counterName, tradeId, message, hedgeAmount, signalType);
                }
            } else {

                final String setStr = arbitrageService.getMainSetStr();
                tradeService.info(tradeId, counterName, String.format("#%s %s", signalTypeEx.getCounterName(), setStr));
                tradeService.info(tradeId, counterName, message);
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
                log.info(message);

                arbitrageService.setSignalType(signalType);
                marketService.setBusy(counterName, MarketState.ARBITRAGE);
                final TradeResponse tradeResponse = marketService.placeOrder(placeOrderArgs);
                if (rp.isAuto() && !marketService.getNameWithType().equals(rp.predefinedMarketNameWithType)) {
                    log.warn("predefinedMarketNameWithType=" + rp.predefinedMarketNameWithType + " and marketService are not match");
                    warningLogger.warn("predefinedMarketNameWithType=" + rp.predefinedMarketNameWithType + " and marketService are not match");
                }

                if (tradeResponse.errorInsufficientFunds() && rp.predefinedMarketNameWithType == null) {
                    marketService.setMarketState(MarketState.READY);
                    resultMsg = switchMarkets(resultMsg, corrName, dc, cm, isEth, maxBtm, maxOk, leftPosVal, rightPosVal, corrObj,
                            placingType, counterName, tradeId, message, hedgeAmount, signalType);
                } else {
                    resultMsg += parseResMsg(tradeResponse);
                    marketService.getArbitrageService().setBusyStackChecker();
                }
            }
        }

        return new RecoveryResult(resultMsg, corrObj.okexThroughZero, correctAmount.signum() <= 0);
    }

    private boolean checkOutsideLimits(String corrName, BigDecimal dc, BigDecimal maxBtm, BigDecimal maxOk, CorrObj corrObj, BigDecimal hedgeAmount,
            MarketServicePreliq marketService, OrderType orderType, BigDecimal correctAmount, SignalType signalType,
            PlacingType placingType) {
        final boolean outsideLimits = posDiffService.outsideLimits(marketService, orderType, placingType, signalType);
        if (outsideLimits) {
            final String msg = String.format("outsideLimits. No %s: amount=%s, maxBtm=%s, maxOk=%s, dc=%s, btmPos=%s, okPos=%s, hedge=%s, signal=%s",
                    corrName,
                    correctAmount,
                    maxBtm, maxOk, dc,
                    arbitrageService.getLeftMarketService().getPos().toString(),
                    arbitrageService.getRightMarketService().getPos().toString(),
                    hedgeAmount.toPlainString(),
                    signalType
            );
            warningLogger.warn(msg);
            marketService.getTradeLogger().warn(msg);
            log.warn(msg);
        }
        return outsideLimits;
    }

    private String switchMarkets(String resultMsg, String corrName, BigDecimal dc, BigDecimal cm, boolean isEth, BigDecimal maxBtm, BigDecimal maxOk,
            BigDecimal leftPosVal, BigDecimal rightPosVal, CorrObj corrObj, PlacingType placingType, String counterName, Long tradeId,
            String message, BigDecimal hedgeAmount, SignalType signalType) {
        // switch the market
        final String switchMsg = String.format("%s switch markets. %s INSUFFICIENT_BALANCE. ", corrObj.signalType, corrObj.marketService.getNameWithType());
        warningLogger.warn(switchMsg);
        corrObj.marketService.getTradeLogger().info(switchMsg);
        log.info(switchMsg);
        resultMsg += switchMsg;
//
        final MarketServicePreliq theOtherService = corrObj.marketService.getArbType() == ArbType.LEFT
                ? arbitrageService.getRightMarketService()
                : arbitrageService.getLeftMarketService();
        posDiffService.switchMarkets(corrObj, dc, cm, isEth, maxBtm, maxOk, theOtherService);
        posDiffService.defineSignalTypeToIncrease(corrObj, leftPosVal, rightPosVal);
        adaptCorrByDqlAfterSwitch(corrObj);

        final String corrNameWithMarket = corrName + " on " + theOtherService.getNameWithType();

        if (corrObj.errorDescription != null) { // DQL violation (open_min or close_min)
            final String msg = String.format("No %s. %s.", corrNameWithMarket, corrObj.errorDescription);
            warningLogger.warn(msg);
            corrObj.marketService.getTradeLogger().warn(msg);
            log.info(msg);
            resultMsg += msg;
            return resultMsg;
        }

        final boolean outsideLimits = checkOutsideLimits(corrNameWithMarket, dc, maxBtm, maxOk, corrObj, hedgeAmount,
                theOtherService, corrObj.orderType, corrObj.correctAmount, corrObj.signalType, placingType);
        if (outsideLimits) {
            final String msg = String.format("No %s. switchMarket: outsideLimits", corrNameWithMarket);
            warningLogger.warn(msg);
            corrObj.marketService.getTradeLogger().warn(msg);
            log.info(msg);
            resultMsg += msg;
            return resultMsg;
        }

        PlaceOrderArgs theOtherMarketArgs = PlaceOrderArgs.builder()
                .orderType(corrObj.orderType)
                .amount(corrObj.correctAmount)
                .placingType(placingType)
                .signalType(corrObj.signalType)
                .attempt(1)
                .tradeId(tradeId)
                .counterName(counterName)
                .contractType(corrObj.contractType)
                .build();
        corrObj.marketService.getTradeLogger().info(message + theOtherMarketArgs.toString());

        arbitrageService.setSignalType(signalType);
        corrObj.marketService.setBusy(counterName, MarketState.ARBITRAGE);
        final TradeResponse theOtherResp = corrObj.marketService.placeOrder(theOtherMarketArgs);

        if (theOtherResp.errorInsufficientFunds()) {
            corrObj.marketService.setMarketState(MarketState.READY);
            final String msg = String.format("No %s. INSUFFICIENT_BALANCE on switched market.", corrNameWithMarket);
            warningLogger.warn(msg);
            corrObj.marketService.getTradeLogger().warn(msg);
            log.info(msg);
            resultMsg += msg;
        } else {
            resultMsg += parseResMsg(theOtherResp);
            corrObj.marketService.getArbitrageService().setBusyStackChecker();
        }
        return resultMsg;
    }

    private String parseResMsg(TradeResponse tradeResponse) {
        String r = " orderId=" + tradeResponse.getOrderId();
        if (tradeResponse.getErrorCode() != null) {
            if (tradeResponse.getOrderId() == null) {
                r += " error: ";
            }
            r += " " + tradeResponse.getErrorCode();
        }
        return r;
    }

    /**
     * Corr/adj by 'trying decreasing pos'.<br>
     * <b>Uses:</b><br>
     * signalType<br>
     * <b>Defines:</b><br>
     * marketService<br> orderType<br> correctAmount<br> contractType<br>
     */
    @SuppressWarnings("Duplicates")
    private void adaptCorrAdjByPosAndDqlAndEBest(final CorrObj corrObj, final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS, final BigDecimal hedgeAmount,
            final BigDecimal dc, final BigDecimal cm, final boolean isEth, boolean btmSo, String predefinedMarketNameWithType) {

        boolean leftIsBtm = arbitrageService.getLeftMarketService().isBtm();
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
            corrObj.orderType = OrderType.BID;
            final boolean isFirstMarketByPos = (bEquiv.compareTo(okEquiv) < 0 && !btmSo) || posDiffService.okexAmountIsZero(corrObj, dc, isEth);
            boolean isFirstMarket = predefinedMarketNameWithType != null
                    ? arbitrageService.getLeftMarketService().getNameWithType().equals(predefinedMarketNameWithType)
                    : isFirstMarketByPos;

            // >>> Recovery_nt_usd_increase_pos (only button) UPDATE
            StringBuilder exLog = new StringBuilder();
            if (predefinedMarketNameWithType == null) { //is NOT Auto
                boolean increaseOnBitmex = isFirstMarket && bP.signum() >= 0;
                boolean increaseOnOkex = !isFirstMarket && (oPL.subtract(oPS)).signum() >= 0;
                if (increaseOnBitmex || increaseOnOkex) {
                    // 1. Сравниваем DQL двух бирж:
                    BigDecimal leftDql = arbitrageService.getLeftMarketService().getLiqInfo().getDqlCurr();
                    BigDecimal rightDql = arbitrageService.getRightMarketService().getLiqInfo().getDqlCurr();
                    exLog.append("leftDql=").append(leftDql).append(",rightDql=").append(rightDql);
                    if (leftDql != null && rightDql != null
                            && leftDql.subtract(rightDql).signum() != 0) {
                        // a) У обеих бирж DQL числовые значения (не na), тогда выбираем ту, где DQL выше (это будет биржа A, другая - B).
                        isFirstMarket = leftDql.subtract(rightDql).signum() > 0;
                    } else {
                        // b) Если хотя бы на одной из бирж DQL равен na или DQL числовые и равны, тогда сравниваем e_best_usd бирж. Там, где больше e_best_usd, та биржа A.
                        BigDecimal leftEBest = arbitrageService.getbEbest();
                        BigDecimal rightEBest = arbitrageService.getoEbest();
                        exLog.append(",leftEBest=").append(leftEBest).append(",rightEBest=").append(rightEBest);
                        isFirstMarket = leftEBest.subtract(rightEBest).signum() >= 0;
                    }
                }
            }
            // <<< endOf Recovery_nt_usd_increase_pos (only button) UPDATE

            final String exLogStr = exLog.length() > 0 ? exLog.toString() : "auto(predefinedMarket) or byPos";
            if (isFirstMarket) {
                // bitmex buy
                posDiffService.defineCorrectAmountBitmex(corrObj, dc, cm, isEth, leftIsBtm);
                corrObj.marketService = arbitrageService.getLeftMarketService();
                if (bP.signum() >= 0) {
                    corrObj.signalType = SignalType.RECOVERY_NTUSD_INCREASE_POS;
                    corrObj.marketService.getTradeLogger().info("RECOVERY_NTUSD_INCREASE_POS: " + exLogStr);
                }
            } else {
                // okcoin buy
                posDiffService.defineCorrectAmountOkex(corrObj, dc, isEth);
                posDiffService.defineOkexThroughZero(corrObj);
                corrObj.marketService = arbitrageService.getRightMarketService();
                if ((oPL.subtract(oPS)).signum() >= 0) {
                    corrObj.signalType = SignalType.RECOVERY_NTUSD_INCREASE_POS;
                    corrObj.marketService.getTradeLogger().info("RECOVERY_NTUSD_INCREASE_POS: " + exLogStr);
                }
            }
        } else {
            corrObj.orderType = OrderType.ASK;
            final boolean isSecondMarketByPos = (bEquiv.compareTo(okEquiv) < 0 || btmSo) && !posDiffService.okexAmountIsZero(corrObj, dc, isEth);
            boolean isSecondMarket = predefinedMarketNameWithType != null
                    ? arbitrageService.getRightMarketService().getNameWithType().equals(predefinedMarketNameWithType)
                    : isSecondMarketByPos;

            // >>> Recovery_nt_usd_increase_pos (only button) UPDATE
            StringBuilder exLog = new StringBuilder();
            if (predefinedMarketNameWithType == null) { //is NOT Auto
                boolean increaseOnBitmex = !isSecondMarket && bP.signum() >= 0;
                boolean increaseOnOkex = isSecondMarket && (oPL.subtract(oPS)).signum() >= 0;
                if (increaseOnBitmex || increaseOnOkex) {
                    // 1. Сравниваем DQL двух бирж:
                    BigDecimal leftDql = arbitrageService.getLeftMarketService().getLiqInfo().getDqlCurr();
                    BigDecimal rightDql = arbitrageService.getRightMarketService().getLiqInfo().getDqlCurr();
                    exLog.append("leftDql=").append(leftDql).append(",rightDql=").append(rightDql);
                    if (leftDql != null && rightDql != null
                            && leftDql.subtract(rightDql).signum() != 0) {
                        // a) У обеих бирж DQL числовые значения (не na), тогда выбираем ту, где DQL выше (это будет биржа A, другая - B).
                        isSecondMarket = !(leftDql.subtract(rightDql).signum() > 0);
                    } else {
                        // b) Если хотя бы на одной из бирж DQL равен na или DQL числовые и равны, тогда сравниваем e_best_usd бирж. Там, где больше e_best_usd, та биржа A.
                        BigDecimal leftEBest = arbitrageService.getbEbest();
                        BigDecimal rightEBest = arbitrageService.getoEbest();
                        exLog.append(",leftEBest=").append(leftEBest).append(",rightEBest=").append(rightEBest);
                        isSecondMarket = !(leftEBest.subtract(rightEBest).signum() >= 0);
                    }
                }
            }
            // <<< endOf Recovery_nt_usd_increase_pos (only button) UPDATE

            final String exLogStr = exLog.length() > 0 ? exLog.toString() : "auto(predefinedMarket) or byPos";
            if (isSecondMarket) {
                // okcoin sell
                corrObj.marketService = arbitrageService.getRightMarketService();
                posDiffService.defineCorrectAmountOkex(corrObj, dc, isEth);
                posDiffService.defineOkexThroughZero(corrObj);
                if ((oPL.subtract(oPS)).signum() <= 0) {
                    corrObj.signalType = SignalType.RECOVERY_NTUSD_INCREASE_POS;
                    corrObj.marketService.getTradeLogger().info("RECOVERY_NTUSD_INCREASE_POS: " + exLogStr);
                }
            } else {
                // bitmex sell
                corrObj.marketService = arbitrageService.getLeftMarketService();
                posDiffService.defineCorrectAmountBitmex(corrObj, dc, cm, isEth, leftIsBtm);
                if (bP.signum() <= 0) {
                    corrObj.signalType = SignalType.RECOVERY_NTUSD_INCREASE_POS;
                    corrObj.marketService.getTradeLogger().info("RECOVERY_NTUSD_INCREASE_POS: " + exLogStr);
                }
            }
        }

        corrObj.contractType = corrObj.marketService != null ? corrObj.marketService.getContractType() : null;
    }

    private void adaptCorrByDqlAfterSwitch(final CorrObj corrObj) {
        if (corrObj.signalType.isIncreasePos()) {
            boolean theOtherMarketIsViolated = corrObj.marketService.isDqlOpenViolated();
            if (theOtherMarketIsViolated) {
                corrObj.correctAmount = BigDecimal.ZERO;
                corrObj.errorDescription = "DQL_open_min is violated";
            }
        }
    }

}
