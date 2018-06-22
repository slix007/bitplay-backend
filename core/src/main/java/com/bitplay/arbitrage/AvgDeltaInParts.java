package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DeltaName;
import com.bitplay.arbitrage.events.DeltaChange;
import com.bitplay.persistance.DeltaRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.borders.BorderDelta;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.fluent.Dlt;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

/**
 * SMA delta keeps in memory. The calculation uses previous delta and past time deltas.
 * <br>
 * The other one {@link AvgDeltaAtOnce} uses whole deltas select.
 */
@Service
@Getter
public class AvgDeltaInParts implements AvgDelta {

    private static final Logger logger = LoggerFactory.getLogger(AvgDeltaInParts.class);

    private long num_sma_btm;     // сумма значений дельт в промежутке delta_hist_per
    private long den_sma_btm;     // количество дельт в промежутке delta_hist_per
    private Instant pre_time_btm;
    private long num_sma_ok;     // сумма значений дельт в промежутке delta_hist_per
    private long den_sma_ok;     // количество дельт в промежутке delta_hist_per
    private Instant pre_time_ok;
    // 10 min cache
//    private Cache<Instant, BigDecimal> b_delta_sma = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();
//    private Cache<Instant, BigDecimal> o_delta_sma = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();

    @Autowired
    private ArbitrageService arbitrageService;
    @Autowired
    private DeltaRepositoryService deltaRepositoryService;
    @Autowired
    private PersistenceService persistenceService;
    private Pair<Instant, BigDecimal> b_delta_sma;
    private Pair<Instant, BigDecimal> o_delta_sma;

    @Override
    public void resetDeltasCache(Integer delta_hist_per, boolean clearData) {
        num_sma_btm = 0;
        den_sma_btm = 0;
        num_sma_ok = 0;
        den_sma_ok = 0;
        Instant currTime = Instant.now();
        pre_time_btm = currTime;
        pre_time_ok = currTime;
        // take current delta
        Long btm = deltaRepositoryService.getLastSavedDelta(DeltaName.B_DELTA).getValue();
        if (btm != null) {
            num_sma_btm = btm;
            den_sma_btm = 1;
        }
        Long ok = deltaRepositoryService.getLastSavedDelta(DeltaName.B_DELTA).getValue();
        if (ok != null) {
            num_sma_ok = ok;
            den_sma_ok = 1;
        }
    }

    @Override
    public synchronized BigDecimal calcAvgDelta(DeltaName deltaName, BigDecimal instantDelta, BorderDelta borderDelta, Instant begin_delta_hist_per) {
        Integer delta_hist_per = borderDelta.getDeltaCalcPast();

        return deltaName == DeltaName.B_DELTA
                ? doTheCalcBtm(delta_hist_per)
                : doTheCalcOk(delta_hist_per);
    }

    @Override
    public synchronized void newDeltaEvent(DeltaChange deltaChange, Instant begin_delta_hist_per) {
        BorderParams borderParams = persistenceService.fetchBorders();
        BorderDelta borderDelta = borderParams.getBorderDelta();
        Integer delta_hist_per = borderDelta.getDeltaCalcPast();

        // 1. comp_b_border_sma_init(); - just save num_sma and den_sma
        Instant currTime = Instant.now();
        if (isReadyForCalc(currTime, begin_delta_hist_per, delta_hist_per)) {
            if (deltaChange.getBtmDelta() != null) {
                num_sma_btm += deltaChange.getBtmDelta().doubleValue();
                den_sma_btm++;
                pre_time_btm = currTime;
            }
            if (deltaChange.getOkDelta() != null) {
                num_sma_ok += deltaChange.getOkDelta().doubleValue();
                den_sma_ok++;
                pre_time_ok = currTime;
            }
        }

        // keep summarizing only while sma_init.
    }

