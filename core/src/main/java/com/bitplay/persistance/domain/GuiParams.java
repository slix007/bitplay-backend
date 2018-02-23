package com.bitplay.persistance.domain;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Created by Sergey Shurmin on 4/18/17.
 */
@Document(collection="guiParamsCollection")
@TypeAlias("guiParams")
public class GuiParams extends AbstractDocument {

    private BigDecimal border1 = BigDecimal.valueOf(10000);
    private BigDecimal border2 = BigDecimal.valueOf(10000);
    private BigDecimal makerDelta = BigDecimal.ZERO;
    private BigDecimal buValue = BigDecimal.ZERO;
    private BigDecimal cumDelta = BigDecimal.ZERO;
    private BigDecimal cumDeltaFact = BigDecimal.ZERO;
    private String lastDelta = null;
    private BigDecimal cumDiffFact1 = BigDecimal.ZERO;
    private BigDecimal cumDiffFact2 = BigDecimal.ZERO;
    private BigDecimal cumDiffFact = BigDecimal.ZERO;
    private BigDecimal cumDiffFactBr = BigDecimal.ZERO;
    private BigDecimal cumAstDiffFact1 = BigDecimal.ZERO;
    private BigDecimal cumAstDiffFact2 = BigDecimal.ZERO;
    private BigDecimal cumAstDiffFact = BigDecimal.ZERO;
    private BigDecimal diffFact = BigDecimal.ZERO;
    private BigDecimal com1 = BigDecimal.ZERO;
    private BigDecimal com2 = BigDecimal.ZERO;
    private BigDecimal cumBitmexMCom = BigDecimal.ZERO;
    private BigDecimal cumCom1 = BigDecimal.ZERO;
    private BigDecimal cumCom2 = BigDecimal.ZERO;

    private BigDecimal astDelta1 = BigDecimal.ZERO;
    private BigDecimal astDelta2 = BigDecimal.ZERO;
    private BigDecimal cumAstDelta1 = BigDecimal.ZERO;
    private BigDecimal cumAstDelta2 = BigDecimal.ZERO;
    private BigDecimal astDeltaFact1 = BigDecimal.ZERO;
    private BigDecimal astDeltaFact2 = BigDecimal.ZERO;
    private BigDecimal cumAstDeltaFact1 = BigDecimal.ZERO;
    private BigDecimal cumAstDeltaFact2 = BigDecimal.ZERO;
    private BigDecimal astCom1 = BigDecimal.ZERO;
    private BigDecimal astCom2 = BigDecimal.ZERO;
    private BigDecimal astCom = BigDecimal.ZERO;
    private BigDecimal cumAstCom1 = BigDecimal.ZERO;
    private BigDecimal cumAstCom2 = BigDecimal.ZERO;
    private BigDecimal cumAstCom = BigDecimal.ZERO;
    private BigDecimal astBitmexMCom = BigDecimal.ZERO;
    private BigDecimal cumAstBitmexMCom = BigDecimal.ZERO;
    private BigDecimal cumSlipM = BigDecimal.ZERO;
    private BigDecimal cumSlipT = BigDecimal.ZERO;

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

    private Date lastOBChange;
    private Date lastCorrCheck;
    private Date lastMDCCheck;

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

    public BigDecimal getBuValue() {
        return buValue;
    }

    public void setBuValue(BigDecimal buValue) {
        this.buValue = buValue;
    }

    public BigDecimal getCumDelta() {
        return cumDelta;
    }

    public void setCumDelta(BigDecimal cumDelta) {
        this.cumDelta = cumDelta;
    }

    public BigDecimal getCumDeltaFact() {
        return cumDeltaFact;
    }

