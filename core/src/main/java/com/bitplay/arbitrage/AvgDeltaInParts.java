package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DeltaName;
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
    private static BigDecimal NONE_VALUE = BigDecimal.valueOf(-9999);
    private Pair<Instant, BigDecimal> b_delta_sma = Pair.of(Instant.now(), NONE_VALUE);
    private Pair<Instant, BigDecimal> o_delta_sma = Pair.of(Instant.now(), NONE_VALUE);
    private Dlt last_saved_btm = null; // последняя сохраненная дельта, для использования при reset
    private Dlt last_saved_ok = null;
    private Dlt first_left_btm = null; // первая слева, для вычитания. Временная точка ple находится внутри её времени жизни.
    private Dlt first_left_ok = null;

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
//        Long btm = deltaRepositoryService.getLastSavedDelta(DeltaName.B_DELTA).getValue();
        if (last_saved_btm != null) {
            num_sma_btm = last_saved_btm.getValue();
            den_sma_btm = 1;
            first_left_btm = last_saved_btm;
        }
//        Long ok = deltaRepositoryService.getLastSavedDelta(DeltaName.B_DELTA).getValue();
        if (last_saved_ok != null) {
            num_sma_ok = last_saved_ok.getValue();
            den_sma_ok = 1;
            first_left_ok = last_saved_ok;
        }
    }

    @Override
    public synchronized void newDeltaEvent(Dlt dlt, Instant begin_delta_hist_per) {
        BorderParams borderParams = persistenceService.fetchBorders();
        BorderDelta borderDelta = borderParams.getBorderDelta();
        Integer delta_hist_per = borderDelta.getDeltaCalcPast();

        // 1. comp_b_border_sma_init(); - just save num_sma and den_sma
        Instant currTime = Instant.now();
        if (!isReadyForCalc(currTime, begin_delta_hist_per, delta_hist_per)) {
            if (dlt.getName() == DeltaName.B_DELTA) {
                num_sma_btm += dlt.getValue();
                den_sma_btm++;
                if (first_left_btm == null) {
                    first_left_btm = dlt;
                }
//                pre_time_btm = currTime;
            } else if (dlt.getName() == DeltaName.O_DELTA) {
                num_sma_ok += dlt.getValue();
                den_sma_ok++;
                if (first_left_ok == null) {
                    first_left_ok = dlt;
                }
//                pre_time_ok = currTime;
            }
        }
        // keep summarizing only while sma_init.

        if (dlt.getName() == DeltaName.B_DELTA) {
            last_saved_btm = dlt;
        } else if (dlt.getName() == DeltaName.O_DELTA) {
            last_saved_ok = dlt;
        }
    }

    @Override
    public synchronized BigDecimal calcAvgDelta(DeltaName deltaName, BigDecimal instantDelta, Instant currTime,
            BorderDelta borderDelta,
            Instant begin_delta_hist_per) {
        Integer delta_hist_per = borderDelta.getDeltaCalcPast();

        return deltaName == DeltaName.B_DELTA
                ? doTheCalcBtm(currTime, delta_hist_per, begin_delta_hist_per)
                : doTheCalcOk(currTime, delta_hist_per, begin_delta_hist_per);
    }

    private BigDecimal doTheCalcBtm(Instant currTime, Integer delta_hist_per, Instant begin_delta_hist_per) {
        // comp_b_border_sma_event();

        // 1. select to remove ple --> le  ----------->  pre_time_{===pre} --> currTime{=== re}
        Date ple_btm = Date.from(pre_time_btm.minusSeconds(delta_hist_per));
        Date le = Date.from(currTime.minusSeconds(delta_hist_per));

        if (den_sma_btm != 0 && first_left_btm != null) { // subtract the last
//            Dlt before_ple_btm_sub = deltaRepositoryService.getIsBefore(DeltaName.B_DELTA, ple_btm);
            Dlt before_ple_btm_sub = first_left_btm.getTimestamp().before(ple_btm) ? first_left_btm : null;
            if (before_ple_btm_sub != null) {
                num_sma_btm -= before_ple_btm_sub.getValue();
                den_sma_btm--;
                first_left_btm = null;
            }
        }
        if (den_sma_btm != 0) { // subtract all from latest period
            List<Dlt> btm_sub = deltaRepositoryService.streamDeltas(DeltaName.B_DELTA, ple_btm, le).collect(Collectors.toList());
            int size = btm_sub.size();
            if (size > 0) {
                int btmLastInd = size - 2; // without last
                if (btmLastInd > 0) {
                    for (int i = 0; i < btmLastInd; i++) {
                        num_sma_btm -= btm_sub.get(i).getValue();
                        den_sma_btm--;
                    }
                }
                first_left_btm = btm_sub.get(size - 1); // the last is not subtracted (it's the first in calc array now)
            }
        }

        // 2. select to add pre ----> re
        Date pre_btm = Date.from(pre_time_btm);
        Date re = Date.from(currTime);

        List<Dlt> btm_add = deltaRepositoryService.streamDeltas(DeltaName.B_DELTA, pre_btm, re).collect(Collectors.toList());
        if (first_left_btm == null && den_sma_btm == 0 && btm_add.size() > 0) {
            first_left_btm = btm_add.get(0);
        }
        for (Dlt dlt: btm_add) {
            num_sma_btm += dlt.getValue();
            den_sma_btm++;
        }

        pre_time_btm = currTime;

        if (den_sma_btm == 0) {
            return null;
        }

        BigDecimal b_delta_sma_value = BigDecimal.valueOf(num_sma_btm / den_sma_btm, 2);
        b_delta_sma = Pair.of(currTime, b_delta_sma_value);
        return b_delta_sma_value;
    }

    private BigDecimal doTheCalcOk(Instant currTime, Integer delta_hist_per, Instant begin_delta_hist_per) {
        // comp_b_border_sma_event();

        // 1. select to remove ple --> le  ----------->  pre_time_{===pre} --> now{=== re}
        Date ple_ok = Date.from(pre_time_ok.minusSeconds(delta_hist_per));
        Date le = Date.from(currTime.minusSeconds(delta_hist_per));

        if (den_sma_ok != 0 && first_left_ok != null) {
//        Dlt before_ple_ok_sub = deltaRepositoryService.getIsBefore(DeltaName.O_DELTA, ple_ok);
            Dlt before_ple_ok_sub = first_left_ok.getTimestamp().before(ple_ok) ? first_left_ok : null;
            if (before_ple_ok_sub != null) {
                num_sma_ok -= before_ple_ok_sub.getValue();
                den_sma_ok--;
                first_left_ok = null;
            }
        }

        if (den_sma_ok != 0) {
            List<Dlt> ok_sub = deltaRepositoryService.streamDeltas(DeltaName.O_DELTA, ple_ok, le).collect(Collectors.toList());
            int size = ok_sub.size();
            if (size > 0) {
                int okLastInd = size - 2; // without last
                if (okLastInd > 0) {
                    for (int i = 0; i < okLastInd; i++) {
                        num_sma_ok -= ok_sub.get(i).getValue();
                        den_sma_ok--;
                    }
                }
                first_left_ok = ok_sub.get(size - 1);
            }
        }

        // 2. select to add pre ----> re
        Date pre_ok = Date.from(pre_time_ok);
        Date re = Date.from(currTime);

        List<Dlt> ok_add = deltaRepositoryService.streamDeltas(DeltaName.O_DELTA, pre_ok, re).collect(Collectors.toList());
        if (first_left_ok == null && den_sma_ok == 0 && ok_add.size() > 0) {
            first_left_ok = ok_add.get(0);
        }
        for (Dlt dlt: ok_add) {
            num_sma_ok += dlt.getValue();
            den_sma_ok++;
        }

        pre_time_ok = currTime;

        if (den_sma_ok == 0) {
            return null;
        }

        BigDecimal o_delta_sma_value = BigDecimal.valueOf(num_sma_ok / den_sma_ok, 2);
        o_delta_sma = Pair.of(currTime, o_delta_sma_value);
        return o_delta_sma_value;
    }

    public BigDecimal getCurrSmaBtmDelta() {
        BigDecimal b_delta_sma_value = den_sma_btm == 0
                ? NONE_VALUE
                : BigDecimal.valueOf(num_sma_btm / den_sma_btm, 2);
        return b_delta_sma_value;
    }

    public BigDecimal getCurrSmaOkDelta() {
        BigDecimal o_delta_sma_value = den_sma_ok == 0
                ? NONE_VALUE
                : BigDecimal.valueOf(num_sma_ok / den_sma_ok, 2);
        return o_delta_sma_value;
    }
}
