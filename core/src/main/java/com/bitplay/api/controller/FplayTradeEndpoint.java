package com.bitplay.api.controller;

import com.bitplay.persistance.FplayTradeRepositoryService;
import com.bitplay.persistance.domain.fluent.FplayTrade;
import com.bitplay.persistance.repository.FplayTradeRepository;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by Sergey Shurmin on 2/25/18.
 */
@Slf4j
@Secured("ROLE_TRADER")
@RestController
public class FplayTradeEndpoint {

    @Autowired
    private FplayTradeRepositoryService fplayTradeRepositoryService;

    @Autowired
    private FplayTradeRepository fplayTradeRepository;


    @RequestMapping(value = "/trade/list", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<FplayTrade> deltas(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "lastDays", required = false) String lastDays,
            @RequestParam(value = "lastHours", required = false) String lastHours,
            @RequestParam(value = "lastCount", required = false) String lastCount
    ) throws ParseException {

        final Stream<FplayTrade> tradeStream;

        if (lastDays != null) {
            final Date fromDate = Date.from(Instant.now().minus(Integer.valueOf(lastDays), ChronoUnit.DAYS));
            final Date toDate = new Date();
            tradeStream = fplayTradeRepository.streamFplayTradeByStartTimestampBetween(fromDate, toDate);
        } else if (lastHours != null) {
            final Date fromDate = Date.from(Instant.now().minus(Integer.valueOf(lastHours), ChronoUnit.HOURS));
            final Date toDate = new Date();
            tradeStream = fplayTradeRepository.streamFplayTradeByStartTimestampBetween(fromDate, toDate);

        } else {
            // from - to
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            final Date fromDate = from != null
                    ? sdf.parse(from)
                    : Date.from(Instant.now().minus(24, ChronoUnit.HOURS));

            final Date toDate = to != null
                    ? sdf.parse(to)
                    : new Date();
            tradeStream = fplayTradeRepository.streamFplayTradeByStartTimestampBetween(fromDate, toDate);
        }

        return tradeStream.collect(Collectors.toList());
    }


}
