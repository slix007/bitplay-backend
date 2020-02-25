package com.bitplay.market.okcoin;

import com.bitplay.arbitrage.events.ArbitrageReadyEvent;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.OkexSettlement;
import com.bitplay.utils.SchedulerUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class OkexSettlementService {

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    private final ScheduledExecutorService checkerExecutor = SchedulerUtils.singleThreadExecutor("okex-settlement-check-%d");

    @EventListener(ArbitrageReadyEvent.class)
    public void init() {
        checkerExecutor.scheduleAtFixedRate(this::timerTick, 10, 1, TimeUnit.SECONDS);
    }

    private void timerTick() {
        settlementMode = calcIsSettlementMode();
    }

    private boolean calcIsSettlementMode() {
        final OkexSettlement s = settingsRepositoryService.getSettings().getOkexSettlement();
        if (s.isActive()) {
            try {
                final LocalTime now = LocalTime.now();
                final LocalTime startOfPeriod = s.getStartAtTime();
                final LocalTime endOfPeriod = s.getStartAtTime().plusMinutes(s.getPeriod());
                return now.isAfter(startOfPeriod) && now.isBefore(endOfPeriod);
            } catch (Exception e) {
                // wrong settings. default is false.
            }
        }

        return false;
    }

    // settlement
    private volatile boolean settlementMode = false;

    public boolean isSettlementMode() {
        return settlementMode;
    }

}
