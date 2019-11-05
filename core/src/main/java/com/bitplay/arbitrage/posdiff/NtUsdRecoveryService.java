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
import com.bitplay.persistance.domain.correction.CorrParams;
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

    final PosDiffService posDiffService;
    final SettingsRepositoryService settingsRepositoryService;
    final BitmexService bitmexService;
    final ArbitrageService arbitrageService;

    public void tryRecovery() {
        posDiffService.addPosDiffTask(() -> {
            try {
                tryRecoveryTask();
            } catch (Exception e) {
                warningLogger.error("recovery_nt_usd is failed." + e.getMessage());
                log.error("recovery_nt_usd is failed.", e);
            }
        });
    }

    private void tryRecoveryTask() {
        if (!arbitrageService.getFirstMarketService().isStarted() || posDiffService.marketsStopped()) {
            return;
        }
    }

    private void doRecovery() {
        posDiffService.stopTimerToImmediateCorrection(); // avoid double-correction

        BigDecimal ntUsd = posDiffService.getDcMainSet().setScale(2, RoundingMode.HALF_UP);

        final Settings s = settingsRepositoryService.getSettings();
        final BigDecimal cm = bitmexService.getCm();
        final boolean isEth = bitmexService.getContractType().isEth();
        final BigDecimal okexBlock = PlacingBlocks.toOkexCont(ntUsd, isEth);
        final Integer maxBlockUsd = s.getCorrParams().getRecoveryNtUsd().getMaxBlockUsd();
        BigDecimal maxBtm = PlacingBlocks.toBitmexContPure(BigDecimal.valueOf(maxBlockUsd), isEth, cm);
        BigDecimal maxOkex = PlacingBlocks.toBitmexContPure(BigDecimal.valueOf(maxBlockUsd), isEth, cm);

        BigDecimal bP = arbitrageService.getFirstMarketService().getPos().getPositionLong();
        final Pos secondPos = arbitrageService.getSecondMarketService().getPos();
        BigDecimal oPL = secondPos.getPositionLong();
        BigDecimal oPS = secondPos.getPositionShort();

        final PosDiffService.CorrObj corrObj = new PosDiffService.CorrObj(baseSignalType);

        // for logs
        String corrName = baseSignalType.getCounterName();

        } else if (baseSignalType == SignalType.ADJ) {
            final Borders minBorders;
            if (persistenceService.fetchBorders().getActiveVersion() == BorderParams.Ver.V1) {
                final GuiParams guiParams = arbitrageService.getParams();
                minBorders = new Borders(guiParams.getBorder1(), guiParams.getBorder2());
            } else {
                minBorders = bordersService.getMinBordersV2(bP, oPL, oPS);
            }
            if (minBorders != null) {
                adaptAdjByPos(corrObj, bP, oPL, oPS, dc, cm, isEth, minBorders, withLogs);
            } else {
                adaptCorrAdjByPos(corrObj, bP, oPL, oPS, hedgeAmount, dc, cm, isEth);
            }


            adaptCorrAdjByMaxVolCorrAndDql(corrObj, corrParams, dc, cm, isEth);

        } else { // corr
            maxBtm = corrParams.getCorr().getMaxVolCorrBitmex();
            maxOkex = corrParams.getCorr().getMaxVolCorrOkex();

            if (arbitrageService.getFirstMarketService().getMarketState() == MarketState.SYSTEM_OVERLOADED) {
                adaptCorrByPosOnBtmSo(corrObj, oPL, oPS, dc, isEth);
            } else {
                adaptCorrAdjByPos(corrObj, bP, oPL, oPS, hedgeAmount, dc, cm, isEth);
            }
            adaptCorrAdjByMaxVolCorrAndDql(corrObj, corrParams, dc, cm, isEth);

        } // end corr

        defineCorrectSignalType(corrObj, bP, oPL, oPS);

        final MarketService marketService = corrObj.marketService;
        final Order.OrderType orderType = corrObj.orderType;
        final BigDecimal correctAmount = corrObj.correctAmount;
        final SignalType signalType = corrObj.signalType;
        final ContractType contractType = corrObj.contractType;

        // 3. check DQL, correctAmount
        if (corrObj.errorDescription != null) { // DQL violation (open_min or close_min)
            countOnStartCorr(corrParams, signalType); // inc counters
            final String msg = String.format("No %s. %s", baseSignalType, corrObj.errorDescription);
            warningLogger.warn(msg);
            corrObj.marketService.getTradeLogger().warn(msg);
            slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, msg);
        } else if (correctAmount.signum() <= 0) {
            countOnStartCorr(corrParams, signalType); // inc counters
            final String msg = String.format("No %s: amount=%s, maxBtm=%s, maxOk=%s, dc=%s, btmPos=%s, okPos=%s, hedge=%s, signal=%s",
                    corrName,
                    correctAmount,
                    maxBtm, maxOkex, dc,
                    arbitrageService.getFirstMarketService().getPos().toString(),
                    arbitrageService.getSecondMarketService().getPos().toString(),
                    hedgeAmount.toPlainString(),
                    signalType
            );
            warningLogger.warn(msg);
            corrObj.marketService.getTradeLogger().warn(msg);
            slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY,
                    String.format("No %s: amount=%s", corrName, correctAmount));
        } else {

            final PlacingType placingType;
            if (!signalType.isAdj()) {
                placingType = PlacingType.TAKER; // correction is only taker
            } else {
                final PosAdjustment posAdjustment = settingsRepositoryService.getSettings().getPosAdjustment();
                placingType = (posAdjustment.getPosAdjustmentPlacingType() == PlacingType.TAKER_FOK && marketService.getName().equals(OkCoinService.NAME))
                        ? PlacingType.TAKER
                        : posAdjustment.getPosAdjustmentPlacingType();
            }

            if (outsideLimits(marketService, orderType, placingType, signalType)) {
                // do nothing
            } else {

                arbitrageService.setSignalType(signalType);

                // Market specific params
                final String counterName = marketService.getCounterNameNext(signalType);
                marketService.setBusy(counterName);

                final Long tradeId = arbitrageService.getLastTradeId();

                final String soMark = getSoMark(corrObj);
                final SignalTypeEx signalTypeEx = new SignalTypeEx(signalType, soMark);

                countOnStartCorr(corrParams, signalType);

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
                if (signalType.isMainSet() && tradeResponse.errorInsufficientFunds()) {
                    // switch the market
                    final String switchMsg = String.format("%s switch markets. %s INSUFFICIENT_BALANCE.", corrObj.signalType, corrObj.marketService.getName());
                    warningLogger.warn(switchMsg);
                    corrObj.marketService.getTradeLogger().info(switchMsg);
                    slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, switchMsg);

                    final MarketServicePreliq theOtherService = corrObj.marketService.getName().equals(BitmexService.NAME) ? okCoinService : bitmexService;
                    switchMarkets(corrObj, dc, cm, isEth, corrParams, theOtherService);
                    defineCorrectSignalType(corrObj, bP, oPL, oPS);
                    PlacingType pl = placingType == PlacingType.TAKER_FOK ? PlacingType.TAKER : placingType;
                    PlaceOrderArgs theOtherMarketArgs = PlaceOrderArgs.builder()
                            .orderType(corrObj.orderType)
                            .amount(corrObj.correctAmount)
                            .placingType(pl)
                            .signalType(corrObj.signalType)
                            .attempt(1)
                            .tradeId(tradeId)
                            .counterName(counterName)
                            .contractType(corrObj.contractType)
                            .build();
                    corrObj.marketService.getTradeLogger().info(message + theOtherMarketArgs.toString());
                    final TradeResponse theOtherResp = corrObj.marketService.placeOrder(theOtherMarketArgs);
                    if (theOtherResp.errorInsufficientFunds()) {
                        final String msg = String.format("No %s. INSUFFICIENT_BALANCE on both markets.", baseSignalType);
                        warningLogger.warn(msg);
                        corrObj.marketService.getTradeLogger().warn(msg);
                        slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, message);
                    }
                }
                corrObj.marketService.getArbitrageService().setBusyStackChecker();

                slackNotifications.sendNotify(signalType.isAdj() ? NotifyType.ADJ_NOTIFY : NotifyType.CORR_NOTIFY, message);
                log.info(message);

            }
        }

    }

    @ToString
    private class CorrObj {

        CorrObj(SignalType signalType) {
            this.signalType = signalType;
        }

        SignalType signalType;
        Order.OrderType orderType;
        BigDecimal correctAmount;
        MarketServicePreliq marketService;
        ContractType contractType;
        String errorDescription;
    }

}
