package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import lombok.Getter;

/**
 * Created by Sergey Shurmin on 7/17/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
public class DeltasMinMaxJson {

    public enum Color {
        BLACK,
        ORANGE,
    }

    private String btmDeltaMin;
    private String okDeltaMin;
    private String btmDeltaMax;
    private String okDeltaMax;
    private Color btmMaxColor;
    private Color okMaxColor;

    public DeltasMinMaxJson(String bDeltaMin, String oDeltaMin, String bDeltaMax, String oDeltaMax) {
        this.btmDeltaMin = bDeltaMin;
        this.okDeltaMin = oDeltaMin;
        this.btmDeltaMax = bDeltaMax;
        this.okDeltaMax = oDeltaMax;
    }

    public DeltasMinMaxJson(String bDeltaMin, String oDeltaMin, String bDeltaMax, String oDeltaMax, Instant bLastRise, Instant oLastRise) {
        this.btmDeltaMin = bDeltaMin;
        this.okDeltaMin = oDeltaMin;
        this.btmDeltaMax = bDeltaMax;
        this.okDeltaMax = oDeltaMax;
        Instant currTime = Instant.now();
        this.btmMaxColor = bLastRise.plusSeconds(120).isAfter(currTime)
                ? Color.ORANGE
                : Color.BLACK;
        this.okMaxColor = oLastRise.plusSeconds(120).isAfter(currTime)
                ? Color.ORANGE
                : Color.BLACK;
    }
}
