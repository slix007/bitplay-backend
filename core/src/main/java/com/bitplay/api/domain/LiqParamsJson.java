package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 7/15/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LiqParamsJson {

    private String bMrLiq;
    private String oMrLiq;

    public LiqParamsJson() {
    }

    public LiqParamsJson(String bMrLiq, String oMrLiq) {
        this.bMrLiq = bMrLiq;
        this.oMrLiq = oMrLiq;
    }

    public String getbMrLiq() {
        return bMrLiq;
    }

    public void setbMrLiq(String bMrLiq) {
        this.bMrLiq = bMrLiq;
    }

    public String getoMrLiq() {
        return oMrLiq;
    }

    public void setoMrLiq(String oMrLiq) {
        this.oMrLiq = oMrLiq;
    }
}
