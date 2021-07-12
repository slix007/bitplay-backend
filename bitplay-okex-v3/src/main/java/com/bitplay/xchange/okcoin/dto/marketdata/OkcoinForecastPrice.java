package com.bitplay.xchange.okcoin.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class OkcoinForecastPrice {

    private final BigDecimal price;

    public OkcoinForecastPrice(@JsonProperty("forecast_price") final BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return "OkcoinForecastPrice{" +
                "price=" + price +
                '}';
    }
}
