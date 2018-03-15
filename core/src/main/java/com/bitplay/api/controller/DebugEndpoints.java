package com.bitplay.api.controller;

import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.service.RestartService;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.BitmexXRateLimit;
import com.bitplay.market.okcoin.OkCoinService;

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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * Created by Sergey Shurmin on 9/23/17.
 */
@RestController
public class DebugEndpoints {

    private final static Logger logger = LoggerFactory.getLogger(DebugEndpoints.class);
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private RestartService restartService;

    @Autowired
    private OkCoinService okCoinService;

    @Autowired
    private ArbitrageService arbitrageService;

    @RequestMapping(value = "/full-restart", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson fullRestart() throws IOException {

        restartService.doFullRestart("API request");

        return new ResultJson("Restart has been sent", "");
    }

    @RequestMapping(value = "/deadlock/check", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson checkDeadlocks() {
        final ResultJson resultJson = detectDeadlock();
//        arbitrageService.getParams()

        String deadLockDescr = resultJson.getDescription();

        final Date lastOBChange = arbitrageService.getParams().getLastOBChange();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        if (lastOBChange != null) {
            deadLockDescr += "<br>last_OB_change=" + sdf.format(lastOBChange);
        }
        final Date lastCorrCheck = arbitrageService.getParams().getLastCorrCheck();
        if (lastCorrCheck != null) {
            deadLockDescr += "<br>last_corr_check=" + sdf.format(lastCorrCheck);
        }
        final Date lastMDCCheck = arbitrageService.getParams().getLastMDCCheck();
        if (lastMDCCheck != null) {
            deadLockDescr += "<br>last_MDC_check=" + sdf.format(lastMDCCheck);
        }
        final BitmexService bs = (BitmexService) (arbitrageService.getFirstMarketService());
        final BitmexXRateLimit bitmexXRateLimit = bs.getxRateLimit();
        if (bitmexXRateLimit != null) {
            final Integer theLimit = bitmexXRateLimit.getxRateLimit();
            deadLockDescr += "<br>xRateLimit=" + (theLimit == 301 ? "no info" : theLimit)
                    + "; lastUpdate: " + sdf.format(bitmexXRateLimit.getLastUpdate());
        }

        return new ResultJson(resultJson.getResult(), deadLockDescr);
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
