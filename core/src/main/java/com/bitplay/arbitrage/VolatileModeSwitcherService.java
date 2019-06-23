package com.bitplay.arbitrage;

import com.bitplay.market.model.BtmFokAutoArgs;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.TradingMode;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import java.math.BigDecimal;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VolatileModeSwitcherService {

    private final ScheduledExecutorService delayService = Executors.newSingleThreadScheduledExecutor(
            new NamedThreadFactory("volatile-mode-delay"));
    private volatile ScheduledFuture<?> delayTask = null;

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private PersistenceService persistenceService;
    @Autowired
    private ArbitrageService arbitrageService;

    boolean trySwitchToVolatileModeBorderV2(final BordersService.TradingSignal tradingSignal) {
        if (tradingSignal.borderValueList != null) {
            BigDecimal minBorder = tradingSignal.getMinBorder();
            BigDecimal maxBorder = tradingSignal.getMaxBorder();
            if (minBorder != null && maxBorder != null && tradingSignal.deltaVal != null && !tradingSignal.deltaVal.isEmpty()) {
                BigDecimal delta = new BigDecimal(tradingSignal.deltaVal);
                return trySwitchToVolatileMode(delta, minBorder, new BtmFokAutoArgs(delta, maxBorder, tradingSignal.borderValue));
            }
        }
        return false;
    }

    boolean trySwitchToVolatileMode(BigDecimal delta, BigDecimal minBorder, BtmFokAutoArgs btmFokAutoArgs) {
        // если delta1 plan - border1 >= Border cross depth или delta2 plan - border2 >= Border cross depth,
        // то это триггер для переключения из Current mode в Volatile Mode.
        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        final BigDecimal borderCrossDepth = settings.getSettingsVolatileMode().getBorderCrossDepth();
        if (settings.getTradingModeAuto()
                && borderCrossDepth.signum() > 0
                && delta.subtract(minBorder).compareTo(borderCrossDepth) >= 0) {
            arbitrageService.setLastBtmFokAutoArgs(btmFokAutoArgs);
            if (settings.getTradingModeState().getTradingMode() == TradingMode.CURRENT) {
                final Integer vModeDelayMs = settings.getSettingsVolatileMode().getVolatileDelayMs();
                if (vModeDelayMs != null && vModeDelayMs > 0) {
                    if (!isTimerActive()) {
                        String msg = String.format("volatile-mode signal. Waiting delay(ms)=%s", vModeDelayMs);
                        log.info(msg);
                        warningLogger.info(msg);
                        delayTask = delayService.schedule(() -> arbitrageService.activateVolatileMode(),
                                vModeDelayMs, TimeUnit.MILLISECONDS);
                    } // else delayTask is in_progress
                } else {
                    arbitrageService.activateVolatileMode();
                    return true;
                }
            } else {
                // already VOLATILE mode, but need to update timestamp
                persistenceService.getSettingsRepositoryService().updateTradingModeState(TradingMode.VOLATILE);
            }
        } else {
            stopVmTimer();
        }
        return false;
    }

    void stopVmTimer() {
        if (delayTask != null && !delayTask.isDone()) {
            delayTask.cancel(false);
        }
    }

    public void restartVolatileDelay(Integer dBeforeMs, Integer dAfterMs) {
        if (delayTask != null && !delayTask.isDone()) {
            final long remainingMs = delayTask.getDelay(TimeUnit.MILLISECONDS);
            if (remainingMs > 0) {
                delayTask.cancel(false);
                if (delayTask.isCancelled()) {
                    final long passedMs = dBeforeMs - remainingMs;
                    // dBeforeMs=100 ---> dAfterMs=50
                    // 1) remainingMs=70 => passed=30 => updated=20  (dAfter-passed)
                    // 2) remainingMs=10 => passed=90 => updated=-40  (dAfter-passed)
                    // dBeforeMs=50 ---> dAfterMs=100
                    // 1) remainingMs=10 => passed=40 => updated=60  (dAfter-passed)
                    // 2) remainingMs=45 => passed=5 => updated=95  (dAfter-passed)
                    final long updated = dAfterMs - passedMs;
                    if (updated > 0) {
                        delayTask = delayService.schedule(() -> arbitrageService.activateVolatileMode(),
                                updated, TimeUnit.MILLISECONDS);
                    } else {
                        arbitrageService.activateVolatileMode();
                    }
                } else {
                    log.warn("restartVolatileDelay. cancel attempt. isCancelled=false");
                }
            } else {
                log.warn("restartVolatileDelay. cancel attempt. remainingMs=" + remainingMs);
            }
        }
    }

    private boolean isTimerActive() {
        return delayTask != null && !delayTask.isDone();
    }

    public long timeToVolatileMode() {
        return isTimerActive()
                ? delayTask.getDelay(TimeUnit.MILLISECONDS)
                : 0;

    }
}
