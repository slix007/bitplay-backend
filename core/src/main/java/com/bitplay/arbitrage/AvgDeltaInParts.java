package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DeltaName;
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
public class AvgDeltaInParts implements AvgDelta {

    private static final Logger logger = LoggerFactory.getLogger(AvgDeltaInParts.class);
//    private static final Logger debugLog = LoggerFactory.getLogger("DEBUG_DELTA_AVG");
    private static final Logger debugLog = LoggerFactory.getLogger("DEBUG_LOG");

    private volatile long num_sma_btm;     // сумма значений дельт в промежутке delta_hist_per
    private volatile long den_sma_btm;     // количество дельт в промежутке delta_hist_per
    private volatile long num_sma_ok;     // сумма значений дельт в промежутке delta_hist_per
    private volatile long den_sma_ok;     // количество дельт в промежутке delta_hist_per

    private Map<Instant, BigDecimal> b_delta_sma_map = new HashMap<>();
    private Map<Instant, BigDecimal> o_delta_sma_map = new HashMap<>();

    private List<Dlt> b_delta = new ArrayList<>();
    private List<Dlt> o_delta = new ArrayList<>();

    private boolean hasErrorsBtm = false;
    private boolean hasErrorsOk = false;

    private final static int BLOCK_TO_CLEAR_OLD = 100;
    private int reBtm = -1;                 // right edge, номер последней дельты в delta_hist_per
    private int leBtm = 0;                 // left edge, первый номер дельты в delta_hist_per
    private int preBtm = -1;                // после наступления события border_comp_per passed предыдущий re
    private int pleBtm = 0;                // после наступления события border_comp_per passed предыдущий le
    private int reOk = -1;                 // right edge, номер последней дельты в delta_hist_per
    private int leOk = 0;                 // left edge, первый номер дельты в delta_hist_per
    private int preOk = -1;                // после наступления события border_comp_per passed предыдущий re
    private int pleOk = 0;                // после наступления события border_comp_per passed предыдущий le

    @Autowired
    private ArbitrageService arbitrageService;
    @Autowired
    private PersistenceService persistenceService;
    private static BigDecimal NONE_VALUE = BigDecimal.valueOf(99999);
    private Pair<Instant, BigDecimal> b_delta_sma = Pair.of(Instant.now(), NONE_VALUE);
    private Pair<Instant, BigDecimal> o_delta_sma = Pair.of(Instant.now(), NONE_VALUE);
    private Dlt last_saved_btm = null; // последняя сохраненная дельта, для использования при reset
    private Dlt last_saved_ok = null;

    private volatile boolean btm_started = false;
    private volatile boolean ok_started = false;


