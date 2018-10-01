package com.bitplay.persistance.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 10/31/17.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Range {

    private BigDecimal min;
    private BigDecimal max;
    private BigDecimal last;

    private Range(BigDecimal min, BigDecimal max) {
        this.min = min;
        this.max = max;
    }

    public static Range empty() {
        return new Range(BigDecimal.valueOf(10000), BigDecimal.valueOf(-10000));
    }

    public void add(BigDecimal val) {
        if (val == null) {
            return;
        }
        last = val;
        if (val.signum() == 0) {
            return;
        }

        if (val.compareTo(min) < 0) {
            min = val;
        }
        if (val.compareTo(max) > 0) {
            max = val;
        }
    }
}
