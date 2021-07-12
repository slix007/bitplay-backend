package com.bitplay.arbitrage;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AfterArbService {


    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("arb-after-worker-%d").build()
    );
    private static final long SHUTDOWN_TIME_SEC = 60;
    private static final long DELAY_MS = 5000;

    public void addTask(AfterArbTask afterArbTask) {

        Future<?> submit = executor.schedule(afterArbTask, DELAY_MS, TimeUnit.MILLISECONDS);

    }

    //TODO shutdown gracefully
//    private void init() {
//        Runtime.getRuntime().addShutdownHook(new Thread() {
//            public void run() {
//                executor.shutdown();
//                if (!executor.awaitTermination(SHUTDOWN_TIME_SEC, TimeUnit.SECONDS)) { //optional *
//                    log.warn("Executor did not terminate in the specified time."); //optional *
//                    List<Runnable> droppedTasks = executor.shutdownNow(); //optional **
//                    log.warn("Executor was abruptly shut down. " + droppedTasks.size() + " tasks will not be executed."); //optional **
//                }
//            }
//        });
//    }
}
