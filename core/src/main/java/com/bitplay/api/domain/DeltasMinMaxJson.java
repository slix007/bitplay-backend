package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 7/17/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeltasMinMaxJson {

    private String bDeltaMin;
    private String oDeltaMin;
    private String bDeltaMax;
    private String oDeltaMax;

    public DeltasMinMaxJson(String bDeltaMin, String oDeltaMin, String bDeltaMax, String oDeltaMax) {
        this.bDeltaMin = bDeltaMin;
        this.oDeltaMin = oDeltaMin;
        this.bDeltaMax = bDeltaMax;
        this.oDeltaMax = oDeltaMax;
    }

    public String getbDeltaMin() {
        return bDeltaMin;
    }

    public String getoDeltaMin() {
        return oDeltaMin;
    }

    public String getbDeltaMax() {
        return bDeltaMax;
    }

    public String getoDeltaMax() {
        return oDeltaMax;
    }
}
