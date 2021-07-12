package com.bitplay.persistance.domain;

import org.springframework.data.annotation.Id;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
public class MarketDocument {

    @Id
    private String marketName;

    public String getMarketName() {
        return marketName;
    }

    public void setMarketName(String marketName) {
        this.marketName = marketName;
    }
}
