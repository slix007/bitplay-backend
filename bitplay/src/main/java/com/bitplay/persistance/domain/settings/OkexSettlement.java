package com.bitplay.persistance.domain.settings;

import java.time.LocalTime;
import lombok.Data;
import org.springframework.data.annotation.Transient;

@Data
public class OkexSettlement {

    private Boolean active;
    private String startAtTimeStr;
    @Transient
    private String nowMomentStr;
    private Integer period;

    public static OkexSettlement createDefault() {
        return new OkexSettlement();
    }

    public boolean isActive() {
        return isInited() && active;
    }

    private boolean isInited() {
        return active != null && startAtTimeStr != null && period != null && getStartAtTime() != null;
    }

    public void setStartAtTime(String str) {
        this.startAtTimeStr = str;
    }

    public LocalTime getStartAtTime() {
        LocalTime parse = null;
        try {
            parse = LocalTime.parse(this.startAtTimeStr);
        } catch (Exception e) {
            // skip error
        }
        return parse; //"10:15:30"
    }

    public String getNowMomentStr() {
        return LocalTime.now().toString();
    }
}
