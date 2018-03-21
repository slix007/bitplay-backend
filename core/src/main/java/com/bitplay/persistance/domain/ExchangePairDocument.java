package com.bitplay.persistance.domain;

import org.springframework.data.mongodb.core.index.Indexed;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
public class ExchangePairDocument extends AbstractDocument {

    @Indexed
    private ExchangePair exchangePair;

    public ExchangePair getExchangePair() {
        return exchangePair;
    }

    public void setExchangePair(ExchangePair exchangePair) {
        this.exchangePair = exchangePair;
    }
}
