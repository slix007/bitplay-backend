package com.bitplay.persistance.domain.correction;

import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Transient;

/**
 * Created by Sergey Shurmin on 3/21/18.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Corr {

    private Integer maxVolCorrUsd;
    // attempts - resets after success
    private Integer currErrorCount;
    private Integer maxErrorCount;
    // all counts
    private Integer succeedCount;
    private Integer failedCount;
    private Integer maxTotalCount;

    @Transient
    private BigDecimal cm;
    @Transient
    private Boolean isEth;

    public static Corr createDefault() {
        return new Corr(1, 0, 3, 0, 0, 20,
                BigDecimal.valueOf(100), false);
    }

    public Integer getMaxVolCorrBitmex() {
        if (isEth == null || cm == null) {
            return 0;
        }
        return PlacingBlocks.toBitmexContPure(BigDecimal.valueOf(maxVolCorrUsd), isEth, cm).intValue();
    }

    public Integer getMaxVolCorrOkex() {
        if (isEth == null || cm == null) {
            return 0;
        }
        return PlacingBlocks.toOkexCont(BigDecimal.valueOf(maxVolCorrUsd), isEth).intValue();
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
