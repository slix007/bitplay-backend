package com.bitplay.settings;

import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.BitmexChangeOnSo;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Getter
@Setter
@Slf4j
@Service
public class BitmexChangeOnSoService {

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("bitmex-change-so-%d").build()
    );

    private volatile ScheduledFuture toResetTask;

    public String getSecToReset() {
        final long secToReset = isActive()
                ? toResetTask.getDelay(TimeUnit.SECONDS)
                : 0;
        return String.valueOf(secToReset);
    }

    public boolean isActive() {
        return toResetTask != null && !toResetTask.isDone();
    }

    public void tryActivate(Integer attempt) {
        final BitmexChangeOnSo bitmexChangeOnSo = settingsRepositoryService.getSettings().getBitmexChangeOnSo();
        final boolean isAuto = bitmexChangeOnSo.getAuto() != null && bitmexChangeOnSo.getAuto();
        if (isAuto && !isActive()) {
            final Integer countToActivate = bitmexChangeOnSo.getCountToActivate();
            if (attempt >= countToActivate) {
                activate();
            }
        }
    }

    public void reset() {
        if (isActive()) {
            toResetTask.cancel(false);
            log.info("BitmexChangeOnSo deactivated manually");
        }
    }

    private void activate() {
        log.info("BitmexChangeOnSo activated");
        final Integer durationSec = settingsRepositoryService.getSettings().getBitmexChangeOnSo().getDurationSec();
        toResetTask = executor.schedule(() -> log.info("BitmexChangeOnSo deactivated"),
                durationSec, TimeUnit.SECONDS);

    }


}
