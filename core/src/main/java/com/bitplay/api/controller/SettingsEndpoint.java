package com.bitplay.api.controller;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.VolatileModeSwitcherService;
import com.bitplay.arbitrage.posdiff.PosDiffService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.market.okcoin.OkexLimitsService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.settings.AbortSignal;
import com.bitplay.persistance.domain.settings.BitmexChangeOnSo;
import com.bitplay.persistance.domain.settings.ContractMode;
import com.bitplay.persistance.domain.settings.ExtraFlag;
import com.bitplay.persistance.domain.settings.Limits;
import com.bitplay.persistance.domain.settings.ManageType;
import com.bitplay.persistance.domain.settings.OkexPostOnlyArgs;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.PosAdjustment;
import com.bitplay.persistance.domain.settings.RestartSettings;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.SettingsVolatileMode;
import com.bitplay.persistance.domain.settings.SysOverloadArgs;
import com.bitplay.persistance.domain.settings.TradingMode;
import com.bitplay.settings.BitmexChangeOnSoService;
import lombok.AllArgsConstructor;
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

import java.util.EnumSet;

/**
 * Created by Sergey Shurmin on 11/27/17.
 */
@AllArgsConstructor
@Secured("ROLE_TRADER")
@RestController
@RequestMapping("/settings")
public class SettingsEndpoint {

    private final static Logger logger = LoggerFactory.getLogger(SettingsEndpoint.class);
    private static final Logger warningLogger = LoggerFactory.getLogger("WARNING_LOG");

    private final VolatileModeSwitcherService volatileModeSwitcherService;
    private final PersistenceService persistenceService;
    private final SettingsRepositoryService settingsRepositoryService;
    private final ArbitrageService arbitrageService;
    private final OkCoinService okCoinService;
    private final BitmexService bitmexService;
    private final PosDiffService posDiffService;
    private final SettingsCorrEndpoint settingsCorrEndpoint;
    private final BitmexChangeOnSoService bitmexChangeOnSoService;
    private final OkexLimitsService okexLimitsService;

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

