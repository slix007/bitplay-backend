package com.bitplay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Created by Sergey Shurmin on 4/29/17.
 */
@Component
public class Config {

    @Value("${market.first}")
    private String firstMarketName;
    @Value("${market.first.key}")
    private String firstMarketKey;
    @Value("${market.first.secret}")
    private String firstMarketSecret;

    @Value("${market.second}")
    private String secondMarketName;
    @Value("${market.second.key}")
    private String secondMarketKey;
    @Value("${market.second.secret}")
    private String secondMarketSecret;

    @Value("${deltas-series.enabled}")
    private Boolean deltasSeriesEnabled;

    public String getFirstMarketName() {
        return firstMarketName;
    }

    public String getFirstMarketKey() {
        return firstMarketKey;
    }

    public String getFirstMarketSecret() {
        return firstMarketSecret;
    }

    public String getSecondMarketName() {
        return secondMarketName;
    }

    public String getSecondMarketKey() {
        return secondMarketKey;
    }

    public String getSecondMarketSecret() {
        return secondMarketSecret;
    }

    public boolean isDeltasSeriesEnabled() {
        return deltasSeriesEnabled != null && deltasSeriesEnabled;
    }

    public void setDeltasSeriesEnabled(Boolean deltasSeriesEnabled) {
        this.deltasSeriesEnabled = deltasSeriesEnabled;
    }
}
