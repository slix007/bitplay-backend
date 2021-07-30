package com.bitplay.xchange.okcoin.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class OkcoinIndexPrice {

    private final BigDecimal indexPrice;

    public OkcoinIndexPrice(
            @JsonProperty("future_index") final BigDecimal indexPrice) {
        this.indexPrice = indexPrice;
    }

    public BigDecimal getIndexPrice() {
        return indexPrice;
    }

    @Override
    public String toString() {
        return "OkcoinIndexPrice{" +
                "indexPrice=" + indexPrice +
                '}';
    }
}
