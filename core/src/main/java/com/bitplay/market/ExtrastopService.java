package com.bitplay.market;

import com.bitplay.api.service.RestartService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Created by Sergey Shurmin on 1/12/18.
 */
@Service
public class ExtrastopService {

    @Autowired
    private BitmexService bitmexService;

    @Autowired
    private OkCoinService okCoinService;

    @Autowired
    private RestartService restartService;

    @Scheduled(initialDelay = 60 * 1000, fixedDelay = 30 * 1000)
    public void checkTimes() {
        final Date bT = bitmexService.getOrderBookLastTimestamp();
        final Date oT = okCoinService.getOrderBook().getTimeStamp();

        if (isBigDiff(bT, "Bitmex") || isBigDiff(oT, "okex")) {
            bitmexService.setMarketState(MarketState.STOPPED);
            okCoinService.setMarketState(MarketState.STOPPED);
            restartService.doDeferredRestart();
        }
    }

    private boolean isBigDiff(Date bT, String name) {
        final LocalTime now = LocalTime.now();
        final int nM = now.getMinute();
        final int nS = now.getSecond();
        final LocalDateTime localBT = LocalDateTime.ofInstant(bT.toInstant(), ZoneId.systemDefault());
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
//        System.out.println(name + " diff: " + diffSec);
//        System.out.println(name + " diff: " + diffSec);
        return diffSec > 60;
    }
}
