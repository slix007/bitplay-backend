package com.bitplay.arbitrage;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 1/25/18.
 */
public class ArbUtils {
    public static BigDecimal getBorder(BordersService.TradingSignal tradingSignal) {
        if (tradingSignal != null && tradingSignal.borderValueList != null && tradingSignal.borderValueList.size() > 0) {
            return tradingSignal.borderValueList.get(0);
        }
        return null;
    }
}
