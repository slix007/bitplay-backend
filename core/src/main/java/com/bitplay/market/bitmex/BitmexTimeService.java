package com.bitplay.market.bitmex;

import org.knowm.xchange.bitmex.dto.BitmexInfoDto;
import org.knowm.xchange.bitmex.service.BitmexMarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.Date;

/**
 * Created by Sergey Shurmin on 10/26/17.
 */
@Service
public class BitmexTimeService {

    @Autowired
    BitmexService bitmexService;

    private volatile String timeCompareString;

    public BitmexTimeService() {
    }

    @Scheduled(fixedDelay = 5000)
    public void getTime() throws IOException {
        final BitmexMarketDataService marketDataService = (BitmexMarketDataService) bitmexService.getExchange().getMarketDataService();

        final Date startTime = new Date();

        final BitmexInfoDto bitmexInfoDto = marketDataService.getBitmexInfoDto();

        final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

        final Date marketTime = bitmexInfoDto.getTimestamp();

        final Date endTime = new Date();

        timeCompareString = String.format("ourReq:%s, bitmex:%s, ourResp:%s<br>" +
                        "bitmex-ourReq:%s ms<br>" +
                        "ourResp-ourReq:%s ms",
                sdf.format(startTime),
                sdf.format(marketTime),
                sdf.format(endTime),
                marketTime.toInstant().toEpochMilli() - startTime.toInstant().toEpochMilli(),
                endTime.toInstant().toEpochMilli() - startTime.toInstant().toEpochMilli()
                );
    }

    public String getTimeCompareString() {
        return timeCompareString;
    }
}
