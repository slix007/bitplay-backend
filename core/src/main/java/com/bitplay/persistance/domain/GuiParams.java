package com.bitplay.persistance.domain;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 4/18/17.
 */
@Document(collection="guiParamsCollection")
@TypeAlias("guiParams")
public class GuiParams extends AbstractDocument {

    private BigDecimal border1 = BigDecimal.valueOf(10000);
    private BigDecimal border2 = BigDecimal.valueOf(10000);
    private BigDecimal makerDelta = BigDecimal.ZERO;
    private BigDecimal sumDelta = new BigDecimal(5);
    private BigDecimal buValue = BigDecimal.ZERO;
    private Integer periodSec = 60;
    private BigDecimal cumDelta = BigDecimal.ZERO;
    private BigDecimal cumDeltaMin = BigDecimal.valueOf(10000);
    private BigDecimal cumDeltaMax = BigDecimal.ZERO;
    private BigDecimal cumDeltaFact = BigDecimal.ZERO;
    private BigDecimal cumDeltaFactMin = BigDecimal.valueOf(10000);
    private BigDecimal cumDeltaFactMax = BigDecimal.ZERO;
    private String lastDelta = null;
    private BigDecimal cumDiffFact1 = BigDecimal.ZERO;
    private BigDecimal cumDiffFact1Min = BigDecimal.valueOf(10000);
    private BigDecimal cumDiffFact1Max = BigDecimal.ZERO;
    private BigDecimal cumDiffFact2 = BigDecimal.ZERO;
    private BigDecimal cumDiffFact2Min = BigDecimal.valueOf(10000);
    private BigDecimal cumDiffFact2Max = BigDecimal.ZERO;
    private BigDecimal cumDiffsFactMin = BigDecimal.valueOf(10000);
    private BigDecimal cumDiffsFactMax = BigDecimal.ZERO;
    private BigDecimal cumDiffFactBr = BigDecimal.ZERO;
    private BigDecimal cumDiffFactBrMin = BigDecimal.valueOf(10000);
    private BigDecimal cumDiffFactBrMax = BigDecimal.ZERO;
    private BigDecimal cumAvgDiffFactBr = BigDecimal.ZERO;
    private BigDecimal cumAvgDiffFact1 = BigDecimal.ZERO;
    private BigDecimal cumAvgDiffFact2 = BigDecimal.ZERO;
    private BigDecimal cumAvgDiffFact = BigDecimal.ZERO;
    private BigDecimal diffFactMin = BigDecimal.valueOf(10000);
    private BigDecimal diffFactMax = BigDecimal.ZERO;
    private BigDecimal diffFact1Min = BigDecimal.valueOf(10000);
    private BigDecimal diffFact1Max = BigDecimal.ZERO;
    private BigDecimal diffFact2Min = BigDecimal.valueOf(10000);
    private BigDecimal diffFact2Max = BigDecimal.ZERO;
    private BigDecimal comMin = BigDecimal.valueOf(10000);
    private BigDecimal comMax = BigDecimal.ZERO;
    private BigDecimal com1 = BigDecimal.ZERO;
    private BigDecimal com1Min = BigDecimal.valueOf(10000);
    private BigDecimal com1Max = BigDecimal.ZERO;
    private BigDecimal com2 = BigDecimal.ZERO;
    private BigDecimal com2Min = BigDecimal.valueOf(10000);
    private BigDecimal com2Max = BigDecimal.ZERO;
    private BigDecimal bitmexMComMin = BigDecimal.valueOf(10000);
    private BigDecimal bitmexMComMax = BigDecimal.ZERO;
    private BigDecimal cumBitmexMCom = BigDecimal.ZERO;
    private BigDecimal cumCom1 = BigDecimal.ZERO;
    private BigDecimal cumCom2 = BigDecimal.ZERO;

    private BigDecimal avgDelta = BigDecimal.ZERO;
    private BigDecimal cumAvgDelta = BigDecimal.ZERO;
    private BigDecimal avgDeltaFact = BigDecimal.ZERO;
    private BigDecimal cumAvgDeltaFact = BigDecimal.ZERO;
    private BigDecimal avgCom1 = BigDecimal.ZERO;
    private BigDecimal avgCom2 = BigDecimal.ZERO;
    private BigDecimal avgCom = BigDecimal.ZERO;
    private BigDecimal cumAvgCom1 = BigDecimal.ZERO;
    private BigDecimal cumAvgCom2 = BigDecimal.ZERO;
    private BigDecimal cumAvgCom = BigDecimal.ZERO;
    private BigDecimal avgBitmexMCom = BigDecimal.ZERO;
    private BigDecimal cumAvgBitmexMCom = BigDecimal.ZERO;

