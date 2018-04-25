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
    private Integer succeedCount;
    private Integer failedCount;
    private Integer currErrorCount;
    private Integer maxErrorCount;

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

    @Override
    public String toString() {
        return String.format("Preliq attempts(curr/max): %s/%s. Total(success/fail): %s/%s",
                currErrorCount, maxErrorCount,
                succeedCount, failedCount);
    }
}
