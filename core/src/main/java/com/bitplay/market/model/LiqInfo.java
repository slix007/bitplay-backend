package com.bitplay.market.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 7/17/17.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LiqInfo {

    public static final BigDecimal DQL_WRONG = BigDecimal.valueOf(-100);

    private volatile BigDecimal dqlCurr;
    private volatile BigDecimal dmrlCurr;
    private volatile String dqlString; // Diff Quote Liq.
    private volatile String dmrlString;// Diff Margin Rate Liq.

    private volatile String dqlStringExtra;

    public BigDecimal getDqlCurr() {
        if (dqlCurr != null && dqlCurr.compareTo(DQL_WRONG) == 0) {
            return null;
        }
        return dqlCurr;
    }

    @Override
    public LiqInfo clone() {
        return new LiqInfo(dqlCurr, dmrlCurr, dqlString, dmrlString, dqlStringExtra);
    }
}
