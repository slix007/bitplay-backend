package com.bitplay.api.service;

import com.bitplay.Config;
import com.bitplay.api.domain.BorderUpdateJson;
import com.bitplay.api.domain.DelayTimerBuilder;
import com.bitplay.api.domain.DelayTimerJson;
import com.bitplay.api.domain.DeltalUpdateJson;
import com.bitplay.api.domain.DeltasJson;
import com.bitplay.api.domain.DeltasMinMaxJson;
import com.bitplay.api.domain.DeltasMinMaxJson.MinMaxData;
import com.bitplay.api.domain.DeltasMinMaxJson.SignalData;
import com.bitplay.api.domain.LiqParamsJson;
import com.bitplay.api.domain.MarketFlagsJson;
import com.bitplay.api.domain.MarketStatesJson;
import com.bitplay.api.domain.PosCorrJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.domain.SumBalJson;
import com.bitplay.api.domain.TimersJson;
import com.bitplay.api.domain.TradeLogJson;
import com.bitplay.api.domain.pos.PosDiffJson;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BordersCalcScheduler;
import com.bitplay.arbitrage.DeltaMinService;
import com.bitplay.arbitrage.DeltasCalcService;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.arbitrage.SignalTimeService;
import com.bitplay.arbitrage.exceptions.NotYetInitializedException;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketService;
import com.bitplay.market.MarketState;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.market.events.BtsEventBox;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.LastPriceDeviationService;
import com.bitplay.persistance.MonitoringDataService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.CumParams;
import com.bitplay.persistance.domain.DeltaParams;
import com.bitplay.persistance.domain.GuiLiqParams;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.LastPriceDeviation;
import com.bitplay.persistance.domain.SignalTimeParams;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.mon.MonRestart;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.security.TraderPermissionsService;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
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

        return convertToDeltasJson();
    }

    //TODO move some params from Deltas to separate Data Object
    public DeltasJson updateMakerDelta(DeltalUpdateJson deltalUpdateJson) {
        GuiParams guiParams = arbitrageService.getParams();
        CumParams cumParams = persistenceService.fetchCumParams();
        if (deltalUpdateJson.getCumDelta() != null) {
            cumParams.setCumDelta(new BigDecimal(deltalUpdateJson.getCumDelta()));
            cumParams.setCumAstDelta1(new BigDecimal(deltalUpdateJson.getCumDelta()));
            cumParams.setCumAstDelta2(new BigDecimal(deltalUpdateJson.getCumDelta()));
        }
        if (deltalUpdateJson.getLastDelta() != null) {
            guiParams.setLastDelta(deltalUpdateJson.getLastDelta());
        }
        if (deltalUpdateJson.getCumDeltaFact() != null) {
            cumParams.setCumDeltaFact(new BigDecimal(deltalUpdateJson.getCumDeltaFact()));
            cumParams.setCumAstDeltaFact1(new BigDecimal(deltalUpdateJson.getCumDeltaFact()));
            cumParams.setCumAstDeltaFact2(new BigDecimal(deltalUpdateJson.getCumDeltaFact()));
        }
        if (deltalUpdateJson.getCumDiffFactBr() != null) {
            cumParams.setCumDiffFactBr(new BigDecimal(deltalUpdateJson.getCumDiffFactBr()));
        }
        if (deltalUpdateJson.getCumDiffFact1() != null) {
            cumParams.setCumDiffFact1(new BigDecimal(deltalUpdateJson.getCumDiffFact1()));
            cumParams.setCumAstDiffFact1(new BigDecimal(deltalUpdateJson.getCumDiffFact1()));
        }
        if (deltalUpdateJson.getCumDiffFact2() != null) {
            cumParams.setCumDiffFact2(new BigDecimal(deltalUpdateJson.getCumDiffFact2()));
            cumParams.setCumAstDiffFact2(new BigDecimal(deltalUpdateJson.getCumDiffFact2()));
        }
        if (deltalUpdateJson.getCumDiffFact() != null) {
            cumParams.setCumDiffFact(new BigDecimal(deltalUpdateJson.getCumDiffFact()));
            cumParams.setCumAstDiffFact(new BigDecimal(deltalUpdateJson.getCumDiffFact()));
        }
        if (deltalUpdateJson.getCumAstDiffFact() != null) {
            cumParams.setCumAstDiffFact1(new BigDecimal(deltalUpdateJson.getCumAstDiffFact()));
            cumParams.setCumAstDiffFact2(new BigDecimal(deltalUpdateJson.getCumAstDiffFact()));
            cumParams.setCumAstDiffFact(new BigDecimal(deltalUpdateJson.getCumAstDiffFact()));
        }
        if (deltalUpdateJson.getCumCom1() != null) {
            cumParams.setCumCom1(new BigDecimal(deltalUpdateJson.getCumCom1()));
            cumParams.setCumAstCom1(new BigDecimal(deltalUpdateJson.getCumCom1()));
        }
        if (deltalUpdateJson.getCumCom2() != null) {
            cumParams.setCumCom2(new BigDecimal(deltalUpdateJson.getCumCom2()));
            cumParams.setCumAstCom2(new BigDecimal(deltalUpdateJson.getCumCom2()));
        }
        if (deltalUpdateJson.getCount1() != null) {
            guiParams.setCounter1(Integer.parseInt(deltalUpdateJson.getCount1()));
            cumParams.setCompletedCounter1(Integer.parseInt(deltalUpdateJson.getCount1()));
        }
        if (deltalUpdateJson.getCount2() != null) {
            guiParams.setCounter2(Integer.parseInt(deltalUpdateJson.getCount2()));
            cumParams.setCompletedCounter2(Integer.parseInt(deltalUpdateJson.getCount2()));
        }
        if (deltalUpdateJson.getDiffFactBrFailsCount() != null) {
            cumParams.setDiffFactBrFailsCount(Integer.parseInt(deltalUpdateJson.getDiffFactBrFailsCount()));
        }
        if (deltalUpdateJson.getCumBitmexMCom() != null) {
            cumParams.setCumBitmexMCom(new BigDecimal(deltalUpdateJson.getCumBitmexMCom()));
            cumParams.setCumAstBitmexMCom(new BigDecimal(deltalUpdateJson.getCumBitmexMCom()));
        }
        if (deltalUpdateJson.getReserveBtc1() != null) {
            guiParams.setReserveBtc1(new BigDecimal(deltalUpdateJson.getReserveBtc1()));
        }
        if (deltalUpdateJson.getReserveBtc2() != null) {
            guiParams.setReserveBtc2(new BigDecimal(deltalUpdateJson.getReserveBtc2()));
        }
        if (deltalUpdateJson.getFundingRateFee() != null) {
            guiParams.setFundingRateFee(new BigDecimal(deltalUpdateJson.getFundingRateFee()));
        }
        if (deltalUpdateJson.getSlip() != null) {
            cumParams.setSlip(new BigDecimal(deltalUpdateJson.getSlip()));
            cumParams.setSlipBr(new BigDecimal(deltalUpdateJson.getSlip()));
        }
        if (deltalUpdateJson.getResetAllCumValues() != null && deltalUpdateJson.getResetAllCumValues()) {
            cumParams.setCumDelta(BigDecimal.ZERO);
            cumParams.setCumAstDelta1(BigDecimal.ZERO);
            cumParams.setCumAstDelta2(BigDecimal.ZERO);
            cumParams.setCumDeltaFact(BigDecimal.ZERO);
            cumParams.setCumAstDeltaFact1(BigDecimal.ZERO);
            cumParams.setCumAstDeltaFact2(BigDecimal.ZERO);
            cumParams.setCumDiffFactBr(BigDecimal.ZERO);
            cumParams.setCumDiffFact1(BigDecimal.ZERO);
            cumParams.setCumAstDiffFact1(BigDecimal.ZERO);
            cumParams.setCumDiffFact2(BigDecimal.ZERO);
            cumParams.setCumAstDiffFact2(BigDecimal.ZERO);
            cumParams.setCumDiffFact(BigDecimal.ZERO);
            cumParams.setCumAstDiffFact(BigDecimal.ZERO);
            cumParams.setCumAstDiffFact1(BigDecimal.ZERO);
            cumParams.setCumAstDiffFact2(BigDecimal.ZERO);
            cumParams.setCumAstDiffFact(BigDecimal.ZERO);
            cumParams.setCumCom1(BigDecimal.ZERO);
            cumParams.setCumAstCom1(BigDecimal.ZERO);
            cumParams.setCumCom2(BigDecimal.ZERO);
            cumParams.setCumAstCom2(BigDecimal.ZERO);
            cumParams.setCumBitmexMCom(BigDecimal.ZERO);
            cumParams.setCumAstBitmexMCom(BigDecimal.ZERO);
            cumParams.setSlipBr(BigDecimal.ZERO);
            cumParams.setSlip(BigDecimal.ZERO);
            guiParams.setCounter1(0);
            guiParams.setCounter2(0);
            cumParams.setCompletedCounter1(0);
            cumParams.setCompletedCounter2(0);
            cumParams.setDiffFactBrFailsCount(0);
        }
        persistenceService.saveCumParams(cumParams);
        arbitrageService.saveParamsToDb();

        return convertToDeltasJson();
    }

    private DeltasJson convertToDeltasJson() {
        final BorderParams borderParams = persistenceService.fetchBorders();
        final Integer delta_hist_per = borderParams.getBorderDelta().getDeltaCalcPast();
        final String deltaSmaUpdateIn = deltasCalcService.getDeltaSmaUpdateIn(delta_hist_per);
        final CumParams cumParams = persistenceService.fetchCumParams();
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
                guiParams.getBorder1().toPlainString(),
                guiParams.getBorder2().toPlainString(),
                cumParams.getCumDelta().toPlainString(),
                cumParams.getCumAstDelta1().add(cumParams.getCumAstDelta2()).setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                guiParams.getLastDelta(),
                cumParams.getCumDeltaFact().toPlainString(),
                cumParams.getCumAstDeltaFact1().add(cumParams.getCumAstDeltaFact2()).setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                cumParams.getCumDiffFactBr().toPlainString(),
                cumParams.getCumDiffFact1().toPlainString(),
                cumParams.getCumDiffFact2().toPlainString(),
                cumParams.getCumDiffFact().toPlainString(),
                cumParams.getCumAstDiffFact1().setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                cumParams.getCumAstDiffFact2().setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                cumParams.getCumAstDiffFact().setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                cumParams.getCumCom1().toPlainString(),
                cumParams.getCumCom2().toPlainString(),
                cumParams.getCumAstCom1().setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                cumParams.getCumAstCom2().setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                String.valueOf(guiParams.getCounter1()),
                String.valueOf(guiParams.getCounter2()),
                String.valueOf(cumParams.getCompletedCounter1()),
                String.valueOf(cumParams.getCompletedCounter2()),
                String.valueOf(cumParams.getDiffFactBrFailsCount()),
                cumParams.getCumBitmexMCom().toPlainString(),
                cumParams.getCumAstBitmexMCom().setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                guiParams.getReserveBtc1().toPlainString(),
                guiParams.getReserveBtc2().toPlainString(),
                "deprecated. use settings",
                guiParams.getFundingRateFee().toPlainString(),
                cumParams.getSlipBr().setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString(),
                cumParams.getSlip().setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString(),
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

    public MarketFlagsJson getStopMoving() {
        return new MarketFlagsJson(
                arbitrageService.getFirstMarketService().getMovingStop(),
                arbitrageService.getSecondMarketService().getMovingStop()
        );
    }

    public MarketFlagsJson toggleStopMoving() {
        arbitrageService.getFirstMarketService().setMovingStop(!arbitrageService.getFirstMarketService().getMovingStop());
        arbitrageService.getSecondMarketService().setMovingStop(!arbitrageService.getSecondMarketService().getMovingStop());
        return new MarketFlagsJson(
                arbitrageService.getFirstMarketService().getMovingStop(),
                arbitrageService.getSecondMarketService().getMovingStop()
        );
    }

    public MarketStatesJson getMarketsStates() {
        String arbState = arbitrageService.getArbState().toString();

        boolean reconnectInProgress = ((BitmexService) arbitrageService.getFirstMarketService()).isReconnectInProgress();
        String btmReconnectState = reconnectInProgress ? "IN_PROGRESS" : "NONE";

        String btmPreliqQueue = String.valueOf(arbitrageService.getFirstMarketService().getPreliqQueue().size());
        String okexPreliqQueue = String.valueOf(arbitrageService.getFirstMarketService().getPreliqQueue().size());

        DelayTimerJson corrDelay = getCorrDelay();
        DelayTimerJson posAdjustmentDelay = getPosAdjustmentDelay();
        DelayTimerJson preliqDelay = getPreliqDelay();

        return new MarketStatesJson(
                arbitrageService.getFirstMarketService().getMarketState().name(),
                arbitrageService.getSecondMarketService().getMarketState().name(),
                arbitrageService.getFirstMarketService().getTimeToReset(),
                arbitrageService.getSecondMarketService().getTimeToReset(),
                String.valueOf(settingsRepositoryService.getSettings().getSignalDelayMs()),
                arbitrageService.getTimeToSignal(),
                arbState,
                btmReconnectState,
                btmPreliqQueue,
                okexPreliqQueue,
                corrDelay,
                posAdjustmentDelay,
                preliqDelay
        );
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

        if (first == MarketState.STOPPED || second == MarketState.STOPPED) {
            slackNotifications.sendNotify(NotifyType.STOPPED, "STOPPED from UI");
        }
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
        MarketService okex = arbitrageService.getSecondMarketService();
        okex.getEventBus().send(new BtsEventBox(BtsEvent.MARKET_FREE_FROM_UI, okex.tryFindLastTradeId()));
        arbitrageService.releaseArbInProgress("", "'UI'");
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
        final String sumBalString = arbitrageService.getSumBalString();
        final String sumEBest = arbitrageService.getSumEBestUsd().toPlainString();
        Settings settings = settingsRepositoryService.getSettings();
        final String sumEBestMin = settings.getEBestMin().toString();
        final String timeToForbidden = traderPermissionsService.getTimeToForbidden();
        final String coldStorage = settings.getColdStorageBtc().toPlainString();
        return new SumBalJson(sumBalString, sumEBest, sumEBestMin, timeToForbidden, coldStorage);
    }

    public PosDiffJson getPosDiff() {
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
        final BorderParams borderParams = persistenceService.fetchBorders();
        final int bordersTableHashCode = borderParams.getBordersV2().getBorderTableHashCode();

        return new TimersJson(startSignalTimerStr, deltaMinTimerStr, bordersTimerStr, String.valueOf(bordersTableHashCode));
    }

    public LastPriceDeviation getLastPriceDeviation() {
        return lastPriceDeviationService.getLastPriceDeviation();
    }

    public LastPriceDeviation fixLastPriceDeviation() {

        lastPriceDeviationService.fixCurrentLastPrice();

        return lastPriceDeviationService.getLastPriceDeviation();
    }

    public LastPriceDeviation updateLastPriceDeviation(LastPriceDeviation update) {
        if (update.getPercentage() != null) {
            LastPriceDeviation toUpdate = lastPriceDeviationService.getLastPriceDeviation();
            toUpdate.setPercentage(update.getPercentage());
            lastPriceDeviationService.saveLastPriceDeviation(toUpdate);
        }
        return lastPriceDeviationService.getLastPriceDeviation();
    }

}
