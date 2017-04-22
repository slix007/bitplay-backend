package com.bitplay.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 4/16/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeResponseJson {

    private String orderId;
    private Object details;

    public TradeResponseJson(String orderId, Object details) {
        this.orderId = orderId;
        this.details = details;
    }

    public Object getDetails() {
        return details;
    }

    public String getOrderId() {
        return orderId;
    }
}
