package com.bitplay.persistance.domain.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.data.annotation.Transient;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class BitmexChangeOnSo {

    private Boolean toConBo;
    private Boolean adjToTaker;
    private Boolean signalTo;
    private PlacingType signalPlacingType; //TAKER or TAKER_FOK
    private Integer countToActivate;
    private Integer durationSec;

    @Transient
    private Long secToReset;

    @Transient
    private Boolean resetFromUi;

    @Transient
    private Boolean testingSo;

    public boolean getAuto() {
        return adjToTaker != null && toConBo != null && signalTo != null &&
                (adjToTaker || toConBo || signalTo);
    }

}