    private int counter1 = 0;
    private int counter2 = 0;
//    private BigDecimal block1 = BigDecimal.valueOf(100);
//    private BigDecimal block2 = BigDecimal.valueOf(1);
    private BigDecimal posBefore = BigDecimal.ZERO;
    private BigDecimal volPlan = BigDecimal.ZERO;
    private BigDecimal reserveBtc1 = BigDecimal.valueOf(0.00001);
    private BigDecimal reserveBtc2 = BigDecimal.valueOf(0.00001);
    private String okCoinOrderType = "maker";
    private BigDecimal hedgeAmount = BigDecimal.ZERO;
    private String posCorr = "stopped"; //enabled
    private BigDecimal maxDiffCorr = BigDecimal.valueOf(1000);
    private Long periodToCorrection = 30L;
    private BigDecimal bMrLiq = BigDecimal.valueOf(75);
    private BigDecimal oMrLiq = BigDecimal.valueOf(20);
    private BigDecimal bDQLOpenMin = BigDecimal.valueOf(300);
    private BigDecimal oDQLOpenMin = BigDecimal.valueOf(350);
    private BigDecimal bDQLCloseMin = BigDecimal.valueOf(100);
    private BigDecimal oDQLCloseMin = BigDecimal.valueOf(150);
    private BigDecimal fundingRateFee = BigDecimal.ZERO;

    public GuiParams() {
    }

    public BigDecimal getBorder1() {
        return border1;
    }

    public void setBorder1(BigDecimal border1) {
        this.border1 = border1;
    }

    public BigDecimal getBorder2() {
        return border2;
    }

    public void setBorder2(BigDecimal border2) {
        this.border2 = border2;
    }

    public BigDecimal getMakerDelta() {
        return makerDelta;
    }

    public void setMakerDelta(BigDecimal makerDelta) {
        this.makerDelta = makerDelta;
    }

    public BigDecimal getSumDelta() {
        return sumDelta;
    }

    public void setSumDelta(BigDecimal sumDelta) {
        this.sumDelta = sumDelta;
    }

    public BigDecimal getBuValue() {
        return buValue;
    }

    public void setBuValue(BigDecimal buValue) {
        this.buValue = buValue;
    }

    public Integer getPeriodSec() {
        return periodSec;
    }

    public void setPeriodSec(Integer periodSec) {
        this.periodSec = periodSec;
    }

    public BigDecimal getCumDelta() {
        return cumDelta;
    }

    public void setCumDelta(BigDecimal cumDelta) {
        this.cumDelta = cumDelta;
    }

    public BigDecimal getCumDeltaMin() {
        return cumDeltaMin;
    }

    public void setCumDeltaMin(BigDecimal cumDeltaMin) {
        this.cumDeltaMin = cumDeltaMin;
    }

    public BigDecimal getCumDeltaMax() {
        return cumDeltaMax;
    }

    public void setCumDeltaMax(BigDecimal cumDeltaMax) {
        this.cumDeltaMax = cumDeltaMax;
    }

    public BigDecimal getCumDeltaFact() {
        return cumDeltaFact;
    }

    public void setCumDeltaFact(BigDecimal cumDeltaFact) {
        this.cumDeltaFact = cumDeltaFact;
    }

    public BigDecimal getCumDeltaFactMin() {
        return cumDeltaFactMin;
    }

    public void setCumDeltaFactMin(BigDecimal cumDeltaFactMin) {
        this.cumDeltaFactMin = cumDeltaFactMin;
    }

    public BigDecimal getCumDeltaFactMax() {
        return cumDeltaFactMax;
    }

    public void setCumDeltaFactMax(BigDecimal cumDeltaFactMax) {
        this.cumDeltaFactMax = cumDeltaFactMax;
    }

    public String getLastDelta() {
        return lastDelta;
    }

    public void setLastDelta(String lastDelta) {
        this.lastDelta = lastDelta;
    }

    public BigDecimal getCumDiffFact1() {
        return cumDiffFact1;
    }

    public void setCumDiffFact1(BigDecimal cumDiffFact1) {
        this.cumDiffFact1 = cumDiffFact1;
    }

    public BigDecimal getCumDiffFact1Min() {
        return cumDiffFact1Min;
    }

