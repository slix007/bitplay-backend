package com.bitplay.persistance;

import com.bitplay.api.domain.SettingsPresetsJson;
import com.bitplay.persistance.dao.SequenceDao;
import com.bitplay.persistance.domain.borders.BorderParams;
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

        return true;
    }

    private void fillPresetWithCurrentSettings(SettingsPreset settingsPreset) {
        final Settings settings = settingsRepositoryService.getSettings();
        final BorderParams borderParams = persistenceService.fetchBorders();

        settingsPreset.setSettings(settings);
        settingsPreset.setBorderParams(borderParams);

        // TODO
//        private CorrParams corrParams; // min/max values
//        private GuiParams guiParams;
//        private GuiLiqParams guiLiqParams;
//        private LastPriceDeviation lastPriceDeviation;
//        private SwapParams swapParams;

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
