package com.bitplay.api.controller;

import com.bitplay.api.domain.MonAllJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.api.service.RestartService;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.model.BitmexXRateLimit;
import com.bitplay.market.okcoin.OOHangedCheckerService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.MonitoringDataService;
import com.bitplay.persistance.domain.mon.Mon;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by Sergey Shurmin on 9/23/17.
 */
@Secured("ROLE_TRADER")
@RestController
@Slf4j
public class DebugEndpoints {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private RestartService restartService;

    @Autowired
    private OOHangedCheckerService ooHangedCheckerService;

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private BitmexService bitmexService;

    @Autowired
    private MonitoringDataService monitoringDataService;

    @RequestMapping(value = "/full-restart", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson fullRestart() throws IOException {

        restartService.doFullRestart(RestartService.API_REQUEST);

        return new ResultJson("Restart has been sent", "");
    }

    @RequestMapping(value = "/bitmex-reconnect", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson reconnect() {

        String isDone = "is done";
        String errorMsg = "";
        try {
            bitmexService.requestReconnect(true);
        } catch (Exception e) {
            log.error("Can't reconnect bitmex", e);
            isDone = "failed";
            errorMsg = e.getMessage();
        }

        return new ResultJson("Reconnect " + isDone, errorMsg);
    }

    @RequestMapping(value = "/bitmex-ob-resubscribe", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson reOBResubscribe() {

        String isDone = "is done";
        String errorMsg = "";
        try {
            bitmexService.reSubscribeOrderBooks(true);
        } catch (Exception e) {
            log.error("Can't re-subscribe bitmex OB", e);
            isDone = " failed.";
            errorMsg = e.getMessage();
        }

        return new ResultJson("Resubscribe OB " + isDone, errorMsg);
    }

    @RequestMapping(value = "/mon/all", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public MonAllJson checkDeadlocks() {
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

        deadLockDescr += "<br>OOHangedChecker: " + ooHangedCheckerService.getStatus();

        deadLockDescr += "<br>BitmexReconnectCount=" + bs.getReconnectCount();
        deadLockDescr += "<br>BitmexOrderBookErrors=" + getBitmexOrderBookErrors();

        if (arbitrageService.getLastCalcSumBal() != null) {
            Date lastCalcSumBal = Date.from(arbitrageService.getLastCalcSumBal());

            deadLockDescr += "<br>Last sum_bal update=" + sdf.format(lastCalcSumBal);
        }

        Mon monBitmexPlacing = monitoringDataService.fetchMon(BitmexService.NAME, "placeOrder");
        Mon monBitmexMoving = monitoringDataService.fetchMon(BitmexService.NAME, "moveMakerOrder");
        Mon monOkexPlacing = monitoringDataService.fetchMon(OkCoinService.NAME, "placeOrder");
        Mon monOkexMoving = monitoringDataService.fetchMon(OkCoinService.NAME, "moveMakerOrder");

        return new MonAllJson(resultJson.getResult(), deadLockDescr,
                monBitmexPlacing, monBitmexMoving,
                monOkexPlacing, monOkexMoving);
    }

    @RequestMapping(value = "/mon/reset", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public MonAllJson resetMon(@RequestBody MonAllJson monAllJson) {
        if (monAllJson.getMonBitmexPlacing() != null) {
            Mon monBitmexPlacing = monitoringDataService.fetchMon(BitmexService.NAME, "placeOrder");
            Mon monBitmexMoving = monitoringDataService.fetchMon(BitmexService.NAME, "moveMakerOrder");
            Mon monOkexPlacing = monitoringDataService.fetchMon(OkCoinService.NAME, "placeOrder");
            Mon monOkexMoving = monitoringDataService.fetchMon(OkCoinService.NAME, "moveMakerOrder");
            monBitmexPlacing = Mon.createDefaults(monBitmexPlacing.getId());
            monBitmexMoving = Mon.createDefaults(monBitmexMoving.getId());
            monOkexPlacing = Mon.createDefaults(monOkexPlacing.getId());
            monOkexMoving = Mon.createDefaults(monOkexMoving.getId());

            monitoringDataService.saveMon(monBitmexPlacing);
            monitoringDataService.saveMon(monBitmexMoving);
            monitoringDataService.saveMon(monOkexPlacing);
            monitoringDataService.saveMon(monOkexMoving);
        }
        return monAllJson;
    }

    public String getBitmexOrderBookErrors() {
        Integer count = bitmexService.getOrderBookErrors();
        String statusString = "0";
        if (count > 0) {
            statusString = String.format("<span style=\"color: red\">%s<span>", count);
        }
        return statusString;
    }


    public static ResultJson detectDeadlock() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long[] threadIds = threadBean.findDeadlockedThreads();
        int deadlockedThreads = (threadIds != null ? threadIds.length : 0);
        final String deadlocksMsg = "Number of deadlocked threads: " + deadlockedThreads;

        if (deadlockedThreads > 0) {
            log.info(deadlocksMsg);
            warningLogger.info(deadlocksMsg);

            ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadIds);
            handleDeadlock(threadInfos);
        }
        return new ResultJson(String.valueOf(deadlockedThreads), deadlocksMsg);
    }

    private static void handleDeadlock(final ThreadInfo[] deadlockedThreads) {
        if (deadlockedThreads != null) {
            log.error("Deadlock detected!");

            Map<Thread, StackTraceElement[]> stackTraceMap = Thread.getAllStackTraces();
            for (ThreadInfo threadInfo : deadlockedThreads) {

                if (threadInfo != null) {

                    for (Thread thread : Thread.getAllStackTraces().keySet()) {

                        if (thread.getId() == threadInfo.getThreadId()) {
                            log.error(threadInfo.toString().trim());

                            for (StackTraceElement ste : thread.getStackTrace()) {
                                log.error("\t" + ste.toString().trim());
                            }
                        }
                    }
                }
            }
        }
    }
}
