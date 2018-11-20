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
 * Created by Sergey Shurmin on 3/24/18.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Preliq {

    private Integer preliqBlockUsd;
    // attempts - resets after success
    private Integer currErrorCount;
    private Integer maxErrorCount;
    // all counts
    private Integer totalCount;
    private Integer succeedCount;
    private Integer failedCount;
    private Integer maxTotalCount;

    @Transient
    private BigDecimal cm;
    @Transient
    private Boolean isEth;

    public static Preliq createDefault() {
        return new Preliq(10, 0, 3, 0, 0, 0, 20,
                BigDecimal.valueOf(100), false);
    }

    public Integer getPreliqBlockBitmex() {
        if (isEth == null || cm == null) {
            return 0;
        }
        return PlacingBlocks.toBitmexCont(BigDecimal.valueOf(preliqBlockUsd), isEth, cm).intValue();
    }

    public Integer getPreliqBlockOkex() {
        if (isEth == null || cm == null) {
            return 0;
        }
        return PlacingBlocks.toOkexCont(BigDecimal.valueOf(preliqBlockUsd), isEth).intValue();
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
