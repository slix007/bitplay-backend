package com.bitplay.api.dto;

import lombok.Data;

@Data
public class ChangePresetJson {

    private String presetName;
    private Boolean noExceptions;
}
