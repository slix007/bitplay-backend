package com.bitplay.market;

import com.bitplay.api.service.RestartService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.RestartMonitoring;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.repository.RestartMonitoringRepository;
import com.bitplay.utils.Utils;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Stream;
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
@Service
public class ExtrastopService {

    private final static Logger logger = LoggerFactory.getLogger(BitmexService.class);
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private BitmexService bitmexService;

    @Autowired
    private OkCoinService okCoinService;

    @Autowired
    private RestartService restartService;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private RestartMonitoringRepository restartMonitoringRepository;

    private String details = "";

    @Scheduled(initialDelay = 60 * 1000, fixedDelay = 10 * 1000)
    public void checkOrderBooks() {
        try {
            if (bitmexService.isReconnectInProgress()) {
                logger.warn("skip checkOrderBooks: bitmex reconnect IN_PROGRESS");
                return;
            }
            Settings settings = settingsRepositoryService.getSettings();
            Integer maxGap = settings.getRestartSettings().getMaxTimestampDelay();

            RestartMonitoring restartMonitoring = restartMonitoringRepository.fetchRestartMonitoring();

            final OrderBook bOB = bitmexService.getOrderBook();
            final Date bT = getBitmexOrderBook3BestTimestamp(bOB); // bitmexService.getOrderBookLastTimestamp();
//            logger.info("Bitmex timestamp: " + bT.toString());
            final OrderBook oOB = okCoinService.getOrderBook();
            final Date oT = oOB.getTimeStamp();
            details = "";

            long bDiffSec = getDiffSec(bT, "Bitmex");
            long oDiffSec = getDiffSec(oT, "Okex");
            restartMonitoring.addBTimestampDelay(BigDecimal.valueOf(bDiffSec));
            restartMonitoring.addOTimestampDelay(BigDecimal.valueOf(oDiffSec));
            restartMonitoringRepository.saveRestartMonitoring(restartMonitoring);

            boolean bWrong = isOrderBookPricesWrong(bOB);
            boolean oWrong = isOrderBookPricesWrong(oOB);
            boolean isBDiff = bDiffSec > maxGap;
            boolean isODiff = oDiffSec > maxGap;
            if (isBDiff || isODiff || bWrong || oWrong) {
                bitmexService.setMarketState(MarketState.STOPPED);
                okCoinService.setMarketState(MarketState.STOPPED);

                details += String.format("maxTimestampDelay(maxDiff)=%s, b_bid_more_ask=%s, o_bid_more_ask=%s",
                        settings.getRestartSettings().getMaxTimestampDelay(),
                        bWrong, oWrong);

                warningLogger.warn(details);
                printBest3Prices(bOB, oOB);

                restartService.doDeferredRestart(details);
            }
        } catch (IllegalArgumentException e) {
            logger.error("on check times", e);
            warningLogger.error("ERROR on check times", e);
            bitmexService.setMarketState(MarketState.STOPPED);
            okCoinService.setMarketState(MarketState.STOPPED);
            restartService.doDeferredRestart(e.getMessage());
        } catch (Exception e) {
            logger.error("on check times", e);
            warningLogger.error("ERROR on check times", e);
        }
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
}
