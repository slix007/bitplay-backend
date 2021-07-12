package com.bitplay.market.model;

import com.bitplay.market.helper.TradeResponseHelper;
import java.util.ArrayList;
import java.util.List;
import com.bitplay.xchange.dto.trade.LimitOrder;

/**
 * Created by Sergey Shurmin on 4/21/17.
 */
public class TradeResponse {

    public static final String TAKER_BECAME_LIMIT = "Taker became limit";
    public static final String TAKER_WAS_CANCELLED_MESSAGE = "Taker wasn't filled. Cancelled";
    public static final String INSUFFICIENT_BALANCE = "Account has insufficient Available Balance";
    private static final TradeResponseHelper tradeResponseHelper = new TradeResponseHelper();

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

    public boolean errorInsufficientFunds() {
        if (this.errorCode != null) {
            return tradeResponseHelper.errorInsufficientFunds(this.errorCode);
        }
        return false;
    }

}
