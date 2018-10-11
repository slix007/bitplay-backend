package com.bitplay.market.model;

import org.knowm.xchange.dto.trade.LimitOrder;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Sergey Shurmin on 4/21/17.
 */
public class TradeResponse {

    String orderId;
    Object specificResponse;
    String errorCode;
    LimitOrder limitOrder;
    List<LimitOrder> cancelledOrders = new ArrayList<>();

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

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public LimitOrder getLimitOrder() {
        return limitOrder;
    }

    public void setLimitOrder(LimitOrder limitOrder) {
        this.limitOrder = limitOrder;
    }

    public List<LimitOrder> getCancelledOrders() {
        return cancelledOrders;
    }

    public void addCancelledOrder(LimitOrder cancelledOrder) {
        if (cancelledOrder != null) {
            cancelledOrders.add(cancelledOrder);
        }
    }
}
