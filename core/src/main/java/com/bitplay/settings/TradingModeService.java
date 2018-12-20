package com.bitplay.settings;

import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.fluent.TradingModeState;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.TradingMode;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class TradingModeService {

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    private final ScheduledExecutorService checkerExecutor = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("SettingsModeChecker-%d").build());

    @EventListener(ApplicationReadyEvent.class)
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
