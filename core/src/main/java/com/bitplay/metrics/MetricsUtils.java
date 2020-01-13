package com.bitplay.metrics;

import com.bitplay.market.model.PlBefore;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class MetricsUtils {

    public static void sendPlBefore(MetricsDictionary d, PlBefore b, Logger logger) {
        if (b.getCreateQuote() != null) {
            final long ms1 = Duration.between(b.getCreateQuote(), b.getGetQuote()).toMillis();
            final long ms2 = Duration.between(b.getGetQuote(), b.getSaveQuote()).toMillis();
            final long ms3 = Duration.between(b.getSaveQuote(), b.getSignalCheck()).toMillis();
            final long ms4 = Duration.between(b.getSignalCheck(), b.getSignalTime()).toMillis();
            final long ms5 = Duration.between(b.getSignalTime(), b.getRequestPlacing()).toMillis();
            final long ms6 = Duration.between(b.getRequestPlacing(), b.getGetAnswerFromPlacing()).toMillis();
            d.getBitmex_plBefore_1().record(ms1, TimeUnit.MILLISECONDS);
            d.getBitmex_plBefore_2().record(ms2, TimeUnit.MILLISECONDS);
            d.getBitmex_plBefore_3().record(ms3, TimeUnit.MILLISECONDS);
            d.getBitmex_plBefore_4().record(ms4, TimeUnit.MILLISECONDS);
            d.getBitmex_plBefore_5().record(ms5, TimeUnit.MILLISECONDS);
            d.getBitmex_plBefore_6().record(ms6, TimeUnit.MILLISECONDS);
            logger.info(String.format("plBefore ms: %s %s %s %s %s %s", ms1, ms2, ms3, ms4, ms5, ms6));
        }

    }
}
