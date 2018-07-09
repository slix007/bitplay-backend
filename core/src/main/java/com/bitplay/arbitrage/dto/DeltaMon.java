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

    private Long btmDeltaMs;
    private Long maxBtmDeltaMs;
    @JsonFormat(pattern="HH:mm:ss.SSS")
    private Date lastBtmDeltaTime;

    private Long okDeltaMs;
    private Long maxOkDeltaMs;
    @JsonFormat(pattern="HH:mm:ss.SSS")
    private Date lastOkDeltaTime;

    private Long btmValidateDeltaMs;
    private Long maxBtmValidateDeltaMs;
    @JsonFormat(pattern="HH:mm:ss.SSS")
    private Date lastBtmValidateDeltaTime;

    private Long okValidateDeltaMs;
    private Long maxOkValidateDeltaMs;
    @JsonFormat(pattern="HH:mm:ss.SSS")
    private Date lastOkValidateDeltaTime;


    public void setAddNewDeltaMs(Long deltaMs) {
        this.deltaMs = deltaMs;
        this.lastDeltaTime = new Date();
        if (maxDeltaMs == null || maxDeltaMs < deltaMs) {
            this.maxDeltaMs = deltaMs;
        }
    }

    public void setBtmDeltaMs(Long deltaMs) {
        this.btmDeltaMs = deltaMs;
        this.lastBtmDeltaTime = new Date();
        if (maxBtmDeltaMs == null || maxBtmDeltaMs < deltaMs) {
            this.maxBtmDeltaMs = deltaMs;
        }
    }

    public void setOkDeltaMs(Long deltaMs) {
        this.okDeltaMs = deltaMs;
        this.lastOkDeltaTime = new Date();
        if (maxOkDeltaMs == null || maxOkDeltaMs < deltaMs) {
            this.maxOkDeltaMs = deltaMs;
        }
    }

    public void setBtmValidateDeltaMs(Long deltaMs) {
        this.btmValidateDeltaMs = deltaMs;
        this.lastBtmValidateDeltaTime = new Date();
        if (maxBtmValidateDeltaMs == null || maxBtmValidateDeltaMs < deltaMs) {
            this.maxBtmValidateDeltaMs = deltaMs;
        }
    }

    public void setOkValidateDeltaMs(Long deltaMs) {
        this.okValidateDeltaMs = deltaMs;
        this.lastOkValidateDeltaTime = new Date();
        if (maxOkValidateDeltaMs == null || maxOkValidateDeltaMs < deltaMs) {
            this.maxOkValidateDeltaMs = deltaMs;
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
