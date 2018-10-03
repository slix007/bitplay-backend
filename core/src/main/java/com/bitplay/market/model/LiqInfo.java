package com.bitplay.market.model;

import com.bitplay.persistance.domain.LiqParams;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by Sergey Shurmin on 7/17/17.
 */
@Getter
@Setter
public class LiqInfo {

    public static final BigDecimal DQL_WRONG = BigDecimal.valueOf(-100);

    private LiqParams liqParams;

    private BigDecimal dqlCurr;
    private BigDecimal dmrlCurr;
    private String dqlString; // Diff Quote Liq.
    private String dmrlString;// Diff Margin Rate Liq.

    public BigDecimal getDqlCurr() {
        if (dqlCurr != null && dqlCurr.compareTo(DQL_WRONG) == 0) {
            return null;
        }
        return dqlCurr;
    }

}
