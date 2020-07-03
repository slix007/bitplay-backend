package com.bitplay.api.controller;

import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.arbitrage.VolatileModeSwitcherService;
import com.bitplay.arbitrage.posdiff.PosDiffService;
import com.bitplay.external.DestinationResolverByFile;
import com.bitplay.market.MarketServicePreliq;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.market.okcoin.OkCoinService;
import com.bitplay.market.okcoin.OkexLimitsService;
import com.bitplay.persistance.PersistenceService;
import com.bitplay.persistance.SettingsRepositoryService;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.settings.AbortSignal;
import com.bitplay.persistance.domain.settings.BitmexChangeOnSo;
import com.bitplay.persistance.domain.settings.BitmexContractType;
import com.bitplay.persistance.domain.settings.BitmexContractTypeEx;
import com.bitplay.persistance.domain.settings.BitmexObType;
import com.bitplay.persistance.domain.settings.ContractMode;
import com.bitplay.persistance.domain.settings.ExtraFlag;
import com.bitplay.persistance.domain.settings.Limits;
import com.bitplay.persistance.domain.settings.ManageType;
import com.bitplay.persistance.domain.settings.OkexContractType;
import com.bitplay.persistance.domain.settings.PlacingBlocks;
import com.bitplay.persistance.domain.settings.PosAdjustment;
import com.bitplay.persistance.domain.settings.RestartSettings;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.SettingsVolatileMode;
import com.bitplay.persistance.domain.settings.SysOverloadArgs;
import com.bitplay.persistance.domain.settings.TradingMode;
import com.bitplay.settings.BitmexChangeOnSoService;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final PosDiffService posDiffService;
    private final SettingsCorrEndpoint settingsCorrEndpoint;
    private final BitmexChangeOnSoService bitmexChangeOnSoService;
    private final DestinationResolverByFile destinationResolverByFile;

    @RequestMapping(value = "/slack", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> getSlackSettings() {
        return destinationResolverByFile.getSettings();
    }

    @RequestMapping(value = "/all", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public Settings getSettings() {
        Settings settings = new Settings();
        try {
            settings = getFullSettings();

            setTransientFields(settings);

        } catch (Exception e) {
            final String error = String.format("Failed to get settings %s", e.toString());
            logger.error(error, e);
        }
        return settings;
    }

    private Settings getFullSettings() {
        final Settings settings = settingsRepositoryService.getSettings();
        setTransientFields(settings);
        return settings;
    }

    private void setTransientFields(Settings settings) {
        // Contract names

        final MarketServicePreliq left = arbitrageService.getLeftMarketService();
        final MarketServicePreliq right = arbitrageService.getRightMarketService();
        if (left == null || right == null) {
            return;
        }
        // left": "XBTUSD_Quarter",
        //"right": "ETH_ThisWeek",
        final ContractMode contractMode = new ContractMode(left.getContractType(), right.getContractType());
        settings.setContractModeCurrent(contractMode);
//        settings.setOkexContractName(settings.getContractMode().getOkexContractType().getContractName());
        settings.setOkexContractNames(OkexContractType.getNameToContractName());
        settings.setBitmexContractNames(settingsRepositoryService.getBitmexContractNames());

        // Corr
        final CorrParams corrParams = settingsCorrEndpoint.getCorrParams();
        settings.setCorrParams(corrParams);

        // BitmexChangeOnSo
        final BitmexChangeOnSo bitmexChangeOnSo = settings.getBitmexChangeOnSo();
        bitmexChangeOnSo.setSecToReset(bitmexChangeOnSoService.getSecToReset());

        // BitmexObType
        BitmexObType bitmexObTypeCurrent = settings.getBitmexObType();
        if (left.isBtm()) {
            bitmexObTypeCurrent = ((BitmexService) left).getBitmexObTypeCurrent();
        }
        settings.setBitmexObTypeCurrent(bitmexObTypeCurrent);
        boolean restartWarnBitmexCt = false;
        if (left.isBtm()) {
            final BitmexService btm = (BitmexService) left;
            final BitmexContractTypeEx ex = btm.getBitmexContractTypeEx();
            final String currSymbol = ex.getSymbol();
            final BitmexContractType bitmexContractType = ex.getBitmexContractType();
            final String inDb = settings.getBitmexContractTypes().getSymbolForType(bitmexContractType);
            restartWarnBitmexCt = !inDb.equals(currSymbol);
        }
        settings.setRestartWarnBitmexCt(restartWarnBitmexCt);

        if (!left.isBtm()) {
            BigDecimal okexLeverage = ((OkCoinService) left).getLeverage();
            settings.getSettingsTransient().setLeftOkexLeverage(okexLeverage);

        }
        BigDecimal okexLeverage = ((OkCoinService) right).getLeverage();
        settings.getSettingsTransient().setRightOkexLeverage(okexLeverage);
    }

    @RequestMapping(value = "/all", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public Settings updateSettings(@RequestBody Settings settingsUpdate) {
        boolean resetPreset = true;

        settingsRepositoryService.setInvalidated();
        Settings settings = settingsRepositoryService.getSettings();
        if (settingsUpdate.getContractMode() != null) {
            final ContractMode c = settingsUpdate.getContractMode();
            final ContractMode curr = settings.getContractMode();
            final ContractMode updated = new ContractMode(
                    c.getLeft() != null ? c.getLeft() : curr.getLeft(),
                    c.getRight() != null ? c.getRight() : curr.getRight()
            );
            settings.setContractMode(updated);
            settingsRepositoryService.saveSettings(settings);
        }

        if (settingsUpdate.getLeftPlacingType() != null) {
            settings.setLeftPlacingType(settingsUpdate.getLeftPlacingType());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getRightPlacingType() != null) {
            settings.setRightPlacingType(settingsUpdate.getRightPlacingType());
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
        if (settingsUpdate.getAllPostOnlyArgs() != null) {
            DtoHelpter.updateNotNullFieldsWithNested(settingsUpdate.getAllPostOnlyArgs(), settings.getAllPostOnlyArgs());
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
                arbitrageService.getLeftMarketService().getDtPreliq().stop();
                arbitrageService.getRightMarketService().getDtPreliq().stop();
            }
            if (update.getKillposDelaySec() != null) {
                current.setKillposDelaySec(update.getKillposDelaySec());
                settingsRepositoryService.saveSettings(settings);
                arbitrageService.getLeftMarketService().getDtKillpos().stop();
                arbitrageService.getRightMarketService().getDtKillpos().stop();
            }
            settingsRepositoryService.saveSettings(settings);
        }

        if (settingsUpdate.getRestartEnabled() != null) {
            settings.setRestartEnabled(settingsUpdate.getRestartEnabled());
            settingsRepositoryService.saveSettings(settings);
        }
        if (settingsUpdate.getFeeSettings() != null) {
            if (settingsUpdate.getFeeSettings().getLeftMakerComRate() != null) {
                settings.getFeeSettings().setLeftMakerComRate(settingsUpdate.getFeeSettings().getLeftMakerComRate());
            }
            if (settingsUpdate.getFeeSettings().getLeftTakerComRate() != null) {
                settings.getFeeSettings().setLeftTakerComRate(settingsUpdate.getFeeSettings().getLeftTakerComRate());
            }
            if (settingsUpdate.getFeeSettings().getRightMakerComRate() != null) {
                settings.getFeeSettings().setRightMakerComRate(settingsUpdate.getFeeSettings().getRightMakerComRate());
            }
            if (settingsUpdate.getFeeSettings().getRightTakerComRate() != null) {
                settings.getFeeSettings().setRightTakerComRate(settingsUpdate.getFeeSettings().getRightTakerComRate());
            }
            settingsRepositoryService.saveSettings(settings);
        }

        if (settingsUpdate.getLimits() != null) {
            final Limits l = settingsUpdate.getLimits();
            settings.getLimits().setIgnoreLimits(l.getIgnoreLimits() != null ? l.getIgnoreLimits() : settings.getLimits().getIgnoreLimits());
            settings.getLimits().setBitmexLimitPrice(l.getBitmexLimitPrice() != null ? l.getBitmexLimitPrice() : settings.getLimits().getBitmexLimitPrice());
//            settings.getLimits().setOkexLimitPrice(l.getOkexLimitPrice() != null ? l.getOkexLimitPrice() : settings.getLimits().getOkexLimitPrice());
            settingsRepositoryService.saveSettings(settings);

            final OkexLimitsService limitsService = (OkexLimitsService) arbitrageService.getRightMarketService().getLimitsService();
            if (settingsUpdate.getLimits().getOkexMinPriceForTest() != null) {
                limitsService.setMinPriceForTest(settingsUpdate.getLimits().getOkexMinPriceForTest());
            }
            if (settingsUpdate.getLimits().getOkexMaxPriceForTest() != null) {
                limitsService.setMaxPriceForTest(settingsUpdate.getLimits().getOkexMaxPriceForTest());
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
        if (settingsUpdate.getImplied() != null) {
            DtoHelpter.updateNotNullFieldsWithNested(settingsUpdate.getImplied(), settings.getImplied());
            settingsRepositoryService.saveSettings(settings);
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
        if (settingsUpdate.getAllFtpd() != null) {
            DtoHelpter.updateNotNullFieldsWithNested(settingsUpdate.getAllFtpd(), settings.getAllFtpd());
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
        if (settingsUpdate.getNtUsdMultiplicityOkexLeft() != null) {
            settings.setNtUsdMultiplicityOkexLeft(settingsUpdate.getNtUsdMultiplicityOkexLeft());
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
        if (settingsUpdate.getBitmexContractTypes() != null) {
            DtoHelpter.updateNotNullFieldsWithNested(settingsUpdate.getBitmexContractTypes(), settings.getBitmexContractTypes());
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
        if (settingsUpdate.getDql() != null) {
            DtoHelpter.updateNotNullFieldsWithNested(settingsUpdate.getDql(), settings.getDql());
            settingsRepositoryService.saveSettings(settings);
        }

        if (settingsUpdate.getPreSignalObReFetch() != null) {
            settings.setPreSignalObReFetch(settingsUpdate.getPreSignalObReFetch());
            settingsRepositoryService.saveSettings(settings);
        }

        if (settingsUpdate.getOkexSettlement() != null) {
            updateOkexSettlement(settingsUpdate, settings);
        }

        if (settingsUpdate.getConBoPortionsRaw() != null) {
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
        if (settingsUpdate.getConBoPortionsRaw().getMinNtUsdToStartOkex() != null) {
            settings.getConBoPortionsRaw().setMinNtUsdToStartOkex(settingsUpdate.getConBoPortionsRaw().getMinNtUsdToStartOkex());
        }
        if (settingsUpdate.getConBoPortionsRaw().getMaxPortionUsdOkex() != null) {
            settings.getConBoPortionsRaw().setMaxPortionUsdOkex(settingsUpdate.getConBoPortionsRaw().getMaxPortionUsdOkex());
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


        if (settingsUpdate.getLeftPlacingType() != null) {
            settings.setLeftPlacingType(settingsUpdate.getLeftPlacingType());
            settingsRepositoryService.saveSettings(mainSettings);
        }
        if (settingsUpdate.getRightPlacingType() != null) {
            settings.setRightPlacingType(settingsUpdate.getRightPlacingType());
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
                arbitrageService.getLeftMarketService().getDtPreliq().stop();
                arbitrageService.getRightMarketService().getDtPreliq().stop();
            }
            if (update.getKillposDelaySec() != null) {
                current.setKillposDelaySec(update.getKillposDelaySec());
                settingsRepositoryService.saveSettings(mainSettings);
                arbitrageService.getLeftMarketService().getDtKillpos().stop();
                arbitrageService.getRightMarketService().getDtKillpos().stop();
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
        if (settingsUpdate.getConBoPortions() != null) {
            if (settingsUpdate.getConBoPortions().getMinNtUsdToStartOkex() != null) {
                settings.getConBoPortions().setMinNtUsdToStartOkex(settingsUpdate.getConBoPortions().getMinNtUsdToStartOkex());
            }
            if (settingsUpdate.getConBoPortions().getMaxPortionUsdOkex() != null) {
                settings.getConBoPortions().setMaxPortionUsdOkex(settingsUpdate.getConBoPortions().getMaxPortionUsdOkex());
            }
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
        final Settings settings = getFullSettings();
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
        final Settings settings = getFullSettings();
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
