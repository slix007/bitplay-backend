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
        return createDiscardOldestTaskExecutor("ntUsdSignalCheckExecutor-");
    }

    @Bean(name = "signalCheckExecutor")
    public Executor signalCheckExecutor() {
        return createDiscardOldestTaskExecutor("signal-check-");
    }

    @Bean(name = "portionsStopCheckExecutor")
    public Executor portionsStopCheckExecutor() {
        return createDiscardOldestTaskExecutor("portions-stop-check-");
    }

    @Bean(name = "movingExecutorLeft")
    public Executor movingExecutorLeft() {
        return createDiscardOldestTaskExecutor("moving-executor-left");
    }

    @Bean(name = "movingExecutorRight")
    public Executor movingExecutorRight() {
        return createDiscardOldestTaskExecutor("moving-executor-right");
    }

    @Bean(name = "settingsVolatileAutoParamsExecutor")
    public Executor settingsVolatileAutoParamsExecutor() {
        return Executors.newSingleThreadExecutor(new NamedThreadFactory("settingsVolatileAutoParamsExecutor"));
    }

    private ThreadPoolTaskExecutor createDiscardOldestTaskExecutor(String threadNamePrefix) {
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
