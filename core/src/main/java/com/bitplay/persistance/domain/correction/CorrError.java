package com.bitplay.persistance.domain.correction;

/**
 * Created by Sergey Shurmin on 3/21/18.
 */
public class CorrError {

    private Integer currentErrorAmount;
    private Integer maxErrorAmount;

    public CorrError() {
    }

    public CorrError(Integer currentErrorAmount, Integer maxErrorAmount) {
        this.maxErrorAmount = maxErrorAmount;
        this.currentErrorAmount = currentErrorAmount;
    }

    public static CorrError createDefault() {
        return new CorrError(0, 3);
    }

    public Integer getCurrentErrorAmount() {
        return currentErrorAmount;
    }

    public void setCurrentErrorAmount(Integer currentErrorAmount) {
        this.currentErrorAmount = currentErrorAmount;
    }

    public Integer getMaxErrorAmount() {
        return maxErrorAmount;
    }

    public void setMaxErrorAmount(Integer maxErrorAmount) {
        this.maxErrorAmount = maxErrorAmount;
    }

    @Override
    public String toString() {
        return String.format("CorrError{%s/%s}", currentErrorAmount, maxErrorAmount);
    }
}
