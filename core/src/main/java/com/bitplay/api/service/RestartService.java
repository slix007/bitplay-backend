package com.bitplay.api.service;

/**
 * Created by Sergey Shurmin on 9/24/17.
 */

import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.persistance.SettingsRepositoryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class RestartService {

    private final static Logger logger = LoggerFactory.getLogger(BitmexService.class);
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    protected final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Autowired
    SettingsRepositoryService settingsRepositoryService;

    final public static String API_REQUEST = "API request";

    public void doFullRestart(String source) throws IOException {
        final Boolean restartEnabled = settingsRepositoryService.getSettings().getRestartEnabled();
        if (restartEnabled == null || restartEnabled || source.equals(API_REQUEST)) {
            final String warningMessage = String.format("Full Restart has been requested by %s", source);
            logger.error(warningMessage);
            warningLogger.error(warningMessage);

            Runtime.getRuntime().exec(new String[]{"/bin/systemctl", "restart", "bitplay2"});
        } else {
            final String warningMessage = String.format("Restart is disabled. The attempt source is %s", source);
            logger.error(warningMessage);
            warningLogger.error(warningMessage);
        }
    }

    public void doDeferredRestart(String details) {
        logger.info("deferred restart. " + details);
        scheduler.schedule(() -> {
            try {
                doFullRestart("deferred after flag STOPPED. " + details);
            } catch (IOException e) {
                logger.error("Error on restart", e);
            }
        }, 30, TimeUnit.SECONDS);
    }
}
