package com.bitplay.security;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.persistance.SettingsRepositoryService;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TraderPermissionsService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private ArbitrageService arbitrageService;

    private Long violateTimeSec;
    private Long maxViolateTimeSec = 10L;

    public boolean hasPermissionByEBestMin() {
        return isEBestMinOk() && !arbitrageService.isArbForbidden();
    }

    public boolean isEBestMinOk() {

        try {
            final BigDecimal sumEBestUsd = arbitrageService.getSumEBestUsd();
            BigDecimal bEbest = arbitrageService.getbEbest();
            BigDecimal oEbest = arbitrageService.getoEbest();
            final Integer eBestMin = settingsRepositoryService.getSettings().getEBestMin();

            if (eBestMin == null) {
                log.warn("WARNING: e_best_min is not set");
                return isValidWithDelay(true, bEbest, oEbest);
            }

//            log.info("e_best_min=" + eBestMin + ", sumEBestUsd=" + sumEBestUsd);
            if (sumEBestUsd.signum() < 0) { // not initialized yet
                log.warn("WARNING: sum_e_best_usd is not yet initialized");
                return isValidWithDelay(true, bEbest, oEbest);
            }
            if (sumEBestUsd.compareTo(BigDecimal.valueOf(eBestMin)) < 0) {
                log.warn("WARNING: sumEBestUsd({}) < e_best_min({})", sumEBestUsd, eBestMin);
                return isValidWithDelay(false, bEbest, oEbest);
            }
        } catch (Exception e) {
            log.error("Check e_best_min permission exception ", e);
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
}
