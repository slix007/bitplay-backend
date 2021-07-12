package com.bitplay.market.bitmex;

import com.bitplay.persistance.domain.Range;
import com.bitplay.persistance.domain.TimeCompareParams;
import com.bitplay.persistance.domain.TimeCompareRange;
import com.bitplay.persistance.repository.TimeCompareParamsRepository;
import com.bitplay.persistance.repository.TimeCompareRangeRepository;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.market.MarketStaticData;
import lombok.extern.slf4j.Slf4j;
import com.bitplay.xchange.bitmex.dto.BitmexInfoDto;
import com.bitplay.xchange.bitmex.service.BitmexMarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Sergey Shurmin on 10/26/17.
 */
@Slf4j
@Component
public class BitmexTimeService {

    @Autowired
    private TimeCompareRangeRepository timeCompareRangeRepository;
    @Autowired
    private ArbitrageService arbitrageService;
    @Autowired
    private TimeCompareParamsRepository timeCompareParamsRepository;
    private final TimeCompare timeCompare = new TimeCompare();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> theSchedule;

    public BitmexTimeService() {
        ((ScheduledThreadPoolExecutor) scheduler).setRemoveOnCancelPolicy(true);

        scheduler.schedule(this::init, 5, TimeUnit.SECONDS);
    }

    private void init() {
        final TimeCompareParams one = timeCompareParamsRepository.findOne(1L);
        long delay = one == null ? 5L : one.getUpdateSeconds().longValue();
        schedule(delay);
    }

    public void schedule(long delay) {
        if (theSchedule != null && !theSchedule.isDone() && !theSchedule.isCancelled()) {
            theSchedule.cancel(true);
        }

        theSchedule = scheduler.scheduleWithFixedDelay(this::getTimeTask, delay, delay, TimeUnit.SECONDS);
    }

    private void getTimeTask() {
        if (arbitrageService.getLeftMarketService().getMarketStaticData() != MarketStaticData.BITMEX) {
            return;
        }
        final BitmexService bitmexService = (BitmexService) arbitrageService.getLeftMarketService();


        final BitmexMarketDataService marketDataService = (BitmexMarketDataService) bitmexService.getExchange().getMarketDataService();

        final Date startTime = new Date();

        final BitmexInfoDto bitmexInfoDto;
        long start = Instant.now().getEpochSecond();
        try {
            bitmexInfoDto = marketDataService.getBitmexInfoDto();
        } catch (SocketTimeoutException e) {
            long end = Instant.now().getEpochSecond();
            final String time = String.valueOf(end - start);
            log.error("can not get BitmexInfo. Timeout=" + time + ". " + e.getMessage());
            return;
        } catch (IOException e) {
            long end = Instant.now().getEpochSecond();
            final String time = String.valueOf(end - start);
            log.error("can not get BitmexInfo. Timeout=" + time, e);
            return;
        }

        final Date marketTime = bitmexInfoDto.getTimestamp();

        final Date endTime = new Date();

        String timeCompareString = composeTimeCompareString(startTime, marketTime, endTime);

        timeCompare.setTimeCompareString(timeCompareString);
    }