    private BigDecimal doTheCalcBtm(Integer delta_hist_per) {
        // comp_b_border_sma_event();
        Instant currTime = Instant.now();

        // 1. select to remove ple --> le  ----------->  pre_time_{===pre} --> now{=== re}
        Date ple_btm = Date.from(pre_time_btm.minusSeconds(delta_hist_per));
        Date le = Date.from(currTime.minusSeconds(delta_hist_per));

        Dlt before_ple_btm_sub = deltaRepositoryService.getIsBefore(DeltaName.B_DELTA, ple_btm);
        List<Dlt> btm_sub = deltaRepositoryService.streamDeltas(DeltaName.B_DELTA, ple_btm, le).collect(Collectors.toList());
        if (before_ple_btm_sub != null) {
            num_sma_btm -= before_ple_btm_sub.getValue();
            den_sma_btm--;
        }
        int btmLastInd = btm_sub.size() - 2; // without last
        if (btmLastInd > 0) {
            for (int i = 0; i < btmLastInd; i++) {
                num_sma_btm -= btm_sub.get(i).getValue();
                den_sma_btm--;
            }
        }

        // 2. select to add pre ----> re
        Date pre_btm = Date.from(pre_time_btm);
        Date re = Date.from(currTime);

        List<Dlt> btm_add = deltaRepositoryService.streamDeltas(DeltaName.B_DELTA, pre_btm, re).collect(Collectors.toList());
        for (Dlt dlt: btm_add) {
            num_sma_btm += dlt.getValue();
            den_sma_btm++;
        }
        pre_time_btm = currTime;

        BigDecimal b_delta_sma_value = BigDecimal.valueOf(num_sma_btm / den_sma_btm, 2);
        b_delta_sma = Pair.of(currTime, b_delta_sma_value);
        return b_delta_sma_value;
    }

    private BigDecimal doTheCalcOk(Integer delta_hist_per) {
        // comp_b_border_sma_event();
        Instant currTime = Instant.now();

        // 1. select to remove ple --> le  ----------->  pre_time_{===pre} --> now{=== re}
        Date ple_ok = Date.from(pre_time_ok.minusSeconds(delta_hist_per));
        Date le = Date.from(currTime.minusSeconds(delta_hist_per));

        Dlt before_ple_ok_sub = deltaRepositoryService.getIsBefore(DeltaName.O_DELTA, ple_ok);
        List<Dlt> ok_sub = deltaRepositoryService.streamDeltas(DeltaName.O_DELTA, ple_ok, le).collect(Collectors.toList());
        if (before_ple_ok_sub != null) {
            num_sma_ok -= before_ple_ok_sub.getValue();
            den_sma_ok--;
        }
        int okLastInd = ok_sub.size() - 2; // without last
        if (okLastInd > 0) {
            for (int i = 0; i < okLastInd; i++) {
                num_sma_ok -= ok_sub.get(i).getValue();
                den_sma_ok--;
            }
        }

        // 2. select to add pre ----> re
        Date pre_ok = Date.from(pre_time_ok);
        Date re = Date.from(currTime);

        List<Dlt> ok_add = deltaRepositoryService.streamDeltas(DeltaName.O_DELTA, pre_ok, re).collect(Collectors.toList());
        for (Dlt dlt: ok_add) {
            num_sma_ok += dlt.getValue();
            den_sma_ok++;
        }
        pre_time_ok = currTime;

        BigDecimal o_delta_sma_value = BigDecimal.valueOf(num_sma_ok / den_sma_ok, 2);
        o_delta_sma = Pair.of(currTime, o_delta_sma_value);
        return o_delta_sma_value;
    }

    public BigDecimal getCurrSmaBtmDelta() {
        BigDecimal b_delta_sma_value = BigDecimal.valueOf(num_sma_btm / den_sma_btm, 2);
        return b_delta_sma_value;
    }

    public BigDecimal getCurrSmaOkDelta() {
        BigDecimal o_delta_sma_value = BigDecimal.valueOf(num_sma_ok / den_sma_ok, 2);
        return o_delta_sma_value;
    }
}
