package com.bitplay.api.controller;

import com.bitplay.api.domain.ChangeRequestJson;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.correction.Corr;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.correction.Preliq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by Sergey Shurmin on 3/21/18.
 */
@RestController
@RequestMapping("/settings")
public class SettingsCorrEndpoint {

    private final static Logger logger = LoggerFactory.getLogger(SettingsEndpoint.class);

    @Autowired
    PersistenceService persistenceService;

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
    public CorrParams updateSettings() {
        persistenceService.saveCorrParams(CorrParams.createDefault());
        return persistenceService.fetchCorrParams();
    }

    @RequestMapping(value = "/corr/reset", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CorrParams updateCorrError(@RequestBody ChangeRequestJson changeRequestJson) {
        CorrParams corrParams = persistenceService.fetchCorrParams();
        if (changeRequestJson.getCommand() != null) {
            corrParams.getCorr().setSucceedCount(0);
            corrParams.getCorr().setFailedCount(0);
            corrParams.getCorr().setCurrErrorCount(0);
            corrParams.getPreliq().setSucceedCount(0);
            corrParams.getPreliq().setFailedCount(0);
            corrParams.getPreliq().setCurrErrorCount(0);
            persistenceService.saveCorrParams(corrParams);
        }
        return corrParams;
    }

    @RequestMapping(value = "/corr", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CorrParams updatePreliq(@RequestBody CorrParams anUpdate) {
        CorrParams corrParams = persistenceService.fetchCorrParams();
        if (anUpdate != null) {
            if (anUpdate.getPreliq() != null) {
                final Preliq uPreliq = anUpdate.getPreliq();
                if (uPreliq.getPreliqBlockOkex() != null) {
                    corrParams.getPreliq().setPreliqBlockOkex(uPreliq.getPreliqBlockOkex());
                    persistenceService.saveCorrParams(corrParams);
                }
                if (uPreliq.getMaxErrorCount() != null) {
                    corrParams.getPreliq().setMaxErrorCount(uPreliq.getMaxErrorCount());
                    persistenceService.saveCorrParams(corrParams);
                }
                if (uPreliq.getMaxTotalCount() != null) {
                    corrParams.getPreliq().setMaxTotalCount(uPreliq.getMaxTotalCount());
                    persistenceService.saveCorrParams(corrParams);
                }
            }
            if (anUpdate.getCorr() != null) {
                final Corr uCorr = anUpdate.getCorr();
                if (uCorr.getMaxVolCorrOkex() != null) {
                    corrParams.getCorr().setMaxVolCorrOkex(uCorr.getMaxVolCorrOkex());
                    persistenceService.saveCorrParams(corrParams);
                }
                if (uCorr.getMaxErrorCount() != null) {
                    corrParams.getCorr().setMaxErrorCount(uCorr.getMaxErrorCount());
                    persistenceService.saveCorrParams(corrParams);
                }
                if (uCorr.getMaxTotalCount() != null) {
                    corrParams.getCorr().setMaxTotalCount(uCorr.getMaxTotalCount());
                    persistenceService.saveCorrParams(corrParams);
                }
            }
        }
        return corrParams;
    }

}
