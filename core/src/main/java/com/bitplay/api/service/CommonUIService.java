package com.bitplay.api.service;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.api.domain.BorderUpdateJson;
import com.bitplay.api.domain.DeltasJson;
import com.bitplay.api.domain.MakerDeltalUpdateJson;
import com.bitplay.api.domain.TradeLogJson;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
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

    public TradeLogJson getOkCoinTradeLog() {
        return getTradeLogJson("./logs/okcoin-trades.log");
    }

    private TradeLogJson getTradeLogJson(String fileName) {
        TradeLogJson tradeLogJson = null;
        try {
            final List<String> allLines = Files.readAllLines(Paths.get(fileName));

            tradeLogJson = new TradeLogJson(allLines);

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
                arbitrageService.getMakerDelta().toPlainString()
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
                arbitrageService.getMakerDelta().toPlainString()
        );
    }

    public DeltasJson updateMakerDelta(MakerDeltalUpdateJson makerDeltalUpdateJson) {
        if (makerDeltalUpdateJson.getMakerDelta() != null) {
            arbitrageService.setMakerDelta(
                    new BigDecimal(makerDeltalUpdateJson.getMakerDelta())
            );
        }

        return new DeltasJson(
                arbitrageService.getDelta1().toPlainString(),
                arbitrageService.getDelta2().toPlainString(),
                arbitrageService.getBorder1().toPlainString(),
                arbitrageService.getBorder2().toPlainString(),
                arbitrageService.getMakerDelta().toPlainString()
        );
    }
}
