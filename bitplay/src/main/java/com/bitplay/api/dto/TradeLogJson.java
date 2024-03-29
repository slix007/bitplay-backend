package com.bitplay.api.dto;

import java.util.List;

/**
 * Created by Sergey Shurmin on 4/17/17.
 */
public class TradeLogJson {
    private List<String> trades;

    public TradeLogJson() {
    }

    public TradeLogJson(List<String> trades) {
        this.trades = trades;
    }

    public void setTrades(List<String> trades) {
        this.trades = trades;
    }

    public List<String> getTrades() {
        return trades;
    }
}
