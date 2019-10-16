package com.bitplay.config;

import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class SpringAsyncConfig {

    @Bean(name = "ntUsdSignalCheckExecutor")
    public Executor threadPoolTaskExecutor() {
        return Executors.newSingleThreadExecutor(new NamedThreadFactory("ntUsdSignalCheckExecutor"));
    }

    @Bean(name = "signalCheckExecutor")
    public Executor signalCheckExecutor() {
        return Executors.newSingleThreadExecutor(new NamedThreadFactory("signal-check"));
    }

    @Bean(name = "portionsStopCheckExecutor")
    public Executor portionsStopCheckExecutor() {
        return Executors.newSingleThreadExecutor(new NamedThreadFactory("portions-stop-check"));
    }

}
