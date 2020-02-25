package com.bitplay.settings;

import com.bitplay.arbitrage.events.ArbitrageReadyEvent;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.fluent.TradingModeState;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.TradingMode;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class TradingModeService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    private final ScheduledExecutorService checkerExecutor = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("SettingsModeChecker-%d").build());

    @EventListener(ArbitrageReadyEvent.class)
    public void init() {
        checkerExecutor.scheduleAtFixedRate(this::timerTick, 10, 1, TimeUnit.SECONDS);
    }

    public long secToReset() {
        final Settings settings = settingsRepositoryService.getSettings();
        final Long secPast = secPast(settings);
        if (secPast == null) {
            return 0;
        }
        final Integer maxDurationSec = settings.getSettingsVolatileMode().getVolatileDurationSec();
        return maxDurationSec - secPast;
    }

    private Long secPast(Settings settings) {
        final TradingModeState tradingModeState = settings.getTradingModeState();
        if (tradingModeState.getTradingMode() == TradingMode.VOLATILE) {
            final Date timestamp = tradingModeState.getTimestamp();
            if (timestamp != null) {
                return Duration.between(timestamp.toInstant(), Instant.now()).getSeconds();
            }
        }
        return null;
    }

    private void timerTick() {
        if (shouldReset()) {
            warningLogger.info("Set TradingMode.CURRENT");
            settingsRepositoryService.updateTradingModeState(TradingMode.CURRENT);
        }
    }

    private boolean shouldReset() {
        final Settings settings = settingsRepositoryService.getSettings();
        final Long secPast = secPast(settings);
        if (secPast != null) {
            final Integer maxDurationSec = settings.getSettingsVolatileMode().getVolatileDurationSec();
            return secPast >= maxDurationSec;
        }
        return false;
    }

}
