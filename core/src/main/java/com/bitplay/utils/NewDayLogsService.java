package com.bitplay.utils;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.settings.ContractMode;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NewDayLogsService {

    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");
    private static final Logger okexTradeLogger = LoggerFactory.getLogger("OKCOIN_TRADE_LOG");
    private static final Logger bitmexTradeLogger = LoggerFactory.getLogger("BITMEX_TRADE_LOG");
    private static final Logger deltasLogger = LoggerFactory.getLogger("DELTAS_LOG");

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Scheduled(initialDelay = 20 * 1000, fixedDelay = 20 * 1000)
    public void startNewDayLogsIfNeeded() {
        final GuiParams params = arbitrageService.getParams();
        if (params.getLastDateLogs() == null
                || !LocalDate.now().isEqual(params.getLastDateLogs())) {
            // какой мод запущен, какой nt по обоим set. Какая дата.
            final ContractMode contractMode = settingsRepositoryService.getSettings().getContractMode();

            final String theLog = String.format("New day %s. Mode=%s, mainSet=%s, extraSet=%s",
                    LocalDate.now().toString(),
                    contractMode.getModeName(),
                    arbitrageService.getMainSetStr(),
                    arbitrageService.getExtraSetStr()
            );
            warningLogger.info(theLog);
            okexTradeLogger.info(theLog);
            bitmexTradeLogger.info(theLog);
            deltasLogger.info(theLog);

            params.setLastDateLogs(LocalDate.now());
            arbitrageService.saveParamsToDb();
        }
    }
}
