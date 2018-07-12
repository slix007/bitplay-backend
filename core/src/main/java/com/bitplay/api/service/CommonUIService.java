package com.bitplay.api.service;

import com.bitplay.Config;
import com.bitplay.api.domain.BorderUpdateJson;
import com.bitplay.api.domain.DeltalUpdateJson;
import com.bitplay.api.domain.DeltasJson;
import com.bitplay.api.domain.DeltasMinMaxJson;
import com.bitplay.api.domain.LiqParamsJson;
import com.bitplay.api.domain.MarketFlagsJson;
import com.bitplay.api.domain.MarketStatesJson;
import com.bitplay.api.domain.PosCorrJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.domain.SumBalJson;
import com.bitplay.api.domain.TradeLogJson;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.BordersCalcScheduler;
import com.bitplay.arbitrage.DeltasCalcService;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.market.MarketState;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.DeltaParams;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.RestartMonitoring;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.repository.RestartMonitoringRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 4/17/17.
 */
@Service
public class CommonUIService {

    private final static Logger logger = LoggerFactory.getLogger(CommonUIService.class);

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private Config config;

    @Autowired
    private DeltasCalcService deltasCalcService;

    @Autowired
    private BordersCalcScheduler bordersCalcScheduler;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private PosDiffService posDiffService;

