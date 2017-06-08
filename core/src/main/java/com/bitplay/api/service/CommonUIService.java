package com.bitplay.api.service;

import com.bitplay.api.domain.BorderUpdateJson;
import com.bitplay.api.domain.DeltalUpdateJson;
import com.bitplay.api.domain.DeltasJson;
import com.bitplay.api.domain.MarketFlagsJson;
import com.bitplay.api.domain.TradableAmountJson;
import com.bitplay.api.domain.TradeLogJson;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.market.events.BtsEvent;

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

    public TradeLogJson getPoloniexTradeLog() {
        return getTradeLogJson("./logs/poloniex-trades.log");
    }

    public TradeLogJson getBitmexTradeLog() {
        return getTradeLogJson("./logs/bitmex-trades.log");
    }

    public TradeLogJson getOkCoinTradeLog() {
        return getTradeLogJson("./logs/okcoin-trades.log");
    }

    public TradeLogJson getDeltasLog() {
        return getTradeLogJson("./logs/deltas.log");
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
                arbitrageService.getBorder1().toPlainString(),
                arbitrageService.getBorder2().toPlainString(),
                arbitrageService.getMakerDelta().toPlainString(),
                arbitrageService.getSumDelta().toPlainString(),
                arbitrageService.getPeriodSec().toString(),
                arbitrageService.getBuValue().toPlainString(),
                arbitrageService.getCumDelta().toPlainString(),
                arbitrageService.getLastDelta(),
                arbitrageService.getCumDeltaFact().toPlainString(),
                arbitrageService.getCumDiffFact1().toPlainString(),
                arbitrageService.getCumDiffFact2().toPlainString(),
                arbitrageService.getCumCom1().toPlainString(),
                arbitrageService.getCumCom2().toPlainString(),
                String.valueOf(arbitrageService.getCounter1()),
                String.valueOf(arbitrageService.getCounter2())
        );
    }

    public DeltasJson updateBorders(BorderUpdateJson borderUpdateJson) {
        if (borderUpdateJson.getBorder1() != null) {
            arbitrageService.setBorder1(new BigDecimal(borderUpdateJson.getBorder1()));
        }
        if (borderUpdateJson.getBorder2() != null) {
            arbitrageService.setBorder2(new BigDecimal(borderUpdateJson.getBorder2()));
        }

        return new DeltasJson(
                arbitrageService.getDelta1().toPlainString(),
                arbitrageService.getDelta2().toPlainString(),
                arbitrageService.getBorder1().toPlainString(),
                arbitrageService.getBorder2().toPlainString(),
                arbitrageService.getMakerDelta().toPlainString(),
                arbitrageService.getSumDelta().toPlainString(),
                arbitrageService.getPeriodSec().toString(),
                arbitrageService.getBuValue().toPlainString(),
                arbitrageService.getCumDelta().toPlainString(),
                arbitrageService.getLastDelta(),
                arbitrageService.getCumDeltaFact().toPlainString(),
                arbitrageService.getCumDiffFact1().toPlainString(),
                arbitrageService.getCumDiffFact2().toPlainString(),
                arbitrageService.getCumCom1().toPlainString(),
                arbitrageService.getCumCom2().toPlainString(),
                String.valueOf(arbitrageService.getCounter1()),
                String.valueOf(arbitrageService.getCounter2())
        );
    }

    public DeltasJson updateMakerDelta(DeltalUpdateJson deltalUpdateJson) {
        if (deltalUpdateJson.getMakerDelta() != null) {
            arbitrageService.setMakerDelta(new BigDecimal(deltalUpdateJson.getMakerDelta()));
        }
        if (deltalUpdateJson.getSumDelta() != null) {
            arbitrageService.setSumDelta(new BigDecimal(deltalUpdateJson.getSumDelta()));
        }
        if (deltalUpdateJson.getPeriodSec() != null) {
            arbitrageService.setPeriodSec(Integer.valueOf(deltalUpdateJson.getPeriodSec()));
        }
        if (deltalUpdateJson.getBuValue() != null) {
            arbitrageService.setBuValue(new BigDecimal(deltalUpdateJson.getBuValue()));
        }
        if (deltalUpdateJson.getCumDelta() != null) {
            arbitrageService.setCumDelta(new BigDecimal(deltalUpdateJson.getCumDelta()));
        }
        if (deltalUpdateJson.getLastDelta() != null) {
            arbitrageService.setLastDelta(deltalUpdateJson.getLastDelta());
        }
        if (deltalUpdateJson.getCumDeltaFact() != null) {
            arbitrageService.setCumDeltaFact(new BigDecimal(deltalUpdateJson.getCumDeltaFact()));
        }
        if (deltalUpdateJson.getCumDiffFact1() != null) {
            arbitrageService.setCumDiffFact1(new BigDecimal(deltalUpdateJson.getCumDiffFact1()));
        }
        if (deltalUpdateJson.getCumDiffFact2() != null) {
            arbitrageService.setCumDiffFact2(new BigDecimal(deltalUpdateJson.getCumDiffFact2()));
        }
        if (deltalUpdateJson.getCumCom1() != null) {
            arbitrageService.setCumCom1(new BigDecimal(deltalUpdateJson.getCumCom1()));
        }
        if (deltalUpdateJson.getCumCom2() != null) {
            arbitrageService.setCumCom2(new BigDecimal(deltalUpdateJson.getCumCom2()));
        }
        if (deltalUpdateJson.getCount1() != null) {
            arbitrageService.setCounter1(Integer.parseInt(deltalUpdateJson.getCount1()));
        }
        if (deltalUpdateJson.getCount2() != null) {
            arbitrageService.setCounter2(Integer.parseInt(deltalUpdateJson.getCount2()));
        }

        return new DeltasJson(
                arbitrageService.getDelta1().toPlainString(),
                arbitrageService.getDelta2().toPlainString(),
                arbitrageService.getBorder1().toPlainString(),
                arbitrageService.getBorder2().toPlainString(),
                arbitrageService.getMakerDelta().toPlainString(),
                arbitrageService.getSumDelta().toPlainString(),
                arbitrageService.getPeriodSec().toString(),
                arbitrageService.getBuValue().toPlainString(),
                arbitrageService.getCumDelta().toPlainString(),
                arbitrageService.getLastDelta(),
                arbitrageService.getCumDeltaFact().toPlainString(),
                arbitrageService.getCumDiffFact1().toPlainString(),
                arbitrageService.getCumDiffFact2().toPlainString(),
                arbitrageService.getCumCom1().toPlainString(),
                arbitrageService.getCumCom2().toPlainString(),
                String.valueOf(arbitrageService.getCounter1()),
                String.valueOf(arbitrageService.getCounter2())
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

    public MarketFlagsJson getMarketsStates() {
        return new MarketFlagsJson(
                arbitrageService.getFirstMarketService().isReadyForArbitrage(),
                arbitrageService.getSecondMarketService().isReadyForArbitrage()
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
        return new TradableAmountJson(arbitrageService.getAmount().toPlainString());
    }

    public TradableAmountJson updateTradableAmount(TradableAmountJson tradableAmountJson) {
        arbitrageService.setAmount(new BigDecimal(tradableAmountJson.getAmount()));
        return new TradableAmountJson(arbitrageService.getAmount().toPlainString());
    }

}
