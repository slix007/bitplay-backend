package com.bitplay.api.controller;

import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.service.RestartService;
import com.bitplay.market.bitmex.BitmexService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Map;

/**
 * Created by Sergey Shurmin on 9/23/17.
 */
@RestController
public class DebugEndpoints {

    private final static Logger logger = LoggerFactory.getLogger(BitmexService.class);
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private RestartService restartService;

    @RequestMapping(value = "/full-restart", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson fullRestart() throws IOException {

        restartService.doFullRestart("API request");

        return new ResultJson("Restart has been sent", "");
    }

    @RequestMapping(value = "/deadlock/check", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson checkDeadlocks() {
        return detectDeadlock();
    }

    public static ResultJson detectDeadlock() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long[] threadIds = threadBean.findDeadlockedThreads();
        int deadlockedThreads = (threadIds != null ? threadIds.length : 0);
        final String deadlocksMsg = "Number of deadlocked threads: " + deadlockedThreads;

        if (deadlockedThreads > 0) {
            logger.info(deadlocksMsg);
            warningLogger.info(deadlocksMsg);

            ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadIds);
            handleDeadlock(threadInfos);
        }
        return new ResultJson(String.valueOf(deadlockedThreads), deadlocksMsg);
    }

    private static void handleDeadlock(final ThreadInfo[] deadlockedThreads) {
        if (deadlockedThreads != null) {
            logger.error("Deadlock detected!");

            Map<Thread, StackTraceElement[]> stackTraceMap = Thread.getAllStackTraces();
            for (ThreadInfo threadInfo : deadlockedThreads) {

                if (threadInfo != null) {

                    for (Thread thread : Thread.getAllStackTraces().keySet()) {

                        if (thread.getId() == threadInfo.getThreadId()) {
                            logger.error(threadInfo.toString().trim());

                            for (StackTraceElement ste : thread.getStackTrace()) {
                                logger.error("\t" + ste.toString().trim());
                            }
                        }
                    }
                }
            }
        }
    }
}
