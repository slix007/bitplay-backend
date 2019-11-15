package com.bitplay.arbitrage.posdiff;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class NtUsdExecutor {

    private final ScheduledExecutorService calcPosDiffExecutor = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("pos-diff-thread-%d").build()
    );

    @PreDestroy
    public void preDestory() {
        calcPosDiffExecutor.shutdown();
    }

    void addTask(Runnable task) {
        calcPosDiffExecutor.execute(task);
    }

    void addScheduledTask(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        calcPosDiffExecutor.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }


}
