package com.bitplay.arbitrage;

import com.bitplay.arbitrage.dto.SignalType;
import com.bitplay.market.MarketService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.GuiLiqParams;
import com.bitplay.persistance.domain.correction.CorrParams;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by Sergey Shurmin on 3/24/18.
 */
@Service
public class PreliqUtilsService {

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private ArbitrageService arbitrageService;


    public void preliqCountersOnRoundDone(boolean isSucceeded, GuiLiqParams params, SignalType signalType,
                                          MarketService first, MarketService second) {
        if (signalType.isPreliq()) {
            // Preliq done: O_DQL > O_DQL_close_min && B_DQL > B_DQL_close_min

            try {
                Thread.sleep(2000);

                first.fetchPosition();
                second.fetchPosition();
            } catch (Exception e) {
                e.printStackTrace();
            }

            boolean isCorrect = false;
            if (isSucceeded) {
                // double check
                final BigDecimal bDQLCloseMin = params.getBDQLCloseMin();
                final BigDecimal oDQLCloseMin = params.getODQLCloseMin();
                if ((first.getLiqInfo().getDqlCurr() == null || first.getLiqInfo().getDqlCurr().compareTo(bDQLCloseMin) > 0)
                        && (second.getLiqInfo().getDqlCurr() == null || second.getLiqInfo().getDqlCurr().compareTo(oDQLCloseMin) > 0)) {
                    isCorrect = true;
                }
            }

            if (isCorrect) {
                final CorrParams corrParams = persistenceService.fetchCorrParams();
//                corrParams.getPreliq().tryIncSuccessful();
                persistenceService.saveCorrParams(corrParams);
                arbitrageService.printToCurrentDeltaLog("Preliq succeed. " + corrParams.getPreliq().toString());
            } else {
                final CorrParams corrParams = persistenceService.fetchCorrParams();
//                corrParams.getPreliq().tryIncFailed();
                persistenceService.saveCorrParams(corrParams);
                arbitrageService.printToCurrentDeltaLog("Preliq failed. " + corrParams.getPreliq().toString());
            }

        }

    }
}
