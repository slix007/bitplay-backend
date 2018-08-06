package com.bitplay.persistance.domain;

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
@TypeAlias("deltaParams")
@Getter
@Setter
public class DeltaParams extends AbstractDocument {

    private String name;
    private BigDecimal bDeltaMin;
    private BigDecimal oDeltaMin;
    private BigDecimal bDeltaMax;
    private BigDecimal oDeltaMax;

    private Instant bLastRise;
    private Instant oLastRise;

    public static DeltaParams createDefault() {
        DeltaParams deltaParams = new DeltaParams();
        deltaParams.bDeltaMax = BigDecimal.valueOf(-10000);
        deltaParams.oDeltaMax = BigDecimal.valueOf(-10000);
        deltaParams.bDeltaMin = BigDecimal.valueOf(10000);
        deltaParams.oDeltaMin = BigDecimal.valueOf(10000);
        return deltaParams;
    }

    public DeltaParams() {
    }

    public void setBDeltaMax(BigDecimal bDeltaMax) {
        if (this.bDeltaMax.compareTo(bDeltaMax) < 0) {
            bLastRise = Instant.now();
        }
        this.bDeltaMax = bDeltaMax;
    }

    public void setODeltaMax(BigDecimal oDeltaMax) {
        if (this.oDeltaMax.compareTo(oDeltaMax) < 0) {
            oLastRise = Instant.now();
        }
        this.oDeltaMax = oDeltaMax;
    }
}
