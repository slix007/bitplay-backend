package com.bitplay.persistance.domain.settings;

import lombok.Data;

/**
 * Created by Sergey Shurmin on 9/1/20.
 */
@Data
public class SettingsTimestamps {

    private Long L_Acceptable_OB_Timestamp_Diff_ms;
    private Long R_Acceptable_OB_Timestamp_Diff_ms;
    private Long L_Acceptable_Get_OB_Delay_ms;
    private Long R_Acceptable_Get_OB_Delay_ms;

    public static SettingsTimestamps createDefault() {
        final SettingsTimestamps s = new SettingsTimestamps();
        s.L_Acceptable_Get_OB_Delay_ms = 0L;
        s.R_Acceptable_Get_OB_Delay_ms = 0L;
        s.L_Acceptable_OB_Timestamp_Diff_ms = 0L;
        s.R_Acceptable_OB_Timestamp_Diff_ms = 0L;
        return s;
    }
}
