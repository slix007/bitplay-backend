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
    private String buValue;
    private String cumDelta;
    private String cumAstDelta;
    private String lastDelta;
    private String cumDeltaFact;
    private String cumAstDeltaFact;
    private String cumDiffFactBr;
    private String cumDiffFact1;
    private String cumDiffFact2;
    private String cumDiffFact;
    private String cumAstDiffFact1;
    private String cumAstDiffFact2;
    private String cumAstDiffFact;
    private String cumCom1;
    private String cumCom2;
    private String cumAstCom1;
    private String cumAstCom2;
    private String count1;
    private String count2;
    private String cumBitmexMCom;
    private String cumAstBitmexMCom;
    private String reserveBtc1;
    private String reserveBtc2;
    private String hedgeAmount;
    private String fundingRateFee;
    private String slip;
    private String delta1Calc;
    private String delta2Calc;

    public DeltasJson(String delta1, String delta2, String border1, String border2, String makerDelta, String buValue, String cumDelta,
            String cumAstDelta, String lastDelta, String cumDeltaFact, String cumAstDeltaFact, String cumDiffFactBr, String cumDiffFact1,
            String cumDiffFact2, String cumDiffFact, String cumAstDiffFact1, String cumAstDiffFact2, String cumAstDiffFact, String cumCom1,
            String cumCom2, String cumAstCom1, String cumAstCom2, String count1, String count2, String cumBitmexMCom, String cumAstBitmexMCom,
            String reserveBtc1, String reserveBtc2, String hedgeAmount, String fundingRateFee, String slip, String delta1Calc, String delta2Calc) {
        this.delta1 = delta1;
        this.delta2 = delta2;
        this.border1 = border1;
        this.border2 = border2;
        this.makerDelta = makerDelta;
        this.buValue = buValue;
        this.cumDelta = cumDelta;
        this.cumAstDelta = cumAstDelta;
        this.lastDelta = lastDelta;
        this.cumDeltaFact = cumDeltaFact;
        this.cumAstDeltaFact = cumAstDeltaFact;
        this.cumDiffFactBr = cumDiffFactBr;
        this.cumDiffFact1 = cumDiffFact1;
        this.cumDiffFact2 = cumDiffFact2;
        this.cumDiffFact = cumDiffFact;
        this.cumAstDiffFact1 = cumAstDiffFact1;
        this.cumAstDiffFact2 = cumAstDiffFact2;
        this.cumAstDiffFact = cumAstDiffFact;
        this.cumCom1 = cumCom1;
        this.cumCom2 = cumCom2;
        this.cumAstCom1 = cumAstCom1;
        this.cumAstCom2 = cumAstCom2;
        this.count1 = count1;
        this.count2 = count2;
        this.cumBitmexMCom = cumBitmexMCom;
        this.cumAstBitmexMCom = cumAstBitmexMCom;
        this.reserveBtc1 = reserveBtc1;
        this.reserveBtc2 = reserveBtc2;
        this.hedgeAmount = hedgeAmount;
        this.fundingRateFee = fundingRateFee;
        this.slip = slip;
        this.delta1Calc = delta1Calc;
        this.delta2Calc = delta2Calc;
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

    public String getBuValue() {
        return buValue;
    }

    public String getCumDelta() {
        return cumDelta;
    }

    public String getCumAstDelta() {
        return cumAstDelta;
    }

    public String getLastDelta() {
        return lastDelta;
    }

    public String getCumDeltaFact() {
        return cumDeltaFact;
    }

    public String getCumAstDeltaFact() {
        return cumAstDeltaFact;
    }

    public String getCumDiffFactBr() {
        return cumDiffFactBr;
    }

    public String getCumDiffFact1() {
        return cumDiffFact1;
    }

    public String getCumDiffFact2() {
        return cumDiffFact2;
    }

    public String getCumDiffFact() {
        return cumDiffFact;
    }

    public String getCumAstDiffFact1() {
        return cumAstDiffFact1;
    }

    public String getCumAstDiffFact2() {
        return cumAstDiffFact2;
    }

    public String getCumAstDiffFact() {
        return cumAstDiffFact;
    }

    public String getCumCom1() {
        return cumCom1;
    }

    public String getCumCom2() {
        return cumCom2;
    }

    public String getCumAstCom1() {
        return cumAstCom1;
    }

    public String getCumAstCom2() {
        return cumAstCom2;
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

    public String getCumAstBitmexMCom() {
        return cumAstBitmexMCom;
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

    public String getSlip() {
        return slip;
    }

    public String getDelta1Calc() {
        return delta1Calc;
    }

    public String getDelta2Calc() {
        return delta2Calc;
    }
}