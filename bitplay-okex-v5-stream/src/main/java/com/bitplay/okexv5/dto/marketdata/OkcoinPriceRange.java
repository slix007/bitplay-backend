package com.bitplay.okexv5.dto.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public class OkcoinPriceRange {

    private BigDecimal highest;
    private BigDecimal lowest;
    private String instrumentId;
    private Instant timestamp;

    public OkcoinPriceRange(
            @JsonProperty("buyLmt") BigDecimal highest,
            @JsonProperty("sellLmt") BigDecimal lowest,
            @JsonProperty("instId") String instrumentId,
            @JsonProperty("ts") Instant timestamp) {
        this.highest = highest;
        this.lowest = lowest;
        this.instrumentId = instrumentId;
        this.timestamp = timestamp;
    }

    public BigDecimal getHighest() {
        return highest;
    }

    public BigDecimal getLowest() {
        return lowest;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "OkcoinPriceRange{" +
                "highest=" + highest +
                ", lowest=" + lowest +
                ", instrumentId='" + instrumentId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
