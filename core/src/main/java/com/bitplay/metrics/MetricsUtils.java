package com.bitplay.metrics;

import com.bitplay.market.model.PlBefore;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class MetricsUtils {

    public static void sendPlBefore(MetricsDictionary d, PlBefore b, Logger logger) {
        long ms1 = -1;
        long ms2 = -1;
        long ms3 = -1;
        long ms4 = -1;
        long ms5 = -1;
        long ms6 = -1;
        long ms7 = -1;
        if (b.getCreateQuote() != null && b.getGetQuote() != null) {
            ms1 = Duration.between(b.getCreateQuote(), b.getGetQuote()).toMillis();
            d.getBitmex_plBefore_1().record(ms1, TimeUnit.MILLISECONDS);
        }
        if (b.getGetQuote() != null && b.getSaveQuote() != null) {
            ms2 = Duration.between(b.getGetQuote(), b.getSaveQuote()).toMillis();
            d.getBitmex_plBefore_2().record(ms2, TimeUnit.MILLISECONDS);
        }
        if (b.getSaveQuote() != null && b.getSignalCheck() != null) {
            ms3 = Duration.between(b.getSaveQuote(), b.getSignalCheck()).toMillis();
            d.getBitmex_plBefore_3().record(ms3, TimeUnit.MILLISECONDS);
        }
        if (b.getSignalCheck() != null && b.getSignalTime() != null) {
            ms4 = Duration.between(b.getSignalCheck(), b.getSignalTime()).toMillis();
            d.getBitmex_plBefore_4().record(ms4, TimeUnit.MILLISECONDS);
        }
        if (b.getSignalTime() != null && b.getRequestPlacing() != null) {
            ms5 = Duration.between(b.getSignalTime(), b.getRequestPlacing()).toMillis();
            d.getBitmex_plBefore_5().record(ms5, TimeUnit.MILLISECONDS);
        }
        if (b.getRequestPlacing() != null && b.getMarketTransactTime() != null) {
            ms6 = Duration.between(b.getRequestPlacing(), b.getMarketTransactTime()).toMillis();
            d.getBitmex_plBefore_6().record(ms6, TimeUnit.MILLISECONDS);
        }
        if (b.getMarketTransactTime() != null && b.getGetAnswerFromPlacing() != null) {
            ms7 = Duration.between(b.getMarketTransactTime(), b.getGetAnswerFromPlacing()).toMillis();
            d.getBitmex_plBefore_7().record(ms7, TimeUnit.MILLISECONDS);
        }
        logger.info(String.format("plBefore ms: %s %s %s %s %s %s %s", ms1, ms2, ms3, ms4, ms5, ms6, ms7));
    }
}
