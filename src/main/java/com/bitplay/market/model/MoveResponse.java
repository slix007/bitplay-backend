package com.bitplay.market.model;

/**
 * Created by Sergey Shurmin on 4/24/17.
 */
public class MoveResponse {
    boolean isOk = false;
    String description;
    MoveOrderStatus moveOrderStatus;

    public enum MoveOrderStatus {
        NO_NEED_MOVING,
        IS_FINISHED,
        MOVED
    }

    public final static String NO_NEED_MOVING = "No need moving";

    public MoveResponse(boolean isOk, String description) {
        this.isOk = isOk;
        this.description = description;
    }

    public MoveResponse(boolean isOk, String description, MoveOrderStatus moveOrderStatus) {
        this.isOk = isOk;
        this.description = description;
        this.moveOrderStatus = moveOrderStatus;
    }

    public boolean isOk() {
        return isOk;
    }

    public void setOk(boolean ok) {
        isOk = ok;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MoveOrderStatus getMoveOrderStatus() {
        return moveOrderStatus;
    }
}
