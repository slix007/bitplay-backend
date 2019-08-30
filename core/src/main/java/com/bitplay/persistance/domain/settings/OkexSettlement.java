package com.bitplay.persistance.domain.settings;

import java.time.LocalTime;
import lombok.Data;

@Data
public class OkexSettlement {

    private Boolean active;
    private LocalTime startAtTime;
    private Integer period;

    public static OkexSettlement createDefault() {
        return new OkexSettlement();
    }

    public boolean isActive() {
        return isInited() && active;
    }

    private boolean isInited() {
        return active != null && startAtTime != null && period != null;
    }
}
