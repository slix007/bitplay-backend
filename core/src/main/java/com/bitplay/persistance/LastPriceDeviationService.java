package com.bitplay.persistance;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.DelayTimer;
import com.bitplay.arbitrage.events.ArbitrageReadyEvent;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.persistance.domain.LastPriceDeviation;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Created by Sergey Shurmin on 6/16/17.
 */
@Slf4j
@Service
public class LastPriceDeviationService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private SlackNotifications slackNotifications;

    @Autowired
    private MongoTemplate mongoTemplate;

    private volatile LastPriceDeviation cacheDev;
    private volatile DelayTimer delayTimer = new DelayTimer();

    private final Executor checkerExecutor = Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat("LastPriceDevChecker-%d").build());

    @EventListener(ArbitrageReadyEvent.class)
    public void init() {
        cacheDev = fetchLastPriceDeviation(); // in case of mongo ChangeSets
    }

    public void saveLastPriceDeviation(LastPriceDeviation lastPriceDeviation) {
        mongoTemplate.save(lastPriceDeviation);
        cacheDev = lastPriceDeviation;
    }

    public LastPriceDeviation getLastPriceDeviation() {
        return cacheDev != null ? cacheDev : fetchLastPriceDeviation();
    }

    public LastPriceDeviation fetchLastPriceDeviation() {
        cacheDev = mongoTemplate.findById(4L, LastPriceDeviation.class);
        return cacheDev;

    }

    public void fixCurrentLastPrice() {

        LastPriceDeviation lastPriceDeviation = fetchLastPriceDeviation();

        setCurrLastPrice(lastPriceDeviation);

        lastPriceDeviation.setBitmexMain(lastPriceDeviation.getBitmexMainCurr());
        lastPriceDeviation.setOkexMain(lastPriceDeviation.getOkexMainCurr());

        saveLastPriceDeviation(lastPriceDeviation);
    }

    public void updateAndCheckDeviationAsync() {
        checkerExecutor.execute(this::updateAndCheckDeviation); // each(bitmex/okex) Ticker update
    }

    private void updateAndCheckDeviation() {
        LastPriceDeviation dev = getLastPriceDeviation();

        timerTick(dev);

        setCurrLastPrice(dev);

        if (dev.getBitmexMainExceed()) {
            String msg = String.format("bitmex last price deviation(curr=%s, base=%s) exceeded $%s ",
                    dev.getBitmexMainCurr(),
                    dev.getBitmexMain(),
                    dev.getMaxDevUsd()
            );
            slackNotifications.sendNotify(NotifyType.LAST_PRICE_DEVIATION, msg);
            warningLogger.info(msg);
            log.info(msg);
            dev.setBitmexMain(dev.getBitmexMainCurr());
        }
        if (dev.getOkexMainExceed()) {
            String msg = String.format("okex last price deviation(curr=%s, base=%s) exceeded $%s",
                    dev.getOkexMainCurr(),
                    dev.getOkexMain(),
                    dev.getMaxDevUsd()
            );
            slackNotifications.sendNotify(NotifyType.LAST_PRICE_DEVIATION, msg);
            warningLogger.info(msg);
            log.info(msg);
            dev.setOkexMain(dev.getOkexMainCurr());
        }

        saveLastPriceDeviation(dev);
    }

    private void timerTick(LastPriceDeviation dev) {
        delayTimer.activate();
        if (dev.getDelaySec() != null) {
            final boolean readyByTime = delayTimer.isReadyByTime(dev.getDelaySec());
            if (readyByTime) {
                fixCurrentLastPrice();
                delayTimer.stop();
                delayTimer.activate();
            }
        }
    }

    public DelayTimer getDelayTimer() {
        return delayTimer;
    }

    private void setCurrLastPrice(LastPriceDeviation dev) {
        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        if (left != null) {
            Ticker bTiker = left.getTicker();
            if (bTiker != null && bTiker.getLast() != null) {
                dev.setBitmexMainCurr(bTiker.getLast());
            }
        }
        final MarketServicePreliq right = arbitrageService.getRightMarketService();
        if (right != null) {
            Ticker oTicker = right.getTicker();
            if (oTicker != null && oTicker.getLast() != null) {
                dev.setOkexMainCurr(oTicker.getLast());
            }
        }
    }
}
