package com.bitplay.security;

import com.bitplay.Config;
import com.bitplay.arbitrage.ArbitrageService;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TraderPermissionsService {

    @Autowired
    private Config config;

    @Autowired
    private ArbitrageService arbitrageService;

    public boolean isEBestMinOk() {

        try {
            final BigDecimal sumEBestUsd = arbitrageService.getSumEBestUsd();
            final Integer eBestMin = config.getEBestMin();

            if (eBestMin == null) {
                log.warn("WARNING: e_best_min is not set");
                return true;
            }

            log.info("e_best_min=" + eBestMin + ", sumEBestUsd=" + sumEBestUsd);
            if (sumEBestUsd.signum() < 0) { // not initialized yet
                log.warn("WARNING: sum_e_best_usd is not yet initialized");
                return true;
            }
            if (sumEBestUsd.compareTo(BigDecimal.valueOf(eBestMin)) < 0) {
                log.warn("WARNING: e_best_min=" + eBestMin + ", sumEBestUsd=" + sumEBestUsd);
                return false;
            }
        } catch (Exception e) {
            log.error("Check permission exception ", e);
            return false;
        }

        return true; // all validations completed
    }

}
