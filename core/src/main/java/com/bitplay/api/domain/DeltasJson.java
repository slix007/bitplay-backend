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
    private String cumAvgDelta;
    private String lastDelta;
    private String cumDeltaFact;
    private String cumAvgDeltaFact;
    private String cumDiffFactBr;
    private String cumAvgDiffFactBr;
    private String cumDiffFact1;
    private String cumDiffFact2;
    private String cumAvgDiffFact1;
    private String cumAvgDiffFact2;
    private String cumAvgDiffFact;
    private String cumCom1;
    private String cumCom2;
    private String cumAvgCom1;
    private String cumAvgCom2;
    private String count1;
    private String count2;
    private String cumBitmexMCom;
    private String cumAvgBitmexMCom;
    private String reserveBtc1;
    private String reserveBtc2;
    private String hedgeAmount;
    private String fundingRateFee;

    public DeltasJson(String delta1, String delta2, String border1, String border2, String makerDelta, String sumDelta, String periodSec, String buValue, String cumDelta, String cumAvgDelta, String lastDelta, String cumDeltaFact, String cumAvgDeltaFact, String cumDiffFactBr, String cumAvgDiffFactBr, String cumDiffFact1, String cumDiffFact2, String cumAvgDiffFact1, String cumAvgDiffFact2, String cumAvgDiffFact, String cumCom1, String cumCom2, String cumAvgCom1, String cumAvgCom2, String count1, String count2, String cumBitmexMCom, String cumAvgBitmexMCom, String reserveBtc1, String reserveBtc2, String hedgeAmount, String fundingRateFee) {
        this.delta1 = delta1;
        this.delta2 = delta2;
        this.border1 = border1;
        this.border2 = border2;
        this.makerDelta = makerDelta;
        this.sumDelta = sumDelta;
        this.periodSec = periodSec;
        this.buValue = buValue;
        this.cumDelta = cumDelta;
        this.cumAvgDelta = cumAvgDelta;
        this.lastDelta = lastDelta;
        this.cumDeltaFact = cumDeltaFact;
        this.cumAvgDeltaFact = cumAvgDeltaFact;
        this.cumDiffFactBr = cumDiffFactBr;
        this.cumAvgDiffFactBr = cumAvgDiffFactBr;
        this.cumDiffFact1 = cumDiffFact1;
        this.cumDiffFact2 = cumDiffFact2;
        this.cumAvgDiffFact1 = cumAvgDiffFact1;
        this.cumAvgDiffFact2 = cumAvgDiffFact2;
        this.cumAvgDiffFact = cumAvgDiffFact;
        this.cumCom1 = cumCom1;
        this.cumCom2 = cumCom2;
        this.cumAvgCom1 = cumAvgCom1;
        this.cumAvgCom2 = cumAvgCom2;
        this.count1 = count1;
        this.count2 = count2;
        this.cumBitmexMCom = cumBitmexMCom;
        this.cumAvgBitmexMCom = cumAvgBitmexMCom;
        this.reserveBtc1 = reserveBtc1;
        this.reserveBtc2 = reserveBtc2;
        this.hedgeAmount = hedgeAmount;
        this.fundingRateFee = fundingRateFee;
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

    public String getCumAvgDelta() {
        return cumAvgDelta;
    }

    public String getLastDelta() {
        return lastDelta;
    }

    public String getCumDeltaFact() {
        return cumDeltaFact;
    }

    public String getCumAvgDeltaFact() {
        return cumAvgDeltaFact;
    }

    public String getCumDiffFactBr() {
        return cumDiffFactBr;
    }

    public String getCumAvgDiffFactBr() {
        return cumAvgDiffFactBr;
    }

    public String getCumDiffFact1() {
        return cumDiffFact1;
    }

    public String getCumDiffFact2() {
        return cumDiffFact2;
    }

    public String getCumAvgDiffFact1() {
        return cumAvgDiffFact1;
    }

    public String getCumAvgDiffFact2() {
        return cumAvgDiffFact2;
    }

    public String getCumAvgDiffFact() {
        return cumAvgDiffFact;
    }

    public String getCumCom1() {
        return cumCom1;
    }

    public String getCumCom2() {
        return cumCom2;
    }

    public String getCumAvgCom1() {
        return cumAvgCom1;
    }

    public String getCumAvgCom2() {
        return cumAvgCom2;
    }

    public String getCount1() {
        return count1;
    }

    public String getCount2() {
        return count2;
    }

    public String getCumBitmexMCom() {
        return cumBitmexMCom;
    }

    public String getCumAvgBitmexMCom() {
        return cumAvgBitmexMCom;
    }

    public String getReserveBtc1() {
        return reserveBtc1;
    }

    public String getReserveBtc2() {
        return reserveBtc2;
    }

    public String getHedgeAmount() {
        return hedgeAmount;
    }

    public String getFundingRateFee() {
        return fundingRateFee;
    }
}