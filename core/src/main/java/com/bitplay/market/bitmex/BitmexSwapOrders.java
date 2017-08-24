package com.bitplay.market.bitmex;

/**
 * Created by Sergey Shurmin on 8/23/17.
 */
public class BitmexSwapOrders {

    private String swapCloseOrderId;
    private String swapOpenOrderId;

    public String getSwapCloseOrderId() {
        return swapCloseOrderId;
    }

    public void setSwapCloseOrderId(String swapCloseOrderId) {
        this.swapCloseOrderId = swapCloseOrderId;
    }

    public String getSwapOpenOrderId() {
        return swapOpenOrderId;
    }

    public void setSwapOpenOrderId(String swapOpenOrderId) {
        this.swapOpenOrderId = swapOpenOrderId;
    }
}
