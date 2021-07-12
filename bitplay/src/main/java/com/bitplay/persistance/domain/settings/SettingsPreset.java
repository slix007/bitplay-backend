package com.bitplay.persistance.domain.settings;

import com.bitplay.persistance.domain.AbstractDocument;
import com.bitplay.persistance.domain.GuiParams;
import com.bitplay.persistance.domain.LastPriceDeviation;
import com.bitplay.persistance.domain.SwapParams;
import com.bitplay.persistance.domain.borders.BorderParams;
import com.bitplay.persistance.domain.correction.CorrParams;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "settingsPresetCollection")
@TypeAlias("settingsPreset")
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@ToString
public class SettingsPreset extends AbstractDocument {

    @Indexed(unique = true)
    private String name;
    private Settings settings;
    private BorderParams borderParams;
    private CorrParams corrParams; // min/max values
    private GuiParams guiParams;
    private LastPriceDeviation lastPriceDeviation;
    private SwapParams swapParams;


}
