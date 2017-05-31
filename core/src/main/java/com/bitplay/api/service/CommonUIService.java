package com.bitplay.api.service;

import com.bitplay.api.domain.BorderUpdateJson;
import com.bitplay.api.domain.DeltalUpdateJson;
import com.bitplay.api.domain.DeltasJson;
import com.bitplay.api.domain.TradeLogJson;
import com.bitplay.arbitrage.ArbitrageService;

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
                arbitrageService.getCumDiffsFact().toPlainString()
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
                arbitrageService.getCumDiffsFact().toPlainString()
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
        if (deltalUpdateJson.getCumDiffs() != null) {
            arbitrageService.setCumDiffsFact(new BigDecimal(deltalUpdateJson.getCumDiffs()));
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
                arbitrageService.getCumDiffsFact().toPlainString()
        );
    }
}