    @Autowired
    private RestartMonitoringRepository restartMonitoringRepository;

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
        if (deltalUpdateJson.getMakerDelta() != null) {
            arbitrageService.getParams().setMakerDelta(new BigDecimal(deltalUpdateJson.getMakerDelta()));
        }
        if (deltalUpdateJson.getBuValue() != null) {
            arbitrageService.getParams().setBuValue(new BigDecimal(deltalUpdateJson.getBuValue()));
        }
        if (deltalUpdateJson.getCumDelta() != null) {
            arbitrageService.getParams().setCumDelta(new BigDecimal(deltalUpdateJson.getCumDelta()));
            arbitrageService.getParams().setCumAstDelta1(new BigDecimal(deltalUpdateJson.getCumDelta()));
            arbitrageService.getParams().setCumAstDelta2(new BigDecimal(deltalUpdateJson.getCumDelta()));
        }
        if (deltalUpdateJson.getLastDelta() != null) {
            arbitrageService.getParams().setLastDelta(deltalUpdateJson.getLastDelta());
        }
        if (deltalUpdateJson.getCumDeltaFact() != null) {
            arbitrageService.getParams().setCumDeltaFact(new BigDecimal(deltalUpdateJson.getCumDeltaFact()));
            arbitrageService.getParams().setCumAstDeltaFact1(new BigDecimal(deltalUpdateJson.getCumDeltaFact()));
            arbitrageService.getParams().setCumAstDeltaFact2(new BigDecimal(deltalUpdateJson.getCumDeltaFact()));
        }
        if (deltalUpdateJson.getCumDiffFactBr() != null) {
            arbitrageService.getParams().setCumDiffFactBr(new BigDecimal(deltalUpdateJson.getCumDiffFactBr()));
        }
        if (deltalUpdateJson.getCumDiffFact1() != null) {
            arbitrageService.getParams().setCumDiffFact1(new BigDecimal(deltalUpdateJson.getCumDiffFact1()));
            arbitrageService.getParams().setCumAstDiffFact1(new BigDecimal(deltalUpdateJson.getCumDiffFact1()));

        }
        if (deltalUpdateJson.getCumDiffFact2() != null) {
            arbitrageService.getParams().setCumDiffFact2(new BigDecimal(deltalUpdateJson.getCumDiffFact2()));
            arbitrageService.getParams().setCumAstDiffFact2(new BigDecimal(deltalUpdateJson.getCumDiffFact2()));
        }
        if (deltalUpdateJson.getCumDiffFact() != null) {
            arbitrageService.getParams().setCumDiffFact(new BigDecimal(deltalUpdateJson.getCumDiffFact()));
            arbitrageService.getParams().setCumAstDiffFact(new BigDecimal(deltalUpdateJson.getCumDiffFact()));
        }
        if (deltalUpdateJson.getCumAstDiffFact() != null) {
            arbitrageService.getParams().setCumAstDiffFact1(new BigDecimal(deltalUpdateJson.getCumAstDiffFact()));
            arbitrageService.getParams().setCumAstDiffFact2(new BigDecimal(deltalUpdateJson.getCumAstDiffFact()));
            arbitrageService.getParams().setCumAstDiffFact(new BigDecimal(deltalUpdateJson.getCumAstDiffFact()));
        }
        if (deltalUpdateJson.getCumCom1() != null) {
            arbitrageService.getParams().setCumCom1(new BigDecimal(deltalUpdateJson.getCumCom1()));
            arbitrageService.getParams().setCumAstCom1(new BigDecimal(deltalUpdateJson.getCumCom1()));
        }
        if (deltalUpdateJson.getCumCom2() != null) {
            arbitrageService.getParams().setCumCom2(new BigDecimal(deltalUpdateJson.getCumCom2()));
            arbitrageService.getParams().setCumAstCom2(new BigDecimal(deltalUpdateJson.getCumCom2()));
        }
        if (deltalUpdateJson.getCount1() != null) {
            arbitrageService.getParams().setCounter1(Integer.parseInt(deltalUpdateJson.getCount1()));
            arbitrageService.getParams().setCompletedCounter1(Integer.parseInt(deltalUpdateJson.getCount2()));
        }
        if (deltalUpdateJson.getCount2() != null) {
            arbitrageService.getParams().setCounter2(Integer.parseInt(deltalUpdateJson.getCount2()));
            arbitrageService.getParams().setCompletedCounter2(Integer.parseInt(deltalUpdateJson.getCount2()));
        }
        if (deltalUpdateJson.getDiffFactBrFailsCount() != null) {
            arbitrageService.getParams().setDiffFactBrFailsCount(Integer.parseInt(deltalUpdateJson.getDiffFactBrFailsCount()));
        }
        if (deltalUpdateJson.getCumBitmexMCom() != null) {
            arbitrageService.getParams().setCumBitmexMCom(new BigDecimal(deltalUpdateJson.getCumBitmexMCom()));
            arbitrageService.getParams().setCumAstBitmexMCom(new BigDecimal(deltalUpdateJson.getCumBitmexMCom()));
        }
        if (deltalUpdateJson.getReserveBtc1() != null) {
            arbitrageService.getParams().setReserveBtc1(new BigDecimal(deltalUpdateJson.getReserveBtc1()));
        }
        if (deltalUpdateJson.getReserveBtc2() != null) {
            arbitrageService.getParams().setReserveBtc2(new BigDecimal(deltalUpdateJson.getReserveBtc2()));
        }
        if (deltalUpdateJson.getHedgeAmount() != null) {
            arbitrageService.getParams().setHedgeAmount(new BigDecimal(deltalUpdateJson.getHedgeAmount()));
        }
        if (deltalUpdateJson.getFundingRateFee() != null) {
            arbitrageService.getParams().setFundingRateFee(new BigDecimal(deltalUpdateJson.getFundingRateFee()));
        }
        if (deltalUpdateJson.getSlip() != null) {
            arbitrageService.getParams().setSlip(new BigDecimal(deltalUpdateJson.getSlip()));
            arbitrageService.getParams().setSlipBr(new BigDecimal(deltalUpdateJson.getSlip()));
        }
        if (deltalUpdateJson.getResetAllCumValues() != null && deltalUpdateJson.getResetAllCumValues()) {
            arbitrageService.getParams().setCumDelta(BigDecimal.ZERO);
            arbitrageService.getParams().setCumAstDelta1(BigDecimal.ZERO);
            arbitrageService.getParams().setCumAstDelta2(BigDecimal.ZERO);
            arbitrageService.getParams().setCumDeltaFact(BigDecimal.ZERO);
            arbitrageService.getParams().setCumAstDeltaFact1(BigDecimal.ZERO);
            arbitrageService.getParams().setCumAstDeltaFact2(BigDecimal.ZERO);
            arbitrageService.getParams().setCumDiffFactBr(BigDecimal.ZERO);
            arbitrageService.getParams().setCumDiffFact1(BigDecimal.ZERO);
            arbitrageService.getParams().setCumAstDiffFact1(BigDecimal.ZERO);
            arbitrageService.getParams().setCumDiffFact2(BigDecimal.ZERO);
            arbitrageService.getParams().setCumAstDiffFact2(BigDecimal.ZERO);
            arbitrageService.getParams().setCumDiffFact(BigDecimal.ZERO);
            arbitrageService.getParams().setCumAstDiffFact(BigDecimal.ZERO);
            arbitrageService.getParams().setCumAstDiffFact1(BigDecimal.ZERO);
            arbitrageService.getParams().setCumAstDiffFact2(BigDecimal.ZERO);
            arbitrageService.getParams().setCumAstDiffFact(BigDecimal.ZERO);
            arbitrageService.getParams().setCumCom1(BigDecimal.ZERO);
            arbitrageService.getParams().setCumAstCom1(BigDecimal.ZERO);
            arbitrageService.getParams().setCumCom2(BigDecimal.ZERO);
            arbitrageService.getParams().setCumAstCom2(BigDecimal.ZERO);
            arbitrageService.getParams().setCumBitmexMCom(BigDecimal.ZERO);
            arbitrageService.getParams().setCumAstBitmexMCom(BigDecimal.ZERO);
            arbitrageService.getParams().setSlipBr(BigDecimal.ZERO);
            arbitrageService.getParams().setSlip(BigDecimal.ZERO);
            arbitrageService.getParams().setCounter1(0);
            arbitrageService.getParams().setCounter2(0);
            arbitrageService.getParams().setCompletedCounter1(0);
            arbitrageService.getParams().setCompletedCounter2(0);
            arbitrageService.getParams().setDiffFactBrFailsCount(0);
        }
        arbitrageService.saveParamsToDb();

