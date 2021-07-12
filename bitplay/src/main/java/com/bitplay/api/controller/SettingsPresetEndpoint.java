package com.bitplay.api.controller;

import com.bitplay.api.dto.ChangePresetJson;
import com.bitplay.api.dto.ResultJson;
import com.bitplay.api.dto.SettingsPresetsJson;
import com.bitplay.persistance.SettingsPresetRepositoryService;
import com.bitplay.persistance.domain.settings.SettingsPreset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Secured("ROLE_TRADER")
@RestController
@RequestMapping("/settings")
@Slf4j
public class SettingsPresetEndpoint {

    @Autowired
    private SettingsPresetRepositoryService settingsPresetRepositoryService;

    @RequestMapping(value = "/preset-all", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public SettingsPresetsJson getAllPresets() {
        return settingsPresetRepositoryService.getPresets();
    }

    @RequestMapping(value = "/preset-save", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public SettingsPreset savePreset(@RequestBody String settingsPresetName) {
        if (settingsPresetName == null) {
            return null;
        }

        return settingsPresetRepositoryService.saveAsPreset(settingsPresetName);
    }

    @RequestMapping(value = "/preset-set", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson setPreset(@RequestBody ChangePresetJson changePresetJson) {
        if (changePresetJson == null) {
            return null;
        }
        final boolean isOk = settingsPresetRepositoryService.setPreset(changePresetJson.getPresetName(), changePresetJson.getNoExceptions());
        return new ResultJson(isOk ? "OK" : "exception", "");
    }

    @RequestMapping(value = "/preset-delete", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission(null, 'e_best_min-check')")
    public ResultJson deletePreset(@RequestBody String settingsPresetName) {
        if (settingsPresetName == null) {
            return null;
        }

        final boolean isOk = settingsPresetRepositoryService.deletePreset(settingsPresetName);
        return new ResultJson(isOk ? "OK" : "exception", "");
    }

}
