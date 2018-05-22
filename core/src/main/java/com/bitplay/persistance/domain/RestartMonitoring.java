package com.bitplay.persistance.domain;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection = "restartMonitoringCollection")
@TypeAlias("restartMonitoring")
@Setter
@Getter
@ToString
public class RestartMonitoring extends AbstractDocument {

    private BigDecimal bTimestampDelayMax;
    private BigDecimal oTimestampDelayMax;

    public RestartMonitoring() {
    }

    public void addBTimestampDelay(BigDecimal val) {
        if (val.compareTo(bTimestampDelayMax) > 0) {
            bTimestampDelayMax = val;
        }
    }

    public void addOTimestampDelay(BigDecimal val) {
        if (val.compareTo(oTimestampDelayMax) > 0) {
            oTimestampDelayMax = val;
        }
    }

    public static RestartMonitoring createDefaults() {
        RestartMonitoring restartMonitoring = new RestartMonitoring();
        restartMonitoring.bTimestampDelayMax = BigDecimal.ZERO;
        restartMonitoring.oTimestampDelayMax = BigDecimal.ZERO;
        return restartMonitoring;
    }
}
