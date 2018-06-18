package com.bitplay.arbitrage.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.Getter;
import lombok.ToString;

/**
 * Parameters for monitoring performance of delta calculations.
 */
@Getter
@ToString
public class DeltaMon {
    private Long deltaMs;
    private Long maxDeltaMs;
    @JsonFormat(pattern="HH:mm:ss.SSS")
    private Date lastDeltaTime;
    private Long borderMs;
    private Long maxBorderMs;
    @JsonFormat(pattern="HH:mm:ss.SSS")
    private Date lastBorderTime;
    private Long bDeltaItems;
    private Long oDeltaItems;

    public void setDeltaMs(Long deltaMs) {
        this.deltaMs = deltaMs;
        this.lastDeltaTime = new Date();
        if (maxDeltaMs == null || maxDeltaMs < deltaMs) {
            this.maxDeltaMs = deltaMs;
        }
    }

    public void setBorderMs(Long borderMs) {
        this.borderMs = borderMs;
        this.lastBorderTime = new Date();
        if (maxBorderMs == null || maxBorderMs < borderMs) {
            this.maxBorderMs = borderMs;
        }
    }

    public void setItmes(Long b, Long o) {
        this.bDeltaItems = b;
        this.oDeltaItems = o;
    }
}
