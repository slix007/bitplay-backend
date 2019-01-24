package com.bitplay.metrics;

import com.bitplay.external.HostResolver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.lang.NonNull;
import io.micrometer.spring.autoconfigure.MeterRegistryCustomizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FplayMetricsRegistryCustomizer implements MeterRegistryCustomizer<MeterRegistry> {

    @Autowired
    private HostResolver hostResolver;

    @Override
    public void customize(@NonNull MeterRegistry registry) {
        registry.config().commonTags("host", hostResolver.getHostname());
    }
}
