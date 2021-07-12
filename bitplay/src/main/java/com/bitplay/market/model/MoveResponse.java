package com.bitplay.market.model;

import com.bitplay.persistance.domain.fluent.FplayOrder;

import com.bitplay.xchange.dto.trade.LimitOrder;

/**
 * Created by Sergey Shurmin on 4/24/17.
 */
public class MoveResponse {

    MoveOrderStatus moveOrderStatus;
    String description;
    LimitOrder newOrder;
    FplayOrder newFplayOrder;
    FplayOrder cancelledFplayOrder;

    public enum MoveOrderStatus {
        ALREADY_FIRST,
        ALREADY_CLOSED,
        MOVED,
        INVALID_AMEND,
        EXCEPTION,
        EXCEPTION_SYSTEM_OVERLOADED,
        EXCEPTION_502_BAD_GATEWAY,
        EXCEPTION_NONCE,
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

    public MoveResponse(MoveOrderStatus moveOrderStatus, String description, LimitOrder newOrder, FplayOrder newFplayOrder) {
        this.moveOrderStatus = moveOrderStatus;
        this.description = description;
        this.newOrder = newOrder;
        this.newFplayOrder = newFplayOrder;
    }

    public MoveResponse(MoveOrderStatus moveOrderStatus, String description, LimitOrder newOrder, FplayOrder newFplayOrder, FplayOrder cancelledFplayOrder) {
        this.moveOrderStatus = moveOrderStatus;
        this.description = description;
        this.newOrder = newOrder;
        this.newFplayOrder = newFplayOrder;
        this.cancelledFplayOrder = cancelledFplayOrder;
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

    public FplayOrder getNewFplayOrder() {
        return newFplayOrder;
    }

    public FplayOrder getCancelledFplayOrder() {
        return cancelledFplayOrder;
    }
}
