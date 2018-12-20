package com.bitplay.persistance.domain;

import com.bitplay.persistance.SignalTimeService;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection = "deltaParamsCollection")
@TypeAlias("signalTime")
@Getter
@Setter
public class SignalTimeParams extends AbstractDocument {

    private String name;
    private BigDecimal signalTimeMin;
    private BigDecimal signalTimeMax;
    private BigDecimal avgNum;
    private BigDecimal avgDen;

    private Instant maxLastRise;

    public static SignalTimeParams createDefault() {
        SignalTimeParams params = new SignalTimeParams();
        params.signalTimeMax = BigDecimal.valueOf(-10000);
        params.signalTimeMin = BigDecimal.valueOf(10000);
        params.avgNum = BigDecimal.ZERO;
        params.avgDen = BigDecimal.ZERO;
        return params;
    }

    public SignalTimeParams() {
    }

    public BigDecimal getSignalTimeAvg() {
        return avgDen == null || avgDen.signum() == 0
                ? SignalTimeService.DEFAULT_VALUE
                : avgNum.divide(avgDen, 2, BigDecimal.ROUND_HALF_UP);
    }

    public void setSignalTimeMax(BigDecimal signalTimeMax) {
        if (this.signalTimeMax.compareTo(signalTimeMax) < 0) {
            maxLastRise = Instant.now();
        }
        this.signalTimeMax = signalTimeMax;
    }
}
