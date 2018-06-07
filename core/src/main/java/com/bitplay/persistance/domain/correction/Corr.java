package com.bitplay.persistance.domain.correction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 3/21/18.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Corr {

    private Integer maxVolCorrOkex;
    private Integer succeedCount;
    private Integer failedCount;
    private Integer currErrorCount;
    private Integer maxErrorCount;
    private Integer maxTotalCount;

    public static Corr createDefault() {
        return new Corr(1, 0, 0, 3, 1, 20);
    }

    public Integer getMaxVolCorrBitmex() {
        return maxVolCorrOkex * 100;
    }

    public boolean hasSpareAttempts() {
        boolean hasSpareCurrent = currErrorCount < maxErrorCount;
        boolean hasSparePermanent = getTotalCount() < maxTotalCount;
        return hasSpareCurrent && hasSparePermanent;
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

    @Override
    public String toString() {
        return String.format("Corr attempts(curr/max): %s/%s. Total(success+fail=total / max): %s+%s=%s / %s",
                currErrorCount, maxErrorCount,
                succeedCount, failedCount,
                succeedCount + failedCount,
                maxTotalCount);
    }
}
