package com.bitplay.persistance;

import com.bitplay.api.domain.SettingsPresetsJson;
import com.bitplay.market.bitmex.BitmexService;
import com.bitplay.persistance.dao.SequenceDao;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.LastPriceDeviation;
import com.bitplay.persistance.domain.SwapParams;
import com.bitplay.persistance.domain.borders.BorderParams;
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

    public SettingsPreset saveAsPreset(String name) {
        SettingsPreset settingsPreset = settingsPresetRepository.findFirstByName(name);
        if (settingsPreset == null) {
            settingsPreset = new SettingsPreset();
            long nextId = sequenceDao.getNextSequenceId(SEQ);
            settingsPreset.setId(nextId);
            settingsPreset.setName(name);
        }
        fillPresetWithCurrentSettings(settingsPreset);
        settingsPresetRepository.save(settingsPreset);

        return settingsPreset;
    }

    public boolean deletePreset(String name) {
        final List<SettingsPreset> deleted = settingsPresetRepository.deleteByName(name);
        return CollectionUtils.isEmpty(deleted);
    }

    public List<SettingsPreset> getAll() {
        final List<SettingsPreset> all = settingsPresetRepository.findAll();
        return all == null ? new ArrayList<>() : all;
    }

    public SettingsPresetsJson getPresets() {
        final List<SettingsPreset> all = getAll();

        return new SettingsPresetsJson("", all);
    }

    public boolean setPreset(String name) {
        final SettingsPreset preset = settingsPresetRepository.findFirstByName(name);
        if (preset == null) {
            return false;
        }
        settingsRepositoryService.saveSettings(preset.getSettings());
        persistenceService.saveBorderParams(preset.getBorderParams());

        final CorrParams corrParams = persistenceService.fetchCorrParams();
        corrParams.setSettingsParts(preset.getCorrParams());
        persistenceService.saveCorrParams(corrParams);
        final GuiParams guiParams = persistenceService.fetchGuiParams();
        guiParams.setSettingsParts(preset.getGuiParams());
        persistenceService.saveGuiParams(guiParams);
        final LastPriceDeviation lastPriceDeviation = persistenceService.fetchLastPriceDeviation();
        lastPriceDeviation.setSettingsParts(preset.getLastPriceDeviation());
        persistenceService.saveLastPriceDeviation(lastPriceDeviation);
        final SwapParams swapParams = persistenceService.fetchSwapParams(BitmexService.NAME);
        swapParams.setSettingsParts(preset.getSwapParams());
        persistenceService.saveSwapParams(swapParams, BitmexService.NAME);

        return true;
    }

    private void fillPresetWithCurrentSettings(SettingsPreset settingsPreset) {
        final Settings settings = settingsRepositoryService.getSettings();
        final BorderParams borderParams = persistenceService.fetchBorders();
        final CorrParams corrParams = persistenceService.fetchCorrParams();
        final GuiParams guiParams = persistenceService.fetchGuiParams();
        final LastPriceDeviation lastPriceDeviation = persistenceService.fetchLastPriceDeviation();
        final SwapParams swapParams = persistenceService.fetchSwapParams(BitmexService.NAME);

        settingsPreset.setSettings(settings);
        settingsPreset.setBorderParams(borderParams);
        settingsPreset.setCorrParams(corrParams); // min/max values
        settingsPreset.setGuiParams(guiParams);
        settingsPreset.setLastPriceDeviation(lastPriceDeviation);
        settingsPreset.setSwapParams(swapParams);
    }

    private String defineCurrentName(List<SettingsPreset> list) {
        final Settings settings = settingsRepositoryService.getSettings();
        final BorderParams borderParams = persistenceService.fetchBorders();

        for (SettingsPreset settingsPreset : list) {
            //TODO
//            settings.get


        }
        return "";
    }
}
