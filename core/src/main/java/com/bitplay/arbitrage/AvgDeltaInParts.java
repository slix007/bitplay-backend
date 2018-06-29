package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DeltaName;
import com.bitplay.persistance.DeltaRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.borders.BorderDelta;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.fluent.Dlt;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private long num_sma_ok;     // сумма значений дельт в промежутке delta_hist_per
    private long den_sma_ok;     // количество дельт в промежутке delta_hist_per

    private Map<Instant, BigDecimal> b_delta_sma_map = new HashMap<>();
    private Map<Instant, BigDecimal> o_delta_sma_map = new HashMap<>();

    private List<Dlt> b_delta = new ArrayList<>();
    private List<Dlt> o_delta = new ArrayList<>();

    final static int BLOCK_TO_CLEAR_OLD = 10;
    int reBtm = 0;                 // right edge, номер последней дельты в delta_hist_per
    int leBtm = 0;                 // left edge, первый номер дельты в delta_hist_per
    int preBtm = 0;                // после наступления события border_comp_per passed предыдущий re
    int pleBtm = 0;                // после наступления события border_comp_per passed предыдущий le
    int reOk = 0;                 // right edge, номер последней дельты в delta_hist_per
    int leOk = 0;                 // left edge, первый номер дельты в delta_hist_per
    int preOk = 0;                // после наступления события border_comp_per passed предыдущий re
    int pleOk = 0;                // после наступления события border_comp_per passed предыдущий le

    @Autowired
    private ArbitrageService arbitrageService;
    @Autowired
    private DeltaRepositoryService deltaRepositoryService;
    @Autowired
    private PersistenceService persistenceService;
    private static BigDecimal NONE_VALUE = BigDecimal.valueOf(99999);
    private Pair<Instant, BigDecimal> b_delta_sma = Pair.of(Instant.now(), NONE_VALUE);
    private Pair<Instant, BigDecimal> o_delta_sma = Pair.of(Instant.now(), NONE_VALUE);
    private Dlt last_saved_btm = null; // последняя сохраненная дельта, для использования при reset
    private Dlt last_saved_ok = null;

    public synchronized void resetDeltasCache() {
        num_sma_btm = 0;
        den_sma_btm = 0;
        num_sma_ok = 0;
        den_sma_ok = 0;
        b_delta_sma_map.clear();

        pleBtm = 0;
        leBtm = 0;
        preBtm = 0;
        reBtm = 0;
        if (last_saved_btm != null) {
            b_delta_sma_map.put(last_saved_btm.getTimestamp().toInstant(), last_saved_btm.getDelta());
            num_sma_btm = last_saved_btm.getValue();
            den_sma_btm = 1;
        }

        pleOk = 0;
        leOk = 0;
        preOk = 0;
        reOk = 0;
        o_delta_sma_map.clear();
        if (last_saved_ok != null) {
            o_delta_sma_map.put(last_saved_ok.getTimestamp().toInstant(), last_saved_ok.getDelta());
            num_sma_ok = last_saved_ok.getValue();
            den_sma_ok = 1;
        }

    }

    @Override
    public synchronized void newDeltaEvent(Dlt dlt, Instant begin_delta_hist_per) {
        BorderParams borderParams = persistenceService.fetchBorders();
        BorderDelta borderDelta = borderParams.getBorderDelta();
        Integer delta_hist_per = borderDelta.getDeltaCalcPast();

        // 1. save the deltas array
        if (dlt.getName() == DeltaName.B_DELTA) {
            b_delta.add(dlt);
            last_saved_btm = dlt;
        } else if (dlt.getName() == DeltaName.O_DELTA) {
            o_delta.add(dlt);
            last_saved_ok = dlt;
        }

        // 2. comp_b_border_sma_init(); - just save num_sma and den_sma
        Instant dltTimestamp = dlt.getTimestamp().toInstant();
        if (!isReadyForCalc(dltTimestamp, begin_delta_hist_per, delta_hist_per)) {
            if (dlt.getName() == DeltaName.B_DELTA) {
                addBtmDlt(dlt);
                preBtm = b_delta.size() - 1;

            } else if (dlt.getName() == DeltaName.O_DELTA) {
                addOkDlt(dlt);
                preOk = o_delta.size() - 1;
            }

            if (dltTimestamp.minusSeconds(delta_hist_per).isAfter(begin_delta_hist_per)) {
                // проверка
                debugLog.error("error comp_b_border_sma_init");
            }

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
        }

        clearOldBtm();
        clearOldOk();

        return result;
    }

    private void clearOldBtm() {
        if (pleBtm > BLOCK_TO_CLEAR_OLD) {
            int size = b_delta.size();
            b_delta = new ArrayList<>(b_delta.subList(BLOCK_TO_CLEAR_OLD, size));
            pleBtm -= BLOCK_TO_CLEAR_OLD;
            leBtm -= BLOCK_TO_CLEAR_OLD;
            preBtm -= BLOCK_TO_CLEAR_OLD;
            reBtm -= BLOCK_TO_CLEAR_OLD;
        }
    }

    private void clearOldOk() {
        if (pleOk > BLOCK_TO_CLEAR_OLD) {
            int size = o_delta.size();
            o_delta = new ArrayList<>(o_delta.subList(BLOCK_TO_CLEAR_OLD, size));
            pleOk -= BLOCK_TO_CLEAR_OLD;
            leOk -= BLOCK_TO_CLEAR_OLD;
            preOk -= BLOCK_TO_CLEAR_OLD;
            reOk -= BLOCK_TO_CLEAR_OLD;
        }
    }

    private BigDecimal doTheCalcBtm(Instant currTime, Integer delta_hist_per) {
        reBtm = b_delta.size() - 1;
        // comp_b_border_sma_event();

        // 1. select to add pre ----> re
        if (b_delta.size() > 0 && preBtm < reBtm) {
            int added = 0;
            for (int i = preBtm + 1; i <= reBtm; i++) {
                addBtmDlt(b_delta.get(i));
                added++;
            }
            debugLog.info("btmx Added {} at pre={}, re={}", added, preBtm, reBtm);
            preBtm = reBtm;
        }

        // 2. subtract all from latest period, but not if it's the only
        if (den_sma_btm > 1) {
            Instant le_time = currTime.minusSeconds(delta_hist_per);

            for (int i = pleBtm; i < reBtm; i++) {
                Instant time = b_delta.get(i).getTimestamp().toInstant();
                if (time.isAfter(le_time)) {
                    leBtm = i;
                    break;
                }
            }
            int removed = 0;
            for (int i = pleBtm; i < leBtm; i++) {
                if (b_delta.get(i).getTimestamp().toInstant().isAfter(le_time)) {
                    debugLog.info("btmx ERROR removing with wrong time.");
                }

                removeBtmDlt(b_delta.get(i));
                removed++;
            }
            debugLog.info("btmx Removed {} at ple={}, le={}", removed, pleBtm, leBtm);
            pleBtm = leBtm;
        }

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
        if (o_delta.size() > 0 && preOk < reOk) {
            int added = 0;
            for (int i = preOk + 1; i <= reOk; i++) {
                addOkDlt(o_delta.get(i));
                added++;
            }
            debugLog.info("okex Added {} at pre={}, re={}", added, preOk, reOk);
            preOk = reOk;
        }

        // 2. subtract all from latest period, but not if it's the only
        if (den_sma_ok > 1) {
            Instant le_time = currTime.minusSeconds(delta_hist_per);

            for (int i = pleOk; i < reOk; i++) {
                Instant time = o_delta.get(i).getTimestamp().toInstant();
                if (time.isAfter(le_time)) {
                    leOk = i;
                    break;
                }
            }
            int removed = 0;
            for (int i = pleOk; i < leOk; i++) {
                if (o_delta.get(i).getTimestamp().toInstant().isAfter(le_time)) {
                    debugLog.info("okex ERROR removing with wrong time.");
                }

                removeOkDlt(o_delta.get(i));
                removed++;
            }
            debugLog.info("okex Removed {} at ple={}, le={}", removed, pleOk, leOk);
            pleOk = leOk;
        }

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
            logger.warn("btmx Double add of " + dlt.toString());
            debugLog.warn("btmx Double add of " + dlt.toString());
        }
        b_delta_sma_map.put(dlt.getTimestamp().toInstant(), dlt.getDelta());
        num_sma_btm += dlt.getValue();
        den_sma_btm++;
        debugLog.info("btmx Added {}", dlt.toString());
    }

    private synchronized void removeBtmDlt(Dlt dlt) {
        b_delta_sma_map.remove(dlt.getTimestamp().toInstant());
        num_sma_btm -= dlt.getValue();
        den_sma_btm--;
        debugLog.info("btmx Removed {}", dlt.toString());
    }

    private synchronized void addOkDlt(Dlt dlt) {
        if (o_delta_sma_map.containsKey(dlt.getTimestamp().toInstant())) {
            logger.warn("okex Double add of " + dlt.toString());
            debugLog.warn("okex Double add of " + dlt.toString());
        }
        o_delta_sma_map.put(dlt.getTimestamp().toInstant(), dlt.getDelta());
        num_sma_ok += dlt.getValue();
        den_sma_ok++;
        debugLog.info("okex Added {}", dlt.toString());
    }

    private synchronized void removeOkDlt(Dlt dlt) {
        o_delta_sma_map.remove(dlt.getTimestamp().toInstant());
        num_sma_ok -= dlt.getValue();
        den_sma_ok--;
        debugLog.info("okex Removed {}", dlt.toString());
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
