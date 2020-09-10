package com.bitplay.config;

import io.micrometer.core.instrument.util.NamedThreadFactory;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class SpringAsyncConfig {

    @Bean(name = "ntUsdSignalCheckExecutor")
    public Executor threadPoolTaskExecutor() {
        return createDiscardOldersTaskExecutor("ntUsdSignalCheckExecutor-");
    }

    @Bean(name = "signalCheckExecutor")
    public Executor signalCheckExecutor() {
        return createDiscardOldersTaskExecutor("signal-check-");
    }

    @Bean(name = "portionsStopCheckExecutor")
    public Executor portionsStopCheckExecutor() {
        return createDiscardOldersTaskExecutor("portions-stop-check-");
    }

    @Bean(name = "movingExecutor")
    public Executor movingExecutor() {
        return Executors.newFixedThreadPool(2, new NamedThreadFactory("moving-executor"));
    }

    @Bean(name = "settingsVolatileAutoParamsExecutor")
    public Executor settingsVolatileAutoParamsExecutor() {
        return Executors.newSingleThreadExecutor(new NamedThreadFactory("settingsVolatileAutoParamsExecutor"));
    }

    private ThreadPoolTaskExecutor createDiscardOldersTaskExecutor(String threadNamePrefix) {
        final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new DiscardOldestPolicy());
        executor.initialize();
        return executor;
    }

}
