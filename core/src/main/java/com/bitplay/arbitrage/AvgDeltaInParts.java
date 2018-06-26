package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DeltaName;
import com.bitplay.persistance.DeltaRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.borders.BorderDelta;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.fluent.Dlt;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.OptionalDouble;
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
//    private static final Logger debugLog = LoggerFactory.getLogger("DEBUG_DELTA_AVG");
    private static final Logger debugLog = LoggerFactory.getLogger("DEBUG_LOG");

    private long num_sma_btm;     // сумма значений дельт в промежутке delta_hist_per
    private long den_sma_btm;     // количество дельт в промежутке delta_hist_per
    private Instant pre_time_btm;
    private long num_sma_ok;     // сумма значений дельт в промежутке delta_hist_per
    private long den_sma_ok;     // количество дельт в промежутке delta_hist_per
    private Instant pre_time_ok;
    // 10 min cache
//    private Cache<Instant, BigDecimal> b_delta_sma = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();
//    private Cache<Instant, BigDecimal> o_delta_sma = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();

    private Map<Instant, BigDecimal> b_delta_sma_map = new HashMap<>();
    private Map<Instant, Dlt> b_delta_sma_map_addtime = new HashMap<>();
    private Map<Instant, BigDecimal> o_delta_sma_map = new HashMap<>();

    @Autowired
    private ArbitrageService arbitrageService;
    @Autowired
    private DeltaRepositoryService deltaRepositoryService;
    @Autowired
    private PersistenceService persistenceService;
    //    @Autowired
