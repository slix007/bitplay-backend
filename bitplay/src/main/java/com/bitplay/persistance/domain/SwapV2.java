package com.bitplay.persistance.domain;

import lombok.Data;

/**
 * Created by Sergey Shurmin on 10/26/17.
 */
@Data
public class SwapV2 {

    String swapOpenType; //sell,buy
    String swapOpenAmount;
    Integer swapTimeCorrMs;
    String msToSwapString;


    public static SwapV2 createDefault() {
        final SwapV2 swapV2 = new SwapV2();
        swapV2.swapOpenType = "";
        swapV2.swapOpenAmount = "";
        swapV2.swapTimeCorrMs = 0;
        swapV2.msToSwapString = "";
        return swapV2;
    }
}
