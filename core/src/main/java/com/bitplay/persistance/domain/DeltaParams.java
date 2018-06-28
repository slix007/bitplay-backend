package com.bitplay.persistance.domain;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

/**
 * Created by Sergey Shurmin on 7/18/17.
 */
@Document(collection = "deltaParamsCollection")
@TypeAlias("deltaParams")
@Getter
@Setter
public class DeltaParams extends AbstractDocument {
    private BigDecimal bDeltaMin = BigDecimal.valueOf(-10000);
    private BigDecimal oDeltaMin = BigDecimal.valueOf(-10000);
    private BigDecimal bDeltaMax = BigDecimal.valueOf(10000);
    private BigDecimal oDeltaMax = BigDecimal.valueOf(10000);

    private Instant bLastRise;
    private Instant oLastRise;

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
