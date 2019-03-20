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

        if (noExceptions) {
            settingsRepositoryService.saveSettings(preset.getSettings());
            persistenceService.saveBorderParams(preset.getBorderParams());

            final CorrParams corrParams = persistenceService.fetchCorrParams();
            corrParams.setSettingsParts(preset.getCorrParams());
            persistenceService.saveCorrParams(corrParams);
            final GuiParams guiParams = persistenceService.fetchGuiParams();
            guiParams.setSettingsParts(preset.getGuiParams(), true);
            persistenceService.saveGuiParams(guiParams);
            arbitrageService.setParams(guiParams);

            final LastPriceDeviation lastPriceDeviation = persistenceService.fetchLastPriceDeviation();
            lastPriceDeviation.setSettingsParts(preset.getLastPriceDeviation());
            persistenceService.saveLastPriceDeviation(lastPriceDeviation);
            final SwapParams swapParams = persistenceService.fetchSwapParams(BitmexService.NAME);
            swapParams.setSettingsParts(preset.getSwapParams());
            persistenceService.saveSwapParams(swapParams, BitmexService.NAME);
        } else {
            // default Exceptions: borders V1, sum_delta V1, borders V2, b_add_delta, ok_add_delta.

            settingsRepositoryService.saveSettings(preset.getSettings());

            final BorderParams presetBp = preset.getBorderParams();
            final BorderParams currBp = persistenceService.fetchBorders();
            presetBp.setBordersV1(currBp.getBordersV1()); // sum_delta V1
            final BordersV2 currBpBordersV2 = currBp.getBordersV2();
            final BordersV2 presetBpBordersV2 = presetBp.getBordersV2();
            presetBpBordersV2.setBorderTableList(currBpBordersV2.getBorderTableList()); // borders V2
            presetBpBordersV2.setbAddDelta(currBpBordersV2.getbAddDelta()); // b_add_delta
            presetBpBordersV2.setOkAddDelta(currBpBordersV2.getOkAddDelta()); //ok_add_delta
            presetBp.setBordersV2(currBpBordersV2);
            persistenceService.saveBorderParams(presetBp);

            final CorrParams corrParams = persistenceService.fetchCorrParams();
            corrParams.setSettingsParts(preset.getCorrParams());
            persistenceService.saveCorrParams(corrParams);
            final GuiParams guiParams = persistenceService.fetchGuiParams();
            guiParams.setSettingsParts(preset.getGuiParams(), false); // borders V1
            persistenceService.saveGuiParams(guiParams);
            arbitrageService.setParams(guiParams);
            final LastPriceDeviation lastPriceDeviation = persistenceService.fetchLastPriceDeviation();
            lastPriceDeviation.setSettingsParts(preset.getLastPriceDeviation());
            persistenceService.saveLastPriceDeviation(lastPriceDeviation);
            final SwapParams swapParams = persistenceService.fetchSwapParams(BitmexService.NAME);
            swapParams.setSettingsParts(preset.getSwapParams());
            persistenceService.saveSwapParams(swapParams, BitmexService.NAME);

            // preset name is still 'custom'
            persistenceService.resetSettingsPreset();
        }

        return true;
    }

    private void fillPresetWithCurrentSettings(SettingsPreset settingsPreset, String name) {
        final Settings settings = settingsRepositoryService.getSettings();
        final BorderParams borderParams = persistenceService.fetchBorders();
        final CorrParams corrParams = persistenceService.fetchCorrParams();
        final GuiParams guiParams = persistenceService.fetchGuiParams();
        final LastPriceDeviation lastPriceDeviation = persistenceService.fetchLastPriceDeviation();
        final SwapParams swapParams = persistenceService.fetchSwapParams(BitmexService.NAME);

        settings.setCurrentPreset(name);
        settingsPreset.setSettings(settings);
        settingsPreset.setBorderParams(borderParams);
        settingsPreset.setCorrParams(corrParams); // min/max values
        settingsPreset.setGuiParams(guiParams);
        settingsPreset.setLastPriceDeviation(lastPriceDeviation);
        settingsPreset.setSwapParams(swapParams);
    }

}
