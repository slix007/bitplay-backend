package com.bitplay.persistance.domain;

/**
 * Created by Sergey Shurmin on 10/26/17.
 */
public class SwapV2 {

    String swapOpenType; //sell,buy
    String swapOpenAmount;

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

    @Override
    public String toString() {
        return "SwapV2{" +
                "swapOpenType='" + swapOpenType + '\'' +
                ", swapOpenAmount='" + swapOpenAmount + '\'' +
                '}';
    }
}
