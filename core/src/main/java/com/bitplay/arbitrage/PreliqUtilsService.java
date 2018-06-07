package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.MarketService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.correction.CorrParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 3/24/18.
 */
@Service
public class PreliqUtilsService {

    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");


    @Autowired
    private PersistenceService persistenceService;


    public void preliqCountersOnRoundDone(boolean isSucceeded, GuiParams params, SignalType signalType,
                                          MarketService first, MarketService second) {
        if (signalType == SignalType.B_PRE_LIQ || signalType == SignalType.O_PRE_LIQ) {
            // Preliq done: O_DQL > O_DQL_close_min && B_DQL > B_DQL_close_min

            try {
                Thread.sleep(2000);

                first.fetchPosition();
                second.fetchPosition();
            } catch (Exception e) {
                e.printStackTrace();
            }

            final CorrParams corrParams = persistenceService.fetchCorrParams();
            boolean isCorrect = false;
            if (isSucceeded) {
                // double check
                final BigDecimal bDQLCloseMin = params.getBDQLCloseMin();
                final BigDecimal oDQLCloseMin = params.getODQLCloseMin();
                if (first.getLiqInfo().getDqlCurr().compareTo(bDQLCloseMin) > 0
                        && second.getLiqInfo().getDqlCurr().compareTo(oDQLCloseMin) > 0) {
                    isCorrect = true;
                }
            }

            if (isCorrect) {
                corrParams.getPreliq().incSuccesses();
                deltasLogger.info("Preliq succeed. " + corrParams.getPreliq().toString());
            } else {
                corrParams.getPreliq().incFails();
                deltasLogger.info("Preliq failed. " + corrParams.getPreliq().toString());
            }

            persistenceService.saveCorrParams(corrParams);
        }

    }
}
