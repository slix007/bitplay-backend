package com.bitplay.arbitrage.posdiff;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.arbitrage.dto.SignalTypeEx;
import com.bitplay.external.NotifyType;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.model.PlaceOrderArgs;
import com.bitplay.market.model.TradeResponse;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.model.Pos;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.settings.ContractType;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.persistance.domain.settings.PosAdjustment;
import com.bitplay.persistance.domain.settings.Settings;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
//@NoArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__(@JsonCreator))
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Slf4j
public class NtUsdRecoveryService {
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private final NtUsdExecutor ntUsdExecutor;
    private final PosDiffService posDiffService;
    private final SettingsRepositoryService settingsRepositoryService;
    private final BitmexService bitmexService;
    private final ArbitrageService arbitrageService;

    public void tryRecovery() {
        ntUsdExecutor.addTask(() -> {
            try {
                tryRecoveryTask();
            } catch (Exception e) {
                warningLogger.error("recovery_nt_usd is failed." + e.getMessage());
                log.error("recovery_nt_usd is failed.", e);
            }
        });
    }

    private void tryRecoveryTask() {
//        if (!arbitrageService.getFirstMarketService().isStarted() || posDiffService.marketsStopped()) {
//            return;
//        }
        doRecovery();
    }

    private void doRecovery() {
        posDiffService.stopTimerToImmediateCorrection(); // avoid double-correction

        BigDecimal ntUsd = posDiffService.getDcMainSet().setScale(2, RoundingMode.HALF_UP);

        final Settings s = settingsRepositoryService.getSettings();
        final BigDecimal cm = bitmexService.getCm();
        final boolean isEth = bitmexService.getContractType().isEth();
//        final BigDecimal okexBlock = PlacingBlocks.toOkexCont(ntUsd, isEth);
//        final BigDecimal btmBlock = PlacingBlocks.toBitmexCont(ntUsd, isEth, cm);
        final Integer maxBlockUsd = s.getCorrParams().getRecoveryNtUsd().getMaxBlockUsd();
        BigDecimal maxBtm = PlacingBlocks.toBitmexCont(BigDecimal.valueOf(maxBlockUsd), isEth, cm);
        BigDecimal maxOk = PlacingBlocks.toOkexCont(BigDecimal.valueOf(maxBlockUsd), isEth);

        BigDecimal bP = arbitrageService.getFirstMarketService().getPos().getPositionLong();
        final Pos secondPos = arbitrageService.getSecondMarketService().getPos();
        BigDecimal oPL = secondPos.getPositionLong();
        BigDecimal oPS = secondPos.getPositionShort();

        final CorrObj corrObj = new CorrObj(SignalType.RECOVERY_NTUSD);

        // for logs
        String corrName = "recovery_nt_usd";

        final BigDecimal hedgeAmount = posDiffService.getHedgeAmountMainSet();
        final BigDecimal dc = posDiffService.getDcMainSet().setScale(2, RoundingMode.HALF_UP);

        adaptCorrAdjByPos(corrObj, bP, oPL, oPS, hedgeAmount, dc, cm, isEth);
        posDiffService.adaptCorrAdjByMaxVolCorrAndDql(corrObj, maxBtm, maxOk, dc, cm, isEth);

        final MarketService marketService = corrObj.marketService;
        final Order.OrderType orderType = corrObj.orderType;
        final BigDecimal correctAmount = corrObj.correctAmount;
        final SignalType signalType = corrObj.signalType;
        final ContractType contractType = corrObj.contractType;

        // 3. check DQL, correctAmount
        if (corrObj.errorDescription != null) { // DQL violation (open_min or close_min)
            final String msg = String.format("No %s. %s", corrName, corrObj.errorDescription);
            warningLogger.warn(msg);
            corrObj.marketService.getTradeLogger().warn(msg);
            log.warn(msg);
        } else if (correctAmount.signum() <= 0) {
            final String msg = String.format("No %s: amount=%s, maxBtm=%s, maxOk=%s, dc=%s, btmPos=%s, okPos=%s, hedge=%s, signal=%s",
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
        } else {
            final PlacingType placingType = PlacingType.TAKER;

            // TODO confirm with Maxim
            if (posDiffService.outsideLimits(marketService, orderType, placingType, signalType)) {
                // do nothing
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
            } else {

                arbitrageService.setSignalType(signalType);

                // Market specific params
                final String counterName = marketService.getCounterNameNext(signalType);
                marketService.setBusy(counterName);

                final Long tradeId = arbitrageService.getLastTradeId();

                final String soMark = getSoMark(corrObj);
                final SignalTypeEx signalTypeEx = new SignalTypeEx(signalType, soMark);


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

                // todo ask Maxkim
                if (signalType.isMainSet() && tradeResponse.errorInsufficientFunds()) {
                    // switch the market
//                    final String switchMsg = String.format("%s switch markets. %s INSUFFICIENT_BALANCE.", corrObj.signalType, corrObj.marketService.getName());
//                    warningLogger.warn(switchMsg);
//                    corrObj.marketService.getTradeLogger().info(switchMsg);
////                    slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, switchMsg);
//
//                    final MarketServicePreliq theOtherService = corrObj.marketService.getName().equals(BitmexService.NAME) ? okCoinService : bitmexService;
//                    switchMarkets(corrObj, dc, cm, isEth, corrParams, theOtherService);
//                    defineCorrectSignalType(corrObj, bP, oPL, oPS);
//                    PlacingType pl = placingType == PlacingType.TAKER_FOK ? PlacingType.TAKER : placingType;
//                    PlaceOrderArgs theOtherMarketArgs = PlaceOrderArgs.builder()
//                            .orderType(corrObj.orderType)
//                            .amount(corrObj.correctAmount)
//                            .placingType(pl)
//                            .signalType(corrObj.signalType)
//                            .attempt(1)
//                            .tradeId(tradeId)
//                            .counterName(counterName)
//                            .contractType(corrObj.contractType)
//                            .build();
//                    corrObj.marketService.getTradeLogger().info(message + theOtherMarketArgs.toString());
//                    final TradeResponse theOtherResp = corrObj.marketService.placeOrder(theOtherMarketArgs);
//                    if (theOtherResp.errorInsufficientFunds()) {
//                        final String msg = String.format("No %s. INSUFFICIENT_BALANCE on both markets.", baseSignalType);
//                        warningLogger.warn(msg);
//                        corrObj.marketService.getTradeLogger().warn(msg);
//                        slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, message);
//                    }
                }
//                corrObj.marketService.getArbitrageService().setBusyStackChecker();

//                slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, message);
                log.info(message);

            }
        }

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
                posDiffService.defineCorrectAmountBitmex(corrObj, dc, cm, isEth);
                corrObj.marketService = arbitrageService.getFirstMarketService();
                if (bP.signum() >= 0) {
                    corrObj.setIncreasePos(true);
                }
            } else {
                // okcoin buy
                posDiffService.defineCorrectAmountOkex(corrObj, dc, isEth);
                if (oPS.signum() > 0 && oPS.subtract(corrObj.correctAmount).signum() < 0) { // orderType==CLOSE_ASK
                    corrObj.correctAmount = oPS;
                }
                corrObj.marketService = arbitrageService.getSecondMarketService();
                if ((oPL.subtract(oPS)).signum() >= 0) {
                    corrObj.setIncreasePos(true);
                }
            }
        } else {
            corrObj.orderType = Order.OrderType.ASK;
            if (bEquiv.compareTo(okEquiv) < 0) {
                // okcoin sell
                posDiffService.defineCorrectAmountOkex(corrObj, dc, isEth);
                if (oPL.signum() > 0 && oPL.subtract(corrObj.correctAmount).signum() < 0) { // orderType==CLOSE_BID
                    corrObj.correctAmount = oPL;
                }
                if ((oPL.subtract(oPS)).signum() <= 0) {
                    corrObj.setIncreasePos(true);
                }
            corrObj.marketService = arbitrageService.getSecondMarketService();
            } else {
                // bitmex sell
                posDiffService.defineCorrectAmountBitmex(corrObj, dc, cm, isEth);
                corrObj.marketService = arbitrageService.getFirstMarketService();
                if (bP.signum() <= 0) {
                    corrObj.setIncreasePos(true);
                }
            }
        }

        corrObj.contractType = corrObj.marketService != null ? corrObj.marketService.getContractType() : null;
    }


}