    public void setCumDiffFact1Min(BigDecimal cumDiffFact1Min) {
        this.cumDiffFact1Min = cumDiffFact1Min;
    }

    public BigDecimal getCumDiffFact1Max() {
        return cumDiffFact1Max;
    }

    public void setCumDiffFact1Max(BigDecimal cumDiffFact1Max) {
        this.cumDiffFact1Max = cumDiffFact1Max;
    }

    public BigDecimal getCumDiffFact2() {
        return cumDiffFact2;
    }

    public void setCumDiffFact2(BigDecimal cumDiffFact2) {
        this.cumDiffFact2 = cumDiffFact2;
    }

    public BigDecimal getCumDiffFact2Min() {
        return cumDiffFact2Min;
    }

    public void setCumDiffFact2Min(BigDecimal cumDiffFact2Min) {
        this.cumDiffFact2Min = cumDiffFact2Min;
    }

    public BigDecimal getCumDiffFact2Max() {
        return cumDiffFact2Max;
    }

    public void setCumDiffFact2Max(BigDecimal cumDiffFact2Max) {
        this.cumDiffFact2Max = cumDiffFact2Max;
    }

    public BigDecimal getCumDiffsFactMin() {
        return cumDiffsFactMin;
    }

    public void setCumDiffsFactMin(BigDecimal cumDiffsFactMin) {
        this.cumDiffsFactMin = cumDiffsFactMin;
    }

    public BigDecimal getCumDiffsFactMax() {
        return cumDiffsFactMax;
    }

    public void setCumDiffsFactMax(BigDecimal cumDiffsFactMax) {
        this.cumDiffsFactMax = cumDiffsFactMax;
    }

    public BigDecimal getCumDiffFactBr() {
        return cumDiffFactBr;
    }

    public void setCumDiffFactBr(BigDecimal cumDiffFactBr) {
        this.cumDiffFactBr = cumDiffFactBr;
    }

    public BigDecimal getCumDiffFactBrMin() {
        return cumDiffFactBrMin;
    }

    public void setCumDiffFactBrMin(BigDecimal cumDiffFactBrMin) {
        this.cumDiffFactBrMin = cumDiffFactBrMin;
    }

    public BigDecimal getCumDiffFactBrMax() {
        return cumDiffFactBrMax;
    }

    public void setCumDiffFactBrMax(BigDecimal cumDiffFactBrMax) {
        this.cumDiffFactBrMax = cumDiffFactBrMax;
    }

    public BigDecimal getCumAvgDiffFactBr() {
        return cumAvgDiffFactBr;
    }

    public void setCumAvgDiffFactBr(BigDecimal cumAvgDiffFactBr) {
        this.cumAvgDiffFactBr = cumAvgDiffFactBr;
    }

    public BigDecimal getCumAvgDiffFact1() {
        return cumAvgDiffFact1;
    }

    public void setCumAvgDiffFact1(BigDecimal cumAvgDiffFact1) {
        this.cumAvgDiffFact1 = cumAvgDiffFact1;
    }

    public BigDecimal getCumAvgDiffFact2() {
        return cumAvgDiffFact2;
    }

    public void setCumAvgDiffFact2(BigDecimal cumAvgDiffFact2) {
        this.cumAvgDiffFact2 = cumAvgDiffFact2;
    }

    public BigDecimal getCumAvgDiffFact() {
        return cumAvgDiffFact;
    }

    public void setCumAvgDiffFact(BigDecimal cumAvgDiffFact) {
        this.cumAvgDiffFact = cumAvgDiffFact;
    }

    public BigDecimal getDiffFactMin() {
        return diffFactMin;
    }

    public void setDiffFactMin(BigDecimal diffFactMin) {
        this.diffFactMin = diffFactMin;
    }

    public BigDecimal getDiffFactMax() {
        return diffFactMax;
    }

    public void setDiffFactMax(BigDecimal diffFactMax) {
        this.diffFactMax = diffFactMax;
    }

    public BigDecimal getDiffFact1Min() {
        return diffFact1Min;
    }

    public void setDiffFact1Min(BigDecimal diffFact1Min) {
        this.diffFact1Min = diffFact1Min;
    }

    public BigDecimal getDiffFact1Max() {
        return diffFact1Max;
    }

    public void setDiffFact1Max(BigDecimal diffFact1Max) {
        this.diffFact1Max = diffFact1Max;
    }

    public BigDecimal getDiffFact2Min() {
        return diffFact2Min;
    }

