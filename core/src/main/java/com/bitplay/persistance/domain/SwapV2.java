package com.bitplay.persistance.domain;

/**
 * Created by Sergey Shurmin on 10/26/17.
 */
public class SwapV2 {

    String swapOpenType; //sell,buy
    String swapOpenAmount;
    Integer swapTimeCorrMs;
    String msToSwapString;

    public String getSwapOpenType() {
        return swapOpenType;
    }

    public void setSwapOpenType(String swapOpenType) {
        this.swapOpenType = swapOpenType;
    }

    public String getSwapOpenAmount() {
        return swapOpenAmount;
    }

    public void setSwapOpenAmount(String swapOpenAmount) {
        this.swapOpenAmount = swapOpenAmount;
    }

    public Integer getSwapTimeCorrMs() {
        return swapTimeCorrMs;
    }

    public void setSwapTimeCorrMs(Integer swapTimeCorrMs) {
        this.swapTimeCorrMs = swapTimeCorrMs;
    }

    public String getMsToSwapString() {
        return msToSwapString;
    }

    public void setMsToSwapString(String msToSwapString) {
        this.msToSwapString = msToSwapString;
    }

    @Override
    public String toString() {
        return "SwapV2{" +
                "swapOpenType='" + swapOpenType + '\'' +
                ", swapOpenAmount='" + swapOpenAmount + '\'' +
                ", swapTimeCorrMs=" + swapTimeCorrMs +
                ", msToSwapString='" + msToSwapString + '\'' +
                '}';
    }
}