//    private AvgDeltaAtOnce avgDeltaAtOnce;
    private static BigDecimal NONE_VALUE = BigDecimal.valueOf(-9999);
    private Pair<Instant, BigDecimal> b_delta_sma = Pair.of(Instant.now(), NONE_VALUE);
    private Pair<Instant, BigDecimal> o_delta_sma = Pair.of(Instant.now(), NONE_VALUE);
    private Dlt last_saved_btm = null; // последняя сохраненная дельта, для использования при reset
    private Dlt last_saved_ok = null;
    private Dlt first_left_btm = null; // первая слева, для вычитания. Временная точка ple находится внутри её времени жизни.
    private Dlt first_left_ok = null;

    @Override
    public synchronized void resetDeltasCache(Integer delta_hist_per, boolean clearData) {
        num_sma_btm = 0;
        den_sma_btm = 0;
        num_sma_ok = 0;
        den_sma_ok = 0;
        Instant currTime = Instant.now();
        pre_time_btm = currTime;
        pre_time_ok = currTime;
        // take current delta
//        Long btm = deltaRepositoryService.getLastSavedDelta(DeltaName.B_DELTA).getValue();
        b_delta_sma_map.clear();
        b_delta_sma_map_addtime.clear();
        if (last_saved_btm != null) {
            b_delta_sma_map.put(last_saved_btm.getTimestamp().toInstant(), last_saved_btm.getDelta());
            b_delta_sma_map_addtime.put(currTime, last_saved_btm);
            num_sma_btm = last_saved_btm.getValue();
            den_sma_btm = 1;
            first_left_btm = last_saved_btm;
        }
//        Long ok = deltaRepositoryService.getLastSavedDelta(DeltaName.B_DELTA).getValue();
        o_delta_sma_map.clear();
        if (last_saved_ok != null) {
            o_delta_sma_map.put(last_saved_ok.getTimestamp().toInstant(), last_saved_ok.getDelta());
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
                addBtmDlt(dlt);
                if (first_left_btm == null) {
                    first_left_btm = dlt;
                }
                pre_time_btm = currTime;
            } else if (dlt.getName() == DeltaName.O_DELTA) {
                o_delta_sma_map.put(dlt.getTimestamp().toInstant(), dlt.getDelta());
                num_sma_ok += dlt.getValue();
                den_sma_ok++;
                if (first_left_ok == null) {
                    first_left_ok = dlt;
                }
                pre_time_ok = currTime;
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

        BigDecimal result = deltaName == DeltaName.B_DELTA
                ? doTheCalcBtm(currTime, delta_hist_per)
                : doTheCalcOk(currTime, delta_hist_per);

        boolean debugAlgorithm = true;
        if (debugAlgorithm) {
            validateBtm();
            validateOk();

//            validateBtm2(instantDelta, currTime, borderDelta, begin_delta_hist_per);

//            validateOk2(instantDelta, currTime, borderDelta, begin_delta_hist_per);
        }

        return result;
    }

    private void validateBtm2(BigDecimal instantDelta, Instant currTime, BorderDelta borderDelta, Instant begin_delta_hist_per) {
        BigDecimal currSmaBtmDelta = getCurrSmaBtmDelta();
        final Date fromDate = Date.from(currTime.minus(borderDelta.getDeltaCalcPast(), ChronoUnit.SECONDS));

        List<Dlt> collect = deltaRepositoryService.streamDeltas(DeltaName.B_DELTA, fromDate, new Date())
                .collect(Collectors.toList());
        final OptionalDouble average = collect.stream()
                .mapToLong(Dlt::getValue)
//                .mapToDouble(BigDecimal::doubleValue)
//                .peek(val -> logger.info("Delta1Part: " + val))
                .average();

        BigDecimal validValBtm = BigDecimal.valueOf(
                average.isPresent() ? average.getAsDouble() : 0)
                .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);

        if (validValBtm.compareTo(currSmaBtmDelta) != 0) {
            debugLog.error("ERROR btm validation2: valid={}, but found={}. "
                            + "\n{}\n{}",
                    validValBtm,
                    currSmaBtmDelta,
                    Arrays.toString(collect.toArray()),
                    Arrays.toString(b_delta_sma_map.values().toArray()));
        }
    }

    private void validateOk2(BigDecimal instantDelta, Instant currTime, BorderDelta borderDelta, Instant begin_delta_hist_per) {
        BigDecimal currSmaOkDelta = getCurrSmaOkDelta();
//        BigDecimal validValOk = avgDeltaAtOnce.calcAvgDelta(DeltaName.O_DELTA, instantDelta, currTime, borderDelta, begin_delta_hist_per);
        final Date fromDate = Date.from(currTime.minus(borderDelta.getDeltaCalcPast(), ChronoUnit.SECONDS));

        List<Dlt> collect = deltaRepositoryService.streamDeltas(DeltaName.O_DELTA, fromDate, new Date())
                .collect(Collectors.toList());
        final OptionalDouble average = collect.stream()
                .mapToLong(Dlt::getValue)
//                .mapToDouble(BigDecimal::doubleValue)
//                .peek(val -> logger.info("Delta1Part: " + val))
                .average();

        BigDecimal validValOk = BigDecimal.valueOf(
                average.isPresent() ? average.getAsDouble() : 0);
//
//        if (validValOk.compareTo(currSmaOkDelta) != 0) {
//            debugLog.error("ERROR ok validation2: valid={}, but found={}. "
//                            + "Size={}, but found {}",
//                    validValOk,
//                    currSmaOkDelta,
//                    collect.size(),
//                    o_delta_sma_map.size());
//        }
    }

    private BigDecimal doTheCalcBtm(Instant currTime, Integer delta_hist_per) {
        // comp_b_border_sma_event();

        // 1. select to add pre ----> re
        Date pre_btm = Date.from(pre_time_btm);
        Date re = Date.from(currTime);

        List<Dlt> btm_add = deltaRepositoryService.streamDeltas(DeltaName.B_DELTA, pre_btm, re).collect(Collectors.toList());
        if (btm_add.size() > 0) {
            if (first_left_btm == null && den_sma_btm == 0) {
                first_left_btm = btm_add.get(0);
            }
            for (Dlt dlt: btm_add) {
                addBtmDlt(dlt);
            }
            debugLog.info("Added {} at pre={}, re={}", btm_add.size(), pre_btm.toInstant().toString(), re.toInstant().toString());
        }

        // 2. select to remove --- ple ----> le  ----------->  pre_time_{===pre} --> currTime{=== re}
        Date ple_btm = Date.from(pre_time_btm.minusSeconds(delta_hist_per));
        Date le = Date.from(currTime.minusSeconds(delta_hist_per));

        // 2.1. subtract the last, but not if it's the only
        if (den_sma_btm > 1 && first_left_btm != null) {
//            Dlt before_ple_btm_sub = deltaRepositoryService.getIsBefore(DeltaName.B_DELTA, ple_btm);
            Dlt before_ple_btm_sub = first_left_btm.getTimestamp().before(ple_btm) ? first_left_btm : null;
            if (before_ple_btm_sub != null) {
                removeBtmDlt(before_ple_btm_sub);
                first_left_btm = null;
            }
        }
        // 2.2. subtract all from latest period, but not if it's the only
        if (den_sma_btm > 1) {
            List<Dlt> btm_sub = deltaRepositoryService.streamDeltas(DeltaName.B_DELTA, ple_btm, le).collect(Collectors.toList());
            int size = btm_sub.size();
            if (size > 0) {
                int btmLastInd = size - 2; // without last
                if (btmLastInd > 0) {
                    for (int i = 0; i < btmLastInd && den_sma_btm > 1; i++) {
                        removeBtmDlt(btm_sub.get(i));
                    }
                }
                first_left_btm = btm_sub.get(size - 1); // the last is not subtracted (it's the first in calc array now)
            }
        }

        pre_time_btm = currTime; // new right edge is registered only on adding deltas(1 or more)

        if (den_sma_btm == 0) {
            return null;
        }

        BigDecimal b_delta_sma_value = BigDecimal.valueOf(num_sma_btm / den_sma_btm, 2);
        b_delta_sma = Pair.of(currTime, b_delta_sma_value);
        return b_delta_sma_value;
    }

    private BigDecimal doTheCalcOk(Instant currTime, Integer delta_hist_per) {
        // comp_b_border_sma_event();

        // 1. select to add pre ----> re
        Date pre_ok = Date.from(pre_time_ok);
        Date re = Date.from(currTime);

        List<Dlt> ok_add = deltaRepositoryService.streamDeltas(DeltaName.O_DELTA, pre_ok, re).collect(Collectors.toList());
        if (ok_add.size() > 0) {
            if (first_left_ok == null && den_sma_ok == 0) {
                first_left_ok = ok_add.get(0);
            }
            for (Dlt dlt: ok_add) {
                addOkDlt(dlt);
            }
            debugLog.info("Added {} at pre={}, re={}", ok_add.size(), pre_ok.toInstant().toString(), re.toInstant().toString());
        }

        // 2. select to remove ple --> le  ----------->  pre_time_{===pre} --> now{=== re}
        Date ple_ok = Date.from(pre_time_ok.minusSeconds(delta_hist_per));
        Date le = Date.from(currTime.minusSeconds(delta_hist_per));

        // 2.1. subtract the last, but not if it's the only
        if (den_sma_ok != 0 && first_left_ok != null) {
//        Dlt before_ple_ok_sub = deltaRepositoryService.getIsBefore(DeltaName.O_DELTA, ple_ok);
            Dlt before_ple_ok_sub = first_left_ok.getTimestamp().before(ple_ok) ? first_left_ok : null;
            if (before_ple_ok_sub != null) {
                removeOkDlt(before_ple_ok_sub);
                first_left_ok = null;
            }
        }

        // 2.2. subtract all from latest period, but not if it's the only
        if (den_sma_ok > 1) {
            List<Dlt> ok_sub = deltaRepositoryService.streamDeltas(DeltaName.O_DELTA, ple_ok, le).collect(Collectors.toList());
            int size = ok_sub.size();
            if (size > 0) {
                int okLastInd = size - 2; // without last
                if (okLastInd > 0) {
                    for (int i = 0; i < okLastInd && den_sma_btm > 1; i++) {
                        removeOkDlt(ok_sub.get(i));
                    }
                }
                first_left_ok = ok_sub.get(size - 1);
            }
        }

        pre_time_ok = currTime; // new right edge is registered only on adding deltas(1 or more)

        if (den_sma_ok == 0) {
            return null;
        }

        BigDecimal o_delta_sma_value = BigDecimal.valueOf(num_sma_ok / den_sma_ok, 2);
        o_delta_sma = Pair.of(currTime, o_delta_sma_value);
        return o_delta_sma_value;
    }

    private void validateBtm() {
        BigDecimal currSmaBtmDelta = getCurrSmaBtmDelta();
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal value: b_delta_sma_map.values()) {
            count++;
            sum = sum.add(value);
        }
        BigDecimal validVal = sum.divide(BigDecimal.valueOf(count), 2, BigDecimal.ROUND_DOWN);

        if (validVal.compareTo(currSmaBtmDelta) != 0) {
            debugLog.error("ERROR btm validation: valid={}, but found={}", validVal, currSmaBtmDelta);
        }
    }

    private void validateOk() {
        BigDecimal currSmaOkDelta = getCurrSmaOkDelta();
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal value: o_delta_sma_map.values()) {
            count++;
            sum = sum.add(value);
        }
        BigDecimal validVal = sum.divide(BigDecimal.valueOf(count), 2, BigDecimal.ROUND_DOWN);

        if (validVal.compareTo(currSmaOkDelta) != 0) {
            debugLog.error("ERROR ok validation: valid={}, but found={}", validVal, currSmaOkDelta);
        }
    }

    private synchronized void addBtmDlt(Dlt dlt) {
        if (b_delta_sma_map.containsKey(dlt.getTimestamp().toInstant())) {
            logger.warn("Double add of " + dlt.toString());
            debugLog.warn("Double add of " + dlt.toString());
        }
        b_delta_sma_map.put(dlt.getTimestamp().toInstant(), dlt.getDelta());
        b_delta_sma_map_addtime.put(Instant.now(), dlt);
        num_sma_btm += dlt.getValue();
        den_sma_btm++;
        debugLog.info("Added {}", dlt.toString());
    }

    private synchronized void removeBtmDlt(Dlt before_ple_btm_sub) {

        Instant keyToRemove = null;
        for (Entry<Instant, Dlt> instantDltEntry: b_delta_sma_map_addtime.entrySet()) {
            if (instantDltEntry.getValue().getTimestamp().equals(before_ple_btm_sub.getTimestamp())) {
                keyToRemove = instantDltEntry.getKey();
                break;
            }
        }
        if (keyToRemove != null) {
            b_delta_sma_map_addtime.remove(keyToRemove);
        }

        b_delta_sma_map.remove(before_ple_btm_sub.getTimestamp().toInstant());
        num_sma_btm -= before_ple_btm_sub.getValue();
        den_sma_btm--;
        debugLog.info("Removed {}", before_ple_btm_sub.toString());
    }

    private synchronized void addOkDlt(Dlt dlt) {
        o_delta_sma_map.put(dlt.getTimestamp().toInstant(), dlt.getDelta());
        num_sma_ok += dlt.getValue();
        den_sma_ok++;
        debugLog.info("Added {}", dlt.toString());
    }

    private synchronized void removeOkDlt(Dlt before_ple_ok_sub) {
        o_delta_sma_map.remove(before_ple_ok_sub.getTimestamp().toInstant());
        num_sma_ok -= before_ple_ok_sub.getValue();
        den_sma_ok--;
        debugLog.info("Removed {}", before_ple_ok_sub.toString());
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
