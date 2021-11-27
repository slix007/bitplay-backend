package com.bitplay.arbitrage.posdiff;

import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.ArbType;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.dto.SignalTypeEx;
import com.bitplay.model.Pos;
import com.bitplay.security.RecoveryStatus;
import com.bitplay.xchange.dto.Order.OrderType;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.Future;

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
    private final AtomicReference<RecoveryStatus> recoveryStatus = new AtomicReference<>(RecoveryStatus.OFF);
    private volatile PlaceOrderArgs placeOrderArgs;

    public Future<String> tryRecoveryByButton() {
        return ntUsdExecutor.runTask(() -> {
            recoveryStatus.set(RecoveryStatus.IN_PROGRESS);
            try {
                final RecoveryResult recoveryResult = doRecovery(new RecoveryParam(null, null));
                return recoveryResult.details;
            } catch (Exception e) {
                log.error("recovery_nt_usd is failed.", e);
                final String msg = "recovery_nt_usd is failed." + e.getMessage();
                warningLogger.error(msg);
                return msg;
            } finally {
                recoveryStatus.set(RecoveryStatus.OFF);
            }
        });
    }

    public Future<String> tryRecoveryAfterKillPos(MarketServicePreliq marketService) {
        return ntUsdExecutor.runTask(() -> {
            recoveryStatus.set(RecoveryStatus.IN_PROGRESS);
            try {
                final String marketWithTypeToRecovery = marketService.getArbType() == ArbType.LEFT
                        ? arbitrageService.getRightMarketService().getNameWithType()
                        : arbitrageService.getLeftMarketService().getNameWithType();

                boolean amount0 = true;
                boolean okexThroughZero = true;
                int attempt = 0;
                String resDetails = "RecoveryNtUsdAfterKillposResult: ";
                while ((amount0 || okexThroughZero) && attempt < 5 && recoveryStatus.get() == RecoveryStatus.IN_PROGRESS) {
                    if (++attempt > 1) {
                        Thread.sleep(500);
                    }
                    RecoveryResult r1 = doRecovery(new RecoveryParam(marketWithTypeToRecovery, marketService.getArbType()));
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
            } finally {
                recoveryStatus.set(RecoveryStatus.OFF);
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

        BigDecimal dc = posDiffService.getDcMainSet(rp).setScale(2, RoundingMode.HALF_UP);

        BigDecimal cm = arbitrageService.getCm();
        boolean leftIsBtm = arbitrageService.getLeftMarketService().isBtm();
        boolean leftOkex = !leftIsBtm;
        final boolean isEth = arbitrageService.isEth();
        final CorrParams corrParams = persistenceService.fetchCorrParams();
        final int maxBlockUsd = rp.isAuto() ? Integer.MAX_VALUE : corrParams.getRecoveryNtUsd().getMaxBlockUsd();
        BigDecimal maxBtm = PlacingBlocks.toBitmexContPure(BigDecimal.valueOf(maxBlockUsd), isEth, cm, leftOkex);
        BigDecimal maxOk = PlacingBlocks.toOkexCont(BigDecimal.valueOf(maxBlockUsd), isEth);

        final BigDecimal leftPosVal = rp.isKpLeft() ? BigDecimal.ZERO : arbitrageService.getLeftMarketService().getPosVal();
        final Pos rightPos = arbitrageService.getRightMarketService().getPos();
        final BigDecimal oPL = rp.isKpRight() ? BigDecimal.ZERO : rightPos.getPositionLong();
        final BigDecimal oPS = rp.isKpRight() ? BigDecimal.ZERO : rightPos.getPositionShort();
        final BigDecimal rightPosVal = oPL.subtract(oPS);

        final CorrObj corrObj = new CorrObj(SignalType.RECOVERY_NTUSD, oPL, oPS);
        corrObj.noSwitch = rp.isAuto();

        final BigDecimal hedgeAmount = posDiffService.getHedgeAmountMainSet();

        final boolean btmSo = arbitrageService.getLeftMarketService().getMarketState() == MarketState.SYSTEM_OVERLOADED;
        trySwitchByPosAndDqlAndEBest(corrObj, leftPosVal, oPL, oPS, hedgeAmount, dc, cm, isEth, btmSo, rp.getPredefinedMarketNameWithType());
        if (rp.isAuto() && !corrObj.marketService.getNameWithType().equals(rp.getPredefinedMarketNameWithType())) {
            log.warn("adaptCorrAdjByPos: predefinedMarketNameWithType=" + rp.getPredefinedMarketNameWithType() + " and marketService are not match");
            warningLogger.warn("adaptCorrAdjByPos: predefinedMarketNameWithType=" + rp.getPredefinedMarketNameWithType() + " and marketService are not match");
        }

        // marketService should be defined
        posDiffService.reupdateSignalTypeToIncrease(corrObj, leftPosVal, rightPosVal);

        corrObj.noSwitch = true;
        posDiffService.validateIncreaseByDqlAndAdaptMaxVol(corrObj, dc, cm, isEth, maxBtm, maxOk);

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
            resultMsg = String
                    .format("No %s: amount=%s, maxLeft=%s, maxRight=%s, (-nt_usd)dc=%s, leftPos=%s, leftPosVal=%s, rightPos=%s, rightPosVal=%s, hedge=%s, signal=%s",
                            corrNameWithMarket,
                            correctAmount,
                            maxBtm, maxOk, dc,
                            arbitrageService.getLeftMarketService().getPos().toString(), leftPosVal,
                            arbitrageService.getRightMarketService().getPos().toString(), rightPosVal,
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
                    marketService, orderType, correctAmount, signalType, placingType, leftPosVal, rightPosVal);
            if (outsideLimits) {
                resultMsg = "outsideLimits. predefinedMarket=" + rp.getPredefinedMarketNameWithType();
                warningLogger.warn(resultMsg);
                marketService.getTradeLogger().warn(resultMsg);
                log.warn(resultMsg);
            } else {

                final String setStr = arbitrageService.getMainSetStr();
                tradeService.info(tradeId, counterName, String.format("#%s %s", signalTypeEx.getCounterName(), setStr));
                tradeService.info(tradeId, counterName, message);
                placeOrderArgs = PlaceOrderArgs.builder()
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

                // check stop flag
                if (recoveryStatus.get() == RecoveryStatus.OFF) {
                    resultMsg += "STOP by RecoveryStatus.OFF";
                    marketService.getTradeLogger().warn(resultMsg);
                    return new RecoveryResult(resultMsg, corrObj.okexThroughZero, true);
                }


                arbitrageService.setSignalType(signalType);
                marketService.setBusy(counterName, MarketState.ARBITRAGE);
                final TradeResponse tradeResponse = marketService.placeOrder(placeOrderArgs);
                if (rp.isAuto() && !marketService.getNameWithType().equals(rp.getPredefinedMarketNameWithType())) {
                    log.warn("predefinedMarketNameWithType=" + rp.getPredefinedMarketNameWithType() + " and marketService are not match");
                    warningLogger.warn("predefinedMarketNameWithType=" + rp.getPredefinedMarketNameWithType() + " and marketService are not match");
                }

                String msg = String.format("tradeResponse.errorInsufficientFunds()=%s, predefinedMarketNameWithType=%s",
                        tradeResponse.errorInsufficientFunds(),
                        rp.getPredefinedMarketNameWithType());
                log.warn(msg);
                warningLogger.warn(msg);
                resultMsg += parseResMsg(tradeResponse);
                marketService.getArbitrageService().setBusyStackChecker();
            }
        }

        return new RecoveryResult(resultMsg, corrObj.okexThroughZero, correctAmount.signum() <= 0);
    }

    private boolean checkOutsideLimits(String corrName, BigDecimal dc, BigDecimal maxBtm, BigDecimal maxOk, CorrObj corrObj, BigDecimal hedgeAmount,
            MarketServicePreliq marketService, OrderType orderType, BigDecimal correctAmount, SignalType signalType,
            PlacingType placingType, BigDecimal leftPosVal, BigDecimal rightPosVal) {
        final boolean outsideLimits = posDiffService.outsideLimits(marketService, orderType, placingType, signalType);
        if (outsideLimits) {
            final String msg = String
                    .format("outsideLimits. No %s: amount=%s, maxBtm=%s, maxOk=%s, (-nt_usd)dc=%s, leftPos=%s, leftPosVal=%s, rightPos=%s, rightPosVal=%s, hedge=%s, signal=%s",
                            corrName,
                            correctAmount,
                            maxBtm, maxOk, dc,
                            arbitrageService.getLeftMarketService().getPos().toString(), leftPosVal,
                            arbitrageService.getRightMarketService().getPos().toString(), rightPosVal,
                            hedgeAmount.toPlainString(),
                            signalType
                    );
            warningLogger.warn(msg);
            marketService.getTradeLogger().warn(msg);
            log.warn(msg);
        }
        return outsideLimits;
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
    private void trySwitchByPosAndDqlAndEBest(final CorrObj corrObj, final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS,
            final BigDecimal hedgeAmount,
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
                boolean isSecondMarket = !isFirstMarket;
                boolean increaseOnBitmex = isFirstMarket && (bitmexUsd.signum() > 0 || bitmexUsd.add(dc.negate()).signum() > 0); //bitmex buy, включая переход через 0
                boolean increaseOnOkex = isSecondMarket && oPS.signum() == 0; // okex buy AND okex has no 'opened-short-pos'
                // 1. Сравниваем DQL двух бирж:
                BigDecimal leftDql = arbitrageService.getLeftMarketService().getLiqInfo().getDqlCurr();
                BigDecimal rightDql = arbitrageService.getRightMarketService().getLiqInfo().getDqlCurr();
                final String dSym = arbitrageService.getBothOkexDsym();
                exLog.append("left").append(dSym).append("=").append(leftDql).append(",right").append(dSym).append("=").append(rightDql);
                BigDecimal leBest = arbitrageService.getbEbest();
                BigDecimal reBest = arbitrageService.getoEbest();
                String leftEBest = String.format("L_e_best%s_%s", leBest, arbitrageService.getbEbestUsd());
                String rightEBest = String.format(", R_e_best%s_%s", reBest, arbitrageService.getoEbestUsd());
                exLog.append(leftEBest).append(rightEBest);
                if (increaseOnBitmex || increaseOnOkex) {
                    if (leftDql != null && rightDql != null
                            && leftDql.subtract(rightDql).signum() != 0) {
                        // a) У обеих бирж DQL числовые значения (не na), тогда выбираем ту, где DQL выше (это будет биржа A, другая - B).
                        isFirstMarket = leftDql.subtract(rightDql).signum() > 0;
                    } else {
                        // b) Если хотя бы на одной из бирж DQL равен na или DQL числовые и равны, тогда сравниваем e_best_usd бирж. Там, где больше e_best_usd, та биржа A.
                        isFirstMarket = leBest.subtract(reBest).signum() >= 0;
                    }
                }
            }
            // <<< endOf Recovery_nt_usd_increase_pos (only button) UPDATE

            String extLogLabel = "RECOVERY_NTUSD: ";
            String exLogStr = exLog.toString();
            if (exLogStr.length() == 0) {
                exLogStr = predefinedMarketNameWithType != null
                        ? "auto(predefinedMarket)"
                        : "byPos";
            }
            if (isFirstMarket) {
                // bitmex buy
                posDiffService.defineCorrectAmountBitmex(corrObj, dc, cm, isEth, leftIsBtm);
                corrObj.marketService = arbitrageService.getLeftMarketService();
                if (bP.signum() >= 0) {
                    corrObj.signalType = SignalType.RECOVERY_NTUSD_INCREASE_POS;
                    extLogLabel = "RECOVERY_NTUSD_INCREASE_POS: ";
                }
            } else {
                // okcoin buy
                posDiffService.defineCorrectAmountOkex(corrObj, dc, isEth);
                corrObj.marketService = arbitrageService.getRightMarketService();
                if ((oPL.subtract(oPS)).signum() >= 0) {
                    corrObj.signalType = SignalType.RECOVERY_NTUSD_INCREASE_POS;
                    extLogLabel = "RECOVERY_NTUSD_INCREASE_POS: ";
                }
            }
            corrObj.marketService.getTradeLogger().info(extLogLabel + exLogStr);
        } else {
            corrObj.orderType = OrderType.ASK;
            final boolean isSecondMarketByPos = (bEquiv.compareTo(okEquiv) < 0 || btmSo) && !posDiffService.okexAmountIsZero(corrObj, dc, isEth);
            boolean isSecondMarket = predefinedMarketNameWithType != null
                    ? arbitrageService.getRightMarketService().getNameWithType().equals(predefinedMarketNameWithType)
                    : isSecondMarketByPos;

            // >>> Recovery_nt_usd_increase_pos (only button) UPDATE
            StringBuilder exLog = new StringBuilder();
            if (predefinedMarketNameWithType == null) { //is NOT Auto
                boolean isFirstMarket = !isSecondMarket;
                // sell when already pos-negative
                boolean increaseOnBitmex = isFirstMarket && (bitmexUsd.signum() < 0 || bitmexUsd.subtract(dc).signum() < 0); //bitmex sell, dc>0, включая переход через 0
                boolean increaseOnOkex = isSecondMarket && oPL.signum() == 0; // okex sell AND okex has no 'opened-long-pos'
                // 1. Сравниваем DQL двух бирж:
                BigDecimal leftDql = arbitrageService.getLeftMarketService().getLiqInfo().getDqlCurr();
                BigDecimal rightDql = arbitrageService.getRightMarketService().getLiqInfo().getDqlCurr();
                final String dSym = arbitrageService.getBothOkexDsym();
                exLog.append("left").append(dSym).append("=").append(leftDql).append(",right").append(dSym).append("=").append(rightDql);
                BigDecimal leBest = arbitrageService.getbEbest();
                BigDecimal reBest = arbitrageService.getoEbest();
                String leftEBest = String.format("L_e_best%s_%s", leBest, arbitrageService.getbEbestUsd());
                String rightEBest = String.format(", R_e_best%s_%s", reBest, arbitrageService.getoEbestUsd());
                exLog.append(leftEBest).append(rightEBest);
                if (increaseOnBitmex || increaseOnOkex) {
                    if (leftDql != null && rightDql != null
                            && leftDql.subtract(rightDql).signum() != 0) {
                        // a) У обеих бирж DQL числовые значения (не na), тогда выбираем ту, где DQL выше (это будет биржа A, другая - B).
                        isSecondMarket = !(leftDql.subtract(rightDql).signum() > 0);
                    } else {
                        // b) Если хотя бы на одной из бирж DQL равен na или DQL числовые и равны, тогда сравниваем e_best_usd бирж. Там, где больше e_best_usd, та биржа A.
                        isSecondMarket = !(leBest.subtract(reBest).signum() >= 0);
                    }
                }
            }
            // <<< endOf Recovery_nt_usd_increase_pos (only button) UPDATE

            String extLogLabel = "RECOVERY_NTUSD: ";
            String exLogStr = exLog.toString();
            if (exLogStr.length() == 0) {
                exLogStr = predefinedMarketNameWithType != null
                        ? "auto(predefinedMarket)"
                        : "byPos";
            }
            if (isSecondMarket) {
                // okcoin sell
                corrObj.marketService = arbitrageService.getRightMarketService();
                posDiffService.defineCorrectAmountOkex(corrObj, dc, isEth);
                if ((oPL.subtract(oPS)).signum() <= 0) {
                    corrObj.signalType = SignalType.RECOVERY_NTUSD_INCREASE_POS;
                    extLogLabel = "RECOVERY_NTUSD_INCREASE_POS: ";
                }
            } else {
                // bitmex sell
                corrObj.marketService = arbitrageService.getLeftMarketService();
                posDiffService.defineCorrectAmountBitmex(corrObj, dc, cm, isEth, leftIsBtm);
                if (bP.signum() <= 0) {
                    corrObj.signalType = SignalType.RECOVERY_NTUSD_INCREASE_POS;
                    extLogLabel = "RECOVERY_NTUSD_INCREASE_POS: ";
                }
            }
            corrObj.marketService.getTradeLogger().info(extLogLabel + exLogStr);
        }

        corrObj.contractType = corrObj.marketService != null ? corrObj.marketService.getContractType() : null;
    }


    public RecoveryStatus getRecoveryStatus() {

        return recoveryStatus.get();
    }

    public void resetRecoveryStatus() {
        if (placeOrderArgs != null) {
            placeOrderArgs.setShouldStopNtUsdRecovery(true);
        }
        recoveryStatus.set(RecoveryStatus.OFF);

    }

}
