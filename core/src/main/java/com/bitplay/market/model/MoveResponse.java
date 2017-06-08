package com.bitplay.market.model;

import org.knowm.xchange.dto.trade.LimitOrder;

/**
 * Created by Sergey Shurmin on 4/24/17.
 */
public class MoveResponse {

    MoveOrderStatus moveOrderStatus;
    String description;
    LimitOrder newOrder;

    public enum MoveOrderStatus {
        ALREADY_FIRST,
        ALREADY_CLOSED,
        MOVED,
        EXCEPTION,
        WAITING_TIMEOUT,
        MOVED_WITH_NEW_ID,
        ONLY_CANCEL
    }

    public MoveResponse(MoveOrderStatus moveOrderStatus, String description) {
        this.moveOrderStatus = moveOrderStatus;
        this.description = description;
    }

    public MoveResponse(MoveOrderStatus moveOrderStatus, String description, LimitOrder newOrder) {
        this.moveOrderStatus = moveOrderStatus;
        this.description = description;
        this.newOrder = newOrder;
    }

    public MoveOrderStatus getMoveOrderStatus() {
        return moveOrderStatus;
    }

    public String getDescription() {
        return description;
    }

    public LimitOrder getNewOrder() {
        return newOrder;
    }
}
