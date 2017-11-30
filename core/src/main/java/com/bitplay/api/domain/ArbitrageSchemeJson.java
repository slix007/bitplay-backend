package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 11/27/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArbitrageSchemeJson {

    private String schemeName;

    public ArbitrageSchemeJson() {
    }

    public ArbitrageSchemeJson(String schemeName) {
        this.schemeName = schemeName;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public void setSchemeName(String schemeName) {
        this.schemeName = schemeName;
    }
}
