package com.bitplay.persistance.domain.correction;

/**
 * Created by Sergey Shurmin on 3/21/18.
 */
public class Corr {

    private Integer succeedCount;
    private Integer failedCount;
    private Integer currErrorCount;
    private Integer maxErrorCount;

    public Corr() {
    }

    public Corr(Integer succeedCount, Integer failedCount, Integer currErrorCount, Integer maxErrorCount) {
        this.succeedCount = succeedCount;
        this.failedCount = failedCount;
        this.maxErrorCount = maxErrorCount;
        this.currErrorCount = currErrorCount;
    }

    public static Corr createDefault() {
        return new Corr(0, 0, 0, 3);
    }

    public Integer getCurrErrorCount() {
        return currErrorCount;
    }

    public void setCurrErrorCount(Integer currErrorCount) {
        this.currErrorCount = currErrorCount;
    }

    public Integer getMaxErrorCount() {
        return maxErrorCount;
    }

    public void setMaxErrorCount(Integer maxErrorCount) {
        this.maxErrorCount = maxErrorCount;
    }

    public boolean hasSpareAttempts() {
        return currErrorCount < maxErrorCount;
    }

    public Integer getTotalCount() {
        return succeedCount + failedCount;
    }

    public Integer getSucceedCount() {
        return succeedCount;
    }

    public void setSucceedCount(Integer succeedCount) {
        this.succeedCount = succeedCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public void incSuccesses() {
        this.succeedCount++;
        this.currErrorCount = 0;
    }

    public void incFails() {
        this.failedCount++;
        this.currErrorCount++;
    }

    @Override
    public String toString() {
        return String.format("Corr errors(curr/max): %s/%s. Total(success/fail): %s/%s",
                currErrorCount, maxErrorCount,
                succeedCount, failedCount);
    }
}
