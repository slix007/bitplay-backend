package com.bitplay.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public class SchedulerUtils {

    public static Scheduler singleThread(String pattern) {
        final ExecutorService executor = singleThreadExecutor(pattern);
        return Schedulers.from(executor);
    }

    public static ScheduledExecutorService singleThreadExecutor(String pattern) {
        final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat(pattern).build();
        return Executors.newSingleThreadScheduledExecutor(namedThreadFactory);
    }

}
