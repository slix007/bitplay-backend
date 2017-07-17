package com.bitplay.market.dto;

/**
 * Created by Sergey Shurmin on 7/17/17.
 */
public class LiqInfo {
    private String dql;
    private String dmrl;// Diff Margin Rate Liq.

    public LiqInfo(String dql, String dmrl) {
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
