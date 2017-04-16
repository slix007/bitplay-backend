package com.bitplay.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 4/16/17.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeResponse {

    private String orderId;

    public TradeResponse(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
