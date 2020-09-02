package com.bitplay.api.service;

import com.bitplay.api.dto.BorderUpdateJson;
import com.bitplay.api.dto.DeltalUpdateJson;
import com.bitplay.api.dto.DeltasJson;
import com.bitplay.api.dto.DeltasMinMaxJson;
import com.bitplay.api.dto.DeltasMinMaxJson.MinMaxData;
import com.bitplay.api.dto.DeltasMinMaxJson.SignalData;
import com.bitplay.api.dto.MarketFlagsJson;
import com.bitplay.api.dto.PosCorrJson;
import com.bitplay.api.dto.ResultJson;
import com.bitplay.api.dto.SumBalJson;
import com.bitplay.api.dto.TimersJson;
import com.bitplay.api.dto.TradeLogJson;
import com.bitplay.api.dto.pos.PosDiffJson;
import com.bitplay.api.dto.states.DelayTimerBuilder;
import com.bitplay.api.dto.states.DelayTimerJson;
import com.bitplay.api.dto.states.MarketStatesJson;
import com.bitplay.api.dto.states.OkexFtpdJson;
import com.bitplay.api.dto.states.OrderPortionsJson;
import com.bitplay.api.dto.states.SignalPartsJson;
import com.bitplay.api.dto.states.SignalPartsJson.Status;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BordersCalcScheduler;
import com.bitplay.arbitrage.BordersService;
import com.bitplay.arbitrage.DeltaMinService;
import com.bitplay.arbitrage.DeltasCalcService;
import com.bitplay.arbitrage.VolatileModeSwitcherService;
import com.bitplay.arbitrage.dto.ArbType;
import com.bitplay.arbitrage.dto.DelayTimer;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.arbitrage.posdiff.DqlStateService;
import com.bitplay.arbitrage.posdiff.NtUsdRecoveryService;
import com.bitplay.arbitrage.posdiff.PosDiffService;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.BtsEventBox;
import com.bitplay.market.model.ArbState;
import com.bitplay.market.model.DqlState;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.market.okcoin.OkexFtpdService;
import com.bitplay.market.okcoin.OkexSettlementService;
import com.bitplay.persistance.CumPersistenceService;
import com.bitplay.persistance.LastPriceDeviationService;
import com.bitplay.persistance.MonitoringDataService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.SignalTimeService;
import com.bitplay.persistance.domain.CumParams;
import com.bitplay.persistance.domain.DeltaParams;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.LastPriceDeviation;
import com.bitplay.persistance.domain.SignalTimeParams;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.mon.MonObTimestamp;
import com.bitplay.persistance.domain.mon.MonRestart;
import com.bitplay.persistance.domain.settings.OkexFtpd;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.security.TraderPermissionsService;
import com.bitplay.settings.BitmexChangeOnSoService;
import com.bitplay.settings.TradingModeService;
import com.bitplay.utils.Utils;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order.OrderType;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 4/17/17.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommonUIService {

    private final ArbitrageService arbitrageService;
    private final DqlStateService dqlStateService;
    private final TraderPermissionsService traderPermissionsService;
    private final DeltasCalcService deltasCalcService;
    private final BordersCalcScheduler bordersCalcScheduler;
    private final SettingsRepositoryService settingsRepositoryService;
    private final PersistenceService persistenceService;
    private final CumPersistenceService cumPersistenceService;
    private final BordersService bordersService;
    private final DeltaMinService deltaMinService;
    private final PosDiffService posDiffService;
    private final MonitoringDataService monitoringDataService;
    private final SignalTimeService signalTimeService;
    private final LastPriceDeviationService lastPriceDeviationService;
    private final TradingModeService tradingModeService;
    private final VolatileModeSwitcherService volatileModeSwitcherService;
    private final BitmexChangeOnSoService bitmexChangeOnSoService;
    private final OkexSettlementService okexSettlementService;
    private final NtUsdRecoveryService ntUsdRecoveryService;

    public TradeLogJson getTradeLog(String marketName, String date) {
        String logName;
        if (date == null || date.trim().length() == 0) {
            logName = String.format("./logs/%s-trades.log", marketName);
        } else {
            logName = String.format("./logs/%s-trades.%s.log", marketName, date);
        }
        return getTradeLogJson(logName);
    }

    public TradeLogJson getDeltasLog(String date) {
        String logName;
        if (date == null || date.trim().length() == 0) {
            logName = "./logs/deltas.log";
        } else {
            logName = String.format("./logs/deltas.%s.log", date);
        }
        return getTradeLogJson(logName);
    }

    public TradeLogJson getWarningLog(String date) {
        String logName;
        if (date == null || date.trim().length() == 0) {
            logName = "./logs/warning.log";
        } else {
            logName = String.format("./logs/warning.%s.log", date);
        }
        return getTradeLogJson(logName);
    }

    public TradeLogJson getDebugLog(String date) {
        String logName;
        if (date == null || date.trim().length() == 0) {
            logName = "./logs/debug.log";
        } else {
            logName = String.format("./logs/debug.%s.log", date);
        }
        return getTradeLogJson(logName);
    }


    private TradeLogJson getTradeLogJson(String fileName) {
        TradeLogJson tradeLogJson = null;
        try {
            final List<String> allLines = Files.readAllLines(Paths.get(fileName));

            tradeLogJson = new TradeLogJson(allLines);

        } catch (NoSuchFileException e) {
            //do nothing
        } catch (IOException e) {
            e.printStackTrace();
        }

        return tradeLogJson;
    }

    public DeltasJson getDeltas() {
        return convertToDeltasJson();
    }

    public DeltasJson updateBorders(BorderUpdateJson borderUpdateJson) {
        if (borderUpdateJson.getBorder1() != null) {
            arbitrageService.getParams().setBorder1(new BigDecimal(borderUpdateJson.getBorder1()));
        }
        if (borderUpdateJson.getBorder2() != null) {
            arbitrageService.getParams().setBorder2(new BigDecimal(borderUpdateJson.getBorder2()));
        }
        arbitrageService.saveParamsToDb();

        persistenceService.resetSettingsPreset();

        return convertToDeltasJson();
    }

    //TODO move some params from Deltas to separate Data Object
    public DeltasJson updateMakerDelta(DeltalUpdateJson deltalUpdateJson) {
        GuiParams guiParams = arbitrageService.getParams();
        if (deltalUpdateJson.getReserveBtc1() != null) {
            guiParams.setReserveBtc1(new BigDecimal(deltalUpdateJson.getReserveBtc1()));
            persistenceService.resetSettingsPreset();
        }
        if (deltalUpdateJson.getReserveBtc2() != null) {
            guiParams.setReserveBtc2(new BigDecimal(deltalUpdateJson.getReserveBtc2()));
            persistenceService.resetSettingsPreset();
        }
        if (deltalUpdateJson.getFundingRateFee() != null) {
            guiParams.setFundingRateFee(new BigDecimal(deltalUpdateJson.getFundingRateFee()));
            persistenceService.resetSettingsPreset();
        }
        arbitrageService.saveParamsToDb();

        return convertToDeltasJson();
    }

    private DeltasJson convertToDeltasJson() {
        final BorderParams borderParams = persistenceService.fetchBorders();
        final Integer delta_hist_per = borderParams.getBorderDelta().getDeltaCalcPast();
        final String deltaSmaUpdateIn = deltasCalcService.getDeltaSmaUpdateIn(delta_hist_per);
        final GuiParams guiParams = arbitrageService.getParams();
        final String delta1Sma = deltasCalcService.getB_delta_sma().toPlainString();
        final String delta2Sma = deltasCalcService.getO_delta_sma().toPlainString();
        final String delta1MinInstant = deltaMinService.getBtmDeltaMinInstant().toPlainString();
        final String delta2MinInstant = deltaMinService.getOkDeltaMinInstant().toPlainString();
        final String delta1MinFixed = deltaMinService.getBtmDeltaMinFixed().toPlainString();
        final String delta2MinFixed = deltaMinService.getOkDeltaMinFixed().toPlainString();
        final LastPriceDeviation lastPriceDeviation = persistenceService.fetchLastPriceDeviation();

        return new DeltasJson(
                arbitrageService.getDelta1().toPlainString(),
                arbitrageService.getDelta2().toPlainString(),
                arbitrageService.getBorder1().toPlainString(),
                arbitrageService.getBorder2().toPlainString(),
                guiParams.getReserveBtc1().toPlainString(),
                guiParams.getReserveBtc2().toPlainString(),
                "deprecated. use settings",
                guiParams.getFundingRateFee().toPlainString(),
                delta1Sma,
                delta2Sma,
                delta1MinInstant,
                delta2MinInstant,
                delta1MinFixed,
                delta2MinFixed,
                deltasCalcService.getBDeltaEveryCalc(),
                deltasCalcService.getODeltaEveryCalc(),
                deltasCalcService.getDeltaHistPerStartedSec(),
                deltaSmaUpdateIn,
                lastPriceDeviation
        );
    }

    private boolean isInitialized() {
        return arbitrageService.getLeftMarketService() != null && arbitrageService.getRightMarketService() != null;
    }

    public MarketFlagsJson getStopMoving() {
        if (!isInitialized()) {
            return new MarketFlagsJson(null, null);
        }
        return new MarketFlagsJson(
                arbitrageService.getLeftMarketService().getMovingStop(),
                arbitrageService.getRightMarketService().getMovingStop()
        );
    }

    public MarketFlagsJson toggleStopMoving() {
        if (!isInitialized()) {
            return new MarketFlagsJson(null, null);
        }
        arbitrageService.getLeftMarketService().setMovingStop(!arbitrageService.getLeftMarketService().getMovingStop());
        arbitrageService.getRightMarketService().setMovingStop(!arbitrageService.getRightMarketService().getMovingStop());
        return new MarketFlagsJson(
                arbitrageService.getLeftMarketService().getMovingStop(),
                arbitrageService.getRightMarketService().getMovingStop()
        );
    }

    public MarketStatesJson getMarketsStates() {
        if (!isInitialized()) {
            return new MarketStatesJson();
        }
        final ArbState arbState = arbitrageService.getArbState();
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        final MarketServicePreliq right = arbitrageService.getRightMarketService();
        final MarketState btmState = left.getMarketState();
        final MarketState okState = right.getMarketState();

        boolean reconnectInProgress = left.isReconnectInProgress();
        String btmReconnectState = reconnectInProgress ? "IN_PROGRESS" : "NONE";

        DelayTimerJson corrDelay = getCorrDelay();
        DelayTimerJson posAdjustmentDelay = getPosAdjustmentDelay();
        DelayTimerJson preliqDelay = getPreliqDelay();
        DelayTimerJson killposDelay = getKillposDelay();

        final String timeToSignal = arbitrageService.getTimeToSignal();

        // SignalPartsJson
        final SignalPartsJson signalPartsJson = new SignalPartsJson();
        signalPartsJson.setSignalDelay(timeToSignal.equals("_ready_") ? Status.OK : (timeToSignal.equals("_none_") ? Status.WRONG : Status.STARTED));
        signalPartsJson.setBtmMaxDelta(arbitrageService.isMaxDeltaViolated(DeltaName.B_DELTA) ? Status.WRONG : Status.OK);
        signalPartsJson.setOkMaxDelta(arbitrageService.isMaxDeltaViolated(DeltaName.O_DELTA) ? Status.WRONG : Status.OK);
        final PosDiffJson posDiff = getPosDiff();
        signalPartsJson.setNtUsd(posDiff.isMainSetEqual() && posDiff.isExtraSetEqual() ? Status.OK : Status.WRONG);
        signalPartsJson.setStates(arbState == ArbState.READY && btmState == MarketState.READY && okState == MarketState.READY ? Status.OK : Status.WRONG);
        final BigDecimal posBtm = left.getPosVal();
        final BigDecimal posOk = right.getPosVal();
        signalPartsJson.setBtmDqlOpen(getDqlOpenStatus(left, posBtm));
        signalPartsJson.setOkDqlOpen(getDqlOpenStatus(right, posOk));
        setAffordableStatus(signalPartsJson);
        final boolean btmLimOut = left.getLimitsService().outsideLimits();
        final DeltaName signalStatusDelta = arbitrageService.getSignalStatusDelta();
        boolean okLimOut = right.getLimitsService().outsideLimits();

        signalPartsJson.setDeltaName(signalStatusDelta == null ? "_" : signalStatusDelta.getDeltaSymbol());

        signalPartsJson.setPriceLimits(!btmLimOut && !okLimOut ? Status.OK : Status.WRONG);

        signalPartsJson.setBtmOrderBook(Utils.isObOk(left.getOrderBook()) ? Status.OK : Status.WRONG);
        signalPartsJson.setBtmOrderBookXBTUSD(Utils.isObOk(left.getOrderBookXBTUSD()) ? Status.OK : Status.WRONG);
        signalPartsJson.setOkOrderBook(Utils.isObOk(right.getOrderBook()) ? Status.OK : Status.WRONG);
        signalPartsJson.setObTsDiffs(arbitrageService.getIsObTsViolated() ? Status.WRONG : Status.OK);

        final OrderPortionsJson orderPortionsJson = new OrderPortionsJson(left.getPortionsProgressForUi(), right.getPortionsProgressForUi());

        final DqlState dqlState = dqlStateService.getCommonDqlState();
        final Settings settings = settingsRepositoryService.getSettings();
        final OkexFtpd leftFtpd = settings.getAllFtpd().get(ArbType.LEFT);
        final OkexFtpd rightFtpd = settings.getAllFtpd().get(ArbType.RIGHT);
        final OkexFtpdJson leftFtpdJson;
        if (left.isBtm()) {
            leftFtpdJson = new OkexFtpdJson(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        } else {
            final OkexFtpdService leftFtpdService = ((OkCoinService) left).getOkexFtpdService();
            leftFtpdJson = new OkexFtpdJson(leftFtpd.getOkexFtpdBod(), leftFtpdService.getBodMax(), leftFtpdService.getBodMin());
        }
        final OkexFtpdService rightFtpdService = ((OkCoinService) right).getOkexFtpdService();
        final OkexFtpdJson rightFtpdJson = new OkexFtpdJson(rightFtpd.getOkexFtpdBod(), rightFtpdService.getBodMax(), rightFtpdService.getBodMin());

        @SuppressWarnings("DuplicatedCode") final MonObTimestamp lt = left.getMonObTimestamp();
        final String leftObTimestampDiff = lt == null ? "" : String.format("%s/%s", lt.getMinObDiff(), lt.getMaxObDiff());
        final String leftGetObTimestamps = lt == null ? "" : String.format("%s/%s", lt.getMinGetOb(), lt.getMaxGetOb());
        final MonObTimestamp rt = right.getMonObTimestamp();
        final String rightObTimestampDiff = rt == null ? "" : String.format("%s/%s", rt.getMinObDiff(), rt.getMaxObDiff());
        final String rightGetObTimestamps = rt == null ? "" : String.format("%s/%s", rt.getMinGetOb(), rt.getMaxGetOb());

        return new MarketStatesJson(
                btmState.toString(),
                okState.toString(),
                left.getTimeToReset(),
                right.getTimeToReset(),
                String.valueOf(settings.getSignalDelayMs()),
                timeToSignal,
                tradingModeService.secToReset(),
                volatileModeSwitcherService.timeToVolatileMode(),
                bitmexChangeOnSoService.getSecToReset(),
                arbState.toString(),
                btmReconnectState,
                corrDelay,
                posAdjustmentDelay,
                preliqDelay,
                killposDelay,
                signalPartsJson,
                posDiff,
                orderPortionsJson,
                okexSettlementService.isSettlementMode(),
                LocalTime.now().toString(),
                dqlState,
                traderPermissionsService.getSebestStatus(),
                leftFtpdJson,
                rightFtpdJson,
                getTwoMarketsIndexDiff(),
                leftObTimestampDiff,
                rightObTimestampDiff,
                leftGetObTimestamps,
                rightGetObTimestamps
        );
    }

    private String getTwoMarketsIndexDiff() {
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        final MarketServicePreliq right = arbitrageService.getRightMarketService();
        final BigDecimal leftIndex = left.getContractIndex().getIndexPrice();
        final BigDecimal rightIndex = right.getContractIndex().getIndexPrice();
        return String.format("Index diff = L_index (%s) - R_index (%s) = %s",
                leftIndex.toPlainString(),
                rightIndex.toPlainString(),
                leftIndex.subtract(rightIndex).toPlainString()
        );
    }

    private Status getDqlOpenStatus(MarketService marketService, final BigDecimal pos) {
        boolean isOk = true;
        if (pos.signum() > 0) {
            isOk = marketService.checkLiquidationEdge(OrderType.BID);
        } else if (pos.signum() < 0) {
            isOk = marketService.checkLiquidationEdge(OrderType.ASK);
        }
        return isOk ? Status.OK : Status.WRONG;
    }

    private void setAffordableStatus(final SignalPartsJson signalPartsJson) {
        final boolean isBtmAffordable = arbitrageService.isAffordableBitmex();
        final boolean isOkAffordable = arbitrageService.isAffordableOkex();
        signalPartsJson.setBtmAffordable(isBtmAffordable ? Status.OK : Status.WRONG);
        signalPartsJson.setOkAffordable(isOkAffordable ? Status.OK : Status.WRONG);
    }

    private DelayTimerJson getCorrDelay() {
        final Integer delaySec = settingsRepositoryService.getSettings().getPosAdjustment().getCorrDelaySec();

        return DelayTimerBuilder.createEmpty(delaySec)
                .addTimer(posDiffService.getDtCorr().secToReady(delaySec), "corr")
                .addTimer(posDiffService.getDtMdc().secToReady(delaySec), "mdc")
                .addTimer(posDiffService.getDtExtraCorr().secToReady(delaySec), "extraCorr")
                .addTimer(posDiffService.getDtExtraMdc().secToReady(delaySec), "extraMdc")
                .toJson();
    }

    private DelayTimerJson getPosAdjustmentDelay() {
        final Integer delaySec = settingsRepositoryService.getSettings().getPosAdjustment().getPosAdjustmentDelaySec();

        return DelayTimerBuilder.createEmpty(delaySec)
                .addTimer(posDiffService.getDtAdj().secToReady(delaySec), "adj")
                .addTimer(posDiffService.getDtExtraAdj().secToReady(delaySec), "extraAdj")
                .addTimer(posDiffService.getDtMdcAdj().secToReady(delaySec), "mdcAdj")
                .addTimer(posDiffService.getDtExtraMdcAdj().secToReady(delaySec), "extraMdcAdj")
                .toJson();
    }

    private DelayTimerJson getPreliqDelay() {
        final Integer delaySec = settingsRepositoryService.getSettings().getPosAdjustment().getPreliqDelaySec();
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        final MarketServicePreliq right = arbitrageService.getRightMarketService();

        long btmToStart = left.getDtPreliq().secToReady(delaySec);
        long okToStart = right.getDtPreliq().secToReady(delaySec);

        return DelayTimerBuilder.createEmpty(delaySec)
                .addTimer(btmToStart, "bitmex")
                .addTimer(okToStart, "okex")
                .toJson();
    }

    private DelayTimerJson getKillposDelay() {
        final Integer delaySec = settingsRepositoryService.getSettings().getPosAdjustment().getKillposDelaySec();
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        final MarketServicePreliq right = arbitrageService.getRightMarketService();

        long btmToStart = left.getDtKillpos().secToReady(delaySec);
        long okToStart = right.getDtKillpos().secToReady(delaySec);

        return DelayTimerBuilder.createEmpty(delaySec)
                .addTimer(btmToStart, "bitmex")
                .addTimer(okToStart, "okex")
                .toJson();
    }

    public MarketStatesJson setMarketsStates(MarketStatesJson marketStatesJson) {
        final MarketState first = MarketState.valueOf(marketStatesJson.getFirstMarket());
        final MarketState second = MarketState.valueOf(marketStatesJson.getSecondMarket());
        arbitrageService.getLeftMarketService().setMarketState(first);
        arbitrageService.getRightMarketService().setMarketState(second);

        return new MarketStatesJson(
                arbitrageService.getLeftMarketService().getMarketState().name(),
                arbitrageService.getRightMarketService().getMarketState().name(),
                arbitrageService.getLeftMarketService().getTimeToReset(),
                arbitrageService.getRightMarketService().getTimeToReset()
        );
    }

    public MarketFlagsJson freeMarketsStates() {
        MarketService btm = arbitrageService.getLeftMarketService();
        btm.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE_FROM_UI, btm.tryFindLastTradeId()));
        OkCoinService okex = (OkCoinService) arbitrageService.getRightMarketService();
        okex.resetWaitingArb("UI");
        okex.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE_FROM_UI, okex.tryFindLastTradeId()));
        arbitrageService.resetArbState("'UI'");
        log.info("Free markets states from UI");
        arbitrageService.printToCurrentDeltaLog("Free markets states from UI");
        return new MarketFlagsJson(
                btm.isReadyForArbitrage(),
                okex.isReadyForArbitrage()
        );
    }

    public ResultJson recoveryNtUsd() {
        final Future<String> stringFuture = ntUsdRecoveryService.tryRecovery();
        String res = "";
        try {
            res = stringFuture.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            res = e.getMessage();
        }
        return new ResultJson(res, "");
    }

    public ResultJson printSumBal() {
        return new ResultJson("OK", "");
    }

    public SumBalJson getSumBal() {
        if (!isInitialized()) {
            return new SumBalJson();
        }

        final String sumBalString = arbitrageService.getSumBalString();
        final String sumBalImpliedString = arbitrageService.getSumBalImpliedString();
        final String sumEBest = arbitrageService.getSumEBestUsd().toPlainString();
        Settings settings = settingsRepositoryService.getSettings();
        final String sumEBestMin = settings.getEBestMin().toString();
        final String timeToForbidden = traderPermissionsService.getTimeToForbidden();
        final String coldStorageBtc = settings.getColdStorageBtc().toPlainString();
        final String coldStorageEth = settings.isEth() ? settings.getColdStorageEth().toPlainString() : null;
        return new SumBalJson(sumBalString, sumBalImpliedString, sumEBest, sumEBestMin, timeToForbidden, coldStorageBtc, coldStorageEth);
    }

    public PosDiffJson getPosDiff() {
        if (!arbitrageService.isInitialized()) {
            return PosDiffJson.notInitialized();
        }

        PosDiffJson posDiff;
        try {
            final PlacingBlocks placingBlocks = settingsRepositoryService.getSettings().getPlacingBlocks();

            String btmUsdInContract = "1";
            final BigDecimal cm = placingBlocks.getCm();
            if (arbitrageService.isEth()) {
                btmUsdInContract = BigDecimal.valueOf(10).divide(cm, 2, RoundingMode.HALF_UP).toPlainString();
            }

            posDiff = new PosDiffJson(
                    posDiffService.isMainSetEqual(),
                    arbitrageService.getMainSetStr(),
                    arbitrageService.getMainSetSource(),
                    posDiffService.isExtraSetEqual(),
                    arbitrageService.getExtraSetStr(),
                    arbitrageService.getExtraSetSource(),
                    placingBlocks,
                    btmUsdInContract,
                    arbitrageService.isEth(),
                    cm
            );

        } catch (NotYetInitializedException e) {
            // do nothing
            posDiff = PosDiffJson.notInitialized();
        }
        return posDiff;
    }

    public PosCorrJson getPosCorr() {
        return new PosCorrJson("",
                arbitrageService.getParams().getPeriodToCorrection(),
                arbitrageService.getParams().getMaxDiffCorr().toPlainString());
    }

    public PosCorrJson updatePosCorr(PosCorrJson posCorrJson) {
        if (posCorrJson.getMaxDiffCorr() != null) {
            arbitrageService.getParams().setMaxDiffCorr(new BigDecimal(posCorrJson.getMaxDiffCorr()));
        }
        if (posCorrJson.getPeriodToCorrection() != null) {
            posDiffService.setPeriodToCorrection(posCorrJson.getPeriodToCorrection());
        }
        arbitrageService.saveParamsToDb();

        persistenceService.resetSettingsPreset();

        return new PosCorrJson("",
                arbitrageService.getParams().getPeriodToCorrection(),
                arbitrageService.getParams().getMaxDiffCorr().toPlainString());
    }

    public DeltasMinMaxJson getDeltaParamsJson() {
        final DeltaParams deltaParams = arbitrageService.getDeltaParams();
        MinMaxData instanDelta = new MinMaxData(
                deltaParams.getBDeltaMin().toPlainString(),
                deltaParams.getODeltaMin().toPlainString(),
                deltaParams.getBDeltaMax().toPlainString(),
                deltaParams.getODeltaMax().toPlainString(),
                deltaParams.getBLastRise(),
                deltaParams.getOLastRise());

        DeltaParams minParams = deltaMinService.fetchDeltaMinParams();
        MinMaxData deltaMin = new MinMaxData(
                minParams.getBDeltaMin().toPlainString(),
                minParams.getODeltaMin().toPlainString(),
                minParams.getBDeltaMax().toPlainString(),
                minParams.getODeltaMax().toPlainString(),
                minParams.getBLastRise(),
                minParams.getOLastRise());

        SignalTimeParams signalTimeParams = signalTimeService.fetchSignalTimeParams();
        SignalData signalData = new SignalData(
                signalTimeParams.getSignalTimeMin().toPlainString(),
                signalTimeParams.getSignalTimeMax().toPlainString(),
                signalTimeParams.getSignalTimeAvg().toPlainString(),
                signalTimeParams.getMaxLastRise()
        );

        return new DeltasMinMaxJson(instanDelta, deltaMin, signalData);
    }

    public DeltasMinMaxJson resetDeltaParamsJson() {
        arbitrageService.resetDeltaParams();
        return getDeltaParamsJson();
    }

    public DeltasMinMaxJson resetDeltaMinParamsJson() {
        deltaMinService.resetDeltaMinParams();
        return getDeltaParamsJson();
    }

    public DeltasMinMaxJson resetSignalTimeParams() {
        signalTimeService.resetSignalTimeParams();
        return getDeltaParamsJson();
    }

    public ResultJson resetObTimestamps() {
        arbitrageService.getLeftMarketService().resetGetObDelay();
        arbitrageService.getRightMarketService().resetGetObDelay();
        return new ResultJson();
    }

    public DeltasMinMaxJson getRestartMonitoringParamsJson() {
        MonRestart monRestart = monitoringDataService.fetchRestartMonitoring();
        return new DeltasMinMaxJson(new MinMaxData(
                "",
                "",
                monRestart.getBTimestampDelayMax().toPlainString(),
                monRestart.getOTimestampDelayMax().toPlainString()), null, null);
    }

    public DeltasMinMaxJson resetRestartMonitoringParamsJson() {
        log.warn("RESET MonRestart");
        MonRestart defaults = MonRestart.createDefaults();
        MonRestart monRestart = monitoringDataService.saveRestartMonitoring(defaults);
        return new DeltasMinMaxJson(new MinMaxData(
                "",
                "",
                monRestart.getBTimestampDelayMax().toPlainString(),
                monRestart.getOTimestampDelayMax().toPlainString()), null, null);
    }

    public ResultJson getDeltaMinTimerString() {
        String timerString = deltaMinService.getTimerString();
        return new ResultJson(timerString, "");
    }

    public ResultJson getUpdateBordersTimerString() {
        final String updateBordersTimerString = bordersCalcScheduler.getUpdateBordersTimerString();
        final BorderParams borderParams = persistenceService.fetchBorders();
        final int tableHashCode = borderParams.getBordersV2().getBorderTableHashCode();

        return new ResultJson(updateBordersTimerString, String.valueOf(tableHashCode));
    }

    public TimersJson getTimersJson() {
        final String startSignalTimerStr = arbitrageService.getStartSignalTimer();
        final String deltaMinTimerStr = deltaMinService.getTimerString();

        final String bordersTimerStr = bordersCalcScheduler.getUpdateBordersTimerString();
        final BorderParams borderParams = bordersService.getBorderParams();
        final int bordersTableHashCode = borderParams.getBordersV2().getBorderTableHashCode();

        return new TimersJson(startSignalTimerStr, deltaMinTimerStr, bordersTimerStr, String.valueOf(bordersTableHashCode));
    }

    public LastPriceDeviation getLastPriceDeviation() {
        final LastPriceDeviation lpd = lastPriceDeviationService.getLastPriceDeviation();
        final DelayTimer delayTimer = lastPriceDeviationService.getDelayTimer();
        if (lpd.getDelaySec() == null) {
            lpd.setDelaySec(-1);
        }
        if (lpd.getMaxDevUsd() == null) {
            lpd.setMaxDevUsd(BigDecimal.valueOf(10));
        }
        final Integer delaySec = lpd.getDelaySec();
        final long toNextFix = delayTimer.secToReadyPrecise(delaySec);
        lpd.setToNextFix((int) toNextFix);
        return lpd;
    }

    public LastPriceDeviation fixLastPriceDeviation() {

        lastPriceDeviationService.fixCurrentLastPrice();
        lastPriceDeviationService.getDelayTimer().stop();

        return lastPriceDeviationService.getLastPriceDeviation();
    }

    public LastPriceDeviation updateLastPriceDeviation(LastPriceDeviation update) {
        if (update.getMaxDevUsd() != null) {
            LastPriceDeviation toUpdate = lastPriceDeviationService.getLastPriceDeviation();
            toUpdate.setMaxDevUsd(update.getMaxDevUsd());
            lastPriceDeviationService.saveLastPriceDeviation(toUpdate);
        }
        if (update.getDelaySec() != null) {
            LastPriceDeviation toUpdate = lastPriceDeviationService.getLastPriceDeviation();
            toUpdate.setDelaySec(update.getDelaySec());
            lastPriceDeviationService.saveLastPriceDeviation(toUpdate);
            lastPriceDeviationService.getDelayTimer().stop();
        }

        persistenceService.resetSettingsPreset();

        return lastPriceDeviationService.getLastPriceDeviation();
    }

    public List<CumParams> getCumParamsList() {
        return cumPersistenceService.fetchAllCum();
    }

    public List<CumParams> resetCumParams(CumParams cumParams) {
        final CumParams toReset = cumPersistenceService.fetchCum(cumParams.getCumType(), cumParams.getCumTimeType());
        toReset.setDefaults();
        cumPersistenceService.saveCumParams(toReset);
        return cumPersistenceService.fetchAllCum();
    }

    public List<CumParams> updateCumParams(CumParams cumParams) {
        final CumParams toUpdate = cumPersistenceService.fetchCum(cumParams.getCumType(), cumParams.getCumTimeType());
        toUpdate.update(cumParams);
        cumPersistenceService.saveCumParams(toUpdate);
        return cumPersistenceService.fetchAllCum();
    }

    public Settings impliedFixCurrent() {
        return arbitrageService.impliedFixCurrent();
    }
}
