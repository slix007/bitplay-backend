package com.bitplay.api.domain;

/**
 * Created by Sergey Shurmin on 4/14/17.
 */
public class FutureIndexJson {
    private String index;
    private String timestamp;
    private String fundingRate;
    private String swapTime;
    private String timeToSwap;
    private String swapType;

    public FutureIndexJson(String index, String timestamp) {
        this.index = index;
        this.timestamp = timestamp;
    }

    public FutureIndexJson(String index, String timestamp, String fundingRate, String swapTime, String timeToSwap, String swapType) {
        this.index = index;
        this.timestamp = timestamp;
        this.fundingRate = fundingRate;
        this.swapTime = swapTime;
        this.timeToSwap = timeToSwap;
        this.swapType = swapType;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getFundingRate() {
        return fundingRate;
    }

    public void setFundingRate(String fundingRate) {
        this.fundingRate = fundingRate;
    }

    public String getSwapTime() {
        return swapTime;
    }

    public void setSwapTime(String swapTime) {
        this.swapTime = swapTime;
    }

    public String getTimeToSwap() {
        return timeToSwap;
    }

    public void setTimeToSwap(String timeToFunding) {
        this.timeToSwap = timeToFunding;
    }

    public String getSwapType() {
        return swapType;
    }

    public void setSwapType(String swapType) {
        this.swapType = swapType;
    }
}
