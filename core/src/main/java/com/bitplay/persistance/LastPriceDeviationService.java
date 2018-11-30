package com.bitplay.persistance;

import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.domain.LastPriceDeviation;
import com.bitplay.utils.Utils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.math.BigDecimal;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.extern.log4j.Log4j;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 6/16/17.
 */
@Log4j
@Service
public class LastPriceDeviationService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private BitmexService bitmexService;

    @Autowired
    private OkCoinService okCoinService;

    @Autowired
    private SlackNotifications slackNotifications;

    @Autowired
    private MongoTemplate mongoTemplate;

    private volatile LastPriceDeviation cacheDev;

    private final Executor checkerExecutor = Executors.newSingleThreadExecutor(
            new ThreadFactoryBuilder().setNameFormat("LastPriceDevChecker-%d").build());

    public void saveLastPriceDeviation(LastPriceDeviation lastPriceDeviation) {
        mongoTemplate.save(lastPriceDeviation);
        cacheDev = lastPriceDeviation;
    }

    public LastPriceDeviation getLastPriceDeviation() {
        return cacheDev != null ? cacheDev : fetchLastPriceDeviation();
    }

    private LastPriceDeviation fetchLastPriceDeviation() {
        cacheDev = mongoTemplate.findById(4L, LastPriceDeviation.class);
        return cacheDev;

    }

    public void fixCurrentLastPrice() {

        LastPriceDeviation lastPriceDeviation = fetchLastPriceDeviation();
        if (lastPriceDeviation == null) {
            lastPriceDeviation = LastPriceDeviation.builder().percentage(BigDecimal.valueOf(10)).build();
        }

        setCurrLastPrice(lastPriceDeviation);

        lastPriceDeviation.setBitmexMain(lastPriceDeviation.getBitmexMainCurr());
        lastPriceDeviation.setBitmexExtra(lastPriceDeviation.getBitmexExtraCurr());
        lastPriceDeviation.setOkexMain(lastPriceDeviation.getOkexMainCurr());

        saveLastPriceDeviation(lastPriceDeviation);
    }

    public void checkDeviationAsync() {
        checkerExecutor.execute(this::checkDeviation);
    }

    private void checkDeviation() {
        LastPriceDeviation dev = fetchLastPriceDeviation();

        setCurrLastPrice(dev);

        if (dev.getBitmexMainExceed()) {
            String msg = String.format("bitmex last price deviation(curr=%s, base=%s) exceeded %s %%",
                    dev.getBitmexMainCurr(),
                    dev.getBitmexMain(),
                    dev.getPercentage()
            );
            slackNotifications.sendNotify(NotifyType.PRICE_CHANGE_10, msg);
            warningLogger.info(msg);
            log.info(msg);
            dev.setBitmexMain(dev.getBitmexMainCurr());
        }
        if (dev.getBitmexExtraExceed()) {
            String msg = String.format("bitmex_extraSet last price deviation(curr=%s, base=%s) exceeded %s %%",
                    dev.getBitmexExtraCurr(),
                    dev.getBitmexExtra(),
                    dev.getPercentage()
            );
            slackNotifications.sendNotify(NotifyType.PRICE_CHANGE_10, msg);
            warningLogger.info(msg);
            log.info(msg);
            dev.setBitmexExtra(dev.getBitmexExtraCurr());
        }
        if (dev.getOkexMainExceed()) {
            String msg = String.format("okex last price deviation(curr=%s, base=%s) exceeded %s %%",
                    dev.getOkexMainCurr(),
                    dev.getOkexMain(),
                    dev.getPercentage()
            );
            slackNotifications.sendNotify(NotifyType.PRICE_CHANGE_10, msg);
            warningLogger.info(msg);
            log.info(msg);
            dev.setOkexMain(dev.getOkexMainCurr());
        }

        saveLastPriceDeviation(dev);
    }

    private void setCurrLastPrice(LastPriceDeviation dev) {
        Ticker bTiker = bitmexService.getTicker();
        if (bTiker != null && bTiker.getLast() != null) {
            dev.setBitmexMainCurr(bTiker.getLast());
        }
        if (bitmexService.getContractType().isEth() && bitmexService.getOrderBookXBTUSD().getBids().size() > 0) {
            LimitOrder bestBid = Utils.getBestBid(bitmexService.getOrderBookXBTUSD());
            dev.setBitmexExtraCurr(bestBid.getLimitPrice());
        }
        Ticker oTicker = okCoinService.getTicker();
        if (oTicker != null && oTicker.getLast() != null) {
            dev.setOkexMainCurr(oTicker.getLast());
        }
    }
}
