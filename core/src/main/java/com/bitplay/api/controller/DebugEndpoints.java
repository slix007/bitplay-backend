package com.bitplay.api.controller;

import com.bitplay.api.domain.ResultJson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * Created by Sergey Shurmin on 9/23/17.
 */
@RestController
public class DebugEndpoints {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @RequestMapping(value = "/deadlock/check", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson checkDeadlocks() {
        return detectDeadlock();
    }

    private ResultJson detectDeadlock() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long[] monThreadIds = threadBean.findMonitorDeadlockedThreads();
        long[] threadIds = threadBean.findDeadlockedThreads();
        int deadlockedThreads = monThreadIds != null ? monThreadIds.length : 0;
        deadlockedThreads += (threadIds != null ? threadIds.length : 0);
        final String deadlocksMsg = "Number of deadlocked threads: " + deadlockedThreads;
        if (deadlockedThreads > 0) {
            System.out.println(deadlocksMsg);
            warningLogger.info(deadlocksMsg);
        }
        return new ResultJson(String.valueOf(deadlockedThreads), deadlocksMsg);
    }
}
