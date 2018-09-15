package com.bitplay.api.service;

/**
 * Created by Sergey Shurmin on 9/24/17.
 */

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RestartService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private ArbitrageService arbitrageService;

    final public static String API_REQUEST = "API request";

    public void scheduleCheckForFullStart() {
        log.info("FullStart-check scheduling");
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("check-for-full-start-thread-%d").build();
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(namedThreadFactory);
        executorService.schedule(() -> {
            log.info("FullStart-check");
            if (!arbitrageService.isFirstDeltasCalculated()) {
                log.info("FullStart-check failed");
                try {
                    doFullRestart("No firstDeltaCalculated after 2 min");
                } catch (IOException e) {
                    log.error("Error on FullStart-check by firstDeltaCalculated ", e);
                }
            } else {
                log.info("FullStart-check was successful");
            }
        }, 2, TimeUnit.MINUTES);
    }

    public void doFullRestart(String source) throws IOException {
        final Boolean restartEnabled = settingsRepositoryService.getSettings().getRestartEnabled();
        if (restartEnabled == null || restartEnabled || source.equals(API_REQUEST)) {
            final String warningMessage = String.format("Full Restart has been requested by %s", source);
            log.error(warningMessage);
            warningLogger.error(warningMessage);

            Runtime.getRuntime().exec(new String[]{"/bin/systemctl", "restart", "bitplay2"});
        } else {
            final String warningMessage = String.format("Restart is disabled. The attempt source is %s", source);
            log.error(warningMessage);
            warningLogger.error(warningMessage);
        }
    }


}
