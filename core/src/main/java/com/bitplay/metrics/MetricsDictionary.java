package com.bitplay.metrics;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.external.HostResolver;
import com.bitplay.market.bitmex.BitmexService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.spring.autoconfigure.MeterRegistryCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class MetricsDictionary {

    private DistributionSummary bitmexDelta;
    private DistributionSummary okexDelta;
    private Counter bitmexReconnectsCounter;
    private Timer okexPing;
    private Timer bitmexPing;
    private Timer bitmexPlacing;
    private Timer bitmexPlacingWhole;
    private Timer bitmexPlacingBefore;
    private Timer bitmex_plBefore_ob_saveTime_traditional10;
    private Timer bitmex_plBefore_ob_saveTime_incremental50;
    private Timer bitmex_plBefore_ob_saveTime_incrementalFull;
    private Timer bitmex_plBefore_checkTime;
    private Timer bitmex_plBefore_preparePlaceTime;
    private Timer bitmexUpdateOrder;
    private Timer okexPlacing;
    private Timer okexPlacingWhole;
    private Timer okexPlacingBefore;
    private Timer okexMovingWhole;
    private Counter bitmexObCounter;
    private Counter okexObCounter;
    private Timer bitmexRecalcAfterUpdate;
    private Timer.Sample bitmexRecalcAfterUpdateSample;
    private Timer okexRecalcAfterUpdate;
    private Timer.Sample okexRecalcAfterUpdateSample;
    private Timer bitmexMovingIter;
    private Timer okexMovingIter;

    private MeterRegistry meterRegistry;
    //TODO fix "The dependencies of some of the beans in the application context form a cycle:"
    //use @RequiredArgsConstructor and private final HostResolver hostResolver;
    @Autowired
    private HostResolver hostResolver;

    @Autowired
    private ArbitrageService arbitrageService;

//    private List<String> words = new CopyOnWriteArrayList<>();

//    public Dictionary(MeterRegistry registry) { // The dependencies of some of the beans in the application context form a cycle:
//        registry.gaugeCollectionSize("fplay.b_delta", Tags.empty(), words);
//        registry.gaugeCollectionSize("fplay.o_delta", Tags.empty(), words);
//        registry.gaugeCollectionSize("dictionary.size", Tags.empty(), this.words);
//    }

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> {
            registry.config()
                    .meterFilter(MeterFilter.ignoreTags("too.much.information"))
                    .meterFilter(MeterFilter.denyNameStartsWith("tomcat"));
            registry.config().commonTags("host", hostResolver.getHostnameForMetrics());
            bitmexDelta = DistributionSummary
                    .builder("fplay.delta.bitmex")
                    .description("b_delta") // optional
                    .baseUnit("quote") // optional (1)
//                    .tags("env", "test") // optional
//                    .scale(100) // optional (2)
                    .register(registry);
            okexDelta = DistributionSummary
                    .builder("fplay.delta.okex")
                    .description("o_delta") // optional
                    .baseUnit("quote") // optional (1)
//                    .tags("env", "test") // optional
//                    .scale(100) // optional (2)
                    .register(registry);

            Gauge.builder("fplay.delta1", arbitrageService, a -> a.getDelta1().doubleValue())
                    .description("delta1")
                    .baseUnit("quote")
                    .register(registry);
            Gauge.builder("fplay.delta2", arbitrageService, a -> a.getDelta2().doubleValue())
                    .description("delta1")
                    .baseUnit("quote")
                    .register(registry);

            okexPing = Timer.builder("fplay.timer.okexPing").register(registry);
            bitmexPing = Timer.builder("fplay.timer.bitmexPing").register(registry);
            bitmexPlacing = Timer.builder("fplay.timer.bitmexPlacing").register(registry);
            okexPlacing = Timer.builder("fplay.timer.okexPlacing").register(registry);
            bitmexPlacingWhole = Timer.builder("fplay.timer.bitmexPlacingWhole").register(registry);
            okexPlacingWhole = Timer.builder("fplay.timer.okexPlacingWhole").register(registry);
            bitmexPlacingBefore = Timer.builder("fplay.timer.bitmexPlacingBefore").register(registry);
            bitmex_plBefore_ob_saveTime_traditional10 = Timer.builder("fplay.timer.bitmex_plBefore_ob_saveTime_traditional10").register(registry);
            bitmex_plBefore_ob_saveTime_incremental50 = Timer.builder("fplay.timer.bitmex_plBefore_ob_saveTime_incremental50").register(registry);
            bitmex_plBefore_ob_saveTime_incrementalFull = Timer.builder("fplay.timer.bitmex_plBefore_ob_saveTime_incrementalFull").register(registry);
            bitmex_plBefore_checkTime = Timer.builder("fplay.timer.bitmex_plBefore_checkTime").register(registry);
            bitmex_plBefore_preparePlaceTime = Timer.builder("fplay.timer.bitmex_plBefore_preparePlaceTime").register(registry);
            okexPlacingBefore = Timer.builder("fplay.timer.okexPlacingBefore").register(registry);
            bitmexUpdateOrder = Timer.builder("fplay.timer.bitmexUpdateOrder").register(registry);
            okexMovingWhole = Timer.builder("fplay.timer.okexMovingWhole").register(registry);
            bitmexRecalcAfterUpdate = Timer.builder("fplay.timer.recalcAfterUpdate").tag("market", "bitmex").register(registry);
            okexRecalcAfterUpdate = Timer.builder("fplay.timer.recalcAfterUpdate").tag("market", "okex").register(registry);
            bitmexMovingIter = Timer.builder("fplay.timer.bitmexMovingIter").tag("market", "okex").register(registry);
            okexMovingIter = Timer.builder("fplay.timer.okexMovingIter").tag("market", "okex").register(registry);

            bitmexReconnectsCounter = registry.counter("fplay.counter.bitmexReconnectsCounter");
            bitmexObCounter = registry.counter("fplay.counter.bitmexObCounter");
            okexObCounter = registry.counter("fplay.counter.okexObCounter");

            meterRegistry = registry;
        };
    }

    public void setDeltas(BigDecimal delta1Update, BigDecimal delta2Update) {
        final double d1 = delta1Update.doubleValue();
        final double d2 = delta2Update.doubleValue();
        bitmexDelta.record(d1);
        okexDelta.record(d2);
//        bitmexDeltaTimer.record((long) (d1 * 100), TimeUnit.MILLISECONDS);
//        okexDeltaTimer.record((long) (d2 * 100), TimeUnit.MILLISECONDS);
    }

    public void incBitmexReconnects() {
        bitmexReconnectsCounter.increment();
    }

    public void incBitmexObCounter() {
        bitmexObCounter.increment();
    }

    public void incOkexObCounter() {
        okexObCounter.increment();
    }

    public void putOkexPing(long ms) {
        okexPing.record(ms, TimeUnit.MILLISECONDS);
    }

    public void putBitmexPing(long ms) {
        bitmexPing.record(ms, TimeUnit.MILLISECONDS);
    }

    public void putBitmexPlacing(long ms) {
        bitmexPlacing.record(ms, TimeUnit.MILLISECONDS);
    }

    public void putOkexPlacing(long ms) {
        okexPlacing.record(ms, TimeUnit.MILLISECONDS);
    }

    public void putBitmexPlacingWhole(long ms) {
        bitmexPlacingWhole.record(ms, TimeUnit.MILLISECONDS);
    }

    public void putOkexPlacingWhole(long ms) {
        okexPlacingWhole.record(ms, TimeUnit.MILLISECONDS);
    }

    public void putBitmexPlacingBefore(long ms) {
        bitmexPlacingBefore.record(ms, TimeUnit.MILLISECONDS);
    }

    public void putBitmex_plBefore_ob_saveTime_traditional10(long ms) {
        bitmex_plBefore_ob_saveTime_traditional10.record(ms, TimeUnit.MILLISECONDS);
    }

    public void putBitmex_plBefore_ob_saveTime_incremental50(long ms) {
        bitmex_plBefore_ob_saveTime_incremental50.record(ms, TimeUnit.MILLISECONDS);
    }

    public void putBitmex_plBefore_ob_saveTime_incrementalFull(long ms) {
        bitmex_plBefore_ob_saveTime_incrementalFull.record(ms, TimeUnit.MILLISECONDS);
    }

    public void putBitmex_plBefore_checkTime(long ms) {
        bitmex_plBefore_checkTime.record(ms, TimeUnit.MILLISECONDS);
    }

    public void putBitmex_plBefore_preparePlaceTime(long ms) {
        bitmex_plBefore_preparePlaceTime.record(ms, TimeUnit.MILLISECONDS);
    }


    public void putOkexPlacingBefore(long ms) {
        okexPlacingBefore.record(ms, TimeUnit.MILLISECONDS);
    }

    public void putBitmexUpdateOrder(Duration duration) {
        bitmexUpdateOrder.record(duration);
    }

    public void putOkexMovingWhole(long ms) {
        okexMovingWhole.record(ms, TimeUnit.MILLISECONDS);
    }

    public void startRecalcAfterUpdate(String name) {
        if (meterRegistry != null) {
            Timer.Sample sample = Timer.start(meterRegistry);
            if (name.equals(BitmexService.NAME)) {
                bitmexRecalcAfterUpdateSample = sample;
            } else {
                okexRecalcAfterUpdateSample = sample;
            }
        }
    }

    public void stopRecalcAfterUpdate(String name) {
        if (meterRegistry != null) {
            if (name.equals(BitmexService.NAME)) {
                if (bitmexRecalcAfterUpdateSample != null) {
                    bitmexRecalcAfterUpdateSample.stop(bitmexRecalcAfterUpdate);
                }
            } else {
                if (okexRecalcAfterUpdateSample != null) {
                    final long stop = okexRecalcAfterUpdateSample.stop(okexRecalcAfterUpdate);
                }
            }
        }
    }

    public Timer getBitmexMovingIter() {
        return bitmexMovingIter;
    }

    public Timer getOkexMovingIter() {
        return okexMovingIter;
    }
}
