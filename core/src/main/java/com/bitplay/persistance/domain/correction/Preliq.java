package com.bitplay.persistance.domain.correction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by Sergey Shurmin on 3/24/18.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Preliq {
    private Integer preliqBlockOkex;
    private Integer succeedCount;
    private Integer failedCount;
    private Integer currErrorCount;
    private Integer maxErrorCount;

    public Preliq() {
    }

    public Preliq(Integer preliqBlockOkex, Integer succeedCount, Integer failedCount, Integer currErrorCount, Integer maxErrorCount) {
        this.preliqBlockOkex = preliqBlockOkex;
        this.succeedCount = succeedCount;
        this.failedCount = failedCount;
        this.currErrorCount = currErrorCount;
        this.maxErrorCount = maxErrorCount;
    }

    public static Preliq createDefault() {
        return new Preliq(1, 0, 0, 0, 3);
    }

    public Integer getPreliqBlockBitmex() {
        return preliqBlockOkex * 100;
    }

    public boolean hasSpareAttempts() {
        return currErrorCount < maxErrorCount;
    }

    public Integer getTotalCount() {
        return succeedCount + failedCount;
    }

    public void incSuccesses() {
        this.succeedCount++;
        this.currErrorCount = 0;
    }

    public void incFails() {
        this.failedCount++;
        this.currErrorCount++;
    }

    public Integer getPreliqBlockOkex() {
        return preliqBlockOkex;
    }

    public void setPreliqBlockOkex(Integer preliqBlockOkex) {
        this.preliqBlockOkex = preliqBlockOkex;
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

    @Override
    public String toString() {
        return String.format("Preliq errors(curr/max): %s/%s. Total(success/fail): %s/%s",
                currErrorCount, maxErrorCount,
                succeedCount, failedCount);
    }
}
