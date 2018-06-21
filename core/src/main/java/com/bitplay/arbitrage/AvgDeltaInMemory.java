package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.DeltaName;
import com.bitplay.arbitrage.events.DeltaChange;
import com.bitplay.persistance.DeltaRepositoryService;
import com.bitplay.persistance.domain.borders.BorderDelta;
import com.bitplay.persistance.domain.fluent.Delta;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Getter
public class AvgDeltaInMemory implements AvgDelta {

    private static final Logger logger = LoggerFactory.getLogger(AvgDeltaInMemory.class);

    private Cache<Instant, Long> bDeltaCache;
    private Cache<Instant, Long> oDeltaCache;
    private volatile BigDecimal bDeltaEveryCalc = BigDecimal.ZERO;
    private volatile BigDecimal oDeltaEveryCalc = BigDecimal.ZERO;

    @Autowired
    private ArbitrageService arbitrageService;

    @Override
    public BigDecimal calcAvgDelta(DeltaName deltaName, BigDecimal instantDelta, BorderDelta borderDelta) {
        return deltaName == DeltaName.B_DELTA
                ? bDeltaEveryCalc
                : oDeltaEveryCalc;
    }

    @Override
    public void newDeltaEvent(DeltaChange deltaChange) {
        if (deltaChange.getBtmDelta() != null) {
            bDeltaCache.put(Instant.now(), (deltaChange.getBtmDelta().multiply(BigDecimal.valueOf(100))).longValue());
            bDeltaEveryCalc = calcDeltaSma(bDeltaCache);
        }
        if (deltaChange.getOkDelta() != null) {
            oDeltaCache.put(Instant.now(), (deltaChange.getOkDelta().multiply(BigDecimal.valueOf(100))).longValue());
            oDeltaEveryCalc = calcDeltaSma(oDeltaCache);
        }
        arbitrageService.getDeltaMon().setItmes(bDeltaCache.size(), oDeltaCache.size());
    }

    private synchronized BigDecimal calcDeltaSma(Cache<Instant, Long> bDeltaCache) {
        long sum = bDeltaCache.asMap().values().stream().mapToLong(Long::longValue).sum();
        return BigDecimal.valueOf(sum / bDeltaCache.size(), 2);
    }

    @Override
    public void resetDeltasCache(Integer delta_hist_per, boolean clearData) {
        if (clearData) {
            bDeltaCache = CacheBuilder.newBuilder()
                    .expireAfterWrite(delta_hist_per, TimeUnit.SECONDS)
                    .build();
            oDeltaCache = CacheBuilder.newBuilder()
                    .expireAfterWrite(delta_hist_per, TimeUnit.SECONDS)
                    .build();
        } else {
            ConcurrentMap<Instant, Long> map1 = bDeltaCache.asMap();
            bDeltaCache = CacheBuilder.newBuilder()
                    .expireAfterWrite(delta_hist_per, TimeUnit.SECONDS)
                    .build();
            bDeltaCache.putAll(map1);
            ConcurrentMap<Instant, Long> map2 = oDeltaCache.asMap();
            oDeltaCache = CacheBuilder.newBuilder()
                    .expireAfterWrite(delta_hist_per, TimeUnit.SECONDS)
                    .build();
            oDeltaCache.putAll(map2);
        }
    }
}