        return convertToDeltasJson();
    }

    private DeltasJson convertToDeltasJson() {
        final BorderParams borderParams = persistenceService.fetchBorders();
        final Integer delta_hist_per = borderParams.getBorderDelta().getDeltaCalcPast();
        final String deltaSmaUpdateIn = deltasCalcService.getDeltaSmaUpdateIn(delta_hist_per);

        return new DeltasJson(
                arbitrageService.getDelta1().toPlainString(),
                arbitrageService.getDelta2().toPlainString(),
                arbitrageService.getParams().getBorder1().toPlainString(),
                arbitrageService.getParams().getBorder2().toPlainString(),
                arbitrageService.getParams().getMakerDelta().toPlainString(),
                arbitrageService.getParams().getBuValue().toPlainString(),
                arbitrageService.getParams().getCumDelta().toPlainString(),
                arbitrageService.getParams().getCumAstDelta1().add(arbitrageService.getParams().getCumAstDelta2()).setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                arbitrageService.getParams().getLastDelta(),
                arbitrageService.getParams().getCumDeltaFact().toPlainString(),
                arbitrageService.getParams().getCumAstDeltaFact1().add(arbitrageService.getParams().getCumAstDeltaFact2()).setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                arbitrageService.getParams().getCumDiffFactBr().toPlainString(),
                arbitrageService.getParams().getCumDiffFact1().toPlainString(),
                arbitrageService.getParams().getCumDiffFact2().toPlainString(),
                arbitrageService.getParams().getCumDiffFact().toPlainString(),
                arbitrageService.getParams().getCumAstDiffFact1().setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                arbitrageService.getParams().getCumAstDiffFact2().setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                arbitrageService.getParams().getCumAstDiffFact().setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                arbitrageService.getParams().getCumCom1().toPlainString(),
                arbitrageService.getParams().getCumCom2().toPlainString(),
                arbitrageService.getParams().getCumAstCom1().setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                arbitrageService.getParams().getCumAstCom2().setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                String.valueOf(arbitrageService.getParams().getCounter1()),
                String.valueOf(arbitrageService.getParams().getCounter2()),
                String.valueOf(arbitrageService.getParams().getCompletedCounter1()),
                String.valueOf(arbitrageService.getParams().getCompletedCounter2()),
                String.valueOf(arbitrageService.getParams().getDiffFactBrFailsCount()),
                arbitrageService.getParams().getCumBitmexMCom().toPlainString(),
                arbitrageService.getParams().getCumAstBitmexMCom().setScale(4, BigDecimal.ROUND_HALF_UP).toPlainString(),
                arbitrageService.getParams().getReserveBtc1().toPlainString(),
                arbitrageService.getParams().getReserveBtc2().toPlainString(),
                arbitrageService.getParams().getHedgeAmount().toPlainString(),
                arbitrageService.getParams().getFundingRateFee().toPlainString(),
                arbitrageService.getParams().getSlipBr().setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString(),
                arbitrageService.getParams().getSlip().setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString(),
                deltasCalcService.getBDeltaSma().toPlainString(),
                deltasCalcService.getODeltaSma().toPlainString(),
                deltasCalcService.getBDeltaEveryCalc(),
                deltasCalcService.getODeltaEveryCalc(),
                deltasCalcService.getDeltaHistPerStartedSec(),
                deltaSmaUpdateIn
        );
    }

    public MarketFlagsJson getStopMoving() {
        return new MarketFlagsJson(
                arbitrageService.getFirstMarketService().isMovingStop(),
                arbitrageService.getSecondMarketService().isMovingStop()
        );
    }

    public MarketFlagsJson toggleStopMoving() {
        arbitrageService.getFirstMarketService().setMovingStop(!arbitrageService.getFirstMarketService().isMovingStop());
        arbitrageService.getSecondMarketService().setMovingStop(!arbitrageService.getSecondMarketService().isMovingStop());
        return new MarketFlagsJson(
                arbitrageService.getFirstMarketService().isMovingStop(),
                arbitrageService.getSecondMarketService().isMovingStop()
        );
    }

    public MarketStatesJson getMarketsStates() {
        return new MarketStatesJson(
                arbitrageService.getFirstMarketService().getMarketState().name(),
                arbitrageService.getSecondMarketService().getMarketState().name(),
                arbitrageService.getFirstMarketService().getTimeToReset(),
                arbitrageService.getSecondMarketService().getTimeToReset(),
                String.valueOf(settingsRepositoryService.getSettings().getSignalDelayMs()),
                arbitrageService.getTimeToSignal()

        );
    }

    public MarketStatesJson setMarketsStates(MarketStatesJson marketStatesJson) {
        arbitrageService.getFirstMarketService().setMarketState(
                MarketState.valueOf(marketStatesJson.getFirstMarket())
        );
        arbitrageService.getSecondMarketService().setMarketState(
                MarketState.valueOf(marketStatesJson.getSecondMarket())
        );

        return new MarketStatesJson(
                arbitrageService.getFirstMarketService().getMarketState().name(),
                arbitrageService.getSecondMarketService().getMarketState().name(),
                arbitrageService.getFirstMarketService().getTimeToReset(),
                arbitrageService.getSecondMarketService().getTimeToReset()
        );
    }

    public MarketFlagsJson freeMarketsStates() {
        arbitrageService.getFirstMarketService().getEventBus().send(BtsEvent.MARKET_FREE_FROM_UI);
        arbitrageService.getSecondMarketService().getEventBus().send(BtsEvent.MARKET_FREE_FROM_UI);
        return new MarketFlagsJson(
                arbitrageService.getFirstMarketService().isReadyForArbitrage(),
                arbitrageService.getSecondMarketService().isReadyForArbitrage()
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
        arbitrageService.printSumBal(true);
        return new ResultJson("OK", "");
    }

    public SumBalJson getSumBal() {
        final String sumBalString = arbitrageService.getSumBalString();
        final String sumEBest = arbitrageService.getSumEBestUsd().toPlainString();
        final String sumEBestMin = config.getEBestMin().toString();
        final String coldStorage = settingsRepositoryService.getSettings().getColdStorageBtc().toPlainString();
        return new SumBalJson(sumBalString, sumEBest, sumEBestMin, coldStorage);
    }

    public ResultJson getPosDiff() {
        return new ResultJson(
                posDiffService.getIsPositionsEqual() ? "0" : "-1",
                arbitrageService.getPosDiffString());
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
        final GuiParams params = arbitrageService.getParams();
        return new LiqParamsJson(params.getBMrLiq().toPlainString(),
                params.getOMrLiq().toPlainString(),
                params.getBDQLOpenMin().toPlainString(),
                params.getODQLOpenMin().toPlainString(),
                params.getBDQLCloseMin().toPlainString(),
                params.getODQLCloseMin().toPlainString());
    }

    public LiqParamsJson updateLiqParams(LiqParamsJson input) {
        if (input.getbMrLiq() != null) {
            arbitrageService.getParams().setBMrLiq(new BigDecimal(input.getbMrLiq()));
        }
        if (input.getoMrLiq() != null) {
            arbitrageService.getParams().setOMrLiq(new BigDecimal(input.getoMrLiq()));
        }
        if (input.getbDQLOpenMin() != null) {
            arbitrageService.getParams().setBDQLOpenMin(new BigDecimal(input.getbDQLOpenMin()));
        }
        if (input.getoDQLOpenMin() != null) {
            arbitrageService.getParams().setODQLOpenMin(new BigDecimal(input.getoDQLOpenMin()));
        }
        if (input.getbDQLCloseMin() != null) {
            arbitrageService.getParams().setBDQLCloseMin(new BigDecimal(input.getbDQLCloseMin()));
        }
        if (input.getoDQLCloseMin() != null) {
            arbitrageService.getParams().setODQLCloseMin(new BigDecimal(input.getoDQLCloseMin()));
        }

        arbitrageService.saveParamsToDb();
        final GuiParams params = arbitrageService.getParams();
        return new LiqParamsJson(params.getBMrLiq().toPlainString(),
                params.getOMrLiq().toPlainString(),
                params.getBDQLOpenMin().toPlainString(),
                params.getODQLOpenMin().toPlainString(),
                params.getBDQLCloseMin().toPlainString(),
                params.getODQLCloseMin().toPlainString());
    }

    public DeltasMinMaxJson getDeltaParamsJson() {
        final DeltaParams deltaParams = arbitrageService.getDeltaParams();
        return new DeltasMinMaxJson(
                deltaParams.getBDeltaMin().toPlainString(),
                deltaParams.getODeltaMin().toPlainString(),
                deltaParams.getBDeltaMax().toPlainString(),
                deltaParams.getODeltaMax().toPlainString(),
                deltaParams.getBLastRise(),
                deltaParams.getOLastRise());
    }

    public DeltasMinMaxJson resetDeltaParamsJson() {
        arbitrageService.resetDeltaParams();
        return getDeltaParamsJson();
    }

    public DeltasMinMaxJson geRestartMonitoringParamsJson() {
        RestartMonitoring restartMonitoring = restartMonitoringRepository.fetchRestartMonitoring();
        return new DeltasMinMaxJson(
                "",
                "",
                restartMonitoring.getBTimestampDelayMax().toPlainString(),
                restartMonitoring.getOTimestampDelayMax().toPlainString());
    }

    public DeltasMinMaxJson resetRestartMonitoringParamsJson() {
        logger.warn("RESET RestartMonitoring");
        RestartMonitoring defaults = RestartMonitoring.createDefaults();
        RestartMonitoring restartMonitoring = restartMonitoringRepository.saveRestartMonitoring(defaults);
        return new DeltasMinMaxJson(
                "",
                "",
                restartMonitoring.getBTimestampDelayMax().toPlainString(),
                restartMonitoring.getOTimestampDelayMax().toPlainString());
    }

    public ResultJson getUpdateBordersTimerString() {
        final String updateBordersTimerString = bordersCalcScheduler.getUpdateBordersTimerString();
        final BorderParams borderParams = persistenceService.fetchBorders();
        final int tableHashCode = borderParams.getBordersV2().getBorderTableHashCode();

        return new ResultJson(updateBordersTimerString, String.valueOf(tableHashCode));
    }
}
