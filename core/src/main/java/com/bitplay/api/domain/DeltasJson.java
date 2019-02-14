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
    private String cumDelta;
    private String cumAstDelta;
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
    private String completedCount1;
    private String completedCount2;
    private String diffFactBrFailsCount;
    private String cumBitmexMCom;
    private String cumAstBitmexMCom;
    private String reserveBtc1;
    private String reserveBtc2;
    private String hedgeAmount;
    private String fundingRateFee;
    private String slipBr;
    private String slip;
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