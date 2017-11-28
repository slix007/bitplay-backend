package com.bitplay.api.controller;

import com.bitplay.api.domain.ArbitrageSchemeJson;
import com.bitplay.api.domain.ResultJson;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.domain.settings.ArbScheme;
import com.bitplay.persistance.domain.settings.Settings;

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
    PersistenceService persistenceService;

    @RequestMapping(value = "/arb-scheme", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ArbitrageSchemeJson getArbScheme() {
        String result;
        try {
            result = persistenceService.getSettings().getArbScheme().toString();
        } catch (Exception e) {
            final String error = String.format("Failed to get ArbScheme %s", e.toString());
            result = error;
            logger.error(error, e);
        }

        return new ArbitrageSchemeJson(result);
    }

    @RequestMapping(value = "/arb-scheme", method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResultJson updateArbScheme(@RequestBody ArbitrageSchemeJson arbitrageSchemeJson) {
        final String schemeName = arbitrageSchemeJson.getSchemeName();
        String result;
        try {
            final Settings settings = persistenceService.getSettings();
            ArbScheme arbScheme = ArbScheme.valueOf(schemeName);
            settings.setArbScheme(arbScheme);
            persistenceService.saveSettings(settings);

            result = arbScheme.toString();

        } catch (Exception e) {
            final String error = String.format("Failed to get ArbScheme %s", e.toString());
            result = error;
            logger.error(error, e);
        }

        return new ResultJson(result, "");
    }
}
