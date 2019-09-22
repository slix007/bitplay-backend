package com.bitplay.api.dto;

import com.bitplay.persistance.domain.settings.SettingsPreset;
import java.util.List;
import lombok.Data;

@Data
public class SettingsPresetsJson {

    private final String currentPreset;
    private final List<SettingsPreset> settingsPresets;
}
