package com.bitplay.api.controller;

import com.bitplay.api.domain.ChangeRequestJson;
import com.bitplay.arbitrage.PosDiffService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.correction.Adj;
import com.bitplay.persistance.domain.correction.Corr;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.correction.Preliq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by Sergey Shurmin on 3/21/18.
 */
@Secured("ROLE_TRADER")
@RestController
@RequestMapping("/settings")
public class SettingsCorrEndpoint {

    private final static Logger logger = LoggerFactory.getLogger(SettingsEndpoint.class);

    @Autowired
    private PersistenceService persistenceService;
    @Autowired
    private PosDiffService posDiffService;
    @Autowired
    private OkCoinService okCoinService;
    @Autowired
    private BitmexService bitmexService;

    @RequestMapping(value = "/corr", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public CorrParams getCorrParams() {
        CorrParams result = new CorrParams();
        try {
            result = persistenceService.fetchCorrParams();
        } catch (Exception e) {
            final String error = String.format("Failed to get corrParams %s", e.toString());
            logger.error(error, e);
        }
        return result;
    }

    @RequestMapping(value = "/corr/create-default", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public CorrParams updateSettings() {
        persistenceService.saveCorrParams(CorrParams.createDefault());
        return persistenceService.fetchCorrParams();
    }

    @RequestMapping(value = "/corr/reset", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public CorrParams updateCorrError(@RequestBody ChangeRequestJson changeRequestJson) {
        CorrParams corrParams = persistenceService.fetchCorrParams();
        if (changeRequestJson.getCommand() != null) {
            corrParams.getCorr().setSucceedCount(0);
            corrParams.getCorr().setFailedCount(0);
            corrParams.getCorr().setTotalCount(0);
            corrParams.getCorr().setCurrErrorCount(0);
            corrParams.getPreliq().setSucceedCount(0);
            corrParams.getPreliq().setTotalCount(0);
            corrParams.getPreliq().setFailedCount(0);
            corrParams.getPreliq().setCurrErrorCount(0);
            corrParams.getAdj().setSucceedCount(0);
            corrParams.getAdj().setFailedCount(0);
            corrParams.getAdj().setTotalCount(0);
            corrParams.getAdj().setCurrErrorCount(0);
            persistenceService.saveCorrParams(corrParams);
        }
        return corrParams;
    }

    @RequestMapping(value = "/corr", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public CorrParams updatePreliq(@RequestBody CorrParams anUpdate) {
        CorrParams corrParams = persistenceService.fetchCorrParams();
        if (anUpdate != null) {
            if (anUpdate.getPreliq() != null) {
                final Preliq uPreliq = anUpdate.getPreliq();
                if (uPreliq.getPreliqBlockUsd() != null) {
                    corrParams.getPreliq().setPreliqBlockUsd(uPreliq.getPreliqBlockUsd());
                    persistenceService.saveCorrParams(corrParams);
                }
                if (uPreliq.getMaxErrorCount() != null) {
                    corrParams.getPreliq().setMaxErrorCount(uPreliq.getMaxErrorCount());
                    persistenceService.saveCorrParams(corrParams);
                    bitmexService.getDtPreliq().stop();
                    okCoinService.getDtPreliq().stop();
                }
                if (uPreliq.getMaxTotalCount() != null) {
                    corrParams.getPreliq().setMaxTotalCount(uPreliq.getMaxTotalCount());
                    persistenceService.saveCorrParams(corrParams);
                    bitmexService.getDtPreliq().stop();
                    okCoinService.getDtPreliq().stop();
                }
            }
            if (anUpdate.getCorr() != null) {
                final Corr uCorr = anUpdate.getCorr();
                if (uCorr.getMaxVolCorrUsd() != null) {
                    corrParams.getCorr().setMaxVolCorrUsd(uCorr.getMaxVolCorrUsd());
                    persistenceService.saveCorrParams(corrParams);
                }
                if (uCorr.getMaxErrorCount() != null) {
                    corrParams.getCorr().setMaxErrorCount(uCorr.getMaxErrorCount());
                    persistenceService.saveCorrParams(corrParams);
                    posDiffService.stopTimer("corr");
                }
                if (uCorr.getMaxTotalCount() != null) {
                    corrParams.getCorr().setMaxTotalCount(uCorr.getMaxTotalCount());
                    persistenceService.saveCorrParams(corrParams);
                    posDiffService.stopTimer("corr");
                }
            }
            if (anUpdate.getAdj() != null) {
                final Adj update = anUpdate.getAdj();
                if (update.getMaxErrorCount() != null) {
                    corrParams.getAdj().setMaxErrorCount(update.getMaxErrorCount());
                    persistenceService.saveCorrParams(corrParams);
                    posDiffService.stopTimer("adj");
                }
                if (update.getMaxTotalCount() != null) {
                    corrParams.getAdj().setMaxTotalCount(update.getMaxTotalCount());
                    persistenceService.saveCorrParams(corrParams);
                    posDiffService.stopTimer("adj");
                }
            }
        }

        persistenceService.resetSettingsPreset();

        return corrParams;
    }

}
