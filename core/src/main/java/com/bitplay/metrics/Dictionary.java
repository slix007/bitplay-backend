package com.bitplay.metrics;

import com.bitplay.arbitrage.ArbitrageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class Dictionary {

    public static final String B_DELTA = "fplay.b_delta";
    public static final String O_DELTA = "fplay.o_delta";

//    @Autowired
//    private HostResolver hostResolver;

    @Autowired
    private ArbitrageService arbitrageService;

//    private List<String> words = new CopyOnWriteArrayList<>();

//    public Dictionary(MeterRegistry registry) { // The dependencies of some of the beans in the application context form a cycle:
//        registry.gaugeCollectionSize("fplay.b_delta", Tags.empty(), words);
//        registry.gaugeCollectionSize("fplay.o_delta", Tags.empty(), words);
//        registry.gaugeCollectionSize("dictionary.size", Tags.empty(), this.words);
//    }

//    @Bean
//    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
//        return registry -> registry.config().commonTags("host", hostResolver.getHostname());
//    }

    @Bean
    FplayMetrics fplayMetrics() {
        return new FplayMetrics(arbitrageService);
    }


}
