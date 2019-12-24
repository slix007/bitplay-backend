package com.bitplay.security;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.dto.ThrottledWarn;
import com.bitplay.external.NotifyType;
import com.bitplay.external.SlackNotifications;
import com.bitplay.persistance.SettingsRepositoryService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Slf4j
@Service
public class TraderPermissionsService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");
    private ThrottledWarn throttledWarn = new ThrottledWarn(warningLogger,30);
    private ThrottledWarn throttledLog = new ThrottledWarn(log,30);

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private SlackNotifications slackNotifications;

    private volatile SebestStatus sebestStatus = SebestStatus.NORMAL;

    private Long violateTimeSec;
    private Long maxViolateTimeSec = 10L;

    public boolean hasPermissionByEBestMin() {
        return isEBestMinOk() // current
                && !arbitrageService.isArbForbidden(); // previously set
    }

    public void checkEBestMin() {
        if (!isEBestMinOk()) {
            final BigDecimal sumEBestUsd = arbitrageService.getSumEBestUsd();
            final Integer eBestMin = settingsRepositoryService.getSettings().getEBestMin();
            String msg = String.format("WARNING: sumEBestUsd(%s) < e_best_min(%s)", sumEBestUsd, eBestMin);
            throttledLog.warn(msg);
            throttledWarn.warn(msg);
            sebestStatus = SebestStatus.LOWER;
            slackNotifications.sendNotify(NotifyType.SEBEST_LOWER, "S_e_best=LOWER: " + msg);
        } else {
            slackNotifications.resetThrottled(NotifyType.SEBEST_LOWER);
        }

    }

    public boolean isEBestMinOk() {

        try {
            final BigDecimal sumEBestUsd = arbitrageService.getSumEBestUsd();
            BigDecimal bEbest = arbitrageService.getbEbest();
            BigDecimal oEbest = arbitrageService.getoEbest();
            final Integer eBestMin = settingsRepositoryService.getSettings().getEBestMin();

            if (eBestMin == null) {
                throttledLog.warn("WARNING: e_best_min is not set");
                return isValidWithDelay(true, bEbest, oEbest);
            }

//            log.info("e_best_min=" + eBestMin + ", sumEBestUsd=" + sumEBestUsd);
            if (sumEBestUsd.signum() < 0) { // not initialized yet
                throttledLog.warn("WARNING: sum_e_best_usd is not yet initialized");
                return isValidWithDelay(true, bEbest, oEbest);
            }
            if (sumEBestUsd.compareTo(BigDecimal.valueOf(eBestMin)) < 0) {
                // Флаг Forbidden не ставится, если s_e_best < s_e_best_min && e_best любой биржи = 0.
                if (bEbest.signum() == 0 || oEbest.signum() == 0 || sumEBestUsd.signum() == 0) {
                    throttledLog.warn(String.format("WARNING: sumEBestUsd(%s) < e_best_min(%s), but equity==0(bEbest=%s, oEbest=%s or sumEBestUsd=%s)",
                            sumEBestUsd, eBestMin, bEbest, oEbest, sumEBestUsd));
                    return isValidWithDelay(true, bEbest, oEbest);
                }
                throttledLog.warn(String.format("WARNING: sumEBestUsd(%s) < e_best_min(%s)", sumEBestUsd, eBestMin));
                return isValidWithDelay(false, bEbest, oEbest);
            }
        } catch (Exception e) {
            throttledLog.error("Check e_best_min permission exception ", e);
            warningLogger.error("Check e_best_min permission exception ", e);
            return isValidWithDelay(false, null, null);
        }

        return isValidWithDelay(true, null, null); // all validations completed
    }

    private boolean isValidWithDelay(boolean isValid, BigDecimal bEbest, BigDecimal oEbest) {
        if (isValid) {
            violateTimeSec = null;
            return true;
        }

        maxViolateTimeSec = 10L;
        if ((bEbest != null && bEbest.signum() == 0) || (oEbest != null && oEbest.signum() == 0)) {
            maxViolateTimeSec = 60L;
        }

        if (violateTimeSec != null
                && Instant.now().getEpochSecond() - violateTimeSec > maxViolateTimeSec) {
            return false;
        }

        if (violateTimeSec == null) {
            violateTimeSec = Instant.now().getEpochSecond();
        }

        return true;
    }

    public String getTimeToForbidden() {
        String str = "_";
        if (violateTimeSec != null) {
            long secToForbidden = maxViolateTimeSec - (Instant.now().getEpochSecond() - violateTimeSec);
            str = secToForbidden >= 0 ? String.valueOf(secToForbidden) : "0";
        }
        return str;
    }

    public boolean isForbidden() {
        return sebestStatus == SebestStatus.LOWER;
    }

    public SebestStatus getSebestStatus() {
        return sebestStatus;
    }

    public void resetSebestMin() {
        sebestStatus = SebestStatus.NORMAL;
        checkEBestMin();
    }
}
