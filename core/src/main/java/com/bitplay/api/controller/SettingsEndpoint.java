package com.bitplay.api.controller;

import com.bitplay.Config;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.settings.ContractMode;
import com.bitplay.persistance.domain.settings.Limits;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.PosAdjustment;
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
/*    @RequestMapping(value = "/reload-e-best-min",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public SumBalJson reloadSumEBestMin() {
        config.reload();
        final Settings settings = settingsRepositoryService.getSettings();
        final String sumEBestMin = settings.getEBestMin().toString();
        final String coldStorage = settings.getColdStorageBtc().toPlainString();
        final String timeToForbidden = traderPermissionsService.getTimeToForbidden();
        return new SumBalJson("", "", sumEBestMin, timeToForbidden, coldStorage);
    }
*/
    @RequestMapping(value = "/all", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public Settings getSettings() {
        Settings settings = new Settings();
        try {
            settings = settingsRepositoryService.getSettings();
            final ContractMode contractMode = ContractMode.parse(bitmexService.getFuturesContractName(),
                    okCoinService.getFuturesContractName());
            settings.setContractModeCurrent(contractMode);
            settings.setOkexContractName(settings.getContractMode().getOkexContractType().getContractName());
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
            current.setFixedBlockUsd(update.getFixedBlockUsd() != null ? update.getFixedBlockUsd() : current.getFixedBlockUsd());
            current.setDynMaxBlockUsd(update.getDynMaxBlockUsd() != null ? update.getDynMaxBlockUsd() : current.getDynMaxBlockUsd());
            settings.setPlacingBlocks(current);
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getPosAdjustment() != null) {
            final PosAdjustment current = settings.getPosAdjustment();
            final PosAdjustment update = settingsUpdate.getPosAdjustment();
            current.setPosAdjustmentMin(update.getPosAdjustmentMin() != null ? update.getPosAdjustmentMin() : current.getPosAdjustmentMin());
            current.setPosAdjustmentMax(update.getPosAdjustmentMax() != null ? update.getPosAdjustmentMax() : current.getPosAdjustmentMax());
            current.setPosAdjustmentPlacingType(
                    update.getPosAdjustmentPlacingType() != null ? update.getPosAdjustmentPlacingType() : current.getPosAdjustmentPlacingType());
            current.setPosAdjustmentDelaySec(
                    update.getPosAdjustmentDelaySec() != null ? update.getPosAdjustmentDelaySec() : current.getPosAdjustmentDelaySec());
            current.setCorrDelaySec(update.getCorrDelaySec() != null ? update.getCorrDelaySec() : current.getCorrDelaySec());
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
            RestartSettings rUpdate = settingsUpdate.getRestartSettings();
            settings.getRestartSettings().setMaxTimestampDelay(rUpdate.getMaxTimestampDelay() != null
                    ? rUpdate.getMaxTimestampDelay()
                    : settings.getRestartSettings().getMaxTimestampDelay());
            settings.getRestartSettings().setMaxBitmexReconnects(rUpdate.getMaxBitmexReconnects() != null
                    ? rUpdate.getMaxBitmexReconnects()
                    : settings.getRestartSettings().getMaxBitmexReconnects());
            settingsRepositoryService.saveSettings(settings);
        }

        if (settingsUpdate.getSignalDelayMs() != null) {
            settings.setSignalDelayMs(settingsUpdate.getSignalDelayMs());
            settingsRepositoryService.saveSettings(settings);
            arbitrageService.restartSignalDelay();
        }
        if (settingsUpdate.getUsdQuoteType() != null) {
            settings.setUsdQuoteType(settingsUpdate.getUsdQuoteType());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getContractMode() != null) {
            settings.setContractMode(settingsUpdate.getContractMode());
            settingsRepositoryService.saveSettings(settings);

            settings.setContractModeCurrent(ContractMode.parse(
                    bitmexService.getFuturesContractName(),
                    okCoinService.getFuturesContractName())
            );
            settings.setOkexContractName(settings.getContractMode().getOkexContractType().getContractName());
        }
        if (settingsUpdate.getHedgeBtc() != null) {
            settings.setHedgeBtc(settingsUpdate.getHedgeBtc());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getHedgeEth() != null) {
            settings.setHedgeEth(settingsUpdate.getHedgeEth());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getHedgeAuto() != null) {
            settings.setHedgeAuto(settingsUpdate.getHedgeAuto());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getOkexFakeTakerDev() != null) {
            settings.setOkexFakeTakerDev(settingsUpdate.getOkexFakeTakerDev());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getAdjustByNtUsd() != null) {
            settings.setAdjustByNtUsd(settingsUpdate.getAdjustByNtUsd());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getNtUsdMultiplicityOkex() != null) {
            settings.setNtUsdMultiplicityOkex(settingsUpdate.getNtUsdMultiplicityOkex());
            settingsRepositoryService.saveSettings(settings);
        }

        return settings;
    }

    @Secured("ROLE_ADMIN")
    @RequestMapping(value = "/all-admin", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Settings updateAdminSettings(@RequestBody Settings settingsUpdate) {
        final Settings settings = settingsRepositoryService.getSettings();
        if (settingsUpdate.getColdStorageBtc() != null) {
            settings.setColdStorageBtc(settingsUpdate.getColdStorageBtc());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getEBestMin() != null) {
            settings.setEBestMin(settingsUpdate.getEBestMin());
            settingsRepositoryService.saveSettings(settings);
        }
        return settings;
    }
}