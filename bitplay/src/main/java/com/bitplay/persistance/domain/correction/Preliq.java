package com.bitplay.persistance.domain.correction;

import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.market.bitmex.BitmexService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Transient;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 3/24/18.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Preliq extends CountedPreliq {

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

    void setSettingsParts(Preliq preliq) {
        this.preliqBlockUsd = preliq.preliqBlockUsd;
        this.maxErrorCount = preliq.maxErrorCount;
        this.maxTotalCount = preliq.maxTotalCount;
    }

    public Integer getPreliqBlockLeft(String marketName) {
        return BitmexService.NAME.equals(marketName) ? getPreliqBlockBitmex() : getPreliqBlockOkex();
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

    @SuppressWarnings("UnnecessaryLocalVariable")
    public boolean hasSpareAttempts() {
        boolean hasSpareCurrent = currErrorCount < maxErrorCount;
        boolean hasSparePermanent = totalCount < maxTotalCount;
        return hasSpareCurrent && hasSparePermanent;
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public boolean hasSpareAttemptsCurrentOnly() {
        boolean hasSpareCurrent = currErrorCount < maxErrorCount;
        return hasSpareCurrent;
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public boolean hasSpareTotalAttempts() {
        boolean hasSparePermanent = totalCount < maxTotalCount;
        return hasSparePermanent;
    }

    public boolean totalCountViolated() {
        boolean hasSparePermanent = totalCount < maxTotalCount;
        return !hasSparePermanent && maxTotalCount > 0;
    }

    public boolean maxErrorCountViolated() {
        boolean hasSpareCurrent = currErrorCount < maxErrorCount;
        return !hasSpareCurrent && maxErrorCount > 0;
    }

    public void incTotalCount(String marketName) {
        super.incTotalCount(marketName);
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
        return String.format("Preliq attempts(curr/max): %s/%s. Total(totalStarted / completed=success+fail / max): %s / %s=%s+%s / %s",
                currErrorCount, maxErrorCount,
                totalCount,
                succeedCount + failedCount,
                succeedCount, failedCount,
                maxTotalCount);
    }

    public String toStringKillpos() {
        return String.format("killpos Attempts(curr/max): %s/%s", currErrorCount, maxErrorCount);
    }
}
