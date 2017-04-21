package com.bitplay.business.model;

/**
 * Created by Sergey Shurmin on 4/21/17.
 */
public class TradeResponse {

    String orderId;
    Object specificResponse;
    String errorCode;

    public TradeResponse() {
    }

    public TradeResponse(String orderId, Object specificResponse, String errorCode) {
        this.orderId = orderId;
        this.specificResponse = specificResponse;
        this.errorCode = errorCode;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Object getSpecificResponse() {
        return specificResponse;
    }

    public void setSpecificResponse(Object specificResponse) {
        this.specificResponse = specificResponse;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorMessage(String errorCode) {
        this.errorCode = errorCode;
    }
}
