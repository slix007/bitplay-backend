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
public class Adj extends CountedWithExtra {

    // attempts - resets after success
    private Integer currErrorCount;
    private Integer maxErrorCount;
    // all counts
    private Integer totalCount;
    private Integer succeedCount;
    private Integer failedCount;
    private Integer maxTotalCount;

    public static Adj createDefault() {
        return new Adj(0, 3, 0, 0, 0, 0);
    }

    void setSettingsParts(Adj adj) {
        this.maxErrorCount = adj.maxErrorCount;
        this.maxTotalCount = adj.maxTotalCount;
    }


    public boolean hasSpareAttempts() {
        boolean hasSpareCurrent = currErrorCount < maxErrorCount;
        boolean hasSparePermanent = getTotalCount() < maxTotalCount;
        return hasSpareCurrent && hasSparePermanent;
    }

    @Override
    public void incTotalCount() {
        super.incTotalCount();
        this.totalCount++;
    }

    @Override
    public void incTotalCountExtra() {
        super.incTotalCountExtra();
        this.totalCount++;
    }

    @Override
    protected void incSuccessful() {
        this.succeedCount++;
        this.currErrorCount = 0;
    }

    @Override
    protected void incFailed() {
        this.failedCount++;
        this.currErrorCount++;
    }

    @Override
    public String toString() {
        return String.format("Adj attempts(curr/max): %s/%s. Total(success+fail / total / max): %s+%s / %s / %s",
                currErrorCount, maxErrorCount,
                succeedCount, failedCount,
                totalCount,
                maxTotalCount);
    }
}
