package com.bitplay.api.domain.futureindex;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 4/14/17.
 */
@Getter
@Setter
public class FutureIndexJson {
    private String index;
    private String timestamp;
    private String fundingRate;
    private String fundingCost;
    private String position;
    private String swapTime;
    private String timeToSwap;
    private String swapType;
    private String timeCompareString;
    private String timeCompareUpdating;
    private LimitsJson limits;

    public FutureIndexJson(String index, String timestamp, LimitsJson limits) {
        this.index = index;
        this.timestamp = timestamp;
        this.limits = limits;
    }

    public FutureIndexJson(String index, String timestamp, String fundingRate,
                           String fundingCost,
                           String position, String swapTime, String timeToSwap, String swapType,
                           String timeCompareString, String timeCompareUpdating, LimitsJson limits) {
        this.index = index;
        this.timestamp = timestamp;
        this.fundingRate = fundingRate;
        this.fundingCost = fundingCost;
        this.position = position;
        this.swapTime = swapTime;
        this.timeToSwap = timeToSwap;
        this.swapType = swapType;
        this.timeCompareString = timeCompareString;
        this.timeCompareUpdating = timeCompareUpdating;
        this.limits = limits;
    }
}
