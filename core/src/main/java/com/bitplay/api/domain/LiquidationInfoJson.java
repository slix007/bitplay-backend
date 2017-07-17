package com.bitplay.api.domain;

/**
 * Created by Sergey Shurmin on 7/17/17.
 */
public class LiquidationInfoJson {

    private String dql; // Diff quote liq.
    private String dmrl; // diff margin rate liq.

    public LiquidationInfoJson(String dql, String dmrl) {
        this.dql = dql;
        this.dmrl = dmrl;
    }

    public String getDql() {
        return dql;
    }

    public String getDmrl() {
        return dmrl;
    }
}
