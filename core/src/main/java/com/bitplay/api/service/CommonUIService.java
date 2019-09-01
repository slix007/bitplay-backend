package com.bitplay.api.service;

import com.bitplay.Config;
import com.bitplay.api.domain.BorderUpdateJson;
import com.bitplay.api.domain.DeltalUpdateJson;
import com.bitplay.api.domain.DeltasJson;
import com.bitplay.api.domain.DeltasMinMaxJson;
import com.bitplay.api.domain.DeltasMinMaxJson.MinMaxData;
import com.bitplay.api.domain.DeltasMinMaxJson.SignalData;
import com.bitplay.api.domain.LiqParamsJson;
import com.bitplay.api.domain.MarketFlagsJson;
import com.bitplay.api.domain.PosCorrJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.domain.SumBalJson;
import com.bitplay.api.domain.TimersJson;
import com.bitplay.api.domain.TradeLogJson;
import com.bitplay.api.domain.ob.LimitsJson;
import com.bitplay.api.domain.pos.PosDiffJson;
import com.bitplay.api.domain.states.DelayTimerBuilder;
import com.bitplay.api.domain.states.DelayTimerJson;
import com.bitplay.api.domain.states.MarketStatesJson;
import com.bitplay.api.domain.states.OrderPortionsJson;
import com.bitplay.api.domain.states.SignalPartsJson;
import com.bitplay.api.domain.states.SignalPartsJson.Status;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BordersCalcScheduler;
import com.bitplay.arbitrage.BordersService;
import com.bitplay.arbitrage.DeltaMinService;
import com.bitplay.arbitrage.DeltasCalcService;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.arbitrage.VolatileModeSwitcherService;
import com.bitplay.arbitrage.dto.DelayTimer;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketService;
import com.bitplay.market.bitmex.BitmexLimitsService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.BtsEventBox;
import com.bitplay.market.model.ArbState;
import com.bitplay.market.model.MarketState;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.market.okcoin.OkexSettlementService;
import com.bitplay.persistance.CumPersistenceService;
import com.bitplay.persistance.LastPriceDeviationService;
import com.bitplay.persistance.MonitoringDataService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.SignalTimeService;
import com.bitplay.persistance.domain.CumParams;
import com.bitplay.persistance.domain.DeltaParams;
import com.bitplay.persistance.domain.GuiLiqParams;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.LastPriceDeviation;
import com.bitplay.persistance.domain.SignalTimeParams;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.mon.MonRestart;
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
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.Order.OrderType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 4/17/17.
 */
@Service
@Slf4j
public class CommonUIService {

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private BitmexService bitmexService;

    @Autowired
    private OkCoinService okCoinService;

    @Autowired
    private Config config;

    @Autowired
    private TraderPermissionsService traderPermissionsService;

    @Autowired
    private DeltasCalcService deltasCalcService;

    @Autowired
    private BordersCalcScheduler bordersCalcScheduler;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private CumPersistenceService cumPersistenceService;

    @Autowired
    private BordersService bordersService;

    @Autowired
    private DeltaMinService deltaMinService;

    @Autowired
    private PosDiffService posDiffService;

    @Autowired
    private MonitoringDataService monitoringDataService;

    @Autowired
    private SignalTimeService signalTimeService;

    @Autowired
    private LastPriceDeviationService lastPriceDeviationService;

    @Autowired
    private SlackNotifications slackNotifications;

    @Autowired
    private TradingModeService tradingModeService;

    @Autowired
    private VolatileModeSwitcherService volatileModeSwitcherService;

    @Autowired
    private BitmexChangeOnSoService bitmexChangeOnSoService;

    @Autowired
    private OkexSettlementService okexSettlementService;

