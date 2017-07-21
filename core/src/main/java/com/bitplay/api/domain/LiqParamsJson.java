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

    public LiqParamsJson() {
    }

    public LiqParamsJson(String bMrLiq, String oMrLiq, String bDQLOpenMin, String oDQLOpenMin) {
        this.bMrLiq = bMrLiq;
        this.oMrLiq = oMrLiq;
        this.bDQLOpenMin = bDQLOpenMin;
        this.oDQLOpenMin = oDQLOpenMin;
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
}
