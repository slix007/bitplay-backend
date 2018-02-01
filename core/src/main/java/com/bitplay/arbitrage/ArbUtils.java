package com.bitplay.arbitrage;

import java.math.BigDecimal;
import java.util.List;

/**
 * Created by Sergey Shurmin on 1/25/18.
 */
public class ArbUtils {

    public static DiffFactBr getDeltaFactBr(BigDecimal deltaFact, List<BigDecimal> borderList) {
        if (borderList == null) {
            return new DiffFactBr(BigDecimal.ZERO, "");
        }

        BigDecimal dFBr = BigDecimal.ZERO;
        StringBuilder str = new StringBuilder();
        str.append(borderList.toString());

        for (BigDecimal bVal : borderList) {
            final BigDecimal aTerm = deltaFact.subtract(bVal);
            dFBr = dFBr.add(aTerm);
            if (str.length() == 0) {
                str.append(aTerm);
            } else {
                str.append(" + ").append(aTerm);
            }
        }

        return new DiffFactBr(dFBr, str.toString());
    }

    public static class DiffFactBr {
        final BigDecimal val;
        final String str;
        private DiffFactBr(BigDecimal val, String str) {
            this.val = val;
            this.str = str;
        }
    }
}
