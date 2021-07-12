package com.bitplay.api.controller;

import com.bitplay.api.dto.Delta;
import com.bitplay.persistance.DeltaRepositoryService;
import com.bitplay.persistance.domain.fluent.DeltaName;
import com.bitplay.persistance.domain.fluent.Dlt;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
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

        final Stream<Dlt> deltaStream;
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

        List<Dlt> dltList = deltaStream.collect(Collectors.toList());
        return convert(dltList);
    }

    private List<Delta> convert(List<Dlt> dltList) {
        Map<Date, Delta> deltas = new LinkedHashMap<>();
        List<Dlt> sorted = dltList.stream()
                .sorted(Comparator.comparing(Dlt::getTimestamp))
                .collect(Collectors.toList());

        BigDecimal prevBtm = null;
        BigDecimal prevOk = null;
        for (Dlt dlt: sorted) {
            Date key = dlt.getTimestamp();
            if (!deltas.containsKey(key)) {
                if (dlt.getName() == DeltaName.B_DELTA && prevOk != null) {
                    deltas.put(key, new Delta(key, dlt.getDelta(), prevOk));
                } else if (dlt.getName() == DeltaName.O_DELTA && prevBtm != null) {
                    deltas.put(key, new Delta(key, prevBtm, dlt.getDelta()));
                }
            } else { // contains the key
                if (dlt.getName() == DeltaName.B_DELTA) {
                    BigDecimal ok = deltas.get(key).getoDelta();
                    deltas.put(key, new Delta(key, dlt.getDelta(), ok));
                } else {
                    BigDecimal btm = deltas.get(key).getbDelta();
                    deltas.put(key, new Delta(key, btm, dlt.getDelta()));
                }
            }

            if (dlt.getName() == DeltaName.B_DELTA) {
                prevBtm = dlt.getDelta();
            } else {
                prevOk = dlt.getDelta();
            }
        }
        return new ArrayList<>(deltas.values());
    }

    @RequestMapping(value = "/deltas/{deltaName}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Dlt> deltas(
            @PathVariable DeltaName deltaName,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "lastHours", required = false) String lastHours,
            @RequestParam(value = "lastCount", required = false) String lastCount
    ) throws ParseException {

        final Stream<Dlt> deltaStream;
        if (lastCount != null) {
            // last count
            deltaStream = deltaRepositoryService.streamDeltas(deltaName, Integer.parseInt(lastCount));

        } else if (lastHours != null) {
            // last hours
            final Date fromDate = Date.from(Instant.now().minus(Integer.valueOf(lastHours), ChronoUnit.HOURS));
            final Date toDate = new Date();
            deltaStream = deltaRepositoryService.streamDeltas(deltaName, fromDate, toDate);

        } else {
            // from - to
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            final Date fromDate = from != null
                    ? sdf.parse(from)
                    : Date.from(Instant.now().minus(24, ChronoUnit.HOURS));

            final Date toDate = to != null
                    ? sdf.parse(to)
                    : new Date();
            deltaStream = deltaRepositoryService.streamDeltas(deltaName, fromDate, toDate);
        }

        return deltaStream.collect(Collectors.toList());
    }

}
