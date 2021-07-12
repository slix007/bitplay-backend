package com.bitplay.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 7/15/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PosCorrJson {
    /**
     * enabled, stopped
     */
    private String status;
    /**
     * in minutes
     */
    private Long periodToCorrection;
    private String maxDiffCorr;

    public PosCorrJson() {
    }

    public PosCorrJson(String status, Long periodToCorrection, String maxDiffCorr) {
        this.status = status;
        this.periodToCorrection = periodToCorrection;
        this.maxDiffCorr = maxDiffCorr;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getPeriodToCorrection() {
        return periodToCorrection;
    }

    public void setPeriodToCorrection(Long periodToCorrection) {
        this.periodToCorrection = periodToCorrection;
    }

    public String getMaxDiffCorr() {
        return maxDiffCorr;
    }

    public void setMaxDiffCorr(String maxDiffCorr) {
        this.maxDiffCorr = maxDiffCorr;
    }
}
