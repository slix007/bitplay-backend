package com.bitplay.market;

import com.bitplay.api.service.RestartService;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.MonitoringDataService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.mon.MonRestart;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.utils.Utils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
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
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
    private BitmexService bitmexService;

    @Autowired
    private OkCoinService okCoinService;

    @Autowired
    private RestartService restartService;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private MonitoringDataService monitoringDataService;

    @Autowired
    private SlackNotifications slackNotifications;

    private volatile String details = "";

    private volatile Instant lastRun = null;

    @Scheduled(initialDelay = 60 * 1000, fixedDelay = 10 * 1000)
    public void checkOrderBooks() {
        Instant start = Instant.now();
        try {
            checkLastRun();

            if (bitmexService.isReconnectInProgress()) {
                log.warn("skip checkOrderBooks: bitmex reconnect IN_PROGRESS");
                return;
            }

            if (isHanged()) {
                warningLogger.warn("Stop markets and schedule restart in 30 sec. Set STOPPED.");

                bitmexService.setMarketState(MarketState.STOPPED);
                okCoinService.setMarketState(MarketState.STOPPED);

                startTimerToRestart(details);
            }

        } catch (IllegalArgumentException e) {
            log.error("Error on check times. Set STOPPED.", e);
//            warningLogger.error("ERROR on check times", e);
            bitmexService.setMarketState(MarketState.STOPPED);
            okCoinService.setMarketState(MarketState.STOPPED);
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

    private synchronized boolean isHanged() {
        Settings settings = settingsRepositoryService.getSettings();
        Integer maxGap = settings.getRestartSettings().getMaxTimestampDelay();

        MonRestart monRestart = monitoringDataService.fetchRestartMonitoring();

        final OrderBook bOB = bitmexService.getOrderBook();
        final Date bT = getBitmexOrderBook3BestTimestamp(bOB); // bitmexService.getOrderBookLastTimestamp();
//            log.info("Bitmex timestamp: " + bT.toString());
        final OrderBook oOB = okCoinService.getOrderBook();
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

        boolean isHanged = false;
        if (isBDiff || isODiff || bWrong || oWrong) {

            details += String.format("maxTimestampDelay(maxDiff)=%s, b_bid_more_ask=%s, o_bid_more_ask=%s",
                    settings.getRestartSettings().getMaxTimestampDelay(),
                    bWrong, oWrong);

            warningLogger.warn(details);
            printBest3Prices(bOB, oOB);

            isHanged = true;

        }
        return isHanged;
    }

    private void printBest3Prices(OrderBook bOB, OrderBook oOB) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        warningLogger.warn(String.format("now time is %s ", sdf.format(new Date())));
        Utils.getBestBids(bOB, 3).forEach(item -> printItem("bitmex", item));
        Utils.getBestAsks(bOB, 3).forEach(item -> printItem("bitmex", item));

        Utils.getBestBids(oOB, 3).forEach(item -> printItem("okex", item));
        Utils.getBestAsks(oOB, 3).forEach(item -> printItem("okex", item));
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
                .orElseThrow(() -> new IllegalArgumentException("Can not get bitmex timestamp"));
    }

    private long getDiffSec(Date marketUpdateTime, String name) {
        long diffSec = Duration.between(marketUpdateTime.toInstant(), Instant.now()).abs().getSeconds();
        details += (name + " diff: " + diffSec + " sec. ");
        return diffSec;
    }

    private void startTimerToRestart(String details) {
        log.info("deferred restart. " + details);
        slackNotifications.sendNotify("STOPPED: OrderBook timestamps." + details);
        scheduler.schedule(() -> {
            try {
                if (isHanged()) {
                    slackNotifications.sendNotify("RESTART: OrderBook timestamps.");
                    restartService.doFullRestart("OrderBook timestamp diff(after flag STOPPED). " + details);
                } else {
                    warningLogger.warn("No restart in 30 sec, back to READY. OrderBooks looks ok.");

                    bitmexService.setMarketState(MarketState.READY);
                    okCoinService.setMarketState(MarketState.READY);
                }
            } catch (IOException e) {
                log.error("Error on restart", e);
            }
        }, 30, TimeUnit.SECONDS);
    }

}
