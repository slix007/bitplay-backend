package com.bitplay.persistance;

import com.bitplay.api.domain.SettingsPresetsJson;
import com.bitplay.arbitrage.ArbitrageService;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.persistance.dao.SequenceDao;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.LastPriceDeviation;
import com.bitplay.persistance.domain.SwapParams;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.borders.BordersV2;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.bitplay.persistance.domain.settings.OkexPostOnlyArgs;
import com.bitplay.persistance.domain.settings.Settings;
import com.bitplay.persistance.domain.settings.SettingsPreset;
import com.bitplay.persistance.repository.SettingsPresetRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class SettingsPresetRepositoryService {

    public static final String SEQ = "s_settingsPreset";

    @Autowired
    private SequenceDao sequenceDao;

    @Autowired
    private SettingsPresetRepository settingsPresetRepository;

    @Autowired
    private SettingsRepositoryService settingsRepositoryService;

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private ArbitrageService arbitrageService;

    public SettingsPreset saveAsPreset(String name) {
        SettingsPreset settingsPreset = settingsPresetRepository.findFirstByName(name);
        if (settingsPreset == null) {
            settingsPreset = new SettingsPreset();
            long nextId = sequenceDao.getNextSequenceId(SEQ);
            settingsPreset.setId(nextId);
            settingsPreset.setName(name);
        }
        fillPresetWithCurrentSettings(settingsPreset, name);
        settingsRepositoryService.saveSettings(settingsPreset.getSettings()); // save currentActive preset name
        settingsPresetRepository.save(settingsPreset);

        return settingsPreset;
    }

    public boolean deletePreset(String name) {
        final List<SettingsPreset> deleted = settingsPresetRepository.deleteByName(name);
        final Settings settings = settingsRepositoryService.getSettings();
        if (name.equals(settings.getCurrentPreset())) {
            settings.setCurrentPreset("");
            settingsRepositoryService.saveSettings(settings);
        }
        return CollectionUtils.isEmpty(deleted);
    }

    public List<SettingsPreset> getAll() {
        final List<SettingsPreset> all = settingsPresetRepository.findAll();
        return all == null ? new ArrayList<>() : all;
    }

    public SettingsPresetsJson getPresets() {
        final List<SettingsPreset> all = getAll();
        final String currentPreset = settingsRepositoryService.getSettings().getCurrentPreset();

        return new SettingsPresetsJson(currentPreset, all);
    }

    @SuppressWarnings("Duplicates")
    public boolean setPreset(String name, Boolean noExceptions) {
        final SettingsPreset preset = settingsPresetRepository.findFirstByName(name);
        if (preset == null) {
            return false;
        }

        setPresetSettings(preset);
        setPresetBorderParams(preset, noExceptions);
        setPresetCorrParams(preset);
        setPresetGuiParams(preset, noExceptions);
        setPresetLastPriceDev(preset);
        setPresetSwapParams(preset);

        if (!noExceptions) {
            // there are exceptions => preset name is still 'custom'
            persistenceService.resetSettingsPreset();
        }

        return true;
    }

    private void setPresetSwapParams(SettingsPreset preset) {
        final SwapParams swapParams = persistenceService.fetchSwapParams(BitmexService.NAME);
        swapParams.setSettingsParts(preset.getSwapParams());
        persistenceService.saveSwapParams(swapParams, BitmexService.NAME);
    }

    private void setPresetLastPriceDev(SettingsPreset preset) {
        final LastPriceDeviation lastPriceDeviation = persistenceService.fetchLastPriceDeviation();
        lastPriceDeviation.setSettingsParts(preset.getLastPriceDeviation());
        persistenceService.saveLastPriceDeviation(lastPriceDeviation);
    }

    private void setPresetGuiParams(SettingsPreset preset, Boolean noExceptions) {
        final GuiParams guiParams = persistenceService.fetchGuiParams();
        guiParams.setSettingsParts(preset.getGuiParams(), noExceptions);
        persistenceService.saveGuiParams(guiParams);
        arbitrageService.setParams(guiParams);
    }

    private void setPresetCorrParams(SettingsPreset preset) {
        final CorrParams corrParams = persistenceService.fetchCorrParams();
        corrParams.setSettingsParts(preset.getCorrParams());
        persistenceService.saveCorrParams(corrParams);

    }

    private void setPresetBorderParams(SettingsPreset preset, Boolean noExceptions) {
        // default Exceptions: borders V1, sum_delta V1, borders V2, b_add_delta, ok_add_delta.
        final BorderParams p = preset.getBorderParams();
        final BorderParams b = persistenceService.fetchBorders();
        b.setActiveVersion(p.getActiveVersion());
        b.setPosMode(p.getPosMode());
        if (noExceptions) {
            b.setBordersV1(p.getBordersV1());
        }
        BordersV2 bordersV2 = b.getBordersV2();
        final BordersV2 p_b2 = p.getBordersV2();
        bordersV2.setMaxLvl(p_b2.getMaxLvl());
        bordersV2.setAutoBaseLvl(p_b2.getAutoBaseLvl());
        bordersV2.setBaseLvlCnt(p_b2.getBaseLvlCnt());
        bordersV2.setBaseLvlType(p_b2.getBaseLvlType());
        bordersV2.setStep(p_b2.getStep());
        bordersV2.setGapStep(p_b2.getGapStep());
        bordersV2.setPlm(p_b2.getPlm());
        if (noExceptions) {
            bordersV2.setBorderTableList(p_b2.getBorderTableList());
            bordersV2.setbAddDelta(p_b2.getbAddDelta());
            bordersV2.setOkAddDelta(p_b2.getOkAddDelta());
        }
        b.setBordersV2(bordersV2);
        b.setRecalcPeriodSec(p.getRecalcPeriodSec());
//        b.getDeltaMinFixPeriodSec() - excluded
        b.setBorderDelta(p.getBorderDelta());
        b.setBtmMaxDelta(p.getBtmMaxDelta());
        b.setOkMaxDelta(p.getOkMaxDelta());
        b.setOnlyOpen(p.getOnlyOpen());
        persistenceService.saveBorderParams(b);
    }

    private void setPresetSettings(SettingsPreset preset) {
        final Settings p = preset.getSettings();
        final Settings s = settingsRepositoryService.getSettings();
        s.setManageType(p.getManageType());
        s.setExtraFlags(p.getExtraFlags());
        s.setArbScheme(p.getArbSchemeRaw());
        s.setBitmexSysOverloadArgs(p.getBitmexSysOverloadArgs());
        s.setOkexSysOverloadArgs(p.getOkexSysOverloadArgs());
        s.setOkexPostOnlyArgs(p.getOkexPostOnlyArgs() != null ? p.getOkexPostOnlyArgs() : OkexPostOnlyArgs.defaults());
        s.setBitmexPlacingType(p.getBitmexPlacingTypeRaw());
        s.setOkexPlacingType(p.getOkexPlacingTypeRaw());
//        s.setBitmexPrice(); - excluded
        s.setPlacingBlocks(p.getPlacingBlocksRaw());
        s.setPosAdjustment(p.getPosAdjustmentRaw());
        s.setRestartEnabled(p.getRestartEnabled());
        s.setFeeSettings(p.getFeeSettings());
        s.setLimits(p.getLimits());
        s.setRestartSettings(p.getRestartSettings());
        s.setSignalDelayMs(p.getSignalDelayMsRaw());
//        s.setColdStorageBtc(); - excluded
//        s.setColdStorageEth(); - excluded
//        s.setEBestMin(); - excluded
        s.setUsdQuoteType(p.getUsdQuoteType());
        s.setOkexFakeTakerDev(p.getOkexFakeTakerDev());
        s.setAdjustByNtUsd(p.getAdjustByNtUsdRaw());
        s.setNtUsdMultiplicityOkex(p.getNtUsdMultiplicityOkex());
        s.setAmountTypeBitmex(p.getAmountTypeBitmex());
        s.setAmountTypeOkex(p.getAmountTypeOkex());
        s.setBitmexFokMaxDiff(p.getBitmexFokMaxDiff());
        s.setBitmexObType(p.getBitmexObType());
        s.setContractMode(p.getContractMode());
//        s.setHedgeBtc(); - excluded
//        s.setHedgeEth(); - excluded
//        s.setHedgeAuto(p.getHedgeAuto()); - excluded
        s.setTradingModeAuto(p.getTradingModeAuto());
        s.setSettingsVolatileMode(p.getSettingsVolatileMode());
        s.setTradingModeState(p.getTradingModeState());
        s.setBitmexChangeOnSo(p.getBitmexChangeOnSo());
//        s.getOkexEbestElast() - excluded
        s.setCurrentPreset(preset.getName());
        s.setDql(p.getDql());
        settingsRepositoryService.saveSettings(s);
    }

    private void fillPresetWithCurrentSettings(SettingsPreset settingsPreset, String name) {
        final Settings settings = settingsRepositoryService.getSettings();
        final BorderParams borderParams = persistenceService.fetchBorders();
        final CorrParams corrParams = persistenceService.fetchCorrParams();
        final GuiParams guiParams = persistenceService.fetchGuiParams();
        final LastPriceDeviation lastPriceDeviation = persistenceService.fetchLastPriceDeviation();
        final SwapParams swapParams = persistenceService.fetchSwapParams(BitmexService.NAME);

        // https://trello.com/c/Jc4kgUHg/669-30ap19-%D0%B8%D1%81%D0%BA%D0%BB%D1%8E%D1%87%D0%B5%D0%BD%D0%B8%D1%8F-presets-an
        //1. e_best_min
        //2. cold storage (btc), cold storage (eth);
        //3. hedge всех mod;
        //4. reserveBtc1, reserveBtc2;
        //5. bitmex_price;
        //6. delta_min period;
        //7. e_best=e_last.
//        settings.setEBestMin(null);
//        settings.setColdStorageBtc(null);
//        settings.setColdStorageEth(null);
//        settings.setHedgeBtc(null);
//        settings.setHedgeEth(null);
//        settings.setHedgeAuto(null);
//        guiParams.setReserveBtc1(null);
//        guiParams.setReserveBtc2(null);
//        settings.setBitmexPrice(null);
//        borderParams.setDeltaMinFixPeriodSec(null);
//        settings.setOkexEbestElast(null);
        // it will be saved as current-settings

        // save
        settings.setCurrentPreset(name);
        settingsPreset.setSettings(settings);
        settingsPreset.setBorderParams(borderParams);
        settingsPreset.setCorrParams(corrParams); // min/max values
        settingsPreset.setGuiParams(guiParams);
        settingsPreset.setLastPriceDeviation(lastPriceDeviation);
        settingsPreset.setSwapParams(swapParams);
    }

}
