package com.bitplay.persistance.domain.settings;

import lombok.Data;
import org.springframework.data.annotation.Transient;

@Data
public class BitmexChangeOnSo {

    private Boolean auto;
    private Integer countToActivate;
    private Integer durationSec;

    @Transient
    private String secToReset;

    @Transient
    private Boolean resetFromUi;

}
