package com.bitplay.api.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 7/17/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LiquidationInfoJson {

    private String dql; // Diff quote liq.
    private String dmrl; // diff margin rate liq.
    private String mmDql;
    private String mmDmrl;

    public LiquidationInfoJson(String dql, String dmrl, String mmDql, String mmDmrl) {
        this.dql = dql;
        this.dmrl = dmrl;
        this.mmDql = mmDql;
        this.mmDmrl = mmDmrl;
    }

    public String getDql() {
        return dql;
    }

    public String getDmrl() {
        return dmrl;
    }

    public String getMmDql() {
        return mmDql;
    }

    public String getMmDmrl() {
        return mmDmrl;
    }
}
