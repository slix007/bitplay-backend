package com.bitplay.service;

import com.bitplay.model.TradeLogJson;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Created by Sergey Shurmin on 4/17/17.
 */
@Service
public class CommonService {

    public TradeLogJson getTradeLog() {
        TradeLogJson tradeLogJson = null;
        try {
            final List<String> allLines = Files.readAllLines(Paths.get("./logs/trades.log"));

            tradeLogJson = new TradeLogJson(allLines);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return tradeLogJson;
    }
}
