package com.bitplay.api.domain;

import lombok.Data;

@Data
public class ChangePresetJson {

    private String presetName;
    private Boolean noExceptions;
}
