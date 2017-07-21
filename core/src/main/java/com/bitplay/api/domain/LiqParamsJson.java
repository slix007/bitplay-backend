package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 7/15/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LiqParamsJson {

    private String bMrLiq;
    private String oMrLiq;
    private String bDQLOpenMin;
    private String oDQLOpenMin;
    private String bDQLCloseMin;
    private String oDQLCloseMin;
    public LiqParamsJson() {
    }

    public LiqParamsJson(String bMrLiq, String oMrLiq, String bDQLOpenMin, String oDQLOpenMin, String bDQLCloseMin, String oDQLCloseMin) {
        this.bMrLiq = bMrLiq;
        this.oMrLiq = oMrLiq;
        this.bDQLOpenMin = bDQLOpenMin;
        this.oDQLOpenMin = oDQLOpenMin;
        this.bDQLCloseMin = bDQLCloseMin;
        this.oDQLCloseMin = oDQLCloseMin;
    }

    public String getbMrLiq() {
        return bMrLiq;
    }

    public String getoMrLiq() {
        return oMrLiq;
    }

    public String getbDQLOpenMin() {
        return bDQLOpenMin;
    }

    public String getoDQLOpenMin() {
        return oDQLOpenMin;
    }

    public String getbDQLCloseMin() {
        return bDQLCloseMin;
    }

    public String getoDQLCloseMin() {
        return oDQLCloseMin;
    }
}
