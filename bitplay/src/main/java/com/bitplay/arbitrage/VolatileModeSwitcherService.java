package com.bitplay.arbitrage;

import com.bitplay.market.model.BtmFokAutoArgs;
import com.bitplay.persistance.DealPricesRepositoryService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.TradeService;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.TradingMode;
import com.bitplay.settings.BitmexChangeOnSoService;
import com.bitplay.settings.SettingsPremService;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VolatileModeSwitcherService {
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private final SettingsPremService settingsPremService;
    private final PersistenceService persistenceService;
    private final ArbitrageService arbitrageService;
    private final BitmexChangeOnSoService bitmexChangeOnSoService;
    private final DealPricesRepositoryService dealPricesRepositoryService;
    private final TradeService fplayTradeService;
    private final VolatileModeAfterService volatileModeAfterService;

    private final ScheduledExecutorService delayService = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("volatile-mode-delay"));
    private volatile ScheduledFuture<?> delayTask = null;

    // it's used for Volatile mode. When the activation is delayed, the param should be kept up-do-date.
    private volatile BtmFokAutoArgs lastBtmFokAutoArgs;

    boolean trySwitchToVolatileModeBorderV2(final BordersService.TradingSignal tradingSignal) {
        if (tradingSignal.borderValueList != null) {
            final BigDecimal minBorder = tradingSignal.getMinBorder();
            final BtmFokAutoArgs btmFokAutoArgs = tradingSignal.toBtmFokAutoArgs();
            final BigDecimal maxBorder = btmFokAutoArgs.getMaxBorder();
            final BigDecimal delta = btmFokAutoArgs.getDelta();
            if (minBorder != null && maxBorder != null && delta != null) {
                return trySwitchToVolatileMode(delta, minBorder, btmFokAutoArgs);
            }
        }
        return false;
    }

    boolean trySwitchToVolatileMode(BigDecimal delta, BigDecimal minBorder, BtmFokAutoArgs btmFokAutoArgs) {
        // если delta1 plan - border1 >= Border cross depth или delta2 plan - border2 >= Border cross depth,
        // то это триггер для переключения из Current mode в Volatile Mode.
        final Settings settings = persistenceService.getSettingsRepositoryService().getSettings();
        final BigDecimal borderCrossDepth = settingsPremService.getBorderCrossDepth();
        if (settings.getTradingModeAuto()
                && borderCrossDepth.signum() > 0
                && delta.subtract(minBorder).compareTo(borderCrossDepth) >= 0) {
            this.lastBtmFokAutoArgs = btmFokAutoArgs;
            if (settings.getTradingModeState().getTradingMode() == TradingMode.CURRENT) {
                final Integer vModeDelayMs = settings.getSettingsVolatileMode().getVolatileDelayMs();
                if (vModeDelayMs != null && vModeDelayMs > 0) {
                    if (!isTimerActive()) {
                        String msg = String.format("volatile-mode signal. Waiting delay(ms)=%s", vModeDelayMs);
                        log.info(msg);
                        warningLogger.info(msg);
                        delayTask = delayService.schedule(this::activateVolatileMode, vModeDelayMs, TimeUnit.MILLISECONDS);
                    } // else delayTask is in_progress
                } else {
                    this.activateVolatileMode();
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
                        delayTask = delayService.schedule(this::activateVolatileMode, updated, TimeUnit.MILLISECONDS);
                    } else {
                        this.activateVolatileMode();
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

    public synchronized void activateVolatileMode() {
        if (persistenceService.getSettingsRepositoryService().getSettings().getTradingModeState().getTradingMode() == TradingMode.CURRENT) {
            final ArbScheme arbScheme = persistenceService.getSettingsRepositoryService().getSettings().getArbSchemeRaw(); //raw is always for current
            final Settings settings = persistenceService.getSettingsRepositoryService().updateTradingModeState(TradingMode.VOLATILE);
            warningLogger.info("Set TradingMode.VOLATILE automatically");
            log.info("Set TradingMode.VOLATILE automatically");

            // if we replace-limit-orders then fix commissions for current signal.
            final PlacingType rightPlacingType = settings.getRightPlacingType();
            final PlacingType leftPlacingType = bitmexChangeOnSoService.getLeftPlacingType();
            final Long tradeId = arbitrageService.getTradeId();
            if (fplayTradeService.isInProgress(tradeId)) {
                if (arbScheme == ArbScheme.R_wait_L_portions) {
                    // cancel bitmex, but replace okex
                    volatileModeAfterService.justSetVolatileMode(tradeId, this.lastBtmFokAutoArgs, true);
                } else {
                    dealPricesRepositoryService.justSetVolatileMode(tradeId, leftPlacingType, rightPlacingType);
                    // replace-limit-orders. it may set CURRENT_VOLATILE
                    volatileModeAfterService.justSetVolatileMode(tradeId, this.lastBtmFokAutoArgs, false);
                }

            }

        }
    }

}
