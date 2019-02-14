package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 4/22/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class DeltalUpdateJson {

    private String cumDelta;
    private String cumDeltaFact;
    private String cumDiffFactBr;
    private String cumDiffFact1;
    private String cumDiffFact2;
    private String cumDiffFact;
    private String cumAstDiffFact;
    private String cumCom1;
    private String cumCom2;
    private String count1;
    private String count2;
    private String cumBitmexMCom;
    private String reserveBtc1;
    private String reserveBtc2;
    private String hedgeAmount;
    private String fundingRateFee;
    private String slip;
    private Boolean resetAllCumValues;
    private String diffFactBrFailsCount;

}
