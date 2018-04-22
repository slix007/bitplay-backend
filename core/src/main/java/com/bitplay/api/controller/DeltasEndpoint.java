package com.bitplay.api.controller;

import com.bitplay.persistance.DeltaRepositoryService;
import com.bitplay.persistance.domain.fluent.Delta;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by Sergey Shurmin on 2/25/18.
 */
@RestController
public class DeltasEndpoint {
    private final static Logger logger = LoggerFactory.getLogger(DeltasEndpoint.class);
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");


    @Autowired
    private DeltaRepositoryService deltaRepositoryService;


    @RequestMapping(value = "/deltas", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Delta> deltas(@RequestParam(value = "from", required = false) String from,
                              @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "lastHours", required = false) String lastHours,
            @RequestParam(value = "lastCount", required = false) String lastCount
                              ) throws ParseException {

        final Stream<Delta> deltaStream;
        if (lastCount != null) {
            // last count
            deltaStream = deltaRepositoryService.streamDeltas(Integer.parseInt(lastCount));

        } else if (lastHours != null) {
            // last hours
            final Date fromDate = Date.from(Instant.now().minus(Integer.valueOf(lastHours), ChronoUnit.HOURS));
            final Date toDate = new Date();
            deltaStream = deltaRepositoryService.streamDeltas(fromDate, toDate);

        } else {
            // from - to
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            final Date fromDate = from != null
                    ? sdf.parse(from)
                    : Date.from(Instant.now().minus(24, ChronoUnit.HOURS));

            final Date toDate = to != null
                    ? sdf.parse(to)
                    : new Date();
            deltaStream = deltaRepositoryService.streamDeltas(fromDate, toDate);
        }

        return deltaStream.collect(Collectors.toList());
    }

}
