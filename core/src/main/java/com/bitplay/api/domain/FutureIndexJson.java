package com.bitplay.api.domain;

/**
 * Created by Sergey Shurmin on 4/14/17.
 */
public class FutureIndexJson {
    private String index;
    private String timestamp;
    private String fundingRate;
    private String timeToFunding;

    public FutureIndexJson(String index, String timestamp) {
        this.index = index;
        this.timestamp = timestamp;
    }

    public FutureIndexJson(String index, String timestamp, String fundingRate, String timeToFunding) {
        this.index = index;
        this.timestamp = timestamp;
        this.fundingRate = fundingRate;
        this.timeToFunding = timeToFunding;
    }

    public String getIndex() {
        return index;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getFundingRate() {
        return fundingRate;
    }

    public String getTimeToFunding() {
        return timeToFunding;
    }
}
