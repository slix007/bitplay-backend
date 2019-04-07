package com.bitplay.api.domain;

import com.bitplay.persistance.domain.LastPriceDeviation;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by Sergey Shurmin on 4/18/17.
 */
@Getter
@AllArgsConstructor
public class DeltasJson {

    private String delta1;
    private String delta2;
    private String border1;
    private String border2;

    private String reserveBtc1;
    private String reserveBtc2;
    private String hedgeAmount;
    private String fundingRateFee;

    private String delta1Sma;
    private String delta2Sma;
    private String delta1MinInstant;
    private String delta2MinInstant;
    private String delta1MinFixed;
    private String delta2MinFixed;
    private String delta1EveryCalc;
    private String delta2EveryCalc;
    private String deltaHistPerStarted;
    private String deltaSmaUpdateIn;

    private LastPriceDeviation lastPriceDeviation;

}