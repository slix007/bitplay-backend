package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.AvgPrice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Sergey Shurmin on 5/24/17.
 */
public class OpenPrices {
    BigDecimal firstOpenPrice = BigDecimal.ZERO;
    BigDecimal secondOpenPrice = BigDecimal.ZERO;
    List<BigDecimal> borderList = new ArrayList<>();
    BigDecimal oBlock = BigDecimal.ZERO;
    BigDecimal bBlock = BigDecimal.ZERO;
    BigDecimal delta1Plan = BigDecimal.ZERO;
    BigDecimal delta2Plan = BigDecimal.ZERO;
    BigDecimal bPricePlan = BigDecimal.ZERO;
    BigDecimal oPricePlan = BigDecimal.ZERO;
    AvgPrice firstPriceFact = new AvgPrice();
    AvgPrice secondPriceFact = new AvgPrice();

    public BigDecimal getFirstOpenPrice() {
        return firstOpenPrice;
    }

    public void setFirstOpenPrice(BigDecimal firstOpenPrice) {
        final BigDecimal fop = firstOpenPrice.setScale(2, BigDecimal.ROUND_HALF_UP);
        firstPriceFact.setOpenPrice(fop);
        this.firstOpenPrice = fop;
    }

    public BigDecimal getSecondOpenPrice() {
        return secondOpenPrice;
    }

    public void setSecondOpenPrice(BigDecimal secondOpenPrice) {
        final BigDecimal sop = secondOpenPrice.setScale(2, BigDecimal.ROUND_HALF_UP);
        secondPriceFact.setOpenPrice(sop);
        this.secondOpenPrice = sop;
    }

    public BigDecimal getDelta1Fact() {
        return (firstOpenPrice != null && secondOpenPrice != null)
                ? firstOpenPrice.subtract(secondOpenPrice).setScale(2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
    }

    public BigDecimal getDelta2Fact() {
        return (firstOpenPrice != null && secondOpenPrice != null)
                ? secondOpenPrice.subtract(firstOpenPrice).setScale(2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;
    }

    public void setBorder(BigDecimal border) {
        this.borderList = Collections.singletonList(border);
    }

    public List<BigDecimal> getBorderList() {
        return borderList;
    }

    public void setBorderList(List<BigDecimal> borderList) {
        this.borderList = Collections.unmodifiableList(borderList);
    }

    public BigDecimal getoBlock() {
        return oBlock;
    }

    public void setoBlock(BigDecimal oBlock) {
        this.oBlock = oBlock;
    }

    public BigDecimal getbBlock() {
        return bBlock;
    }

    public void setbBlock(BigDecimal bBlock) {
        this.bBlock = bBlock;
    }

    public BigDecimal getDelta1Plan() {
        return delta1Plan;
    }

    public void setDelta1Plan(BigDecimal delta1Plan) {
        this.delta1Plan = delta1Plan;
    }

    public BigDecimal getDelta2Plan() {
        return delta2Plan;
    }

    public void setDelta2Plan(BigDecimal delta2Plan) {
        this.delta2Plan = delta2Plan;
    }

    public BigDecimal getbPricePlan() {
        return bPricePlan;
    }

    public void setbPricePlan(BigDecimal bPricePlan) {
        this.bPricePlan = bPricePlan;
    }

    public BigDecimal getoPricePlan() {
        return oPricePlan;
    }

    public void setoPricePlan(BigDecimal oPricePlan) {
        this.oPricePlan = oPricePlan;
    }

    public AvgPrice getFirstPriceFact() {
        return firstPriceFact;
    }

    public void setFirstPriceFact(AvgPrice firstPriceFact) {
        this.firstPriceFact = firstPriceFact;
    }

    public AvgPrice getSecondPriceFact() {
        return secondPriceFact;
    }

    public void setSecondPriceFact(AvgPrice secondPriceFact) {
        this.secondPriceFact = secondPriceFact;
    }
}
