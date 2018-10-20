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
public class Adj {

    // attempts - resets after success
    private Integer currErrorCount;
    private Integer maxErrorCount;
    // all counts
    private Integer succeedCount;
    private Integer failedCount;
    private Integer maxTotalCount;

    public static Adj createDefault() {
        return new Adj(0, 3, 0, 0, 20);
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
        return String.format("Adj attempts(curr/max): %s/%s. Total(success+fail=total / max): %s+%s=%s / %s",
                currErrorCount, maxErrorCount,
                succeedCount, failedCount,
                succeedCount + failedCount,
                maxTotalCount);
    }
}