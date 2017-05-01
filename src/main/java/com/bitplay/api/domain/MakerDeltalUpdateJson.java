package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 4/22/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MakerDeltalUpdateJson {

    private String makerDelta;

    public String getMakerDelta() {
        return makerDelta;
    }

    public void setMakerDelta(String makerDelta) {
        this.makerDelta = makerDelta;
    }
}
