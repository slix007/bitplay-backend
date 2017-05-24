package com.bitplay.api.domain;

/**
 * Created by Sergey Shurmin on 4/18/17.
 */
public class DeltasJson {

    private String delta1;
    private String delta2;
    private String border1;
    private String border2;
    private String makerDelta;
    private String sumDelta;
    private String periodSec;
    private String buValue;
    private String cumDelta;
    private String lastDelta;
    private String cumDeltaFact;
    private String cumDiffs;

    public DeltasJson(String delta1, String delta2, String border1, String border2, String makerDelta, String sumDelta, String periodSec, String buValue, String cumDelta, String lastDelta, String cumDeltaFact, String cumDiffs) {
        this.delta1 = delta1;
        this.delta2 = delta2;
        this.border1 = border1;
        this.border2 = border2;
        this.makerDelta = makerDelta;
        this.sumDelta = sumDelta;
        this.periodSec = periodSec;
        this.buValue = buValue;
        this.cumDelta = cumDelta;
        this.lastDelta = lastDelta;
        this.cumDeltaFact = cumDeltaFact;
        this.cumDiffs = cumDiffs;
    }

    public String getDelta1() {
        return delta1;
    }

    public String getDelta2() {
        return delta2;
    }

    public String getBorder1() {
        return border1;
    }

    public String getBorder2() {
        return border2;
    }

    public String getMakerDelta() {
        return makerDelta;
    }

    public String getSumDelta() {
        return sumDelta;
    }

    public String getPeriodSec() {
        return periodSec;
    }

    public String getBuValue() {
        return buValue;
    }

    public String getCumDelta() {
        return cumDelta;
    }

    public String getLastDelta() {
        return lastDelta;
    }

    public String getCumDeltaFact() {
        return cumDeltaFact;
    }

    public String getCumDiffs() {
        return cumDiffs;
    }
}
