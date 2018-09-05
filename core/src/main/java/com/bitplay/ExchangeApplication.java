package com.bitplay;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@SpringBootApplication
@EnableScheduling
@EnableMongoAuditing
public class ExchangeApplication extends SpringBootServletInitializer implements SchedulingConfigurer {

	public static void main(String[] args) {
        new ExchangeApplication()
                .configure(new SpringApplicationBuilder(ExchangeApplication.class))
                .run(args);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService taskScheduler() {
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("scheduled-%d")
                .build();
        return Executors.newScheduledThreadPool(10, namedThreadFactory);
    }
}
