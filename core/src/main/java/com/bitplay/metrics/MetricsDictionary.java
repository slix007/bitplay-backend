package com.bitplay.metrics;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.external.HostResolver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.spring.autoconfigure.MeterRegistryCustomizer;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class MetricsDictionary {

    private DistributionSummary bitmexDelta;
    private DistributionSummary okexDelta;
    private Counter bitmexReconnectsCounter;
    private Timer okexPing;
    private Timer bitmexPing;

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

            okexPing = Timer.builder("fplay.timer.okexPing")
//                    .publishPercentiles(0.5, 0.95) // median and 95th percentile
//                    .publishPercentileHistogram()
                    .register(registry);
            bitmexPing = Timer.builder("fplay.timer.bitmexPing")
                    .register(registry);

            bitmexReconnectsCounter = registry.counter("bitmexReconnectsCounter");

//            FunctionCounter counter = FunctionCounter
//                    .builder("counter", state, state -> state.count())
//                    .baseUnit("beans") // optional
//                    .description("a description of what this counter does") // optional
//                    .tags("region", "test") // optional
//                    .register(registry);
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

    public void putOkexPing(long ms) {
        okexPing.record(ms, TimeUnit.MILLISECONDS);
    }

    public void putBitmexPing(long ms) {
        bitmexPing.record(ms, TimeUnit.MILLISECONDS);
    }
}