    synchronized void resetDeltasCache() {
        num_sma_btm = 0;
        den_sma_btm = 0;
        num_sma_ok = 0;
        den_sma_ok = 0;
        b_delta_sma_map.clear();
        b_delta.clear();

        pleBtm = 0;
        leBtm = 0;
        preBtm = -1;
        reBtm = -1;
        if (last_saved_btm != null) {
            addBtmDlt(last_saved_btm);
            b_delta.add(last_saved_btm);
            preBtm = 0;
        }

        pleOk = 0;
        leOk = 0;
        preOk = -1;
        reOk = -1;
        o_delta_sma_map.clear();
        o_delta.clear();
        if (last_saved_ok != null) {
            addOkDlt(last_saved_ok);
            o_delta.add(last_saved_ok);
            preOk = 0;
        }

        hasErrorsBtm = false;
        hasErrorsOk = false;

        btm_started = false;
        ok_started = false;

        debugLog.info("RESET");

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
            if (!btm_started && dlt.getName() == DeltaName.B_DELTA) {
                addBtmDlt(dlt);
                preBtm = b_delta.size() - 1;

            }
            if (!ok_started && dlt.getName() == DeltaName.O_DELTA) {
                addOkDlt(dlt);
                preOk = o_delta.size() - 1;
            }

            if (dltTimestamp.minusSeconds(delta_hist_per).isAfter(begin_delta_hist_per)) {
                // проверка
                debugLog.error("error comp_b_border_sma_init");
                if (dlt.getName() == DeltaName.B_DELTA) {
                    hasErrorsBtm = true;
                } else {
                    hasErrorsOk = true;
                }
            }

        }

    }

    @Override
    public synchronized BigDecimal calcAvgDelta(DeltaName deltaName, BigDecimal instantDelta, Instant currTime,
            BorderDelta borderDelta,
            Instant begin_delta_hist_per) {
        Integer delta_hist_per = borderDelta.getDeltaCalcPast();

        BigDecimal result;

        if (deltaName == DeltaName.B_DELTA) {
            long startMs = System.nanoTime();
            result = doTheCalcBtm(currTime, delta_hist_per);
            long endMs = System.nanoTime();
            arbitrageService.getDeltaMon().setBtmDeltaMs((endMs - startMs) / 1000);
        } else {
            long startMs = System.nanoTime();
            result = doTheCalcOk(currTime, delta_hist_per);
            long endMs = System.nanoTime();
            arbitrageService.getDeltaMon().setOkDeltaMs((endMs - startMs) / 1000);
        }

        boolean debugAlgorithm = true;
        if (debugAlgorithm) {
            if (deltaName == DeltaName.B_DELTA) {
                long sMs = System.nanoTime();
                validateBtm(result);
                long eMs = System.nanoTime();
                arbitrageService.getDeltaMon().setBtmValidateDeltaMs((eMs - sMs) / 1000);
            } else {
                long sMs = System.nanoTime();
                validateOk(result);
                long eMs = System.nanoTime();
                arbitrageService.getDeltaMon().setOkValidateDeltaMs((eMs - sMs) / 1000);
            }
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
            debugLog.info("btmx cleared " + BLOCK_TO_CLEAR_OLD);
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
            debugLog.info("okex cleared " + BLOCK_TO_CLEAR_OLD);
        }
    }

    private synchronized BigDecimal doTheCalcBtm(Instant currTime, Integer delta_hist_per) {
        btm_started = true;
        // comp_b_border_sma_event();
        reBtm = b_delta.size() - 1;
        if (reBtm == -1) {
            return null;
        }

        // 1. select to add pre ----> re
        if (b_delta.size() > 0 && preBtm < reBtm) {
            int added = 0;
            for (int i = preBtm + 1; i <= reBtm; i++) { // include rightEdge element
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
            for (int i = pleBtm; i < leBtm; i++) { // don't remove leftEdge element
                if (b_delta.get(i).getTimestamp().toInstant().isAfter(le_time)) {
                    debugLog.info("btmx ERROR removing with wrong time.");
                }

                removeBtmDlt(b_delta.get(i));
                removed++;
            }
            debugLog.info("btmx Removed {} at ple={}, le={}, num_sma_btm={}, den_sma_btm={}",
                    removed, pleBtm, leBtm, num_sma_btm, den_sma_btm);
            pleBtm = leBtm;
        }

        if (den_sma_btm == 0) {
            return null;
        }

        BigDecimal b_delta_sma_value = BigDecimal.valueOf(num_sma_btm / den_sma_btm, 2);
        b_delta_sma = Pair.of(currTime, b_delta_sma_value);
        return b_delta_sma_value;
    }

    private synchronized BigDecimal doTheCalcOk(Instant currTime, Integer delta_hist_per) {
        ok_started = true;
        // comp_b_border_sma_event();
        reOk = o_delta.size() - 1;
        if (reOk == -1) {
            return null;
        }

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
            debugLog.info("okex Removed {} at ple={}, le={}, num_sma_ok={}, den_sma_ok={}",
                    removed, pleOk, leOk, num_sma_ok, den_sma_ok);
            pleOk = leOk;
        }

        if (den_sma_ok == 0) {
            return null;
        }

        BigDecimal o_delta_sma_value = BigDecimal.valueOf(num_sma_ok / den_sma_ok, 2);
        o_delta_sma = Pair.of(currTime, o_delta_sma_value);
        return o_delta_sma_value;
    }

    private synchronized void validateBtm(BigDecimal result) {
        if (den_sma_btm == 0 && b_delta_sma_map.size() == 0) {
            return;
        }
        BigDecimal currSmaBtmDelta = getCurrSmaBtmDelta();
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal value: b_delta_sma_map.values()) {
            count++;
            sum = sum.add(value);
        }
        BigDecimal validVal = count == 0 ? BigDecimal.ZERO
                : sum.divide(BigDecimal.valueOf(count), 2, BigDecimal.ROUND_DOWN);

        if (validVal.compareTo(currSmaBtmDelta) != 0) {
            debugLog.error("ERROR btm validation: valid={}, but found={}, firstResult={}",
                    validVal, currSmaBtmDelta, result);
            hasErrorsBtm = true;
        }
    }

    private synchronized void validateOk(BigDecimal result) {
        if (den_sma_ok == 0 && o_delta_sma_map.size() == 0) {
            return;
        }
        BigDecimal currSmaOkDelta = getCurrSmaOkDelta();
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal value: o_delta_sma_map.values()) {
            count++;
            sum = sum.add(value);
        }
        BigDecimal validVal = count == 0 ? BigDecimal.ZERO
                : sum.divide(BigDecimal.valueOf(count), 2, BigDecimal.ROUND_DOWN);

        if (validVal.compareTo(currSmaOkDelta) != 0) {
            debugLog.error("ERROR ok validation: valid={}, but found={}, firstResult={}",
                    validVal, currSmaOkDelta, result);
            hasErrorsOk = true;
        }
    }

    private synchronized void addBtmDlt(Dlt dlt) {
        if (b_delta_sma_map.containsKey(dlt.getTimestamp().toInstant())) {
            logger.warn("btmx Double add of " + dlt.toString());
            debugLog.warn("btmx Double add of " + dlt.toString());
            return;
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
            return;
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

    public synchronized BigDecimal getCurrSmaBtmDelta() {
        BigDecimal b_delta_sma_value = den_sma_btm == 0
                ? NONE_VALUE
                : BigDecimal.valueOf(num_sma_btm / den_sma_btm, 2);
        return b_delta_sma_value;
    }

    public synchronized BigDecimal getCurrSmaOkDelta() {
        BigDecimal o_delta_sma_value = den_sma_ok == 0
                ? NONE_VALUE
                : BigDecimal.valueOf(num_sma_ok / den_sma_ok, 2);
        return o_delta_sma_value;
    }

    public synchronized Map<Instant, BigDecimal> getB_delta_sma_map() {
        return b_delta_sma_map;
    }

    public synchronized Map<Instant, BigDecimal> getO_delta_sma_map() {
        return o_delta_sma_map;
    }

    public synchronized long getNum_sma_btm() {
        return num_sma_btm;
    }

    public synchronized long getDen_sma_btm() {
        return den_sma_btm;
    }

    public synchronized long getNum_sma_ok() {
        return num_sma_ok;
    }

    public synchronized long getDen_sma_ok() {
        return den_sma_ok;
    }

    public synchronized boolean isHasErrorsBtm() {
        return hasErrorsBtm;
    }

    public synchronized boolean isHasErrorsOk() {
        return hasErrorsOk;
    }

    public synchronized Pair<Instant, BigDecimal> getB_delta_sma() {
        return b_delta_sma;
    }

    public synchronized Pair<Instant, BigDecimal> getO_delta_sma() {
        return o_delta_sma;
    }

    public synchronized List<Dlt> getB_delta() {
        return b_delta;
    }

    public synchronized List<Dlt> getO_delta() {
        return o_delta;
    }
}
