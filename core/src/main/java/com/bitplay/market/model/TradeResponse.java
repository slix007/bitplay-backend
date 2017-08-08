package com.bitplay.market.model;

import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.trade.LimitOrder;

/**
 * Created by Sergey Shurmin on 4/21/17.
 */
public class TradeResponse {

    String orderId;
    Object specificResponse;
    String errorCode;
    LimitOrder limitOrder;
    Order cancelledOrder;

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

    public Order getCancelledOrder() {
        return cancelledOrder;
    }

    public void setCancelledOrder(Order cancelledOrder) {
        this.cancelledOrder = cancelledOrder;
    }
}
