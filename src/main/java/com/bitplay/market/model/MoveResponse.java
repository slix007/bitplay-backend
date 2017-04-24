package com.bitplay.market.model;

/**
 * Created by Sergey Shurmin on 4/24/17.
 */
public class MoveResponse {
    boolean isOk = false;
    String description;

    public MoveResponse(boolean isOk, String description) {
        this.isOk = isOk;
        this.description = description;
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
}
