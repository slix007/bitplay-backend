package com.bitplay.api.controller;

import com.bitplay.Config;
import com.bitplay.api.domain.SumBalJson;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.Limits;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.RestartSettings;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.SysOverloadArgs;
import com.bitplay.security.TraderPermissionsService;
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
 * Created by Sergey Shurmin on 11/27/17.
 */
@Secured("ROLE_TRADER")
@RestController
@RequestMapping("/settings")
public class SettingsEndpoint {

    private final static Logger logger = LoggerFactory.getLogger(SettingsEndpoint.class);

    @Autowired
    private Config config;

    @Autowired
    SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private ArbitrageService arbitrageService;

    @Autowired
    private OkCoinService okCoinService;

    @Autowired
    private BitmexService bitmexService;

    @Autowired
    private TraderPermissionsService traderPermissionsService;

    /**
     * The only method that works without @PreAuthorize("hasPermission(null, 'e_best_min-check')")
     */
    @RequestMapping(value = "/reload-e-best-min",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public SumBalJson reloadSumEBestMin() {
        config.reload();
        final String sumEBestMin = config.getEBestMin().toString();
        final String coldStorage = config.getColdStorage().toPlainString();
        final String timeToForbidden = traderPermissionsService.getTimeToForbidden();
        return new SumBalJson("", "", sumEBestMin, timeToForbidden, coldStorage);
    }

    @RequestMapping(value = "/all", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public Settings getSettings() {
        Settings settings = new Settings();
        try {
            settings = settingsRepositoryService.getSettings();
            settings.setBitmexContractTypeCurrent(bitmexService.getFuturesContractName());
            settings.setOkexContractTypeCurrent(okCoinService.getFuturesContractName());
            settings.setOkexContractName(settings.getOkexContractType().getContractName());
        } catch (Exception e) {
            final String error = String.format("Failed to get settings %s", e.toString());
            logger.error(error, e);
        }
        return settings;
    }

    @RequestMapping(value = "/all", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public Settings updateSettings(@RequestBody Settings settingsUpdate) {
        final Settings settings = settingsRepositoryService.getSettings();
        if (settingsUpdate.getBitmexPlacingType() != null) {
            settings.setBitmexPlacingType(settingsUpdate.getBitmexPlacingType());
            settingsRepositoryService.saveSettings(settings);
        }
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
        if (settingsUpdate.getOkexSysOverloadArgs() != null) {
            final SysOverloadArgs refToUpdate = settings.getOkexSysOverloadArgs();
            if (settingsUpdate.getOkexSysOverloadArgs().getPlaceAttempts() != null) {
                refToUpdate.setPlaceAttempts(settingsUpdate.getOkexSysOverloadArgs().getPlaceAttempts());
            }
            if (settingsUpdate.getOkexSysOverloadArgs().getMovingErrorsForOverload() != null) {
                refToUpdate.setMovingErrorsForOverload(settingsUpdate.getOkexSysOverloadArgs().getMovingErrorsForOverload());
            }
            if (settingsUpdate.getOkexSysOverloadArgs().getOverloadTimeSec() != null) {
                refToUpdate.setOverloadTimeSec(settingsUpdate.getOkexSysOverloadArgs().getOverloadTimeSec());
            }

            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getBitmexPrice() != null) {
            settings.setBitmexPrice(settingsUpdate.getBitmexPrice());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getPlacingBlocks() != null) {
            final PlacingBlocks current = settings.getPlacingBlocks();
            final PlacingBlocks update = settingsUpdate.getPlacingBlocks();
            current.setActiveVersion(update.getActiveVersion() != null ? update.getActiveVersion() : current.getActiveVersion());
            current.setFixedBlockOkex(update.getFixedBlockOkex() != null ? update.getFixedBlockOkex() : current.getFixedBlockOkex());
            current.setDynMaxBlockOkex(update.getDynMaxBlockOkex() != null ? update.getDynMaxBlockOkex() : current.getDynMaxBlockOkex());
            settings.setPlacingBlocks(current);
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getRestartEnabled() != null) {
            settings.setRestartEnabled(settingsUpdate.getRestartEnabled());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getFeeSettings() != null) {
            if (settingsUpdate.getFeeSettings().getbMakerComRate() != null) {
                settings.getFeeSettings().setbMakerComRate(settingsUpdate.getFeeSettings().getbMakerComRate());
            }
            if (settingsUpdate.getFeeSettings().getbTakerComRate() != null) {
                settings.getFeeSettings().setbTakerComRate(settingsUpdate.getFeeSettings().getbTakerComRate());
            }
            if (settingsUpdate.getFeeSettings().getoMakerComRate() != null) {
                settings.getFeeSettings().setoMakerComRate(settingsUpdate.getFeeSettings().getoMakerComRate());
            }
            if (settingsUpdate.getFeeSettings().getoTakerComRate() != null) {
                settings.getFeeSettings().setoTakerComRate(settingsUpdate.getFeeSettings().getoTakerComRate());
            }
            settingsRepositoryService.saveSettings(settings);
        }

        if (settingsUpdate.getLimits() != null) {
            final Limits l = settingsUpdate.getLimits();
            settings.getLimits().setIgnoreLimits(l.getIgnoreLimits() != null ? l.getIgnoreLimits() : settings.getLimits().getIgnoreLimits());
            settings.getLimits().setBitmexLimitPrice(l.getBitmexLimitPrice() != null ? l.getBitmexLimitPrice() : settings.getLimits().getBitmexLimitPrice());
            settings.getLimits().setOkexLimitPrice(l.getOkexLimitPrice() != null ? l.getOkexLimitPrice() : settings.getLimits().getOkexLimitPrice());
            settingsRepositoryService.saveSettings(settings);
        }

        if (settingsUpdate.getRestartSettings() != null) {
            RestartSettings restartSettings = settingsUpdate.getRestartSettings();
            settings.getRestartSettings().setMaxTimestampDelay(
                    restartSettings != null ? restartSettings.getMaxTimestampDelay() : settings.getRestartSettings().getMaxTimestampDelay());
            settingsRepositoryService.saveSettings(settings);
        }

        if (settingsUpdate.getSignalDelayMs() != null) {
            settings.setSignalDelayMs(settingsUpdate.getSignalDelayMs());
            settingsRepositoryService.saveSettings(settings);
            arbitrageService.restartSignalDelay();
        }
//        if (settingsUpdate.getColdStorageBtc() != null) {
//            settings.setColdStorageBtc(settingsUpdate.getColdStorageBtc());
//            settingsRepositoryService.saveSettings(settings);
//        }
        if (settingsUpdate.getUsdQuoteType() != null) {
            settings.setUsdQuoteType(settingsUpdate.getUsdQuoteType());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getOkexContractType() != null) {
            settings.setOkexContractType(settingsUpdate.getOkexContractType());
            settingsRepositoryService.saveSettings(settings);

            settings.setOkexContractTypeCurrent(okCoinService.getFuturesContractName());
            settings.setOkexContractName(settings.getOkexContractType().getContractName());
        }
        if (settingsUpdate.getBitmexContractType() != null) {
            settings.setBitmexContractType(settingsUpdate.getBitmexContractType());
            settingsRepositoryService.saveSettings(settings);

            settings.setBitmexContractTypeCurrent(bitmexService.getFuturesContractName());
        }
        return settings;
    }
}
