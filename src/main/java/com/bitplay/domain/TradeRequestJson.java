package com.bitplay.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 4/15/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeRequestJson {

    public enum Type {
        BUY,
        SELL,
    }
    public enum PlacementType {
        TAKER,
        MAKER,
    }

    private Type type;
    private PlacementType placementType;
    private String amount;

    public TradeRequestJson() {
    }

    public TradeRequestJson(Type type, PlacementType placementType, String amount) {
        this.type = type;
        this.placementType = placementType;
        this.amount = amount;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public PlacementType getPlacementType() {
        return placementType;
    }

    public void setPlacementType(PlacementType placementType) {
        this.placementType = placementType;
    }
}
