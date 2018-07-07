package com.bitplay.security;

import com.bitplay.Config;
import com.bitplay.arbitrage.ArbitrageService;
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
    private Config config;

    @Autowired
    private ArbitrageService arbitrageService;

    private Long violateTimeSec;

    public boolean hasPermissionByEBestMin() {
        return isEBestMinOk() && !arbitrageService.isArbForbidden();
    }

    public boolean isEBestMinOk() {

        try {
            final BigDecimal sumEBestUsd = arbitrageService.getSumEBestUsd();
            final Integer eBestMin = config.getEBestMin();

            if (eBestMin == null) {
                log.warn("WARNING: e_best_min is not set");
                return isValidWithDelay(true);
            }

//            log.info("e_best_min=" + eBestMin + ", sumEBestUsd=" + sumEBestUsd);
            if (sumEBestUsd.signum() < 0) { // not initialized yet
                log.warn("WARNING: sum_e_best_usd is not yet initialized");
                return isValidWithDelay(true);
            }
            if (sumEBestUsd.compareTo(BigDecimal.valueOf(eBestMin)) < 0) {
                log.warn("WARNING: sumEBestUsd({}) < e_best_min({})", sumEBestUsd, eBestMin);
                warningLogger.warn("WARNING: sumEBestUsd({}) < e_best_min({})", sumEBestUsd, eBestMin);
                return isValidWithDelay(false);
            }
        } catch (Exception e) {
            log.error("Check e_best_min permission exception ", e);
            warningLogger.error("Check e_best_min permission exception ", e);
            return isValidWithDelay(false);
        }

        return isValidWithDelay(true); // all validations completed
    }

    private boolean isValidWithDelay(boolean isValid) {
        if (isValid) {
            violateTimeSec = null;
        }

        final Long VIOLATE_TIME_SEC = 5L;
        if (violateTimeSec != null
                && Instant.now().getEpochSecond() - violateTimeSec > VIOLATE_TIME_SEC) {
            return false;
        }

        if (violateTimeSec == null && !isValid) {
            violateTimeSec = Instant.now().getEpochSecond();
        }

        return true;
    }
}