            setTransientFields(settings);

        } catch (Exception e) {
            final String error = String.format("Failed to get settings %s", e.toString());
            logger.error(error, e);
        }
        return settings;
    }

    private void setTransientFields(Settings settings) {
        // Contract names
        final ContractMode contractMode = ContractMode.parse(bitmexService.getFuturesContractName(), okCoinService.getFuturesContractName());
        settings.setContractModeCurrent(contractMode);
        settings.setOkexContractName(settings.getContractMode().getOkexContractType().getContractName());

        // Corr
        final CorrParams corrParams = settingsCorrEndpoint.getCorrParams();
        settings.setCorrParams(corrParams);

        // BitmexChangeOnSo
        final BitmexChangeOnSo bitmexChangeOnSo = settings.getBitmexChangeOnSo();
        bitmexChangeOnSo.setSecToReset(bitmexChangeOnSoService.getSecToReset());

        // BitmexObType
        settings.setBitmexObTypeCurrent(bitmexService.getBitmexObTypeCurrent());

        settings.getSettingsTransient().setOkexLeverage(okCoinService.getLeverage());
    }

    @RequestMapping(value = "/all", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public Settings updateSettings(@RequestBody Settings settingsUpdate) {
        boolean resetPreset = true;

        settingsRepositoryService.setInvalidated();
        Settings settings = settingsRepositoryService.getSettings();
        if (settingsUpdate.getBitmexPlacingType() != null) {
            settings.setBitmexPlacingType(settingsUpdate.getBitmexPlacingType());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getOkexPlacingType() != null) {
            settings.setOkexPlacingType(settingsUpdate.getOkexPlacingType());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getManageType() != null) {
            final ManageType manageType = settingsUpdate.getManageType();
            settings.setManageType(manageType);
            if (manageType == ManageType.AUTO) {
                settings.getExtraFlags().remove(ExtraFlag.STOP_MOVING);
            } else if (manageType == ManageType.MANUAL) {
                settings.getExtraFlags().add(ExtraFlag.STOP_MOVING);
            }
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getArbScheme() != null) {
            settings.setArbScheme(settingsUpdate.getArbScheme());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getAbortSignal() != null) {
            final AbortSignal as = settingsUpdate.getAbortSignal();
            if (as.getAbortSignalPtsEnabled() != null) {
                settings.getAbortSignal().setAbortSignalPtsEnabled(as.getAbortSignalPtsEnabled());
            }
            if (as.getAbortSignalPts() != null) {
                settings.getAbortSignal().setAbortSignalPts(as.getAbortSignalPts());
            }
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
            if (settingsUpdate.getBitmexSysOverloadArgs().getOverloadTimeMs() != null) {
                refToUpdate.setOverloadTimeMs(settingsUpdate.getBitmexSysOverloadArgs().getOverloadTimeMs());
            }
            if (settingsUpdate.getBitmexSysOverloadArgs().getBetweenAttemptsMs() != null) {
                refToUpdate.setBetweenAttemptsMs(settingsUpdate.getBitmexSysOverloadArgs().getBetweenAttemptsMs());
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
            if (settingsUpdate.getOkexSysOverloadArgs().getOverloadTimeMs() != null) {
                refToUpdate.setOverloadTimeMs(settingsUpdate.getOkexSysOverloadArgs().getOverloadTimeMs());
            }

            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getOkexPostOnlyArgs() != null) {
            final OkexPostOnlyArgs ref = settings.getOkexPostOnlyArgs();
            final OkexPostOnlyArgs uRef = settingsUpdate.getOkexPostOnlyArgs();
            if (uRef.getPostOnlyEnabled() != null) {
                ref.setPostOnlyEnabled(uRef.getPostOnlyEnabled());
            }
            if (uRef.getPostOnlyWithoutLast() != null) {
                ref.setPostOnlyWithoutLast(uRef.getPostOnlyWithoutLast());
            }
            if (uRef.getPostOnlyAttempts() != null) {
                ref.setPostOnlyAttempts(uRef.getPostOnlyAttempts());
            }
            if (uRef.getPostOnlyBetweenAttemptsMs() != null) {
                ref.setPostOnlyBetweenAttemptsMs(uRef.getPostOnlyBetweenAttemptsMs());
            }
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getBitmexPrice() != null) {
            settings.setBitmexPrice(settingsUpdate.getBitmexPrice());
            settingsRepositoryService.saveSettings(settings);
            resetPreset = false;
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
            if (update.getPosAdjustmentDelaySec() != null) {
                current.setPosAdjustmentDelaySec(update.getPosAdjustmentDelaySec());
                settingsRepositoryService.saveSettings(settings);
                posDiffService.stopTimer("adj");
            }
            if (update.getCorrDelaySec() != null) {
                current.setCorrDelaySec(update.getCorrDelaySec());
                settingsRepositoryService.saveSettings(settings);
                posDiffService.stopTimer("corr");
            }
            if (update.getPreliqDelaySec() != null) {
                current.setPreliqDelaySec(update.getPreliqDelaySec());
                settingsRepositoryService.saveSettings(settings);
                bitmexService.getDtPreliq().stop();
                okCoinService.getDtPreliq().stop();
            }
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
//            settings.getLimits().setOkexLimitPrice(l.getOkexLimitPrice() != null ? l.getOkexLimitPrice() : settings.getLimits().getOkexLimitPrice());
            settingsRepositoryService.saveSettings(settings);

            if (settingsUpdate.getLimits().getOkexMinPriceForTest() != null) {
                okexLimitsService.setMinPriceForTest(settingsUpdate.getLimits().getOkexMinPriceForTest());
            }
            if (settingsUpdate.getLimits().getOkexMaxPriceForTest() != null) {
                okexLimitsService.setMaxPriceForTest(settingsUpdate.getLimits().getOkexMaxPriceForTest());
            }
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
            resetPreset = false;
        }
        if (settingsUpdate.getHedgeEth() != null) {
            settings.setHedgeEth(settingsUpdate.getHedgeEth());
            settingsRepositoryService.saveSettings(settings);
            resetPreset = false;
        }
        if (settingsUpdate.getHedgeAuto() != null) {
            settings.setHedgeAuto(settingsUpdate.getHedgeAuto());
            settingsRepositoryService.saveSettings(settings);
            resetPreset = false;
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
        if (settingsUpdate.getBitmexFokMaxDiff() != null) {
            settings.setBitmexFokMaxDiff(settingsUpdate.getBitmexFokMaxDiff());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getBitmexFokMaxDiffAuto() != null) {
            settings.setBitmexFokMaxDiffAuto(settingsUpdate.getBitmexFokMaxDiffAuto());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getBitmexFokTotalDiff() != null) {
            settings.setBitmexFokTotalDiff(settingsUpdate.getBitmexFokTotalDiff());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getBitmexObType() != null) {
            settings.setBitmexObType(settingsUpdate.getBitmexObType());
            settingsRepositoryService.saveSettings(settings);
        }

        // TradingMode.VOLATILE
        if (settingsUpdate.getTradingModeAuto() != null) {
            settings.setTradingModeAuto(settingsUpdate.getTradingModeAuto());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getTradingModeState() != null && settingsUpdate.getTradingModeState().getTradingMode() != null) {
            warningLogger.info("Set TradingMode." + settingsUpdate.getTradingModeState().getTradingMode() + " by manual");
            if (settingsUpdate.getTradingModeState().getTradingMode() == TradingMode.VOLATILE) {
                volatileModeSwitcherService.activateVolatileMode();
                settings = settingsRepositoryService.getSettings();
            } else {
                settings = settingsRepositoryService.updateTradingModeState(settingsUpdate.getTradingModeState().getTradingMode());
            }

        }
        if (settingsUpdate.getSettingsVolatileMode() != null) {
            final SettingsVolatileMode settingsVolatileMode = settings.getSettingsVolatileMode() != null
                    ? settings.getSettingsVolatileMode() : new SettingsVolatileMode();
            settings.setSettingsVolatileMode(settingsVolatileMode);
            settings = updateVolatileMode(settingsUpdate.getSettingsVolatileMode(), settingsVolatileMode, settings);
        }
        if (settingsUpdate.getBitmexChangeOnSo() != null) {
            updateBitmexChangeOnSo(settingsUpdate.getBitmexChangeOnSo(), settings);
        }

        if (settingsUpdate.getOkexEbestElast() != null) {
            settings.setOkexEbestElast(settingsUpdate.getOkexEbestElast());
            settingsRepositoryService.saveSettings(settings);
            resetPreset = false;
        }
        if (settingsUpdate.getDql() != null && settingsUpdate.getDql().getDqlLevel() != null) {
            settings.getDql().setDqlLevel(settingsUpdate.getDql().getDqlLevel());
            settingsRepositoryService.saveSettings(settings);
        }

        if (settingsUpdate.getPreSignalObReFetch() != null) {
            settings.setPreSignalObReFetch(settingsUpdate.getPreSignalObReFetch());
            settingsRepositoryService.saveSettings(settings);
        }

        if (settingsUpdate.getOkexSettlement() != null) {
            updateOkexSettlement(settingsUpdate, settings);
        }

        if (settingsUpdate.getConBoPortions() != null) {
            updateConBoPortions(settingsUpdate, settings);
        }

        if (resetPreset) {
            persistenceService.resetSettingsPreset();
        }

        final CorrParams corrParams = settingsCorrEndpoint.getCorrParams();
        settings.setCorrParams(corrParams);

        setTransientFields(settings);

        return settings;
    }

    private void updateOkexSettlement(@RequestBody Settings settingsUpdate, Settings settings) {
        if (settingsUpdate.getOkexSettlement().getActive() != null) {
            settings.getOkexSettlement().setActive(settingsUpdate.getOkexSettlement().getActive());
        }
        if (settingsUpdate.getOkexSettlement().getStartAtTimeStr() != null) {
            settings.getOkexSettlement().setStartAtTime(settingsUpdate.getOkexSettlement().getStartAtTimeStr());
        }
        if (settingsUpdate.getOkexSettlement().getPeriod() != null) {
            settings.getOkexSettlement().setPeriod(settingsUpdate.getOkexSettlement().getPeriod());
        }
        settingsRepositoryService.saveSettings(settings);
    }

    private void updateConBoPortions(Settings settingsUpdate, Settings settings) {
        if (settingsUpdate.getConBoPortions().getMinNtUsdToStartOkex() != null) {
            settings.getConBoPortions().setMinNtUsdToStartOkex(settingsUpdate.getConBoPortions().getMinNtUsdToStartOkex());
        }
        if (settingsUpdate.getConBoPortions().getMaxPortionUsdOkex() != null) {
            settings.getConBoPortions().setMaxPortionUsdOkex(settingsUpdate.getConBoPortions().getMaxPortionUsdOkex());
        }
        settingsRepositoryService.saveSettings(settings);
    }

    private void updateBitmexChangeOnSo(BitmexChangeOnSo update, Settings mainSettings) {
        // set new if
        mainSettings.setBitmexChangeOnSo(mainSettings.getBitmexChangeOnSo() != null ? mainSettings.getBitmexChangeOnSo() : new BitmexChangeOnSo());
        final BitmexChangeOnSo current = mainSettings.getBitmexChangeOnSo();

        if (update.getToConBo() != null) {
            current.setToConBo(update.getToConBo());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (update.getAdjToTaker() != null) {
            current.setAdjToTaker(update.getAdjToTaker());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (update.getSignalPlacingType() != null) {
            current.setSignalPlacingType(update.getSignalPlacingType());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (update.getSignalTo() != null) {
            current.setSignalTo(update.getSignalTo());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (update.getCountToActivate() != null) {
            current.setCountToActivate(update.getCountToActivate());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (update.getDurationSec() != null) {
            current.setDurationSec(update.getDurationSec());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (update.getResetFromUi() != null) {
            bitmexChangeOnSoService.reset();
        }
        if (update.getTestingSo() != null) {
            bitmexChangeOnSoService.setTestingSo(update.getTestingSo());
            current.setTestingSo(update.getTestingSo());
        }
    }

    @SuppressWarnings("Duplicates")
    private Settings updateVolatileMode(SettingsVolatileMode settingsUpdate, SettingsVolatileMode settings, Settings mainSettings) {
        if (settingsUpdate.getFieldToRemove() != null) {
            settings.getActiveFields().remove(settingsUpdate.getFieldToRemove());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (settingsUpdate.getFieldToAdd() != null) {
            settings.getActiveFields().add(settingsUpdate.getFieldToAdd());
            settingsRepositoryService.saveSettings(mainSettings);
        }


        if (settingsUpdate.getBitmexPlacingType() != null) {
            settings.setBitmexPlacingType(settingsUpdate.getBitmexPlacingType());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (settingsUpdate.getOkexPlacingType() != null) {
            settings.setOkexPlacingType(settingsUpdate.getOkexPlacingType());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (settingsUpdate.getSignalDelayMs() != null) {
            settings.setSignalDelayMs(settingsUpdate.getSignalDelayMs());
            settingsRepositoryService.saveSettings(mainSettings);
            arbitrageService.restartSignalDelay();
        }
        if (settingsUpdate.getPlacingBlocks() != null) {
            final PlacingBlocks current = settings.getPlacingBlocks();
            final PlacingBlocks update = settingsUpdate.getPlacingBlocks();
            current.setActiveVersion(update.getActiveVersion() != null ? update.getActiveVersion() : current.getActiveVersion());
            current.setFixedBlockUsd(update.getFixedBlockUsd() != null ? update.getFixedBlockUsd() : current.getFixedBlockUsd());
            current.setDynMaxBlockUsd(update.getDynMaxBlockUsd() != null ? update.getDynMaxBlockUsd() : current.getDynMaxBlockUsd());
            settings.setPlacingBlocks(current);
            settingsRepositoryService.saveSettings(mainSettings);
        }

        if (settingsUpdate.getPosAdjustment() != null) {
            final PosAdjustment current = settings.getPosAdjustment();
            final PosAdjustment update = settingsUpdate.getPosAdjustment();
            current.setPosAdjustmentMin(update.getPosAdjustmentMin() != null ? update.getPosAdjustmentMin() : current.getPosAdjustmentMin());
            current.setPosAdjustmentMax(update.getPosAdjustmentMax() != null ? update.getPosAdjustmentMax() : current.getPosAdjustmentMax());
            current.setPosAdjustmentPlacingType(
                    update.getPosAdjustmentPlacingType() != null ? update.getPosAdjustmentPlacingType() : current.getPosAdjustmentPlacingType());
            if (update.getPosAdjustmentDelaySec() != null) {
                current.setPosAdjustmentDelaySec(update.getPosAdjustmentDelaySec());
                settingsRepositoryService.saveSettings(mainSettings);
                posDiffService.stopTimer("adj");
            }
            if (update.getCorrDelaySec() != null) {
                current.setCorrDelaySec(update.getCorrDelaySec());
                settingsRepositoryService.saveSettings(mainSettings);
                posDiffService.stopTimer("corr");
            }
            if (update.getPreliqDelaySec() != null) {
                current.setPreliqDelaySec(update.getPreliqDelaySec());
                settingsRepositoryService.saveSettings(mainSettings);
                bitmexService.getDtPreliq().stop();
                okCoinService.getDtPreliq().stop();
            }
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (settingsUpdate.getAdjustByNtUsd() != null) {
            settings.setAdjustByNtUsd(settingsUpdate.getAdjustByNtUsd());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (settingsUpdate.getBAddBorder() != null) {
            settings.setBAddBorder(settingsUpdate.getBAddBorder());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (settingsUpdate.getOAddBorder() != null) {
            settings.setOAddBorder(settingsUpdate.getOAddBorder());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (settingsUpdate.getVolatileDurationSec() != null) {
            mainSettings = settingsRepositoryService.updateVolatileDurationSec(settingsUpdate.getVolatileDurationSec());
        }
        if (settingsUpdate.getVolatileDelayMs() != null) {
            volatileModeSwitcherService.restartVolatileDelay(settings.getVolatileDelayMs(), settingsUpdate.getVolatileDelayMs());
            settings.setVolatileDelayMs(settingsUpdate.getVolatileDelayMs());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (settingsUpdate.getBorderCrossDepth() != null) {
            settings.setBorderCrossDepth(settingsUpdate.getBorderCrossDepth());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (settingsUpdate.getCorrMaxTotalCount() != null) {
            settings.setCorrMaxTotalCount(settingsUpdate.getCorrMaxTotalCount());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (settingsUpdate.getAdjMaxTotalCount() != null) {
            settings.setAdjMaxTotalCount(settingsUpdate.getAdjMaxTotalCount());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (settingsUpdate.getArbScheme() != null) {
            settings.setArbScheme(settingsUpdate.getArbScheme());
            settingsRepositoryService.saveSettings(mainSettings);
        }

        //    private BigDecimal bAddBorder;
        //    private BigDecimal oAddBorder;
        //volatileDurationSec
        return mainSettings;
    }

    @RequestMapping(value = "/toggle-stop-moving",
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public Settings toggleMovingStop() {
        final Settings settings = settingsRepositoryService.getSettings();
        final EnumSet<ExtraFlag> extraFlags = settings.getExtraFlags();
        if (extraFlags.contains(ExtraFlag.STOP_MOVING)) {
            extraFlags.remove(ExtraFlag.STOP_MOVING);
        } else {
            extraFlags.add(ExtraFlag.STOP_MOVING);
        }
        settingsRepositoryService.saveSettings(settings);
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
        if (settingsUpdate.getColdStorageEth() != null) {
            settings.setColdStorageEth(settingsUpdate.getColdStorageEth());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getEBestMin() != null) {
            settings.setEBestMin(settingsUpdate.getEBestMin());
            settingsRepositoryService.saveSettings(settings);
        }

        return settings;
    }
}
