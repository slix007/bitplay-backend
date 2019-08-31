package com.bitplay.persistance.domain.settings;

import java.time.LocalTime;
import lombok.Data;
import org.springframework.data.annotation.Transient;

@Data
public class OkexSettlement {

    private Boolean active;
    @Transient
    private String startAtTimeStr;
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

    public void fillStartAtTime() {
        this.startAtTime = LocalTime.parse(this.startAtTimeStr); //"10:15:30"
    }
}
