package com.bitplay.api.domain;

import com.bitplay.persistance.domain.settings.SettingsPreset;
import java.util.List;
import lombok.Data;

@Data
public class SettingsPresetsJson {

    private final String activeName;
    private final List<SettingsPreset> settingsPresets;
}
