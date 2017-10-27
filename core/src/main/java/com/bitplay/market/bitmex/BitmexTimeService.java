package com.bitplay.market.bitmex;

import org.knowm.xchange.bitmex.dto.BitmexInfoDto;
import org.knowm.xchange.bitmex.service.BitmexMarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.SimpleDateFormat;
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
        final BitmexInfoDto bitmexInfoDto = marketDataService.getBitmexInfoDto();

        final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

        final Date marketTime = bitmexInfoDto.getTimestamp();
        timeCompareString = String.format("bitmex:%s, local:%s",
                sdf.format(marketTime),
                sdf.format(new Date()));
    }

    public String getTimeCompareString() {
        return timeCompareString;
    }
}
