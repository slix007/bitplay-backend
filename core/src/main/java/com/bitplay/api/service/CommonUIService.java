package com.bitplay.api.service;

import com.bitplay.api.domain.BorderUpdateJson;
import com.bitplay.api.domain.ChangeRequestJson;
import com.bitplay.api.domain.DeltalUpdateJson;
import com.bitplay.api.domain.DeltasJson;
import com.bitplay.api.domain.DeltasMinMaxJson;
import com.bitplay.api.domain.LiqParamsJson;
import com.bitplay.api.domain.MarketFlagsJson;
import com.bitplay.api.domain.MarketStatesJson;
import com.bitplay.api.domain.PlacingTypeJson;
import com.bitplay.api.domain.PosCorrJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.domain.TradableAmountJson;
import com.bitplay.api.domain.TradeLogJson;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.market.events.BtsEvent;
import com.bitplay.persistance.domain.DeltaParams;
import com.bitplay.persistance.domain.GuiParams;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.List;

/**
 * Created by Sergey Shurmin on 4/17/17.
 */
@Service
public class CommonUIService {

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private PosDiffService posDiffService;

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
        return new DeltasJson(
                arbitrageService.getDelta1().toPlainString(),
                arbitrageService.getDelta2().toPlainString(),
                arbitrageService.getParams().getBorder1().toPlainString(),
                arbitrageService.getParams().getBorder2().toPlainString(),
                arbitrageService.getParams().getMakerDelta().toPlainString(),
                arbitrageService.getParams().getSumDelta().toPlainString(),
                arbitrageService.getParams().getPeriodSec().toString(),
                arbitrageService.getParams().getBuValue().toPlainString(),
                arbitrageService.getParams().getCumDelta().toPlainString(),
                arbitrageService.getParams().getLastDelta(),
                arbitrageService.getParams().getCumDeltaFact().toPlainString(),
                arbitrageService.getParams().getCumDiffFact1().toPlainString(),
                arbitrageService.getParams().getCumDiffFact2().toPlainString(),
                arbitrageService.getParams().getCumCom1().toPlainString(),
                arbitrageService.getParams().getCumCom2().toPlainString(),
                String.valueOf(arbitrageService.getParams().getCounter1()),
                String.valueOf(arbitrageService.getParams().getCounter2()),
                arbitrageService.getParams().getCumBitmexMCom().toPlainString(),
                arbitrageService.getParams().getReserveBtc1().toPlainString(),
                arbitrageService.getParams().getReserveBtc2().toPlainString(),
                arbitrageService.getParams().getHedgeAmount().toPlainString(),
                arbitrageService.getParams().getFundingRateFee().toPlainString()
        );
    }

    public DeltasJson updateBorders(BorderUpdateJson borderUpdateJson) {
        if (borderUpdateJson.getBorder1() != null) {
            arbitrageService.getParams().setBorder1(new BigDecimal(borderUpdateJson.getBorder1()));
        }
        if (borderUpdateJson.getBorder2() != null) {
            arbitrageService.getParams().setBorder2(new BigDecimal(borderUpdateJson.getBorder2()));
        }
        arbitrageService.saveParamsToDb();

        return new DeltasJson(
                arbitrageService.getDelta1().toPlainString(),
                arbitrageService.getDelta2().toPlainString(),
                arbitrageService.getParams().getBorder1().toPlainString(),
                arbitrageService.getParams().getBorder2().toPlainString(),
                arbitrageService.getParams().getMakerDelta().toPlainString(),
                arbitrageService.getParams().getSumDelta().toPlainString(),
                arbitrageService.getParams().getPeriodSec().toString(),
                arbitrageService.getParams().getBuValue().toPlainString(),
                arbitrageService.getParams().getCumDelta().toPlainString(),
                arbitrageService.getParams().getLastDelta(),
                arbitrageService.getParams().getCumDeltaFact().toPlainString(),
                arbitrageService.getParams().getCumDiffFact1().toPlainString(),
                arbitrageService.getParams().getCumDiffFact2().toPlainString(),
                arbitrageService.getParams().getCumCom1().toPlainString(),
                arbitrageService.getParams().getCumCom2().toPlainString(),
                String.valueOf(arbitrageService.getParams().getCounter1()),
                String.valueOf(arbitrageService.getParams().getCounter2()),
                arbitrageService.getParams().getCumBitmexMCom().toPlainString(),
                arbitrageService.getParams().getReserveBtc1().toPlainString(),
                arbitrageService.getParams().getReserveBtc2().toPlainString(),
                arbitrageService.getParams().getHedgeAmount().toPlainString(),
                arbitrageService.getParams().getFundingRateFee().toPlainString()
        );
    }

    //TODO move some params from Deltas to separate Data Object
    public DeltasJson updateMakerDelta(DeltalUpdateJson deltalUpdateJson) {
        if (deltalUpdateJson.getMakerDelta() != null) {
            arbitrageService.getParams().setMakerDelta(new BigDecimal(deltalUpdateJson.getMakerDelta()));
        }
        if (deltalUpdateJson.getSumDelta() != null) {
            arbitrageService.getParams().setSumDelta(new BigDecimal(deltalUpdateJson.getSumDelta()));
        }
        if (deltalUpdateJson.getPeriodSec() != null) {
            arbitrageService.setPeriodSec(Integer.valueOf(deltalUpdateJson.getPeriodSec()));
        }
        if (deltalUpdateJson.getBuValue() != null) {
            arbitrageService.getParams().setBuValue(new BigDecimal(deltalUpdateJson.getBuValue()));
        }
        if (deltalUpdateJson.getCumDelta() != null) {
            arbitrageService.getParams().setCumDelta(new BigDecimal(deltalUpdateJson.getCumDelta()));
        }
        if (deltalUpdateJson.getLastDelta() != null) {
            arbitrageService.getParams().setLastDelta(deltalUpdateJson.getLastDelta());
        }
        if (deltalUpdateJson.getCumDeltaFact() != null) {
            arbitrageService.getParams().setCumDeltaFact(new BigDecimal(deltalUpdateJson.getCumDeltaFact()));
        }
        if (deltalUpdateJson.getCumDiffFact1() != null) {
            arbitrageService.getParams().setCumDiffFact1(new BigDecimal(deltalUpdateJson.getCumDiffFact1()));
        }
        if (deltalUpdateJson.getCumDiffFact2() != null) {
            arbitrageService.getParams().setCumDiffFact2(new BigDecimal(deltalUpdateJson.getCumDiffFact2()));
        }
        if (deltalUpdateJson.getCumCom1() != null) {
            arbitrageService.getParams().setCumCom1(new BigDecimal(deltalUpdateJson.getCumCom1()));
        }
        if (deltalUpdateJson.getCumCom2() != null) {
            arbitrageService.getParams().setCumCom2(new BigDecimal(deltalUpdateJson.getCumCom2()));
        }
        if (deltalUpdateJson.getCount1() != null) {
            arbitrageService.getParams().setCounter1(Integer.parseInt(deltalUpdateJson.getCount1()));
        }
        if (deltalUpdateJson.getCount2() != null) {
            arbitrageService.getParams().setCounter2(Integer.parseInt(deltalUpdateJson.getCount2()));
        }
        if (deltalUpdateJson.getCumBitmexMCom() != null) {
            arbitrageService.getParams().setCumBitmexMCom(new BigDecimal(deltalUpdateJson.getCumBitmexMCom()));
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
        arbitrageService.saveParamsToDb();

        return new DeltasJson(
                arbitrageService.getDelta1().toPlainString(),
                arbitrageService.getDelta2().toPlainString(),
                arbitrageService.getParams().getBorder1().toPlainString(),
                arbitrageService.getParams().getBorder2().toPlainString(),
                arbitrageService.getParams().getMakerDelta().toPlainString(),
                arbitrageService.getParams().getSumDelta().toPlainString(),
                arbitrageService.getParams().getPeriodSec().toString(),
                arbitrageService.getParams().getBuValue().toPlainString(),
                arbitrageService.getParams().getCumDelta().toPlainString(),
                arbitrageService.getParams().getLastDelta(),
                arbitrageService.getParams().getCumDeltaFact().toPlainString(),
                arbitrageService.getParams().getCumDiffFact1().toPlainString(),
                arbitrageService.getParams().getCumDiffFact2().toPlainString(),
                arbitrageService.getParams().getCumCom1().toPlainString(),
                arbitrageService.getParams().getCumCom2().toPlainString(),
                String.valueOf(arbitrageService.getParams().getCounter1()),
                String.valueOf(arbitrageService.getParams().getCounter2()),
                arbitrageService.getParams().getCumBitmexMCom().toPlainString(),
                arbitrageService.getParams().getReserveBtc1().toPlainString(),
                arbitrageService.getParams().getReserveBtc2().toPlainString(),
                arbitrageService.getParams().getHedgeAmount().toPlainString(),
                arbitrageService.getParams().getFundingRateFee().toPlainString()
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
                arbitrageService.getSecondMarketService().getMarketState().name()
        );
    }

    public MarketFlagsJson freeMarketsStates() {
        arbitrageService.getFirstMarketService().getEventBus().send(BtsEvent.MARKET_FREE);
        arbitrageService.getSecondMarketService().getEventBus().send(BtsEvent.MARKET_FREE);
        return new MarketFlagsJson(
                arbitrageService.getFirstMarketService().isReadyForArbitrage(),
                arbitrageService.getSecondMarketService().isReadyForArbitrage()
        );
    }

    public TradableAmountJson getTradableAmount() {
        return new TradableAmountJson(arbitrageService.getParams().getBlock1().toPlainString(),
                arbitrageService.getParams().getBlock2().toPlainString());
    }

    public TradableAmountJson updateTradableAmount(TradableAmountJson tradableAmountJson) {
        if (tradableAmountJson.getBlock1() != null) {
            arbitrageService.getParams().setBlock1(new BigDecimal(tradableAmountJson.getBlock1()));
        }
        if (tradableAmountJson.getBlock2() != null) {
            arbitrageService.getParams().setBlock2(new BigDecimal(tradableAmountJson.getBlock2()));
        }

        arbitrageService.saveParamsToDb();
        return new TradableAmountJson(arbitrageService.getParams().getBlock1().toPlainString(),
                arbitrageService.getParams().getBlock2().toPlainString());
    }

    public ResultJson printSumBal() {
        arbitrageService.printSumBal(true);
        return new ResultJson("OK", "");
    }

    public ResultJson getSumBal() {
        final String sumBalString = arbitrageService.getSumBalString();
        return new ResultJson(sumBalString, "");
    }

    public ResultJson getPosDiff() {
        return new ResultJson(
                posDiffService.getIsPositionsEqual() ? "0" : "-1",
                arbitrageService.getPosDiffString());
    }

    public PlacingTypeJson getPlacingType() {
        return new PlacingTypeJson(
                "maker",
                arbitrageService.getParams().getOkCoinOrderType()
        );
    }

    public PlacingTypeJson updatePlacingType(PlacingTypeJson placingTypeJson) {
        arbitrageService.getParams().setOkCoinOrderType(placingTypeJson.getSecondMarket());
        arbitrageService.saveParamsToDb();

        return new PlacingTypeJson(
                "maker",
                arbitrageService.getParams().getOkCoinOrderType()
        );
    }

    public PosCorrJson getPosCorr() {
        return new PosCorrJson(arbitrageService.getParams().getPosCorr(),
                arbitrageService.getParams().getPeriodToCorrection(),
                arbitrageService.getParams().getMaxDiffCorr().toPlainString());
    }

    public PosCorrJson updatePosCorr(PosCorrJson posCorrJson) {
        if (posCorrJson.getStatus() != null) {
            arbitrageService.getParams().setPosCorr(posCorrJson.getStatus());
        }
        if (posCorrJson.getMaxDiffCorr() != null) {
            arbitrageService.getParams().setMaxDiffCorr(new BigDecimal(posCorrJson.getMaxDiffCorr()));
        }
        if (posCorrJson.getPeriodToCorrection() != null) {
            posDiffService.setPeriodToCorrection(posCorrJson.getPeriodToCorrection());
        }
        arbitrageService.saveParamsToDb();
        return new PosCorrJson(arbitrageService.getParams().getPosCorr(),
                arbitrageService.getParams().getPeriodToCorrection(),
                arbitrageService.getParams().getMaxDiffCorr().toPlainString());
    }

    public LiqParamsJson getLiqParams() {
        final GuiParams params = arbitrageService.getParams();
        return new LiqParamsJson(params.getbMrLiq().toPlainString(),
                params.getoMrLiq().toPlainString(),
                params.getbDQLOpenMin().toPlainString(),
                params.getoDQLOpenMin().toPlainString(),
                params.getbDQLCloseMin().toPlainString(),
                params.getoDQLCloseMin().toPlainString());
    }

    public LiqParamsJson updateLiqParams(LiqParamsJson input) {
        if (input.getbMrLiq() != null) {
            arbitrageService.getParams().setbMrLiq(new BigDecimal(input.getbMrLiq()));
        }
        if (input.getoMrLiq() != null) {
            arbitrageService.getParams().setoMrLiq(new BigDecimal(input.getoMrLiq()));
        }
        if (input.getbDQLOpenMin() != null) {
            arbitrageService.getParams().setbDQLOpenMin(new BigDecimal(input.getbDQLOpenMin()));
        }
        if (input.getoDQLOpenMin() != null) {
            arbitrageService.getParams().setoDQLOpenMin(new BigDecimal(input.getoDQLOpenMin()));
        }
        if (input.getbDQLCloseMin() != null) {
            arbitrageService.getParams().setbDQLCloseMin(new BigDecimal(input.getbDQLCloseMin()));
        }
        if (input.getoDQLCloseMin() != null) {
            arbitrageService.getParams().setoDQLCloseMin(new BigDecimal(input.getoDQLCloseMin()));
        }

        arbitrageService.saveParamsToDb();
        final GuiParams params = arbitrageService.getParams();
        return new LiqParamsJson(params.getbMrLiq().toPlainString(),
                params.getoMrLiq().toPlainString(),
                params.getbDQLOpenMin().toPlainString(),
                params.getoDQLOpenMin().toPlainString(),
                params.getbDQLCloseMin().toPlainString(),
                params.getoDQLCloseMin().toPlainString());
    }

    public ResultJson getImmediateCorrection() {
        return new ResultJson(String.valueOf(posDiffService.isImmediateCorrectionEnabled()), "");
    }

    public ResultJson updateImmediateCorrection(ChangeRequestJson command) {
        if (command.getCommand().equals("true")) {
            posDiffService.setImmediateCorrectionEnabled(true);
        }
        return new ResultJson(String.valueOf(posDiffService.isImmediateCorrectionEnabled()), "");
    }

    public DeltasMinMaxJson getDeltaParamsJson() {
        final DeltaParams deltaParams = arbitrageService.getDeltaParams();
        return new DeltasMinMaxJson(
                deltaParams.getbDeltaMin().toPlainString(),
                deltaParams.getoDeltaMin().toPlainString(),
                deltaParams.getbDeltaMax().toPlainString(),
                deltaParams.getoDeltaMax().toPlainString());
    }

    public DeltasMinMaxJson resetDeltaParamsJson() {
        arbitrageService.resetDeltaParams();
        return getDeltaParamsJson();
    }

    public ResultJson getUpdateBordersTimerString() {
        final String updateBordersTimerString = arbitrageService.getUpdateBordersTimerString();
        return new ResultJson(updateBordersTimerString, "");
    }
}
