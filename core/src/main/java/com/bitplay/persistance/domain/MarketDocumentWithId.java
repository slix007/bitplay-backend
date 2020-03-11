package com.bitplay.persistance.domain;

import org.springframework.data.mongodb.core.index.Indexed;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
public class MarketDocumentWithId extends AbstractDocument {

    @Indexed
    private String marketName;

    public String getMarketName() {
        return marketName;
    }

    public void setMarketName(String marketName) {
        this.marketName = marketName;
    }
}
