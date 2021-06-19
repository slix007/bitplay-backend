package com.bitplay.persistance.domain.settings;

import lombok.Data;

@Data
public class BtmAvgPriceUpdateSettings {

    private Integer updateAttempts;
    private Integer updateDelayMs;

    public static BtmAvgPriceUpdateSettings createDefault() {
        final BtmAvgPriceUpdateSettings s = new BtmAvgPriceUpdateSettings();
        s.updateAttempts = 1;
        s.updateDelayMs = 1000;
        return s;
    }

}
