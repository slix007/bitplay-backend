package com.bitplay.persistance.domain;

import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection="swapParamsCollection")
@TypeAlias("swapParams")
public class SwapParams extends MarketDocument {
    private String customSwapTime = "";
    private BigDecimal cumFundingRate = BigDecimal.ZERO;
    private BigDecimal cumFundingCost = BigDecimal.ZERO;
    private BigDecimal cumSwapProfit = BigDecimal.ZERO;
    private BigDecimal cumFee = BigDecimal.ZERO;
    private BigDecimal cumSpl = BigDecimal.ZERO;
    private BigDecimal cumSwapDiff = BigDecimal.ZERO;

    public String getCustomSwapTime() {
        return customSwapTime;
    }

    public void setCustomSwapTime(String customSwapTime) {
        this.customSwapTime = customSwapTime;
    }

    public BigDecimal getCumFundingRate() {
        return cumFundingRate;
    }

    public void setCumFundingRate(BigDecimal cumFundingRate) {
        this.cumFundingRate = cumFundingRate;
    }

    public BigDecimal getCumFundingCost() {
        return cumFundingCost;
    }

    public void setCumFundingCost(BigDecimal cumFundingCost) {
        this.cumFundingCost = cumFundingCost;
    }

    public BigDecimal getCumSwapProfit() {
        return cumSwapProfit;
    }

    public void setCumSwapProfit(BigDecimal cumSwapProfit) {
        this.cumSwapProfit = cumSwapProfit;
    }

    public BigDecimal getCumFee() {
        return cumFee;
    }

    public void setCumFee(BigDecimal cumFee) {
        this.cumFee = cumFee;
    }

    public BigDecimal getCumSpl() {
        return cumSpl;
    }

    public void setCumSpl(BigDecimal cumSpl) {
        this.cumSpl = cumSpl;
    }

    public BigDecimal getCumSwapDiff() {
        return cumSwapDiff;
    }

    public void setCumSwapDiff(BigDecimal cumSwapDiff) {
        this.cumSwapDiff = cumSwapDiff;
    }
}