    public void setDiffFact2Min(BigDecimal diffFact2Min) {
        this.diffFact2Min = diffFact2Min;
    }

    public BigDecimal getDiffFact2Max() {
        return diffFact2Max;
    }

    public void setDiffFact2Max(BigDecimal diffFact2Max) {
        this.diffFact2Max = diffFact2Max;
    }

    public BigDecimal getComMin() {
        return comMin;
    }

    public void setComMin(BigDecimal comMin) {
        this.comMin = comMin;
    }

    public BigDecimal getComMax() {
        return comMax;
    }

    public void setComMax(BigDecimal comMax) {
        this.comMax = comMax;
    }

    public BigDecimal getCom1() {
        return com1;
    }

    public void setCom1(BigDecimal com1) {
        this.com1 = com1;
    }

    public BigDecimal getCom1Min() {
        return com1Min;
    }

    public void setCom1Min(BigDecimal com1Min) {
        this.com1Min = com1Min;
    }

    public BigDecimal getCom1Max() {
        return com1Max;
    }

    public void setCom1Max(BigDecimal com1Max) {
        this.com1Max = com1Max;
    }

    public BigDecimal getCom2() {
        return com2;
    }

    public void setCom2(BigDecimal com2) {
        this.com2 = com2;
    }

    public BigDecimal getCom2Min() {
        return com2Min;
    }

    public void setCom2Min(BigDecimal com2Min) {
        this.com2Min = com2Min;
    }

    public BigDecimal getCom2Max() {
        return com2Max;
    }

    public void setCom2Max(BigDecimal com2Max) {
        this.com2Max = com2Max;
    }

    public BigDecimal getBitmexMComMin() {
        return bitmexMComMin;
    }

    public void setBitmexMComMin(BigDecimal bitmexMComMin) {
        this.bitmexMComMin = bitmexMComMin;
    }

    public BigDecimal getBitmexMComMax() {
        return bitmexMComMax;
    }

    public void setBitmexMComMax(BigDecimal bitmexMComMax) {
        this.bitmexMComMax = bitmexMComMax;
    }

    public BigDecimal getCumBitmexMCom() {
        return cumBitmexMCom;
    }

    public void setCumBitmexMCom(BigDecimal cumBitmexMCom) {
        this.cumBitmexMCom = cumBitmexMCom;
    }

    public BigDecimal getCumCom1() {
        return cumCom1;
    }

    public void setCumCom1(BigDecimal cumCom1) {
        this.cumCom1 = cumCom1;
    }

    public BigDecimal getCumCom2() {
        return cumCom2;
    }

    public void setCumCom2(BigDecimal cumCom2) {
        this.cumCom2 = cumCom2;
    }

    public BigDecimal getAvgDelta() {
        return avgDelta;
    }

    public void setAvgDelta(BigDecimal avgDelta) {
        this.avgDelta = avgDelta;
    }

    public BigDecimal getCumAvgDelta() {
        return cumAvgDelta;
    }

    public void setCumAvgDelta(BigDecimal cumAvgDelta) {
        this.cumAvgDelta = cumAvgDelta;
    }

    public BigDecimal getAvgDeltaFact() {
        return avgDeltaFact;
    }

    public void setAvgDeltaFact(BigDecimal avgDeltaFact) {
        this.avgDeltaFact = avgDeltaFact;
    }

    public BigDecimal getCumAvgDeltaFact() {
        return cumAvgDeltaFact;
    }

    public void setCumAvgDeltaFact(BigDecimal cumAvgDeltaFact) {
        this.cumAvgDeltaFact = cumAvgDeltaFact;
    }

    public BigDecimal getAvgCom1() {
        return avgCom1;
    }

    public void setAvgCom1(BigDecimal avgCom1) {
        this.avgCom1 = avgCom1;
    }

    public BigDecimal getAvgCom2() {
        return avgCom2;
    }

    public void setAvgCom2(BigDecimal avgCom2) {
        this.avgCom2 = avgCom2;
    }

    public BigDecimal getAvgCom() {
        return avgCom;
    }

    public void setAvgCom(BigDecimal avgCom) {
        this.avgCom = avgCom;
    }

    public BigDecimal getCumAvgCom1() {
        return cumAvgCom1;
    }

    public void setCumAvgCom1(BigDecimal cumAvgCom1) {
        this.cumAvgCom1 = cumAvgCom1;
    }

    public BigDecimal getCumAvgCom2() {
        return cumAvgCom2;
    }

