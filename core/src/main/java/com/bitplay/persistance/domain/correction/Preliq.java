package com.bitplay.persistance.domain.correction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 3/24/18.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Preliq {
    private Integer preliqBlockOkex;
    // attempts - resets after success
    private Integer currErrorCount;
    private Integer maxErrorCount;
    // all counts
    private Integer totalCount;
    private Integer succeedCount;
    private Integer failedCount;
    private Integer maxTotalCount;

    public static Preliq createDefault() {
        return new Preliq(1, 0, 3, 0, 0, 0, 20);
    }

    public Integer getPreliqBlockBitmex() {
        return preliqBlockOkex * 100;
    }

    public boolean hasSpareAttempts() {
        boolean hasSpareCurrent = currErrorCount < maxErrorCount;
        boolean hasSparePermanent = totalCount < maxTotalCount;
        return hasSpareCurrent && hasSparePermanent;
    }

    public void incTotalCount() {
        this.totalCount++;
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
        return String.format("Preliq attempts(curr/max): %s/%s. Total(totalStarted / completed=success+fail / max): %s / %s=%s+%s / %s",
                currErrorCount, maxErrorCount,
                totalCount,
                succeedCount + failedCount,
                succeedCount, failedCount,
                maxTotalCount);
    }
}
