package com.bitplay.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 4/15/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeRequest {

    public enum Type {BUY, SELL}

    private Type type;
    private String amount;

    public TradeRequest() {
    }

    public TradeRequest(Type type, String amount) {
        this.type = type;
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
}
