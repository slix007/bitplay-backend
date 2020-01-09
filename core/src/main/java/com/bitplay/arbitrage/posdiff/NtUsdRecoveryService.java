package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.dto.SignalTypeEx;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.bitmex.BitmexService;
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
    private final OkCoinService okCoinService;
    private final BitmexService bitmexService;
    private final ArbitrageService arbitrageService;
    private final TradeService tradeService;

    public Future<String> tryRecovery() {
        return ntUsdExecutor.runTask(() -> {
            try {
                RecoveryResult recoveryResult = doRecovery(false);
                return recoveryResult.details;
            } catch (Exception e) {
                log.error("recovery_nt_usd is failed.", e);
                final String msg = "recovery_nt_usd is failed." + e.getMessage();
                warningLogger.error(msg);
                return msg;
            }
        });
    }

    public Future<String> tryRecoveryAfterKillPos() {
        return ntUsdExecutor.runTask(() -> {
            try {
                RecoveryResult r1 = doRecovery(true);
                String resDetails = r1.details;
                if (r1.okexThroughZero) {
                    RecoveryResult r2 = doRecovery(true);
                    resDetails += "; Second: " + r2.details;
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
    class RecoveryResult {
        private final String details;
        private final Boolean okexThroughZero;
    }

    @SuppressWarnings("Duplicates")
    private RecoveryResult doRecovery(boolean afterKillpos) {
        posDiffService.stopTimerToImmediateCorrection(); // avoid double-correction

        arbitrageService.getFirstMarketService().stopAllActions("recovery-nt-usd:stopAllActions");
        arbitrageService.getSecondMarketService().stopAllActions("recovery-nt-usd:stopAllActions");
        arbitrageService.resetArbState("recovery-nt-usd");

        BigDecimal dc = posDiffService.getDcMainSet().setScale(2, RoundingMode.HALF_UP);

        final BigDecimal cm = bitmexService.getCm();
        final boolean isEth = bitmexService.getContractType().isEth();
        final CorrParams corrParams = persistenceService.fetchCorrParams();
        final int maxBlockUsd = afterKillpos ? Integer.MAX_VALUE : corrParams.getRecoveryNtUsd().getMaxBlockUsd();
        BigDecimal maxBtm = PlacingBlocks.toBitmexContPure(BigDecimal.valueOf(maxBlockUsd), isEth, cm);
        BigDecimal maxOk = PlacingBlocks.toOkexCont(BigDecimal.valueOf(maxBlockUsd), isEth);

        BigDecimal bP = arbitrageService.getFirstMarketService().getPos().getPositionLong();
        final Pos secondPos = arbitrageService.getSecondMarketService().getPos();
        BigDecimal oPL = secondPos.getPositionLong();
        BigDecimal oPS = secondPos.getPositionShort();

        final CorrObj corrObj = new CorrObj(SignalType.RECOVERY_NTUSD, oPL, oPS);

        final BigDecimal hedgeAmount = posDiffService.getHedgeAmountMainSet();

        final boolean btmSo = arbitrageService.getFirstMarketService().getMarketState() == MarketState.SYSTEM_OVERLOADED;
        adaptCorrAdjByPos(corrObj, bP, oPL, oPS, hedgeAmount, dc, cm, isEth, btmSo);

        posDiffService.adaptCorrAdjByMaxVolCorrAndDql(corrObj, maxBtm, maxOk, dc, cm, isEth);

        posDiffService.defineSignalTypeToIncrease(corrObj, bP, oPL, oPS);

        final MarketService marketService = corrObj.marketService;
        final OrderType orderType = corrObj.orderType;
        final BigDecimal correctAmount = corrObj.correctAmount;
        final SignalType signalType = corrObj.signalType;
        final ContractType contractType = corrObj.contractType;

        String corrName = "recovery_nt_usd";
        String corrNameWithMarket = corrName + " on " + marketService.getName();

        String resultMsg = "";
        if (corrObj.errorDescription != null) { // DQL violation (open_min or close_min)
            resultMsg = String.format("No %s. %s.", corrNameWithMarket, corrObj.errorDescription);
            warningLogger.warn(resultMsg);
            corrObj.marketService.getTradeLogger().warn(resultMsg);
            log.warn(resultMsg);
        } else if (correctAmount.signum() <= 0) {
            resultMsg = String.format("No %s: amount=%s, maxBtm=%s, maxOk=%s, dc=%s, btmPos=%s, okPos=%s, hedge=%s, signal=%s",
                    corrNameWithMarket,
                    correctAmount,
                    maxBtm, maxOk, dc,
                    arbitrageService.getFirstMarketService().getPos().toString(),
                    arbitrageService.getSecondMarketService().getPos().toString(),
                    hedgeAmount.toPlainString(),
                    signalType
            );
            warningLogger.warn(resultMsg);
            corrObj.marketService.getTradeLogger().warn(resultMsg);
            log.warn(resultMsg);
        } else {
            final PlacingType placingType = PlacingType.TAKER;
            final String counterName = signalType.getCounterName();

            final Long tradeId = arbitrageService.getLastTradeId();

            final String soMark = (corrObj.marketService.getName().equals(OkCoinService.NAME)
                    && corrObj.signalType.isIncreasePos()
                    && arbitrageService.getFirstMarketService().getMarketState() == MarketState.SYSTEM_OVERLOADED)
                    ? "_SO" : "";
            final SignalTypeEx signalTypeEx = new SignalTypeEx(signalType, soMark);

            final String message = String.format("#%s %s %s amount=%s c=%s. recovery_nt_usd maxBlock=%s ",
                    counterName, placingType, orderType, correctAmount, contractType, maxBlockUsd);

            final boolean outsideLimits = checkOutsideLimits(corrNameWithMarket, dc, maxBtm, maxOk, corrObj, hedgeAmount,
                            marketService, orderType, correctAmount, signalType, placingType);
            if (outsideLimits) {
                resultMsg = switchMarkets(resultMsg, corrName, dc, cm, isEth, maxBtm, maxOk, bP, oPL, oPS, corrObj,
                        placingType, counterName, tradeId, message, hedgeAmount, signalType);
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

                if (tradeResponse.errorInsufficientFunds()) {
                    marketService.setMarketState(MarketState.READY);
                    resultMsg = switchMarkets(resultMsg, corrName, dc, cm, isEth, maxBtm, maxOk, bP, oPL, oPS, corrObj,
                            placingType, counterName, tradeId, message, hedgeAmount, signalType);
                } else {
                    resultMsg += parseResMsg(tradeResponse);
                    corrObj.marketService.getArbitrageService().setBusyStackChecker();
                }
            }
        }

        return new RecoveryResult(resultMsg, corrObj.okexThroughZero);
    }

    private boolean checkOutsideLimits(String corrName, BigDecimal dc, BigDecimal maxBtm, BigDecimal maxOk, CorrObj corrObj, BigDecimal hedgeAmount,
                                       MarketService marketService, OrderType orderType, BigDecimal correctAmount, SignalType signalType,
                                       PlacingType placingType) {
        final boolean outsideLimits = posDiffService.outsideLimits(marketService, orderType, placingType, signalType);
        if (outsideLimits) {
            final String msg = String.format("outsideLimits. No %s: amount=%s, maxBtm=%s, maxOk=%s, dc=%s, btmPos=%s, okPos=%s, hedge=%s, signal=%s",
                    corrName,
                    correctAmount,
                    maxBtm, maxOk, dc,
                    arbitrageService.getFirstMarketService().getPos().toString(),
                    arbitrageService.getSecondMarketService().getPos().toString(),
                    hedgeAmount.toPlainString(),
                    signalType
            );
            warningLogger.warn(msg);
            corrObj.marketService.getTradeLogger().warn(msg);
            log.warn(msg);
        }
        return outsideLimits;
    }

    private String switchMarkets(String resultMsg, String corrName, BigDecimal dc, BigDecimal cm, boolean isEth, BigDecimal maxBtm, BigDecimal maxOk,
                                 BigDecimal bP, BigDecimal oPL, BigDecimal oPS, CorrObj corrObj, PlacingType placingType, String counterName, Long tradeId,
                                 String message, BigDecimal hedgeAmount, SignalType signalType) {
        // switch the market
        final String switchMsg = String.format("%s switch markets. %s INSUFFICIENT_BALANCE. ", corrObj.signalType, corrObj.marketService.getName());
        warningLogger.warn(switchMsg);
        corrObj.marketService.getTradeLogger().info(switchMsg);
        log.info(switchMsg);
        resultMsg += switchMsg;
//
        final MarketServicePreliq theOtherService = corrObj.marketService.getName().equals(BitmexService.NAME)
                ? okCoinService : bitmexService;
        posDiffService.switchMarkets(corrObj, dc, cm, isEth, maxBtm, maxOk, theOtherService);
        posDiffService.defineSignalTypeToIncrease(corrObj, bP, oPL, oPS);
        adaptCorrByDqlAfterSwitch(corrObj);

        final String corrNameWithMarket = corrName + " on " + theOtherService.getName();

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
     * marketService<br>
     * orderType<br>
     * marketService<br>
     * correctAmount<br>
     * contractType<br>
     */
    @SuppressWarnings("Duplicates")
    private void adaptCorrAdjByPos(final CorrObj corrObj, final BigDecimal bP, final BigDecimal oPL, final BigDecimal oPS, final BigDecimal hedgeAmount,
                                   final BigDecimal dc, final BigDecimal cm, final boolean isEth, boolean btmSo) {

        final BigDecimal okexUsd = isEth
                ? (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(10))
                : (oPL.subtract(oPS)).multiply(BigDecimal.valueOf(100));

        final BigDecimal bitmexUsd = isEth
                ? bP.multiply(BigDecimal.valueOf(10)).divide(cm, 2, RoundingMode.HALF_UP)
                : bP;

        //noinspection UnnecessaryLocalVariable
        final BigDecimal okEquiv = okexUsd;
        final BigDecimal bEquiv = bitmexUsd.subtract(hedgeAmount);

        if (dc.signum() < 0) {
            corrObj.orderType = OrderType.BID;
            if ((bEquiv.compareTo(okEquiv) < 0 && !btmSo)
                    || posDiffService.okexAmountIsZero(corrObj, dc, isEth)) {
                // bitmex buy
                posDiffService.defineCorrectAmountBitmex(corrObj, dc, cm, isEth);
                corrObj.marketService = arbitrageService.getFirstMarketService();
                if (bP.signum() >= 0) {
                    corrObj.signalType = SignalType.RECOVERY_NTUSD_INCREASE_POS;
                }
            } else {
                // okcoin buy
                posDiffService.defineCorrectAmountOkex(corrObj, dc, isEth);
                posDiffService.defineOkexThroughZero(corrObj);
                corrObj.marketService = arbitrageService.getSecondMarketService();
                if ((oPL.subtract(oPS)).signum() >= 0) {
                    corrObj.signalType = SignalType.RECOVERY_NTUSD_INCREASE_POS;
                }
            }
        } else {
            corrObj.orderType = OrderType.ASK;
            if ((bEquiv.compareTo(okEquiv) < 0 || btmSo)
                    && !posDiffService.okexAmountIsZero(corrObj, dc, isEth)) {
                // okcoin sell
                posDiffService.defineCorrectAmountOkex(corrObj, dc, isEth);
                posDiffService.defineOkexThroughZero(corrObj);
                if ((oPL.subtract(oPS)).signum() <= 0) {
                    corrObj.signalType = SignalType.RECOVERY_NTUSD_INCREASE_POS;
                }
                corrObj.marketService = arbitrageService.getSecondMarketService();
            } else {
                // bitmex sell
                posDiffService.defineCorrectAmountBitmex(corrObj, dc, cm, isEth);
                corrObj.marketService = arbitrageService.getFirstMarketService();
                if (bP.signum() <= 0) {
                    corrObj.signalType = SignalType.RECOVERY_NTUSD_INCREASE_POS;
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
