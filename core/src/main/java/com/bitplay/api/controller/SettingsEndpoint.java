package com.bitplay.api.controller;

import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.SysOverloadArgs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by Sergey Shurmin on 11/27/17.
 */
@RestController
@RequestMapping("/settings")
public class SettingsEndpoint {

    private final static Logger logger = LoggerFactory.getLogger(SettingsEndpoint.class);

    @Autowired
    SettingsRepositoryService settingsRepositoryService;

    @RequestMapping(value = "/all", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public Settings getSettings() {
        Settings result = new Settings();
        try {
            result = settingsRepositoryService.getSettings();
        } catch (Exception e) {
            final String error = String.format("Failed to get ArbScheme %s", e.toString());
            logger.error(error, e);
        }
        return result;
    }

    @RequestMapping(value = "/all", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Settings updateSettings(@RequestBody Settings settingsUpdate) {
        final Settings settings = settingsRepositoryService.getSettings();
        if (settingsUpdate.getOkexPlacingType() != null) {
            settings.setOkexPlacingType(settingsUpdate.getOkexPlacingType());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getArbScheme() != null) {
            settings.setArbScheme(settingsUpdate.getArbScheme());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getBitmexSysOverloadArgs() != null) {
            final SysOverloadArgs refToUpdate = settings.getBitmexSysOverloadArgs();
            if (settingsUpdate.getBitmexSysOverloadArgs().getPlaceAttempts() != null) {
                refToUpdate.setPlaceAttempts(settingsUpdate.getBitmexSysOverloadArgs().getPlaceAttempts());
            }
            if (settingsUpdate.getBitmexSysOverloadArgs().getMovingErrorsForOverload() != null) {
                refToUpdate.setMovingErrorsForOverload(settingsUpdate.getBitmexSysOverloadArgs().getMovingErrorsForOverload());
            }
            if (settingsUpdate.getBitmexSysOverloadArgs().getOverloadTimeSec() != null) {
                refToUpdate.setOverloadTimeSec(settingsUpdate.getBitmexSysOverloadArgs().getOverloadTimeSec());
            }

            settingsRepositoryService.saveSettings(settings);
        }
//        if (settingsUpdate.getOkexSysOverloadArgs() != null) {
//            final SysOverloadArgs refToUpdate = settings.getOkexSysOverloadArgs();
//            if (settingsUpdate.getOkexSysOverloadArgs().getMovingErrorsForOverload() != null) {
//                refToUpdate.setMovingErrorsForOverload(settingsUpdate.getOkexSysOverloadArgs().getMovingErrorsForOverload());
//            }
//            if (settingsUpdate.getOkexSysOverloadArgs().getOverloadTimeSec() != null) {
//                refToUpdate.setOverloadTimeSec(settingsUpdate.getOkexSysOverloadArgs().getOverloadTimeSec());
//            }
//
//            settingsRepositoryService.saveSettings(settings);
//        }

        return settings;
    }
}
