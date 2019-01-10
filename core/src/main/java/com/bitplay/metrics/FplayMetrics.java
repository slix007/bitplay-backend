package com.bitplay.metrics;

import com.bitplay.arbitrage.ArbitrageService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.config.MeterFilter;

public class FplayMetrics implements MeterBinder {

    private ArbitrageService arbitrageService;

    public FplayMetrics(ArbitrageService arbitrageService) {
        this.arbitrageService = arbitrageService;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.config()
                .meterFilter(MeterFilter.ignoreTags("too.much.information"))
                .meterFilter(MeterFilter.denyNameStartsWith("tomcat"));

        Gauge.builder("delta1", arbitrageService, a -> a.getDelta1().doubleValue())
                .description("delta1")
                .baseUnit("buffers")
                .register(registry);
        Gauge.builder("delta2", arbitrageService, a -> a.getDelta2().doubleValue())
                .description("delta1")
                .baseUnit("buffers")
                .register(registry);
    }


}
