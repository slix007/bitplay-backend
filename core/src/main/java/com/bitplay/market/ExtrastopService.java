package com.bitplay.market;

import com.bitplay.api.service.RestartService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.utils.Utils;

import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.stream.Stream;

/**
 * Created by Sergey Shurmin on 1/12/18.
 */
@Service
public class ExtrastopService {

    private final static Logger logger = LoggerFactory.getLogger(BitmexService.class);

    @Autowired
    private BitmexService bitmexService;

    @Autowired
    private OkCoinService okCoinService;

    @Autowired
    private RestartService restartService;

    private String details = "";

    @Scheduled(initialDelay = 60 * 1000, fixedDelay = 30 * 1000)
    public void checkOrderBooks() {
        try {
            final OrderBook bOB = bitmexService.getOrderBook();
            final Date bT = getBitmexOrderBook3BestTimestamp(bOB); // bitmexService.getOrderBookLastTimestamp();
//            logger.info("Bitmex timestamp: " + bT.toString());
            final OrderBook oOB = okCoinService.getOrderBook();
            final Date oT = oOB.getTimeStamp();
            details = "";

            if (isBigDiff(bT, "Bitmex") || isBigDiff(oT, "okex")
                    || isOrderBookPricesWrong(bOB)
                    || isOrderBookPricesWrong(oOB)) {
                bitmexService.setMarketState(MarketState.STOPPED);
                okCoinService.setMarketState(MarketState.STOPPED);
                restartService.doDeferredRestart(details);
            }
        } catch (IllegalArgumentException e) {
            bitmexService.setMarketState(MarketState.STOPPED);
            okCoinService.setMarketState(MarketState.STOPPED);
            restartService.doDeferredRestart(e.getMessage());
        } catch (Exception e) {
            logger.error("on check times", e);
        }
    }

    private boolean isOrderBookPricesWrong(OrderBook orderBook) {
        LimitOrder bid1 = Utils.getBestBid(orderBook);
        LimitOrder ask1 = Utils.getBestAsk(orderBook);
        return bid1.compareTo(ask1) > 0;
    }

    private Date getBitmexOrderBook3BestTimestamp(OrderBook orderBook) {
        return Stream.concat(Utils.getBestBids(orderBook, 3).stream(),
                Utils.getBestAsks(orderBook, 3).stream())
                .map(LimitOrder::getTimestamp)
                .reduce((date, date2) -> date.before(date2) ? date : date2)
                .orElseThrow(() -> new IllegalArgumentException("Can not get bitmex timestamp"));
    }

    private boolean isBigDiff(Date marketUpdateTime, String name) {
        final LocalTime now = LocalTime.now();
        final int nM = now.getMinute();
        final int nS = now.getSecond();
        final LocalDateTime localBT = LocalDateTime.ofInstant(marketUpdateTime.toInstant(), ZoneId.systemDefault());
        final int bM = localBT.getMinute();
        final int bS = localBT.getSecond();

        final int nowSec = nM * 60 + nS;
        final int marketSec = bM * 60 + bS;
        int diffSec = nowSec - marketSec;

        if (nowSec == marketSec) {
            // all is good
        } else if (nowSec > marketSec) { // 58:23 ---> 59:49
            diffSec = nowSec - marketSec;
        } else { // 59:40 ---> 00:10
            diffSec = ((nM + 60) * 60 + nS) - marketSec;
        }
        details += (name + " diff: " + diffSec + " sec. ");
//        System.out.println(name + " diff: " + diffSec);
//        System.out.println(name + " diff: " + diffSec);
        return diffSec > 60;
    }
}
