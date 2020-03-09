package com.bitplay.market;

import com.bitplay.api.service.RestartService;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.events.ArbitrageReadyEvent;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.okcoin.OkexSettlementService;
import com.bitplay.persistance.MonitoringDataService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.mon.MonRestart;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.utils.Utils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Created by Sergey Shurmin on 1/12/18.
 */
@Slf4j
@Service
public class ExtrastopService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    protected final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder()
            .setNameFormat("extrastop-service-%d").build());

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private RestartService restartService;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private MonitoringDataService monitoringDataService;

    @Autowired
    private SlackNotifications slackNotifications;

    @Autowired
    private OkexSettlementService okexSettlementService;

    private String details = "";

    private volatile Instant lastRun = null;

    @EventListener(ArbitrageReadyEvent.class)
    public void init() {
        log.info("ExtrastopService has started");
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                checkOrderBooks();
            } catch (Exception e) {
                log.error("Error on checkOrderBooks", e);
            }
        }, 0, 10, TimeUnit.SECONDS);
    }

    //    @Scheduled(initialDelay = 60 * 1000, fixedDelay = 10 * 1000)
    private void checkOrderBooks() {
        Instant start = Instant.now();
        try {
            if (!arbitrageService.isFirstDeltasCalculated()) {
                // not started yet
                return;
            }

            checkLastRun();

            if (arbitrageService.getLeftMarketService().isReconnectInProgress()) {
                log.warn("skip checkOrderBooks: bitmex reconnect IN_PROGRESS");
                return;
            }

            if (isHanged()) {
                final String msg = "Set STOPPED. Stop markets and schedule restart in 30 sec.";
                warningLogger.warn(msg);
                arbitrageService.setArbStateStopped();
                slackNotifications.sendNotify(NotifyType.STOPPED, msg);

                startTimerToRestart(details);
            }

        } catch (IllegalArgumentException e) {
            final String msg = "Set STOPPED. Error on check times: " + e.getMessage();
            log.error(msg, e);
            arbitrageService.setArbStateStopped();
            slackNotifications.sendNotify(NotifyType.STOPPED, msg);

            startTimerToRestart(e.getMessage());
        } catch (Exception e) {
            log.error("on check times", e);
//            warningLogger.error("ERROR on check times", e);
        }
        Instant end = Instant.now();
        Utils.logIfLong(start, end, log, "checkOrderBooks");
    }

    private void checkLastRun() {
        Instant now = Instant.now();
        if (lastRun != null && Duration.between(lastRun, now).getSeconds() > 15) {
            LocalDateTime ldt = getLastRun();
            String msg = String.format("checkOrderBooks lastRun(%s) was too long (%s)", ldt, Duration.between(lastRun, now).getSeconds());
            warningLogger.warn(msg);
            log.warn(msg);
        }
        lastRun = now;
    }

    public LocalDateTime getLastRun() {
        return lastRun == null ? null : LocalDateTime.ofInstant(lastRun, ZoneId.systemDefault());
    }

    private boolean isHanged() {
        Settings settings = settingsRepositoryService.getSettings();
        Integer maxGap = settings.getRestartSettings().getMaxTimestampDelay();

        MonRestart monRestart = monitoringDataService.fetchRestartMonitoring();

        final OrderBook bOB = arbitrageService.getLeftMarketService().getOrderBook();
        final Date bT = getBitmexOrderBook3BestTimestamp(bOB); // bitmexService.getOrderBookLastTimestamp();
//            log.info("Bitmex timestamp: " + bT.toString());
        final OrderBook oOB = arbitrageService.getRightMarketService().getOrderBook();
        final Date oT = oOB.getTimeStamp();
        details = "";

        long bDiffSec = getDiffSec(bT, "Bitmex");
        long oDiffSec = getDiffSec(oT, "Okex");
        monRestart.addBTimestampDelay(BigDecimal.valueOf(bDiffSec));
        monRestart.addOTimestampDelay(BigDecimal.valueOf(oDiffSec));
        monitoringDataService.saveRestartMonitoring(monRestart);

        boolean bWrong = isOrderBookPricesWrong(bOB);
        boolean oWrong = isOrderBookPricesWrong(oOB);
        boolean isBDiff = bDiffSec > maxGap;
        boolean isODiff = oDiffSec > maxGap;
        if (okexSettlementService.isSettlementMode()) {
            oWrong = false;
            isODiff = false;
        }

        boolean isHanged = false;
        if (isBDiff || isODiff || bWrong || oWrong) {

            details += String.format("maxTimestampDelay(maxDiff)=%s, b_bid_more_ask=%s, o_bid_more_ask=%s",
                    settings.getRestartSettings().getMaxTimestampDelay(),
                    bWrong, oWrong);

            warningLogger.warn(details);
            printBest3Prices(bOB, oOB);

            isHanged = true;

        }
        // check bitmex extraSet
        if (!isHanged && arbitrageService.isEth()) {
            isHanged = isHangedExtra(settings, maxGap);
        }

        return isHanged;
    }

    private boolean isHangedExtra(Settings settings, Integer maxGap) {
        boolean isHanged = false;
        final OrderBook bOBExtra = arbitrageService.getLeftMarketService().getOrderBookXBTUSD();
        final Date bTExtra = getBitmexOrderBook3BestTimestamp(bOBExtra); // bitmexService.getOrderBookLastTimestamp();
        long bDiffSecExtra = getDiffSec(bTExtra, "Bitmex-XBTUSD");
        boolean bWrongExtra = isOrderBookPricesWrong(bOBExtra);
        boolean isBDiffExtra = bDiffSecExtra > maxGap;
        if (bWrongExtra || isBDiffExtra) {
            details += String.format("Bitmex-XBTUSD extraSet maxTimestampDelay(maxDiff)=%s, b_XBTUSD_bid_more_ask=%s,",
                    settings.getRestartSettings().getMaxTimestampDelay(),
                    bWrongExtra);
            warningLogger.warn(details);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
            warningLogger.warn(String.format("now time is %s ", sdf.format(new Date())));
            Utils.getBestBids(bOBExtra, 3).forEach(item -> printItem("bitmex-XBTUSD", item));
            Utils.getBestAsks(bOBExtra, 3).forEach(item -> printItem("bitmex-XBTUSD", item));

            isHanged = true;
        }
        return isHanged;
    }

    private void printBest3Prices(OrderBook bOB, OrderBook oOB) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        warningLogger.warn(String.format("now time is %s ", sdf.format(new Date())));
        Utils.getBestBids(bOB, 3).forEach(item -> printItem("left", item));
        Utils.getBestAsks(bOB, 3).forEach(item -> printItem("left", item));

        Utils.getBestBids(oOB, 3).forEach(item -> printItem("right", item));
        Utils.getBestAsks(oOB, 3).forEach(item -> printItem("right", item));
    }

    private void printItem(String market, LimitOrder item) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

        warningLogger.warn(String.format("%s: %s: t=%s, q=%s, c=%s, id=%s",
                market,
                item.getType(),
                sdf.format(item.getTimestamp()),
                item.getLimitPrice(),
                item.getTradableAmount(),
                item.getId()));
    }

    private boolean isOrderBookPricesWrong(OrderBook orderBook) {
        LimitOrder bid1 = Utils.getBestBid(orderBook);
        LimitOrder ask1 = Utils.getBestAsk(orderBook);
        return bid1.compareTo(ask1) >= 0;
    }

    private Date getBitmexOrderBook3BestTimestamp(OrderBook orderBook) {
        return Stream.concat(Utils.getBestBids(orderBook, 3).stream(),
                Utils.getBestAsks(orderBook, 3).stream())
                .map(LimitOrder::getTimestamp)
                .reduce((date, date2) -> date.after(date2) ? date : date2) // use the latest date
                .orElseThrow(() -> new IllegalArgumentException("Can not get left timestamp"));
    }

    private long getDiffSec(Date marketUpdateTime, String name) {
        long diffSec = Duration.between(marketUpdateTime.toInstant(), Instant.now()).abs().getSeconds();
        details += (name + " diff: " + diffSec + " sec. ");
        return diffSec;
    }

    private void startTimerToRestart(String details) {
        log.info("deferred restart. " + details);
        slackNotifications.sendNotify(NotifyType.REBOOT_TIMESTAMP_OLD, "STOPPED: OrderBook timestamps." + details);
        scheduler.schedule(() -> {
            try {
                if (isHanged()) {
                    slackNotifications.sendNotify(NotifyType.REBOOT_TIMESTAMP_OLD, "RESTART: OrderBook timestamps.");
                    restartService.doFullRestart("OrderBook timestamp diff(after flag STOPPED). " + details);
                } else {
                    warningLogger.warn("No restart in 30 sec, back to READY. OrderBooks looks ok.");

                    arbitrageService.resetArbState("orderBook-hangs");
                    slackNotifications.sendNotify(NotifyType.REBOOT_TIMESTAMP_OLD, "OrderBook timestamps is OK." + details);
                }
            } catch (Exception e) {
                log.error("Error on restart", e);
            }
        }, 30, TimeUnit.SECONDS);
    }

}