    public void setCumDeltaFact(BigDecimal cumDeltaFact) {
        this.cumDeltaFact = cumDeltaFact;
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

    public BigDecimal getCumDiffFact2() {
        return cumDiffFact2;
    }

    public void setCumDiffFact2(BigDecimal cumDiffFact2) {
        this.cumDiffFact2 = cumDiffFact2;
    }

    public BigDecimal getCumDiffFact() {
        return cumDiffFact;
    }

    public void setCumDiffFact(BigDecimal cumDiffFact) {
        this.cumDiffFact = cumDiffFact;
    }

    public BigDecimal getCumDiffFactBr() {
        return cumDiffFactBr;
    }

    public void setCumDiffFactBr(BigDecimal cumDiffFactBr) {
        this.cumDiffFactBr = cumDiffFactBr;
    }

    public BigDecimal getCumAstDiffFact1() {
        return cumAstDiffFact1;
    }

    public void setCumAstDiffFact1(BigDecimal cumAstDiffFact1) {
        this.cumAstDiffFact1 = cumAstDiffFact1;
    }

    public BigDecimal getCumAstDiffFact2() {
        return cumAstDiffFact2;
    }

    public void setCumAstDiffFact2(BigDecimal cumAstDiffFact2) {
        this.cumAstDiffFact2 = cumAstDiffFact2;
    }

    public BigDecimal getCumAstDiffFact() {
        return cumAstDiffFact;
    }

    public void setCumAstDiffFact(BigDecimal cumAstDiffFact) {
        this.cumAstDiffFact = cumAstDiffFact;
    }

    public BigDecimal getDiffFact() {
        return diffFact;
    }

    public void setDiffFact(BigDecimal diffFact) {
        this.diffFact = diffFact;
    }

    public BigDecimal getCom1() {
        return com1;
    }

    public void setCom1(BigDecimal com1) {
        this.com1 = com1;
    }

    public BigDecimal getCom2() {
        return com2;
    }

    public void setCom2(BigDecimal com2) {
        this.com2 = com2;
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

    public BigDecimal getAstDelta1() {
        return astDelta1;
    }

    public void setAstDelta1(BigDecimal astDelta1) {
        this.astDelta1 = astDelta1;
    }

    public BigDecimal getAstDelta2() {
        return astDelta2;
    }

    public void setAstDelta2(BigDecimal astDelta2) {
        this.astDelta2 = astDelta2;
    }

    public BigDecimal getCumAstDelta1() {
        return cumAstDelta1;
    }

    public void setCumAstDelta1(BigDecimal cumAstDelta1) {
        this.cumAstDelta1 = cumAstDelta1;
    }

    public BigDecimal getCumAstDelta2() {
        return cumAstDelta2;
    }

    public void setCumAstDelta2(BigDecimal cumAstDelta2) {
        this.cumAstDelta2 = cumAstDelta2;
    }

    public BigDecimal getAstDeltaFact1() {
        return astDeltaFact1;
    }

    public void setAstDeltaFact1(BigDecimal astDeltaFact1) {
        this.astDeltaFact1 = astDeltaFact1;
    }

    public BigDecimal getAstDeltaFact2() {
        return astDeltaFact2;
    }

    public void setAstDeltaFact2(BigDecimal astDeltaFact2) {
        this.astDeltaFact2 = astDeltaFact2;
    }

    public BigDecimal getCumAstDeltaFact1() {
        return cumAstDeltaFact1;
    }

    public void setCumAstDeltaFact1(BigDecimal cumAstDeltaFact1) {
        this.cumAstDeltaFact1 = cumAstDeltaFact1;
    }

    public BigDecimal getCumAstDeltaFact2() {
        return cumAstDeltaFact2;
    }

    public void setCumAstDeltaFact2(BigDecimal cumAstDeltaFact2) {
        this.cumAstDeltaFact2 = cumAstDeltaFact2;
    }

    public BigDecimal getAstCom1() {
        return astCom1;
    }

    public void setAstCom1(BigDecimal astCom1) {
        this.astCom1 = astCom1;
    }

    public BigDecimal getAstCom2() {
        return astCom2;
    }

    public void setAstCom2(BigDecimal astCom2) {
        this.astCom2 = astCom2;
    }

    public BigDecimal getAstCom() {
        return astCom;
    }

    public void setAstCom(BigDecimal astCom) {
        this.astCom = astCom;
    }

    public BigDecimal getCumAstCom1() {
        return cumAstCom1;
    }

    public void setCumAstCom1(BigDecimal cumAstCom1) {
        this.cumAstCom1 = cumAstCom1;
    }

    public BigDecimal getCumAstCom2() {
        return cumAstCom2;
    }

    public void setCumAstCom2(BigDecimal cumAstCom2) {
        this.cumAstCom2 = cumAstCom2;
    }

    public BigDecimal getCumAstCom() {
        return cumAstCom;
    }

    public void setCumAstCom(BigDecimal cumAstCom) {
        this.cumAstCom = cumAstCom;
    }

    public BigDecimal getAstBitmexMCom() {
        return astBitmexMCom;
    }

    public void setAstBitmexMCom(BigDecimal astBitmexMCom) {
        this.astBitmexMCom = astBitmexMCom;
    }

    public BigDecimal getCumAstBitmexMCom() {
        return cumAstBitmexMCom;
    }

    public void setCumAstBitmexMCom(BigDecimal cumAstBitmexMCom) {
        this.cumAstBitmexMCom = cumAstBitmexMCom;
    }

    public BigDecimal getCumSlipM() {
        return cumSlipM;
    }

    public void setCumSlipM(BigDecimal cumSlipM) {
        this.cumSlipM = cumSlipM;
    }

    public BigDecimal getCumSlipT() {
        return cumSlipT;
    }

    public void setCumSlipT(BigDecimal cumSlipT) {
        this.cumSlipT = cumSlipT;
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

    public Date getLastOBChange() {
        return lastOBChange;
    }

    public void setLastOBChange(Date lastOBChange) {
        this.lastOBChange = lastOBChange;
    }

    public Date getLastCorrCheck() {
        return lastCorrCheck;
    }

    public void setLastCorrCheck(Date lastCorrCheck) {
        this.lastCorrCheck = lastCorrCheck;
    }

    public Date getLastMDCCheck() {
        return lastMDCCheck;
    }

    public void setLastMDCCheck(Date lastMDCCheck) {
        this.lastMDCCheck = lastMDCCheck;
    }
}