    public void setCumAvgCom2(BigDecimal cumAvgCom2) {
        this.cumAvgCom2 = cumAvgCom2;
    }

    public BigDecimal getCumAvgCom() {
        return cumAvgCom;
    }

    public void setCumAvgCom(BigDecimal cumAvgCom) {
        this.cumAvgCom = cumAvgCom;
    }

    public BigDecimal getAvgBitmexMCom() {
        return avgBitmexMCom;
    }

    public void setAvgBitmexMCom(BigDecimal avgBitmexMCom) {
        this.avgBitmexMCom = avgBitmexMCom;
    }

    public BigDecimal getCumAvgBitmexMCom() {
        return cumAvgBitmexMCom;
    }

    public void setCumAvgBitmexMCom(BigDecimal cumAvgBitmexMCom) {
        this.cumAvgBitmexMCom = cumAvgBitmexMCom;
    }

    public int getCounter1() {
        return counter1;
    }

    public void setCounter1(int counter1) {
        this.counter1 = counter1;
    }

    public int getCounter2() {
        return counter2;
    }

    public void setCounter2(int counter2) {
        this.counter2 = counter2;
    }

    public BigDecimal getPosBefore() {
        return posBefore;
    }

    public void setPosBefore(BigDecimal posBefore) {
        this.posBefore = posBefore;
    }

    public BigDecimal getVolPlan() {
        return volPlan;
    }

    public void setVolPlan(BigDecimal volPlan) {
        this.volPlan = volPlan;
    }

    public BigDecimal getReserveBtc1() {
        return reserveBtc1;
    }

    public void setReserveBtc1(BigDecimal reserveBtc1) {
        this.reserveBtc1 = reserveBtc1;
    }

    public BigDecimal getReserveBtc2() {
        return reserveBtc2;
    }

    public void setReserveBtc2(BigDecimal reserveBtc2) {
        this.reserveBtc2 = reserveBtc2;
    }

    public String getOkCoinOrderType() {
        return okCoinOrderType;
    }

    public void setOkCoinOrderType(String okCoinOrderType) {
        this.okCoinOrderType = okCoinOrderType;
    }

    public BigDecimal getHedgeAmount() {
        return hedgeAmount;
    }

    public void setHedgeAmount(BigDecimal hedgeAmount) {
        this.hedgeAmount = hedgeAmount;
    }

    public String getPosCorr() {
        return posCorr;
    }

    public void setPosCorr(String posCorr) {
        this.posCorr = posCorr;
    }

    public BigDecimal getMaxDiffCorr() {
        return maxDiffCorr;
    }

    public void setMaxDiffCorr(BigDecimal maxDiffCorr) {
        this.maxDiffCorr = maxDiffCorr;
    }

    public Long getPeriodToCorrection() {
        return periodToCorrection;
    }

    public void setPeriodToCorrection(Long periodToCorrection) {
        this.periodToCorrection = periodToCorrection;
    }

    public BigDecimal getbMrLiq() {
        return bMrLiq;
    }

    public void setbMrLiq(BigDecimal bMrLiq) {
        this.bMrLiq = bMrLiq;
    }

    public BigDecimal getoMrLiq() {
        return oMrLiq;
    }

    public void setoMrLiq(BigDecimal oMrLiq) {
        this.oMrLiq = oMrLiq;
    }

    public BigDecimal getbDQLOpenMin() {
        return bDQLOpenMin;
    }

    public void setbDQLOpenMin(BigDecimal bDQLOpenMin) {
        this.bDQLOpenMin = bDQLOpenMin;
    }

    public BigDecimal getoDQLOpenMin() {
        return oDQLOpenMin;
    }

    public void setoDQLOpenMin(BigDecimal oDQLOpenMin) {
        this.oDQLOpenMin = oDQLOpenMin;
    }

    public BigDecimal getbDQLCloseMin() {
        return bDQLCloseMin;
    }

    public void setbDQLCloseMin(BigDecimal bDQLCloseMin) {
        this.bDQLCloseMin = bDQLCloseMin;
    }

    public BigDecimal getoDQLCloseMin() {
        return oDQLCloseMin;
    }

    public void setoDQLCloseMin(BigDecimal oDQLCloseMin) {
        this.oDQLCloseMin = oDQLCloseMin;
    }

    public BigDecimal getFundingRateFee() {
        return fundingRateFee;
    }

    public void setFundingRateFee(BigDecimal fundingRateFee) {
        this.fundingRateFee = fundingRateFee;
    }
}