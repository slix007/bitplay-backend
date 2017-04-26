package com.bitplay.market.model;

/**
 * Created by Sergey Shurmin on 4/24/17.
 */
public class MoveResponse {

    MoveOrderStatus moveOrderStatus;
    String description;

    public enum MoveOrderStatus {
        ALREADY_FIRST,
        ALREADY_CLOSED,
        MOVED,
        EXCEPTION
    }

    public MoveResponse(MoveOrderStatus moveOrderStatus, String description) {
        this.moveOrderStatus = moveOrderStatus;
        this.description = description;
    }

    public MoveOrderStatus getMoveOrderStatus() {
        return moveOrderStatus;
    }

    public String getDescription() {
        return description;
    }
}