    private String composeTimeCompareString(Date startTime, Date marketTime, Date endTime) {
        final TimeCompareRange timeCompareRange = fetchTCR();
        long first = 0;
        long second = 0;
        long third = 0;

        if (startTime != null && marketTime != null && endTime != null) {
            first = marketTime.toInstant().toEpochMilli() - startTime.toInstant().toEpochMilli();
            second = endTime.toInstant().toEpochMilli() - startTime.toInstant().toEpochMilli();
            third = endTime.toInstant().toEpochMilli() - marketTime.toInstant().toEpochMilli();

            updateRanges(timeCompareRange, first, second, third);
        }
        timeCompare.setTimeCompareRange(timeCompareRange);

        final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        final String ourReq = startTime != null ? sdf.format(startTime) : null;
        final String bitmex = marketTime != null ? sdf.format(marketTime) : null;
        final String ourResp = endTime != null ? sdf.format(endTime) : null;
        return String.format("ourReq:%s, bitmex:%s, ourResp:%s<br>" +
                        "bitmex-ourReq:%s ms; ..... %s...%s <br>" +
                        "ourResp-ourReq:%s ms; ... %s...%s <br>" +
                        "ourResp-bitmex:%s ms; .... %s...%s",
                ourReq,
                bitmex,
                ourResp,
                first, timeCompareRange.getBitmexOurReq().getMin(), timeCompareRange.getBitmexOurReq().getMax(),
                second, timeCompareRange.getOurRespOurReq().getMin(), timeCompareRange.getOurRespOurReq().getMax(),
                third, timeCompareRange.getOurRespBitmex().getMin(), timeCompareRange.getOurRespBitmex().getMax());
    }

    private void updateRanges(TimeCompareRange timeCompareRange, long first, long second, long third) {
        boolean toSave = false;
        {
            final BigDecimal firstD = BigDecimal.valueOf(first);
            final Range bitmexOurReq = timeCompareRange.getBitmexOurReq();
            if (bitmexOurReq.getMin().compareTo(firstD) > 0) {
                bitmexOurReq.setMin(firstD);
                toSave = true;
            }
            if (bitmexOurReq.getMax().compareTo(firstD) < 0) {
                bitmexOurReq.setMax(firstD);
                toSave = true;
            }
        }
        {
            final Range ourRespOurReq = timeCompareRange.getOurRespOurReq();
            final BigDecimal secondD = BigDecimal.valueOf(second);
            if (ourRespOurReq.getMin().compareTo(secondD) > 0) {
                ourRespOurReq.setMin(secondD);
                toSave = true;
            }
            if (ourRespOurReq.getMax().compareTo(secondD) < 0) {
                ourRespOurReq.setMax(secondD);
                toSave = true;
            }
        }
        {
            final Range ourRespBitmex = timeCompareRange.getOurRespBitmex();
            final BigDecimal thirdD = BigDecimal.valueOf(third);
            if (ourRespBitmex.getMin().compareTo(thirdD) > 0) {
                ourRespBitmex.setMin(thirdD);
                toSave = true;
            }
            if (ourRespBitmex.getMax().compareTo(thirdD) < 0) {
                ourRespBitmex.setMax(thirdD);
                toSave = true;
            }
        }

        if (toSave) {
            timeCompareRangeRepository.save(timeCompareRange);
        }
    }

    public String getTimeCompareString() {
        return timeCompare.getTimeCompareString();
    }

    private TimeCompareRange fetchTCR() {
        TimeCompareRange one = timeCompareRangeRepository.findOne(1L);
        if (one == null) {
            one = TimeCompareRange.empty();
        }
        return one;
    }

    public Integer fetchTimeCompareUpdating() {
        TimeCompareParams one = timeCompareParamsRepository.findOne(1L);
        return one == null ? 10 : one.getUpdateSeconds();
    }

    public Integer updateTimeCompareUpdating(Integer delay) {
        saveTimeCompareUpdating(delay);
        schedule(delay);
        return delay;
    }

    private void saveTimeCompareUpdating(Integer delay) {
        TimeCompareParams one = timeCompareParamsRepository.findOne(1L);
        if (one != null) {
            one.setUpdateSeconds(delay);
        } else {
            one = new TimeCompareParams();
            one.setId(1L);
            one.setUpdateSeconds(delay);
        }
        timeCompareParamsRepository.save(one);
    }

    public String resetTimeCompare() {
        final TimeCompareRange empty = TimeCompareRange.empty();
        timeCompare.setTimeCompareRange(empty);
        timeCompareRangeRepository.save(empty);

        final String s = composeTimeCompareString(null, null, null);
        timeCompare.setTimeCompareString(s);
        return s;
    }

}
