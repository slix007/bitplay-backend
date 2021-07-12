package com.bitplay.xchange.okcoin.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Date;

public class OkcoinMarkPrice {

    private final String symbol;
    private final String contractType;
    private final BigDecimal markPrice;
    private final Date timestamp;

    public OkcoinMarkPrice(
            @JsonProperty("symbol") final String symbol,
            @JsonProperty("contract_type") final String contractType,
            @JsonProperty("mark_price") final BigDecimal markPrice,
            @JsonProperty("timestamp") Date timestamp) {
        this.symbol = symbol;
        this.contractType = contractType;
        this.markPrice = markPrice;
        this.timestamp = timestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getContractType() {
        return contractType;
    }

    public BigDecimal getMarkPrice() {
        return markPrice;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "OkcoinMarkPrice{" +
                "symbol='" + symbol + '\'' +
                ", contractType='" + contractType + '\'' +
                ", markPrice=" + markPrice +
                ", timestamp=" + timestamp +
                '}';
    }
}
