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
    private String buValue;
    private String cumDelta;
    private String lastDelta;
    private String cumDeltaFact;
    private String cumDiffs;

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

    public String getBuValue() {
        return buValue;
    }

    public void setBuValue(String buValue) {
        this.buValue = buValue;
    }

    public String getCumDelta() {
        return cumDelta;
    }

    public void setCumDelta(String cumDelta) {
        this.cumDelta = cumDelta;
    }

    public String getLastDelta() {
        return lastDelta;
    }

    public void setLastDelta(String lastDelta) {
        this.lastDelta = lastDelta;
    }

    public String getCumDeltaFact() {
        return cumDeltaFact;
    }

    public void setCumDeltaFact(String cumDeltaFact) {
        this.cumDeltaFact = cumDeltaFact;
    }

    public String getCumDiffs() {
        return cumDiffs;
    }

    public void setCumDiffs(String cumDiffs) {
        this.cumDiffs = cumDiffs;
    }
}