    public TradeLogJson getPoloniexTradeLog() {
        return getTradeLogJson("./logs/poloniex-trades.log");
    }

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
        return arbitrageService.getFirstMarketService() != null && arbitrageService.getSecondMarketService() != null;
    }

    public MarketFlagsJson getStopMoving() {
        if (!isInitialized()) {
            return new MarketFlagsJson(null, null);
        }
        return new MarketFlagsJson(
                arbitrageService.getFirstMarketService().getMovingStop(),
                arbitrageService.getSecondMarketService().getMovingStop()
        );
    }

    public MarketFlagsJson toggleStopMoving() {
        if (!isInitialized()) {
            return new MarketFlagsJson(null, null);
        }
        arbitrageService.getFirstMarketService().setMovingStop(!arbitrageService.getFirstMarketService().getMovingStop());
        arbitrageService.getSecondMarketService().setMovingStop(!arbitrageService.getSecondMarketService().getMovingStop());
        return new MarketFlagsJson(
                arbitrageService.getFirstMarketService().getMovingStop(),
                arbitrageService.getSecondMarketService().getMovingStop()
        );
    }

    public MarketStatesJson getMarketsStates() {
        if (!isInitialized()) {
            return new MarketStatesJson();
        }
        final ArbState arbState = arbitrageService.getArbState();
        final MarketState btmState = arbitrageService.getFirstMarketService().getMarketState();
        final MarketState okState = arbitrageService.getSecondMarketService().getMarketState();

        boolean reconnectInProgress = ((BitmexService) arbitrageService.getFirstMarketService()).isReconnectInProgress();
        String btmReconnectState = reconnectInProgress ? "IN_PROGRESS" : "NONE";

        DelayTimerJson corrDelay = getCorrDelay();
        DelayTimerJson posAdjustmentDelay = getPosAdjustmentDelay();
        DelayTimerJson preliqDelay = getPreliqDelay();

        final String timeToSignal = arbitrageService.getTimeToSignal();

        // SignalPartsJson
        final SignalPartsJson signalPartsJson = new SignalPartsJson();
        signalPartsJson.setSignalDelay(timeToSignal.equals("_ready_") ? Status.OK : (timeToSignal.equals("_none_") ? Status.WRONG : Status.STARTED));
        signalPartsJson.setBtmMaxDelta(arbitrageService.isMaxDeltaViolated(DeltaName.B_DELTA) ? Status.WRONG : Status.OK);
        signalPartsJson.setOkMaxDelta(arbitrageService.isMaxDeltaViolated(DeltaName.O_DELTA) ? Status.WRONG : Status.OK);
        final PosDiffJson posDiff = getPosDiff();
        signalPartsJson.setNtUsd(posDiff.isMainSetEqual() && posDiff.isExtraSetEqual() ? Status.OK : Status.WRONG);
        signalPartsJson.setStates(arbState == ArbState.READY && btmState == MarketState.READY && okState == MarketState.READY ? Status.OK : Status.WRONG);
        final BigDecimal posBtm = bitmexService.getPosVal();
        final BigDecimal posOk = okCoinService.getPosVal();
        signalPartsJson.setBtmDqlOpen(getDqlOpenStatus(bitmexService, posBtm));
        signalPartsJson.setOkDqlOpen(getDqlOpenStatus(okCoinService, posOk));
        setAffordableStatus(signalPartsJson);
        final boolean btmLimOut = ((BitmexLimitsService) bitmexService.getLimitsService()).outsideLimits();
        final DeltaName signalStatusDelta = arbitrageService.getSignalStatusDelta();
        boolean okLimOut = false;
        if (signalStatusDelta != null) {
            final LimitsJson limitsJson = okCoinService.getLimitsService().getLimitsJson();
            if (limitsJson.getIgnoreLimits()) {
                okLimOut = false;
            } else {
                if (signalStatusDelta == DeltaName.B_DELTA) {
                    okLimOut = !limitsJson.getInsideLimitsEx().getBtmDelta();
                } else {
                    okLimOut = !limitsJson.getInsideLimitsEx().getOkDelta();
                }
            }
        }
        signalPartsJson.setDeltaName(signalStatusDelta == null ? "_" : signalStatusDelta.getDeltaSymbol());

        signalPartsJson.setPriceLimits(!btmLimOut && !okLimOut ? Status.OK : Status.WRONG);

        signalPartsJson.setBtmOrderBook(Utils.isObOk(bitmexService.getOrderBook()) ? Status.OK : Status.WRONG);
        signalPartsJson.setBtmOrderBookXBTUSD(Utils.isObOk(bitmexService.getOrderBookXBTUSD()) ? Status.OK : Status.WRONG);
        signalPartsJson.setOkOrderBook(Utils.isObOk(okCoinService.getOrderBook()) ? Status.OK : Status.WRONG);

        final OrderPortionsJson orderPortionsJson = new OrderPortionsJson(bitmexService.getPortionsProgressForUi(), okCoinService.getPortionsProgressForUi());

        return new MarketStatesJson(
                btmState.toString(),
                okState.toString(),
                arbitrageService.getFirstMarketService().getTimeToReset(),
                arbitrageService.getSecondMarketService().getTimeToReset(),
                String.valueOf(settingsRepositoryService.getSettings().getSignalDelayMs()),
                timeToSignal,
                tradingModeService.secToReset(),
                volatileModeSwitcherService.timeToVolatileMode(),
                bitmexChangeOnSoService.getSecToReset(),
                arbState.toString(),
                btmReconnectState,
                corrDelay,
                posAdjustmentDelay,
                preliqDelay,
                signalPartsJson,
                posDiff,
                orderPortionsJson,
                okexSettlementService.isSettlementMode(),
                LocalTime.now().toString()
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
//        final PlacingBlocks placingBlocks = settingsRepositoryService.getSettings().getPlacingBlocks();

//        BigDecimal btmBlock;
//        BigDecimal okBlock;
//        if (placingBlocks.getActiveVersion() == PlacingBlocks.Ver.FIXED) {
//            btmBlock = placingBlocks.getFixedBlockBitmex();
//            okBlock = placingBlocks.getFixedBlockOkex();
//        } else {
//            // the minimum is 1 okex contract
//            okBlock = BigDecimal.valueOf(1);
//            final BigDecimal usd = PlacingBlocks.okexContToUsd(okBlock, placingBlocks.isEth());
//            btmBlock = PlacingBlocks.toBitmexCont(usd, placingBlocks.isEth(), placingBlocks.getCm());
//        }

//        final BigDecimal posBtm = bitmexService.getPosition().getPositionLong().subtract(bitmexService.getPosition().getPositionShort());
//        final BigDecimal posOk = okCoinService.getPosition().getPositionLong().subtract(okCoinService.getPosition().getPositionShort());
        final boolean isBtmAffordable = arbitrageService.isAffordableBitmex();
        final boolean isOkAffordable = arbitrageService.isAffordableOkex();
        signalPartsJson.setBtmAffordable(isBtmAffordable ? Status.OK : Status.WRONG);
        signalPartsJson.setOkAffordable(isOkAffordable ? Status.OK : Status.WRONG);
    }

//    private boolean isAffordable(BigDecimal block, BigDecimal pos, MarketService marketService) {
//        boolean isOk;
//        if (pos.signum() > 0) {
//            isOk = marketService.isAffordable(OrderType.BID, block);
//        } else if (pos.signum() < 0) {
//            isOk = marketService.isAffordable(OrderType.ASK, block);
//        } else {
//            isOk = marketService.isAffordable(OrderType.BID, block);
//        }
//        return isOk;
//    }


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

        long btmToStart = bitmexService.getDtPreliq().secToReady(delaySec);
        long okToStart = okCoinService.getDtPreliq().secToReady(delaySec);

        return DelayTimerBuilder.createEmpty(delaySec)
                .addTimer(btmToStart, "bitmex")
                .addTimer(okToStart, "okex")
                .toJson();
    }

    public MarketStatesJson setMarketsStates(MarketStatesJson marketStatesJson) {
        final MarketState first = MarketState.valueOf(marketStatesJson.getFirstMarket());
        final MarketState second = MarketState.valueOf(marketStatesJson.getSecondMarket());
        arbitrageService.getFirstMarketService().setMarketState(first);
        arbitrageService.getSecondMarketService().setMarketState(second);

        if (first == MarketState.FORBIDDEN || second == MarketState.FORBIDDEN) {
            slackNotifications.sendNotify(NotifyType.FORBIDDEN, "FORBIDDEN from UI");
        }

        return new MarketStatesJson(
                arbitrageService.getFirstMarketService().getMarketState().name(),
                arbitrageService.getSecondMarketService().getMarketState().name(),
                arbitrageService.getFirstMarketService().getTimeToReset(),
                arbitrageService.getSecondMarketService().getTimeToReset()
        );
    }

    public MarketFlagsJson freeMarketsStates() {
        MarketService btm = arbitrageService.getFirstMarketService();
        btm.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE_FROM_UI, btm.tryFindLastTradeId()));
        OkCoinService okex = (OkCoinService) arbitrageService.getSecondMarketService();
        okex.resetWaitingArb();
        okex.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE_FROM_UI, okex.tryFindLastTradeId()));
        arbitrageService.resetArbState("", "'UI'");
        log.info("Free markets states from UI");
        arbitrageService.printToCurrentDeltaLog("Free markets states from UI");
        return new MarketFlagsJson(
                btm.isReadyForArbitrage(),
                okex.isReadyForArbitrage()
        );
    }
