package com.bitplay.okexv5.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Date;

public class OkcoinMarkPrice {
    private final BigDecimal markPrice;
    private final String instrumentId;
    private final Date timestamp;

    public OkcoinMarkPrice(
            @JsonProperty("markPx") BigDecimal markPrice,
            @JsonProperty("instId") String instrumentId,
            @JsonProperty("ts") Date timestamp) {
        this.markPrice = markPrice;
        this.instrumentId = instrumentId;
        this.timestamp = timestamp;
    }

    public BigDecimal getMarkPrice() {
        return markPrice;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "OkcoinMarkPrice{" +
                "markPrice=" + markPrice +
                ", instrumentId='" + instrumentId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
