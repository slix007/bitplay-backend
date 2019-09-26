package com.bitplay.config;

import io.micrometer.core.instrument.util.NamedThreadFactory;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class SpringAsyncConfig {

    @Bean(name = "ntUsdSignalCheckExecutor")
    public Executor threadPoolTaskExecutor() {
        return Executors.newSingleThreadExecutor(new NamedThreadFactory("ntUsdSignalCheckExecutor"));
    }

}
