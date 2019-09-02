package com.bitplay.settings;

import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.bitmex.BitmexTradeLogger;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.BitmexChangeOnSo;
import com.bitplay.persistance.domain.settings.PlacingType;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Getter
@Setter
@Slf4j
@Service
public class BitmexChangeOnSoService {

    private volatile boolean testingSo = false;
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private BitmexTradeLogger tradeLogger;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("bitmex-change-so-%d").build()
    );

    private volatile ScheduledFuture toResetTask;

    public long getSecToReset() {
        return isActive()
                ? toResetTask.getDelay(TimeUnit.SECONDS)
                : 0;
    }

    public PlacingType getPlacingTypeToChange(SignalType signalType) {
        if (isActive()) {
            if (signalType.isAdj() && settingsRepositoryService.getSettings().getBitmexChangeOnSo().getAdjToTaker()) {
                return PlacingType.TAKER;
            }
            if (signalType == SignalType.AUTOMATIC && settingsRepositoryService.getSettings().getBitmexChangeOnSo().getSignalTo()) {
                return settingsRepositoryService.getSettings().getBitmexChangeOnSo().getSignalPlacingType();
            }
        }
        return null;
    }

    public PlacingType getPlacingType(SignalType signalType) {
        final PlacingType placingTypeToChange = getPlacingTypeToChange(signalType);
        return placingTypeToChange != null ? placingTypeToChange : settingsRepositoryService.getSettings().getBitmexPlacingType();
    }

    public PlacingType getPlacingType() {
        return getPlacingType(SignalType.AUTOMATIC);
    }

    public boolean toConBoActive() {
        return isActive() && settingsRepositoryService.getSettings().getBitmexChangeOnSo().getToConBo();
    }

    public boolean isActive() {
        return toResetTask != null && !toResetTask.isDone();
    }

    public void tryActivate(Integer attempt) {
        final BitmexChangeOnSo bitmexChangeOnSo = settingsRepositoryService.getSettings().getBitmexChangeOnSo();
        final boolean isAuto = bitmexChangeOnSo.getAuto();
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
            final String msg = "BitmexChangeOnSo deactivated manually";
            log.info(msg);
            warningLogger.info(msg);
            tradeLogger.info(msg);
        }
    }

    private void activate() {
        final BitmexChangeOnSo bitmexChangeOnSo = settingsRepositoryService.getSettings().getBitmexChangeOnSo();
        List<String> arr = new ArrayList<>();
        if (bitmexChangeOnSo.getToConBo()) {
            arr.add("CON_B_O");
        }
        if (bitmexChangeOnSo.getAdjToTaker()) {
            arr.add("Adj_to_TAKER");
        }
        if (bitmexChangeOnSo.getSignalTo()) {
            arr.add("Signal_to_" + bitmexChangeOnSo.getSignalPlacingType());
        }
        final String msgStart = "BitmexChangeOnSo activated " + String.join(", ", arr);
        log.info(msgStart);
        warningLogger.info(msgStart);
        tradeLogger.info(msgStart);
        final Integer durationSec = bitmexChangeOnSo.getDurationSec();
        toResetTask = executor.schedule(() -> {
                    final String msg = "BitmexChangeOnSo deactivated";
                    log.info(msg);
                    warningLogger.info(msg);
                    tradeLogger.info(msg);
                },
                durationSec, TimeUnit.SECONDS);

    }


}
