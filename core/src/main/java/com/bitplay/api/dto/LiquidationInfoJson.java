package com.bitplay.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Created by Sergey Shurmin on 7/17/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@Getter
public class LiquidationInfoJson {

    private String dqlVal; // Diff quote liq.
    private String dql; // Diff quote liq.
    private String dmrl; // diff margin rate liq.
    private String mmDql;
    private String mmDmrl;
    private String dqlExtra; // Diff quote liq.
    private String mmDqlExtra;

    public LiquidationInfoJson(String dqlVal ,String dql, String dmrl, String mmDql, String mmDmrl) {
        this.dqlVal = dqlVal;
        this.dql = dql;
        this.dmrl = dmrl;
        this.mmDql = mmDql;
        this.mmDmrl = mmDmrl;
    }
}
