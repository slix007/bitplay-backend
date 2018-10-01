package com.bitplay.persistance.domain.correction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private Integer maxVolCorrOkex;
    // attempts - resets after success
    private Integer currErrorCount;
    private Integer maxErrorCount;
    // all counts
    private Integer succeedCount;
    private Integer failedCount;
    private Integer maxTotalCount;

    @Transient
    private BigDecimal cm = BigDecimal.valueOf(100);

    public static Corr createDefault() {
        return new Corr(1, 0, 3, 0, 0, 20,
                BigDecimal.valueOf(100));
    }

    public Integer getMaxVolCorrBitmex() {
        return getMaxVolCorrBitmex(cm);
    }

    public Integer getMaxVolCorrBitmex(BigDecimal cm) {
        this.cm = cm;
        return BigDecimal.valueOf(maxVolCorrOkex).multiply(cm).setScale(0, RoundingMode.HALF_UP).intValue();
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
