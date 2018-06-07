package com.bitplay.persistance.domain.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@ToString
public class RestartSettings {

    /**
     * Max gap between current time and oldest item timestamp in orderBook. When the gap overcome this value, then restart should be done.
     */
    private Integer maxTimestampDelay;


    public static RestartSettings createDefaults() {
        RestartSettings restartSettings = new RestartSettings();
        restartSettings.setMaxTimestampDelay(60);
        return restartSettings;
    }


}
