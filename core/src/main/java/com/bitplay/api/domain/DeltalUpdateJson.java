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
    private String cumDiffFact1;
    private String cumDiffFact2;
    private String cumCom1;
    private String cumCom2;
    private String count1;
    private String count2;
    private String cumBitmexMCom;
    private String reserveBtc1;
    private String reserveBtc2;
    private String hedgeAmount;
    private String fundingRateFee;

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

    public String getCumDiffFact1() {
        return cumDiffFact1;
    }

    public void setCumDiffFact1(String cumDiffFact1) {
        this.cumDiffFact1 = cumDiffFact1;
    }

    public String getCumDiffFact2() {
        return cumDiffFact2;
    }

    public void setCumDiffFact2(String cumDiffFact2) {
        this.cumDiffFact2 = cumDiffFact2;
    }

    public String getCumCom1() {
        return cumCom1;
    }

    public void setCumCom1(String cumCom1) {
        this.cumCom1 = cumCom1;
    }

    public String getCumCom2() {
        return cumCom2;
    }

    public void setCumCom2(String cumCom2) {
        this.cumCom2 = cumCom2;
    }

    public String getCount1() {
        return count1;
    }

    public void setCount1(String count1) {
        this.count1 = count1;
    }

    public String getCount2() {
        return count2;
    }

    public void setCount2(String count2) {
        this.count2 = count2;
    }

    public String getCumBitmexMCom() {
        return cumBitmexMCom;
    }

    public void setCumBitmexMCom(String cumBitmexMCom) {
        this.cumBitmexMCom = cumBitmexMCom;
    }

    public String getReserveBtc1() {
        return reserveBtc1;
    }

    public void setReserveBtc1(String reserveBtc1) {
        this.reserveBtc1 = reserveBtc1;
    }

    public String getReserveBtc2() {
        return reserveBtc2;
    }

    public void setReserveBtc2(String reserveBtc2) {
        this.reserveBtc2 = reserveBtc2;
    }

    public String getHedgeAmount() {
        return hedgeAmount;
    }

    public void setHedgeAmount(String hedgeAmount) {
        this.hedgeAmount = hedgeAmount;
    }

    public String getFundingRateFee() {
        return fundingRateFee;
    }

    public void setFundingRateFee(String fundingRateFee) {
        this.fundingRateFee = fundingRateFee;
    }
}
