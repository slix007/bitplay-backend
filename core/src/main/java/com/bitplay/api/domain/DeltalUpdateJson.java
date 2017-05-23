package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 4/22/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeltalUpdateJson {

    private String makerDelta;
    private String sumDelta;
    private String periodSec;

    public String getMakerDelta() {
        return makerDelta;
    }

    public void setMakerDelta(String makerDelta) {
        this.makerDelta = makerDelta;
    }

    public String getSumDelta() {
        return sumDelta;
    }

    public void setSumDelta(String sumDelta) {
        this.sumDelta = sumDelta;
    }

    public String getPeriodSec() {
        return periodSec;
    }

    public void setPeriodSec(String periodSec) {
        this.periodSec = periodSec;
    }
}
