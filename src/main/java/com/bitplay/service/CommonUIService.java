package com.bitplay.service;

import com.bitplay.business.arbitrage.ArbitrageService;
import com.bitplay.model.DeltasJson;
import com.bitplay.model.TradeLogJson;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
        return new DeltasJson(arbitrageService.getDelta1().toPlainString(),
                arbitrageService.getDelta2().toPlainString());
    }
}