//
//    public TradableAmountJson getTradableAmount() {
//        final PlacingBlocks placingBlocks = settingsRepositoryService.getSettings().getPlacingBlocks();
//        return new TradableAmountJson(arbitrageService.getParams().getBlock1().toPlainString(),
//                arbitrageService.getParams().getBlock2().toPlainString());
//    }
//
//    public TradableAmountJson updateTradableAmount(TradableAmountJson tradableAmountJson) {
//        if (tradableAmountJson.getBlock1() != null) {
//            arbitrageService.getParams().setBlock1(new BigDecimal(tradableAmountJson.getBlock1()));
//        }
//        if (tradableAmountJson.getBlock2() != null) {
//            arbitrageService.getParams().setBlock2(new BigDecimal(tradableAmountJson.getBlock2()));
//        }
//
//        arbitrageService.saveParamsToDb();
//        return new TradableAmountJson(arbitrageService.getParams().getBlock1().toPlainString(),
//                arbitrageService.getParams().getBlock2().toPlainString());
//    }

    public ResultJson printSumBal() {
//        arbitrageService.printSumBal(null,"button");
        return new ResultJson("OK", "");
    }

    public SumBalJson getSumBal() {
        if (!isInitialized()) {
            return new SumBalJson();
        }

        final String sumBalString = arbitrageService.getSumBalString();
        final String sumEBest = arbitrageService.getSumEBestUsd().toPlainString();
        Settings settings = settingsRepositoryService.getSettings();
        final String sumEBestMin = settings.getEBestMin().toString();
        final String timeToForbidden = traderPermissionsService.getTimeToForbidden();
        final String coldStorageBtc = settings.getColdStorageBtc().toPlainString();
        final String coldStorageEth = settings.isEth() ? settings.getColdStorageEth().toPlainString() : null;
        return new SumBalJson(sumBalString, sumEBest, sumEBestMin, timeToForbidden, coldStorageBtc, coldStorageEth);
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
            if (bitmexService.getContractType().isEth()) {
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
                    bitmexService.getContractType().isEth(),
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

    public LiqParamsJson getLiqParams() {
        final GuiLiqParams params = persistenceService.fetchGuiLiqParams();
        return new LiqParamsJson(params.getBMrLiq().toPlainString(),
                params.getOMrLiq().toPlainString(),
                params.getBDQLOpenMin().toPlainString(),
                params.getODQLOpenMin().toPlainString(),
                params.getBDQLCloseMin().toPlainString(),
                params.getODQLCloseMin().toPlainString());
    }

    public LiqParamsJson updateLiqParams(LiqParamsJson input) {
        final GuiLiqParams guiLiqParams = persistenceService.fetchGuiLiqParams();
        if (input.getbMrLiq() != null) {
            guiLiqParams.setBMrLiq(new BigDecimal(input.getbMrLiq()));
        }
        if (input.getoMrLiq() != null) {
            guiLiqParams.setOMrLiq(new BigDecimal(input.getoMrLiq()));
        }
        if (input.getbDQLOpenMin() != null) {
            guiLiqParams.setBDQLOpenMin(new BigDecimal(input.getbDQLOpenMin()));
        }
        if (input.getoDQLOpenMin() != null) {
            guiLiqParams.setODQLOpenMin(new BigDecimal(input.getoDQLOpenMin()));
        }
        if (input.getbDQLCloseMin() != null) {
            guiLiqParams.setBDQLCloseMin(new BigDecimal(input.getbDQLCloseMin()));
        }
        if (input.getoDQLCloseMin() != null) {
            guiLiqParams.setODQLCloseMin(new BigDecimal(input.getoDQLCloseMin()));
        }

        persistenceService.saveGuiLiqParams(guiLiqParams);

        persistenceService.resetSettingsPreset();

        final GuiLiqParams saved = persistenceService.fetchGuiLiqParams();

        return new LiqParamsJson(saved.getBMrLiq().toPlainString(),
                saved.getOMrLiq().toPlainString(),
                saved.getBDQLOpenMin().toPlainString(),
                saved.getODQLOpenMin().toPlainString(),
                saved.getBDQLCloseMin().toPlainString(),
                saved.getODQLCloseMin().toPlainString());
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

}
