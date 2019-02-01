package com.bitplay.metrics;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.external.HostResolver;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.spring.autoconfigure.MeterRegistryCustomizer;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class MetricsDictionary {

    private DistributionSummary bitmexDelta;
    private DistributionSummary okexDelta;
//    private Timer bitmexDeltaTimer;
//    private Timer okexDeltaTimer;

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

//            bitmexDeltaTimer = Timer.builder("fplay.timer_delta1")
//                    .publishPercentiles(0.5, 0.95) // median and 95th percentile
//                    .publishPercentileHistogram()
//                    .register(registry);
//            okexDeltaTimer = Timer.builder("fplay.timer_delta2")
////                    .publishPercentiles(0.5, 0.95) // median and 95th percentile
////                    .publishPercentileHistogram()
//                    .register(registry);
//                    .sla(Duration.ofMillis(100))
//                    .minimumExpectedValue(Duration.ofMillis(1))
//                    .maximumExpectedValue(Duration.ofSeconds(10));
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
}