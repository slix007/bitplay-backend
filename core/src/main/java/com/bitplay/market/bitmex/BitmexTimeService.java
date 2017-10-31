package com.bitplay.market.bitmex;

import com.bitplay.persistance.domain.Range;
import com.bitplay.persistance.domain.TimeCompareRange;
import com.bitplay.persistance.repository.TimeCompareRangeRepository;

import org.knowm.xchange.bitmex.dto.BitmexInfoDto;
import org.knowm.xchange.bitmex.service.BitmexMarketDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Sergey Shurmin on 10/26/17.
 */
@Service
public class BitmexTimeService {

    private final TimeCompare timeCompare = new TimeCompare();

    @Autowired
    BitmexService bitmexService;
    @Autowired
    private TimeCompareRangeRepository timeCompareRangeRepository;

    public BitmexTimeService() {
    }

    @Scheduled(fixedDelay = 1000)
    public void getTime() throws IOException {
        final BitmexMarketDataService marketDataService = (BitmexMarketDataService) bitmexService.getExchange().getMarketDataService();

        final Date startTime = new Date();

        final BitmexInfoDto bitmexInfoDto = marketDataService.getBitmexInfoDto();

        final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

        final Date marketTime = bitmexInfoDto.getTimestamp();

        final Date endTime = new Date();

        final long first = marketTime.toInstant().toEpochMilli() - startTime.toInstant().toEpochMilli();
        final long second = endTime.toInstant().toEpochMilli() - startTime.toInstant().toEpochMilli();
        final long third = endTime.toInstant().toEpochMilli() - marketTime.toInstant().toEpochMilli();

        final TimeCompareRange timeCompareRange = fetchTCR();
        updateRanges(timeCompareRange, first, second, third);

        timeCompare.setTimeCompareRange(timeCompareRange);

        String timeCompareString = String.format("ourReq:%s, bitmex:%s, ourResp:%s<br>" +
                        "bitmex-ourReq:%s ms; ..... %s...%s <br>" +
                        "ourResp-ourReq:%s ms; ... %s...%s <br>" +
                        "ourResp-bitmex:%s ms; .... %s...%s",
                sdf.format(startTime),
                sdf.format(marketTime),
                sdf.format(endTime),
                first, timeCompareRange.getBitmexOurReq().getMin(), timeCompareRange.getBitmexOurReq().getMax(),
                second, timeCompareRange.getOurRespOurReq().getMin(), timeCompareRange.getOurRespOurReq().getMax(),
                third, timeCompareRange.getOurRespBitmex().getMin(), timeCompareRange.getOurRespBitmex().getMax());
        timeCompare.setTimeCompareString(timeCompareString);
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

    public TimeCompare getTimeCompare() {
        return timeCompare;
    }

    private TimeCompareRange fetchTCR() {
        TimeCompareRange one = timeCompareRangeRepository.findOne(1L);
        if (one == null) {
            one = new TimeCompareRange();
            one.setId(1L);
            one.setBitmexOurReq(Range.empty());
            one.setOurRespOurReq(Range.empty());
            one.setOurRespBitmex(Range.empty());
        }
        return one;
    }
}
