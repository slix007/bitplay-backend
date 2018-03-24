package com.bitplay.arbitrage;

import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.correction.CorrParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 3/24/18.
 */
@Service
public class PreliqUtilsService {

    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");


    @Autowired
    private PersistenceService persistenceService;


    public void preliqCountersOnRoundDone(boolean isSucceed) {
        final CorrParams corrParams = persistenceService.fetchCorrParams();
        if (isSucceed) {
            corrParams.getPreliq().incSuccesses();
            deltasLogger.info("Preliq succeed. " + corrParams.getPreliq().toString());
        } else {
            corrParams.getPreliq().incFails();
            deltasLogger.info("Preliq failed. " + corrParams.getPreliq().toString());
        }
        persistenceService.saveCorrParams(corrParams);

    }
}
